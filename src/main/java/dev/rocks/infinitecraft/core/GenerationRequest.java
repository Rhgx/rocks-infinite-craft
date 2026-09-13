package dev.rocks.infinitecraft.core;

import java.util.List;

public record GenerationRequest(String first, String second, List<CatalogEntry> candidates, List<String> supportedTraits,
        List<List<String>> ingredientEffects, int maxOutputCount, int power, int silliness, java.util.Map<String, List<String>> supportedPotions, int maxTraits, List<String> inheritedTraits, int rarityQuality, int dataPriority) {
    public GenerationRequest(String first, String second, List<CatalogEntry> candidates, List<String> supportedTraits,
            List<List<String>> ingredientEffects, int maxOutputCount, int power, int silliness,
            java.util.Map<String, List<String>> supportedPotions, int maxTraits, List<String> inheritedTraits, int rarityQuality) {
        this(first, second, candidates, supportedTraits, ingredientEffects, maxOutputCount, power, silliness,
                supportedPotions, maxTraits, inheritedTraits, rarityQuality, 0);
    }
    public GenerationRequest(String first, String second, List<CatalogEntry> candidates, List<String> supportedTraits,
            List<List<String>> ingredientEffects, int maxOutputCount, int power, int silliness,
            java.util.Map<String, List<String>> supportedPotions, int maxTraits, List<String> inheritedTraits) {
        this(first, second, candidates, supportedTraits, ingredientEffects, maxOutputCount, power, silliness,
                supportedPotions, maxTraits, inheritedTraits, 0);
    }
    public GenerationRequest(String first, String second, List<CatalogEntry> candidates, List<String> supportedTraits,
            List<List<String>> ingredientEffects, int maxOutputCount, int power, int silliness,
            java.util.Map<String, List<String>> supportedPotions, int maxTraits) {
        this(first, second, candidates, supportedTraits, ingredientEffects, maxOutputCount, power, silliness, supportedPotions, maxTraits, List.of());
    }
    public GenerationRequest(String first, String second, List<CatalogEntry> candidates, List<String> supportedTraits,
            List<List<String>> ingredientEffects, int maxOutputCount, int power, int silliness,
            java.util.Map<String, List<String>> supportedPotions) {
        this(first, second, candidates, supportedTraits, ingredientEffects, maxOutputCount, power, silliness, supportedPotions, 3);
    }
    public GenerationRequest(String first, String second, List<CatalogEntry> candidates, List<String> supportedTraits,
            List<List<String>> ingredientEffects, int maxOutputCount, int power, int silliness) {
        this(first, second, candidates, supportedTraits, ingredientEffects, maxOutputCount, power, silliness, java.util.Map.of());
    }
    public GenerationRequest(String first, String second, List<CatalogEntry> candidates, List<String> supportedTraits,
            List<List<String>> ingredientEffects, int maxOutputCount) {
        this(first, second, candidates, supportedTraits, ingredientEffects, maxOutputCount, 50, 50);
    }
    public GenerationRequest(String first, String second, List<CatalogEntry> candidates, List<String> supportedTraits,
            List<List<String>> ingredientEffects) {
        this(first, second, candidates, supportedTraits, ingredientEffects, 8);
    }
    public GenerationRequest(String first, String second, List<CatalogEntry> candidates, List<String> supportedTraits) {
        this(first, second, candidates, supportedTraits, List.of());
    }
    public GenerationRequest(String first, String second, List<CatalogEntry> candidates) {
        this(first, second, candidates, List.of());
    }

    public GenerationRequest {
        if (dataPriority < 0 || dataPriority > 2) throw new IllegalArgumentException("Invalid data priority");
        if (rarityQuality < 0 || rarityQuality > 100) throw new IllegalArgumentException("Rarity quality must be 0 to 100");
        inheritedTraits = inheritedTraits == null ? List.of() : List.copyOf(inheritedTraits);
        if (maxTraits < 0 || maxTraits > 8) throw new IllegalArgumentException("Trait limit must be 0 to 8");
        if (power < 0 || power > 100 || silliness < 0 || silliness > 100)
            throw new IllegalArgumentException("Generation factors must be 0 to 100");
        if (maxOutputCount < 1 || maxOutputCount > 64) throw new IllegalArgumentException("Output count limit must be 1 to 64");
        supportedPotions = supportedPotions == null ? java.util.Map.of() : supportedPotions.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(java.util.Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        candidates = List.copyOf(candidates);
        supportedTraits = supportedTraits == null ? List.of() : List.copyOf(supportedTraits);
        ingredientEffects = ingredientEffects == null ? List.of() : ingredientEffects.stream().map(List::copyOf).toList();
    }
}
