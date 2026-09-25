package dev.rocks.infinitecraft.discovery;

import dev.rocks.infinitecraft.item.FusionOrigin;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

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
            var names = new LinkedHashSet<String>();
            names.add(discoverer);
            names.addAll(discoverers);
            discoverers = List.copyOf(names);
        }
    }

    private final DiscoveryStore store;
    private final List<Entry> entries = new ArrayList<>();
    private final List<String> encoded = new ArrayList<>();
    private final List<Set<UUID>> players = new ArrayList<>();
    private int revision;
    private final Map<ResultKey, List<Entry>> outputs = new LinkedHashMap<>();
    private final Map<ResultKey, Set<UUID>> favorites = new LinkedHashMap<>();

    /** Immediate-parent lore is presentation metadata, so it does not split one result into several outputs. */
    public record ResultKey(ItemStack stack) {
        public ResultKey {
            stack = FusionOrigin.strip(stack).copyWithCount(1);
            stack.remove(DataComponents.DAMAGE);
            stack.remove(DataComponents.REPAIR_COST);
        }
        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(stack);
        }
        @Override
        public boolean equals(Object other) {
            return other instanceof ResultKey key && ItemStack.isSameItemSameComponents(stack, key.stack);
        }
    }

    private record RecipeKey(ResultKey result, ResultKey first, ResultKey second) {
        @Override
        public int hashCode() {
            return 31 * result.hashCode() + first.hashCode() + second.hashCode();
        }
        @Override
        public boolean equals(Object other) {
            return other instanceof RecipeKey key && result.equals(key.result)
                    && (first.equals(key.first) && second.equals(key.second)
                    || first.equals(key.second) && second.equals(key.first));
        }
    }

    public int outputCount() {
        return outputs.size();
    }

    public int revision() {
        return revision;
    }

    public DiscoveryCollection(Path file, HolderLookup.Provider registries) throws IOException {
        store = new DiscoveryStore(file, registries);
        for (var stored : store.load()) {
            var entry = stored.entry();
            favorites.computeIfAbsent(new ResultKey(entry.result()), ignored -> new HashSet<>()).addAll(stored.favorites());
            players.add(new HashSet<>(stored.owners()));
            entries.add(entry);
            outputs.computeIfAbsent(new ResultKey(entry.result()), ignored -> new ArrayList<>()).add(entry);
            encoded.add(stored.encoded());
        }
        if (deduplicateLoaded()) store.save(snapshot());
    }

    private boolean deduplicateLoaded() {
        var indexes = new LinkedHashMap<RecipeKey, Integer>();
        var uniqueEntries = new ArrayList<Entry>();
        var uniquePlayers = new ArrayList<Set<UUID>>();
        boolean changed = false;
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            var key = new RecipeKey(new ResultKey(entry.result()), new ResultKey(entry.first()), new ResultKey(entry.second()));
            var existingIndex = indexes.get(key);
            if (existingIndex == null) {
                indexes.put(key, uniqueEntries.size());
                uniqueEntries.add(new Entry(entry.first(), entry.second(), entry.result(), entry.discoverer(),
                        uniqueEntries.size() + 1, entry.discoverers()));
                uniquePlayers.add(new HashSet<>(players.get(index)));
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



    public List<Entry> entries() {
        return snapshot(entries);
    }

    private static List<Entry> snapshot(List<Entry> source) {
        var snapshot = List.copyOf(source);
        var copies = new Entry[snapshot.size()];
        // Each view owns its copies; repeated reads within a dialog need no further copying.
        return new AbstractList<>() {
            @Override
            public Entry get(int index) {
                var entry = snapshot.get(index);
                if (copies[index] == null) copies[index] = new Entry(entry.first(), entry.second(), entry.result(),
                        entry.discoverer(), entry.id(), entry.discoverers());
                return copies[index];
            }
            @Override
            public int size() {
                return snapshot.size();
            }
        };
    }

    public boolean record(ItemStack first, ItemStack second, ItemStack output, String discoverer) {
        return record(first, second, output, discoverer, null);
    }

    public boolean owns(int id, UUID player, String name) {
        return id > 0 && id <= entries.size() && (players.get(id - 1).contains(player)
                || entries.get(id - 1).discoverer().equals(name));
    }

    public List<Entry> entries(UUID player, String name) {
        return snapshot(entries.stream().filter(entry -> owns(entry.id(), player, name)).toList());
    }

    public boolean record(ItemStack first, ItemStack second, ItemStack output, String discoverer, UUID player) {
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
        var owners = new HashSet<UUID>();
        if (player != null) owners.add(player);
        players.add(owners);
        encoded.add(encode(entry, owners));
        entries.add(entry);
        outputs.computeIfAbsent(outputKey, ignored -> new ArrayList<>()).add(entry);
        revision++;
        return newOutput;
    }

    public static boolean samePair(ItemStack first, ItemStack second, ItemStack otherFirst, ItemStack otherSecond) {
        var firstKey = new ResultKey(first);
        var secondKey = new ResultKey(second);
        var otherFirstKey = new ResultKey(otherFirst);
        var otherSecondKey = new ResultKey(otherSecond);
        return firstKey.equals(otherFirstKey) && secondKey.equals(otherSecondKey)
                || firstKey.equals(otherSecondKey) && secondKey.equals(otherFirstKey);
    }

    public record InputPair(ItemStack first, ItemStack second) {
    }

    /** Reversed inputs follow the earliest discovered orientation for their component-aware pair. */
    public InputPair normalize(ItemStack first, ItemStack second) {
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

    private String encode(Entry entry, Set<UUID> owners) {
        return store.encode(entry, owners, favorites.getOrDefault(new ResultKey(entry.result()), Set.of()));
    }

    public Set<Integer> favoriteIds(UUID player) {
        var ids = new HashSet<Integer>();
        for (var entry : entries) {
            if (favorites.getOrDefault(new ResultKey(entry.result()), Set.of()).contains(player)) ids.add(entry.id());
        }
        return Set.copyOf(ids);
    }

    public void toggleFavorite(int id, UUID player) {
        if (id < 1 || id > entries.size()) throw new IllegalArgumentException("Unknown discovery");
        var key = new ResultKey(entries.get(id - 1).result());
        var starred = favorites.computeIfAbsent(key, ignored -> new HashSet<>());
        if (!starred.remove(player)) starred.add(player);
        for (var entry : outputs.get(key)) encoded.set(entry.id() - 1, encode(entry, players.get(entry.id() - 1)));
    }

    public static List<Entry> uniqueResults(List<Entry> recipes) {
        return groupResults(recipes).values().stream().map(List::getFirst).toList();
    }

    public static Map<ResultKey, List<Entry>> groupResults(List<Entry> recipes) {
        var groups = new LinkedHashMap<ResultKey, List<Entry>>();
        for (var recipe : recipes)
            groups.computeIfAbsent(new ResultKey(recipe.result()), ignored -> new ArrayList<>()).add(recipe);
        return groups;
    }

    public static List<String> discoverers(List<Entry> recipes) {
        return recipes.stream().flatMap(recipe -> recipe.discoverers().stream()).distinct().toList();
    }

    /** Each entry is encoded once; the export worker writes this immutable snapshot. */
    public List<String> snapshot() {
        return List.copyOf(encoded);
    }

    /** Keep only the latest waiting snapshot while an atomic write is in progress. */
    public void saveAsync(Executor worker, Consumer<IOException> onError) {
        store.saveAsync(snapshot(), worker, onError);
    }

    public void save(List<String> snapshot) throws IOException {
        store.save(snapshot);
    }
}
