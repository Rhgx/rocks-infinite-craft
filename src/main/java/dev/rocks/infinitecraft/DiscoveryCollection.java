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
    public record Entry(ItemStack first, ItemStack second, ItemStack result, String discoverer, int id,
            List<String> discoverers) {
        public Entry(ItemStack first, ItemStack second, ItemStack result, String discoverer) {
            this(first, second, result, discoverer, 0, List.of(discoverer));
        }
        public Entry(ItemStack first, ItemStack second, ItemStack result, String discoverer, int id) {
            this(first, second, result, discoverer, id, List.of(discoverer));
        }
        public Entry {
            first = first.copyWithCount(1);
            second = second.copyWithCount(1);
            result = result.copyWithCount(1);
            var names = new java.util.LinkedHashSet<String>();
            names.add(discoverer);
            names.addAll(discoverers);
            discoverers = List.copyOf(names);
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

    /** Immediate-parent lore is presentation metadata, so it does not split one result into several outputs. */
    public record ResultKey(ItemStack stack) {
        public ResultKey {
            stack = FusionOrigin.strip(stack).copyWithCount(1);
            stack.remove(net.minecraft.core.component.DataComponents.DAMAGE);
            stack.remove(net.minecraft.core.component.DataComponents.REPAIR_COST);
        }
        @Override public int hashCode() { return ItemStack.hashItemAndComponents(stack); }
        @Override public boolean equals(Object other) {
            return other instanceof ResultKey key && ItemStack.isSameItemSameComponents(stack, key.stack);
        }
    }

    private record RecipeKey(ResultKey result, ResultKey first, ResultKey second) {
        @Override public int hashCode() { return 31 * result.hashCode() + first.hashCode() + second.hashCode(); }
        @Override public boolean equals(Object other) {
            return other instanceof RecipeKey key && result.equals(key.result)
                    && (first.equals(key.first) && second.equals(key.second)
                    || first.equals(key.second) && second.equals(key.first));
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
                String discoverer = value.get("discoverer").getAsString();
                var discoverers = new ArrayList<String>();
                if (value.has("discoverers")) value.getAsJsonArray("discoverers")
                        .forEach(name -> discoverers.add(name.getAsString()));
                var entry = new Entry(decode(value, "first"), decode(value, "second"), decode(value, "result"),
                        discoverer, entries.size() + 1, discoverers);
                var owners = new java.util.HashSet<java.util.UUID>();
                if (value.has("players")) value.getAsJsonArray("players").forEach(id -> owners.add(java.util.UUID.fromString(id.getAsString())));
                players.add(owners);
                entries.add(entry);
                outputs.computeIfAbsent(new ResultKey(entry.result()), ignored -> new ArrayList<>()).add(entry);
                encoded.add(value.toString());
            }
            if (deduplicateLoaded()) save(snapshot());
        } catch (RuntimeException invalid) { throw new IOException("Invalid discovery collection; file left unchanged", invalid); }
    }

    private boolean deduplicateLoaded() {
        var indexes = new java.util.LinkedHashMap<RecipeKey, Integer>();
        var uniqueEntries = new ArrayList<Entry>();
        var uniquePlayers = new ArrayList<java.util.Set<java.util.UUID>>();
        boolean changed = false;
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            var key = new RecipeKey(new ResultKey(entry.result()), new ResultKey(entry.first()), new ResultKey(entry.second()));
            var existingIndex = indexes.get(key);
            if (existingIndex == null) {
                indexes.put(key, uniqueEntries.size());
                uniqueEntries.add(new Entry(entry.first(), entry.second(), entry.result(), entry.discoverer(),
                        uniqueEntries.size() + 1, entry.discoverers()));
                uniquePlayers.add(new java.util.HashSet<>(players.get(index)));
                continue;
            }
            var existing = uniqueEntries.get(existingIndex);
            var names = new ArrayList<>(existing.discoverers());
            entry.discoverers().stream().filter(name -> !names.contains(name)).forEach(names::add);
            uniqueEntries.set(existingIndex, new Entry(existing.first(), existing.second(), existing.result(),
                    existing.discoverer(), existing.id(), names));
            uniquePlayers.get(existingIndex).addAll(players.get(index));
            changed = true;
        }
        if (!changed) return false;
        entries.clear();
        entries.addAll(uniqueEntries);
        players.clear();
        players.addAll(uniquePlayers);
        encoded.clear();
        outputs.clear();
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            encoded.add(encode(entry, players.get(index)));
            outputs.computeIfAbsent(new ResultKey(entry.result()), ignored -> new ArrayList<>()).add(entry);
        }
        revision++;
        return true;
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
                if (copies[index] == null) copies[index] = new Entry(entry.first(), entry.second(), entry.result(),
                        entry.discoverer(), entry.id(), entry.discoverers());
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
            boolean samePair = samePair(existing.first(), existing.second(), first, second);
            if (!samePair) continue;
            if (player != null && (players.get(i).add(player) || !existing.discoverers().contains(discoverer))) {
                var names = new ArrayList<>(existing.discoverers());
                if (!names.contains(discoverer)) names.add(discoverer);
                var updated = new Entry(existing.first(), existing.second(), existing.result(), existing.discoverer(),
                        existing.id(), names);
                entries.set(i, updated);
                var recipes = outputs.get(outputKey);
                recipes.set(recipes.indexOf(existing), updated);
                encoded.set(i, encode(updated, players.get(i)));
                revision++;
            }
            return false;
        }
        var entry = new Entry(first, second, output, discoverer, entries.size() + 1);
        var owners = new java.util.HashSet<java.util.UUID>();
        if (player != null) owners.add(player);
        players.add(owners);
        encoded.add(encode(entry, owners));
        entries.add(entry);
        outputs.computeIfAbsent(outputKey, ignored -> new ArrayList<>()).add(entry);
        revision++;
        return newOutput;
    }

    static boolean samePair(ItemStack first, ItemStack second, ItemStack otherFirst, ItemStack otherSecond) {
        var firstKey = new ResultKey(first);
        var secondKey = new ResultKey(second);
        var otherFirstKey = new ResultKey(otherFirst);
        var otherSecondKey = new ResultKey(otherSecond);
        return firstKey.equals(otherFirstKey) && secondKey.equals(otherSecondKey)
                || firstKey.equals(otherSecondKey) && secondKey.equals(otherFirstKey);
    }

    record InputPair(ItemStack first, ItemStack second) {}

    /** Reversed inputs follow the earliest discovered orientation for their component-aware pair. */
    InputPair normalize(ItemStack first, ItemStack second) {
        var firstKey = new ResultKey(first);
        var secondKey = new ResultKey(second);
        for (var entry : entries) {
            var storedFirst = new ResultKey(entry.first());
            var storedSecond = new ResultKey(entry.second());
            if (storedFirst.equals(firstKey) && storedSecond.equals(secondKey)) return new InputPair(first, second);
            if (storedFirst.equals(secondKey) && storedSecond.equals(firstKey)) return new InputPair(second, first);
        }
        return new InputPair(first, second);
    }

    private String encode(Entry entry, java.util.Set<java.util.UUID> owners) {
        var value = new JsonObject();
        value.add("first", ItemStack.CODEC.encodeStart(ops, entry.first()).getOrThrow());
        value.add("second", ItemStack.CODEC.encodeStart(ops, entry.second()).getOrThrow());
        value.add("result", ItemStack.CODEC.encodeStart(ops, entry.result()).getOrThrow());
        value.addProperty("discoverer", entry.discoverer());
        var names = new com.google.gson.JsonArray();
        entry.discoverers().forEach(names::add);
        value.add("discoverers", names);
        var ids = new com.google.gson.JsonArray();
        owners.stream().map(java.util.UUID::toString).sorted().forEach(ids::add);
        value.add("players", ids);
        return value.toString();
    }

    public static List<Entry> uniqueResults(List<Entry> recipes) {
        return groupResults(recipes).values().stream().map(List::getFirst).toList();
    }

    public static java.util.Map<ResultKey, List<Entry>> groupResults(List<Entry> recipes) {
        var groups = new java.util.LinkedHashMap<ResultKey, List<Entry>>();
        for (var recipe : recipes)
            groups.computeIfAbsent(new ResultKey(recipe.result()), ignored -> new ArrayList<>()).add(recipe);
        return groups;
    }

    public static List<String> discoverers(List<Entry> recipes) {
        return recipes.stream().flatMap(recipe -> recipe.discoverers().stream()).distinct().toList();
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
