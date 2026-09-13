package dev.rocks.infinitecraft;

import java.util.AbstractList;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import com.google.gson.JsonElement;
import net.minecraft.world.item.ItemStack;

/** World discoveries contain the actual successful output, not an unvalidated model proposal. */
public final class DiscoveryCollection {
    public record Entry(ItemStack first, ItemStack second, ItemStack result, String discoverer, int id) {
        public Entry(ItemStack first, ItemStack second, ItemStack result, String discoverer) {
            this(first, second, result, discoverer, 0);
        }
        public Entry {
            first = first.copyWithCount(1);
            second = second.copyWithCount(1);
            result = result.copyWithCount(1);
        }
    }

    private final Path file;
    private final RegistryOps<JsonElement> ops;
    private final List<Entry> entries = new ArrayList<>();
    private final List<String> encoded = new ArrayList<>();
    private final List<java.util.Set<java.util.UUID>> players = new ArrayList<>();
    private int revision;
    private List<String> pendingSave;
    private boolean saving;
    private final java.util.Map<ResultKey, List<Entry>> outputs = new java.util.LinkedHashMap<>();

    /** Counts do not distinguish recipes; all item components do. Keys own their stack. */
    record ResultKey(ItemStack stack) {
        ResultKey { stack = stack.copyWithCount(1); }
        @Override public int hashCode() { return ItemStack.hashItemAndComponents(stack); }
        @Override public boolean equals(Object other) {
            return other instanceof ResultKey key && ItemStack.isSameItemSameComponents(stack, key.stack);
        }
    }

    public int outputCount() { return outputs.size(); }

    public int revision() { return revision; }

    public DiscoveryCollection(Path file, HolderLookup.Provider registries) throws IOException {
        this.file = file;
        ops = registries.createSerializationContext(JsonOps.INSTANCE);
        if (!Files.exists(file)) return;
        try {
            for (var element : JsonParser.parseString(Files.readString(file)).getAsJsonArray()) {
                var value = element.getAsJsonObject();
                var entry = new Entry(decode(value, "first"), decode(value, "second"), decode(value, "result"),
                        value.get("discoverer").getAsString(), entries.size() + 1);
                var owners = new java.util.HashSet<java.util.UUID>();
                if (value.has("players")) value.getAsJsonArray("players").forEach(id -> owners.add(java.util.UUID.fromString(id.getAsString())));
                players.add(owners);
                entries.add(entry);
                outputs.computeIfAbsent(new ResultKey(entry.result()), ignored -> new ArrayList<>()).add(entry);
                encoded.add(value.toString());
            }
        } catch (RuntimeException invalid) { throw new IOException("Invalid discovery collection; file left unchanged", invalid); }
    }

    private ItemStack decode(JsonObject value, String key) {
        return ItemStack.CODEC.parse(ops, value.get(key)).getOrThrow();
    }

    public List<Entry> entries() {
        return snapshot(entries);
    }

    private static List<Entry> snapshot(List<Entry> source) {
        var snapshot = List.copyOf(source);
        var copies = new Entry[snapshot.size()];
        // Each view owns its copies; repeated reads within a dialog need no further copying.
        return new AbstractList<>() {
            @Override public Entry get(int index) {
                var entry = snapshot.get(index);
                if (copies[index] == null) copies[index] = new Entry(entry.first(), entry.second(), entry.result(), entry.discoverer(), entry.id());
                return copies[index];
            }
            @Override public int size() { return snapshot.size(); }
        };
    }

    public boolean record(ItemStack first, ItemStack second, ItemStack output, String discoverer) {
        return record(first, second, output, discoverer, null);
    }

    public boolean owns(int id, java.util.UUID player, String name) {
        return id > 0 && id <= entries.size() && (players.get(id - 1).contains(player)
                || entries.get(id - 1).discoverer().equals(name));
    }

    public List<Entry> entries(java.util.UUID player, String name) {
        return snapshot(entries.stream().filter(entry -> owns(entry.id(), player, name)).toList());
    }

    public boolean record(ItemStack first, ItemStack second, ItemStack output, String discoverer, java.util.UUID player) {
        var outputKey = new ResultKey(output);
        boolean newOutput = !outputs.containsKey(outputKey);
        for (var existing : outputs.getOrDefault(outputKey, List.of())) {
            int i = existing.id() - 1;
            boolean samePair = ItemStack.isSameItemSameComponents(existing.first(), first)
                    && ItemStack.isSameItemSameComponents(existing.second(), second)
                    || ItemStack.isSameItemSameComponents(existing.first(), second)
                    && ItemStack.isSameItemSameComponents(existing.second(), first);
            if (!samePair) continue;
            if (player != null && players.get(i).add(player)) {
                var value = JsonParser.parseString(encoded.get(i)).getAsJsonObject();
                var owners = new com.google.gson.JsonArray();
                players.get(i).stream().map(java.util.UUID::toString).sorted().forEach(owners::add);
                value.add("players", owners);
                encoded.set(i, value.toString());
                revision++;
            }
            return false;
        }
        var entry = new Entry(first, second, output, discoverer, entries.size() + 1);
        var value = new JsonObject();
        value.add("first", ItemStack.CODEC.encodeStart(ops, entry.first()).getOrThrow());
        value.add("second", ItemStack.CODEC.encodeStart(ops, entry.second()).getOrThrow());
        value.add("result", ItemStack.CODEC.encodeStart(ops, entry.result()).getOrThrow());
        value.addProperty("discoverer", discoverer);
        var owners = new java.util.HashSet<java.util.UUID>();
        if (player != null) owners.add(player);
        players.add(owners);
        var ids = new com.google.gson.JsonArray();
        owners.forEach(id -> ids.add(id.toString()));
        value.add("players", ids);
        encoded.add(value.toString());
        entries.add(entry);
        outputs.computeIfAbsent(outputKey, ignored -> new ArrayList<>()).add(entry);
        revision++;
        return newOutput;
    }

    static List<Entry> uniqueResults(List<Entry> recipes) {
        return groupResults(recipes).values().stream().map(List::getFirst).toList();
    }

    static java.util.Map<ResultKey, List<Entry>> groupResults(List<Entry> recipes) {
        var groups = new java.util.LinkedHashMap<ResultKey, List<Entry>>();
        for (var recipe : recipes)
            groups.computeIfAbsent(new ResultKey(recipe.result()), ignored -> new ArrayList<>()).add(recipe);
        return groups;
    }

    /** Each entry is encoded once; the export worker writes this immutable snapshot. */
    public List<String> snapshot() { return List.copyOf(encoded); }

    /** Keep only the latest waiting snapshot while an atomic write is in progress. */
    public synchronized void saveAsync(java.util.concurrent.Executor worker, java.util.function.Consumer<IOException> onError) {
        pendingSave = snapshot();
        if (saving) return;
        saving = true;
        try {
            worker.execute(() -> {
                while (true) {
                    List<String> next;
                    synchronized (this) {
                        next = pendingSave;
                        pendingSave = null;
                        if (next == null) { saving = false; return; }
                    }
                    try { save(next); }
                    catch (IOException error) { onError.accept(error); }
                }
            });
        } catch (RuntimeException error) { saving = false; throw error; }
    }

    public void save(List<String> snapshot) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "discoveries-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary)) {
                writer.write('[');
                for (int i = 0; i < snapshot.size(); i++) {
                    if (i > 0) writer.write(',');
                    writer.write(snapshot.get(i));
                }
                writer.write(']');
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
