package dev.rocks.infinitecraft.traits;

import dev.rocks.infinitecraft.InfiniteCraftMod;

import java.util.Set;
import java.util.stream.Collectors;

/** Live trigger settings come from the host, never from a joining client's configuration. */
public final class TraitSettings {
    public static final Set<String> CHANCE_TRAITS = TraitRegistry.definitions().stream()
            .filter(trait -> trait.triggerChance() >= 0).map(TraitDefinition::id)
            .collect(Collectors.toUnmodifiableSet());

    private TraitSettings() {}

    public static int defaultChance(String id) {
        var trait = TraitRegistry.get(id);
        return trait == null || trait.triggerChance() < 0 ? 100 : Math.round(trait.triggerChance() * 100);
    }

    public static float chance(String id) {
        var runtime = InfiniteCraftMod.runtime();
        if (runtime == null) return defaultChance(id) / 100F;
        if (runtime.settings().disabledTraits.contains(id)) return 0;
        return runtime.settings().traitChances.getOrDefault(id, defaultChance(id)) / 100F;
    }
}
