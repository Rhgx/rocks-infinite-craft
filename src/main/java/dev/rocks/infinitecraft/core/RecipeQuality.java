package dev.rocks.infinitecraft.core;

import java.util.HashMap;

/** Rarity raises the floor within the host's power budget, without adding traits. */
public final class RecipeQuality {
    private RecipeQuality() {}

    public static int fromRarities(int first, int second) {
        if (first < 0 || first > 3 || second < 0 || second > 3)
            throw new IllegalArgumentException("Rarity rank must be 0 to 3");
        return Math.min(100, Math.max(first, second) * 25 + Math.min(first, second) * 5);
    }

    public static RecipeResult apply(RecipeResult result, GenerationRequest request) {
        if (request.rarityQuality() == 0 || request.supportedTraits().isEmpty()) return result;
        double ceiling = request.power() / 100.0;
        double floor = request.rarityQuality() / 100.0 * ceiling;
        var strengths = new HashMap<>(result.strengths());
        for (var trait : result.traits()) {
            var range = TraitStrengths.RANGES.get(trait);
            if (range == null || range.maximum() == range.minimum()) continue;
            double defaultStrength = (range.defaultValue() - range.minimum()) / (range.maximum() - range.minimum());
            strengths.put(trait, Math.clamp(strengths.getOrDefault(trait, defaultStrength), floor, ceiling));
        }
        return new RecipeResult(result.itemId(), result.count(), result.name(), result.traits(), strengths,
                result.activations(), result.nameStyle(), result.potion(), result.dyeColor(), result.itemModel(), result.nameParts());
    }
}
