package dev.rocks.infinitecraft.core;

import dev.rocks.infinitecraft.traits.StrengthRange;
import dev.rocks.infinitecraft.traits.TraitRegistry;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Model values are normalized; only the host defines the actual component ranges. */
public final class TraitStrengths {
    public static final Map<String, StrengthRange> RANGES = TraitRegistry.definitions().stream()
            .filter(trait -> trait.range() != null)
            .collect(Collectors.toUnmodifiableMap(trait -> trait.id(), trait -> trait.range()));

    private TraitStrengths() {
    }

    public static void validate(List<String> traits, Map<String, Double> strengths) {
        strengths.forEach((trait, value) -> {
            if (!traits.contains(trait) || !RANGES.containsKey(trait) || value == null
                    || !Double.isFinite(value) || value < 0 || value > 1) {
                throw new IllegalArgumentException("Trait strength must be between 0 and 1 for a selected adjustable trait");
            }
        });
    }

    public static double value(String trait, Map<String, Double> strengths) {
        StrengthRange range = RANGES.get(trait);
        Double normalized = strengths.get(trait);
        if (normalized == null) {
            return range.defaultValue();
        }
        return range.minimum() + normalized * (range.maximum() - range.minimum());
    }
}
