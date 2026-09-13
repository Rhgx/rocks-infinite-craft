package dev.rocks.infinitecraft.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rocks.infinitecraft.core.RecipeResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

/** World-local discoveries. A rejected file is never replaced with an empty store. */
public final class RecipeStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private volatile Map<String, RecipeResult> recipes = Map.of();
    private volatile Set<String> blocked = Set.of();

    public RecipeStore(Path file) throws IOException {
        this.file = file.toAbsolutePath();
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (!root.has("schemaVersion") || !root.get("schemaVersion").getAsString().equals("1")) {
                throw new IOException("Unsupported recipe schema in " + file + "; file left unchanged");
            }
            JsonObject entries = root.getAsJsonObject("recipes");
            if (entries == null) throw new IllegalArgumentException("Missing recipes object");
            Map<String, RecipeResult> loaded = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                JsonObject value = entry.getValue().getAsJsonObject();
                String item = value.get("itemId").getAsString();
                String rawCount = value.get("count").getAsString();
                int count = Integer.parseInt(rawCount);
                if (entry.getKey().isBlank() || !validItemId(item) || count < 1 || count > 64) {
                    throw new IllegalArgumentException("Invalid recipe entry " + entry.getKey());
                }
                loaded.put(entry.getKey(), GSON.fromJson(value, RecipeResult.class));
            }
            recipes = Map.copyOf(loaded);
            if (root.has("blocked")) {
                Set<String> loadedBlocks = new HashSet<>();
                for (JsonElement entry : root.getAsJsonArray("blocked")) {
                    if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString() || entry.getAsString().isBlank())
                        throw new IllegalArgumentException("Invalid blocked recipe");
                    loadedBlocks.add(entry.getAsString());
                }
                blocked = Set.copyOf(loadedBlocks);
            }
        } catch (RuntimeException exception) {
            throw new IOException("Invalid recipe file " + file + "; file left unchanged", exception);
        }
    }

    public Optional<RecipeResult> get(String key) {
        return Optional.ofNullable(recipes.get(key));
    }

    public boolean isBlocked(String key) { return blocked.contains(key); }

    public synchronized void put(String key, RecipeResult result) throws IOException {
        put(key, result, true);
    }

    private void put(String key, RecipeResult result, boolean clearVariants) throws IOException {
        if (key == null || key.isBlank() || result == null || !validItemId(result.itemId())
                || result.count() < 1 || result.count() > 64) {
            throw new IllegalArgumentException("Invalid recipe");
        }
        Map<String, RecipeResult> next = new HashMap<>(recipes);
        if (clearVariants) next.keySet().removeIf(saved -> saved.startsWith(key + "#"));
        next.put(key, result);
        Set<String> nextBlocked = new HashSet<>(blocked);
        nextBlocked.removeIf(block -> block.equals(key) || clearVariants && block.startsWith(key + "#"));
        write(next, nextBlocked);
    }

    public synchronized void remove(String key) throws IOException {
        Map<String, RecipeResult> next = new HashMap<>(recipes);
        next.keySet().removeIf(saved -> saved.equals(key) || saved.startsWith(key + "#"));
        Set<String> nextBlocked = new HashSet<>(blocked);
        nextBlocked.removeIf(block -> block.equals(key) || block.startsWith(key + "#"));
        write(next, nextBlocked);
    }

    synchronized boolean blockIf(String key, java.util.function.BooleanSupplier active) throws IOException {
        if (!active.getAsBoolean()) return false;
        Set<String> nextBlocked = new HashSet<>(blocked);
        nextBlocked.add(key);
        write(recipes, nextBlocked);
        return true;
    }

    synchronized boolean updateIf(String key, RecipeResult result, java.util.function.BooleanSupplier active) throws IOException {
        return updateIf(key, result, active, false);
    }

    synchronized boolean updateIf(String key, RecipeResult result, java.util.function.BooleanSupplier active, boolean clearVariants) throws IOException {
        // Check after acquiring the writer lock: an old engine must not save after a replacement's edit.
        if (!active.getAsBoolean()) return false;
        if (result == null) remove(key); else put(key, result, clearVariants);
        return true;
    }

    private void write(Map<String, RecipeResult> next, Set<String> nextBlocked) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.add("recipes", GSON.toJsonTree(next));
        root.add("blocked", GSON.toJsonTree(nextBlocked));
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "recipes-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            // Fail closed when the filesystem cannot atomically replace the save.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            recipes = Map.copyOf(next);
            blocked = Set.copyOf(nextBlocked);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean validItemId(String id) {
        return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+");
    }
}
