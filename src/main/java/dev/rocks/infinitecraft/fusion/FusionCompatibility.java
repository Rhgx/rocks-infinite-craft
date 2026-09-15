package dev.rocks.infinitecraft.fusion;

import dev.rocks.infinitecraft.core.RecipeResult;
import dev.rocks.infinitecraft.discovery.DiscoveryCollection;
import dev.rocks.infinitecraft.item.ItemDataFusion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Caches which result items can safely retain a component-aware ingredient pair. */
final class FusionCompatibility {
    private static final int CACHE_LIMIT = 128;

    private record Key(DiscoveryCollection.ResultKey first, DiscoveryCollection.ResultKey second) {
    }

    private final Map<Key, Set<String>> cache = new LinkedHashMap<>();

    Set<String> outputs(ItemStack first, ItemStack second, RecipeResult saved, Set<String> allowed) {
        if (saved != null) {
            return allowed.contains(saved.itemId()) && accepts(saved.itemId(), first, second)
                    ? Set.of(saved.itemId())
                    : Set.of();
        }

        var key = new Key(new DiscoveryCollection.ResultKey(first), new DiscoveryCollection.ResultKey(second));
        var cached = cache.get(key);
        if (cached != null) return cached;

        var compatible = allowed.stream()
                .filter(id -> accepts(id, first, second))
                .collect(Collectors.toUnmodifiableSet());
        if (cache.size() >= CACHE_LIMIT) cache.remove(cache.keySet().iterator().next());
        cache.put(key, compatible);
        return compatible;
    }

    boolean accepts(String id, ItemStack first, ItemStack second) {
        ItemStack output = BuiltInRegistries.ITEM.getValue(Identifier.parse(id)).getDefaultInstance();
        return !ItemDataFusion.prepare(output, first, second, false).isEmpty();
    }

    void clear() {
        cache.clear();
    }
}
