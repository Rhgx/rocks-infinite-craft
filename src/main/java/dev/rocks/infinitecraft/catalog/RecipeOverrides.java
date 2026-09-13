package dev.rocks.infinitecraft.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rocks.infinitecraft.core.CatalogEntry;
import dev.rocks.infinitecraft.core.PairKey;
import dev.rocks.infinitecraft.core.RecipeResult;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class RecipeOverrides {
    private RecipeOverrides() {}

    /** Load into a temporary map. The caller must retain its active map when this throws. */
    public static Map<String, RecipeResult> load(MinecraftServer server, List<CatalogEntry> catalog)
            throws IOException {
        Map<String, RecipeResult> loaded = new HashMap<>();
        var resources = server.getResourceManager().listResources("infinitecraft/recipes",
                id -> id.getPath().endsWith(".json"));
        for (var resource : resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            try (Reader reader = resource.getValue().openAsReader()) {
                var recipe = parse(JsonParser.parseReader(reader), catalog);
                if (loaded.putIfAbsent(recipe.getKey(), recipe.getValue()) != null) {
                    throw new IllegalArgumentException("Multiple files define the same input pair");
                }
            } catch (RuntimeException exception) {
                throw new IOException("Invalid Infinite Craft recipe " + resource.getKey() + ": "
                        + exception.getMessage(), exception);
            }
        }
        return Map.copyOf(loaded);
    }

    public static Map.Entry<String, RecipeResult> parse(JsonElement json, List<CatalogEntry> catalog) {
        if (!json.isJsonObject()) throw new IllegalArgumentException("Recipe must be an object");
        JsonObject object = json.getAsJsonObject();
        Set<String> fields = Set.of("first", "second", "result", "count");
        if (!fields.containsAll(object.keySet())) throw new IllegalArgumentException("Unknown recipe field");
        String first = identifier(object, "first");
        String second = identifier(object, "second");
        String result = identifier(object, "result");
        int count = 1;
        if (object.has("count")) {
            JsonElement value = object.get("count");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("count must be an integer");
            }
            try {
                count = value.getAsBigDecimal().intValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("count must be an integer", exception);
            }
        }
        if (count < 1 || count > 64) throw new IllegalArgumentException("count must be between 1 and 64");
        Map<String, CatalogEntry> items = catalog.stream().filter(entry -> entry.kind().equals("item"))
                .collect(Collectors.toMap(CatalogEntry::id, entry -> entry));
        if (!items.containsKey(first) || !items.containsKey(second)) {
            throw new IllegalArgumentException("Ingredient is not a registered item");
        }
        CatalogEntry output = items.get(result);
        if (output == null || !output.craftable()) throw new IllegalArgumentException("Result is not an allowed item");
        return Map.entry(PairKey.of(first, second), new RecipeResult(result, count));
    }

    private static String identifier(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || !value.getAsString().matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException(key + " must be a full namespaced item ID");
        }
        return value.getAsString();
    }
}
