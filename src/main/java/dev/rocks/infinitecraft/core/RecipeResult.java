package dev.rocks.infinitecraft.core;

import dev.rocks.infinitecraft.traits.TraitRegistry;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record RecipeResult(
        String itemId,
        int count,
        String name,
        List<String> traits,
        Map<String, Double> strengths,
        Map<String, String> activations,
        NameStyle nameStyle,
        String potion,
        String dyeColor,
        String itemModel,
        List<NamePart> nameParts) {

    public RecipeResult(String itemId, int count, String name, List<String> traits, Map<String, Double> strengths,
            Map<String, String> activations, NameStyle nameStyle, String potion, String dyeColor, String itemModel) {
        this(itemId, count, name, traits, strengths, activations, nameStyle, potion, dyeColor, itemModel, List.of());
    }
    public RecipeResult(String itemId, int count, String name, List<String> traits, Map<String, Double> strengths,
            Map<String, String> activations, NameStyle nameStyle, String potion, String dyeColor) {
        this(itemId, count, name, traits, strengths, activations, nameStyle, potion, dyeColor, "");
    }
    public RecipeResult(String itemId, int count, String name, List<String> traits, Map<String, Double> strengths,
            Map<String, String> activations, NameStyle nameStyle, String potion) {
        this(itemId, count, name, traits, strengths, activations, nameStyle, potion, "");
    }
    public RecipeResult(String itemId, int count, String name, List<String> traits, Map<String, Double> strengths,
            Map<String, String> activations, NameStyle nameStyle) {
        this(itemId, count, name, traits, strengths, activations, nameStyle, "");
    }
    public RecipeResult(String itemId, int count, String name, List<String> traits, Map<String, Double> strengths) {
        this(itemId, count, name, traits, strengths, Map.of(), NameStyle.PLAIN);
    }

    public RecipeResult(String itemId, int count, String name, List<String> traits) {
        this(itemId, count, name, traits, Map.of());
    }

    public RecipeResult(String itemId, int count) {
        this(itemId, count, "", List.of());
    }

    public RecipeResult {
        itemModel = itemModel == null ? "" : itemModel;
        if (!itemModel.isEmpty() && !ValidationPatterns.isVanillaItemModel(itemModel))
            throw new IllegalArgumentException("Invalid vanilla item model");
        dyeColor = dyeColor == null ? "" : dyeColor;
        if (!ValidationPatterns.isOptionalHexColor(dyeColor)) throw new IllegalArgumentException("Invalid dye color");
        potion = potion == null ? "" : potion;
        if (!potion.isEmpty() && !ValidationPatterns.isResourceId(potion)) throw new IllegalArgumentException("Invalid potion ID");
        name = name == null ? "" : name;
        nameParts = nameParts == null ? List.of() : List.copyOf(nameParts);
        if (nameParts.size() > 8) throw new IllegalArgumentException("At most eight name segments");
        if (!nameParts.isEmpty()) {
            String joined = nameParts.stream().map(NamePart::text).collect(Collectors.joining());
            if (!name.isEmpty() && !name.equals(joined)) throw new IllegalArgumentException("Name segments must match the name");
            name = joined;
        }
        traits = traits == null ? List.of() : List.copyOf(traits);
        strengths = strengths == null ? Map.of() : Map.copyOf(strengths);
        TraitStrengths.validate(traits, strengths);
        activations = activations == null ? Map.of() : Map.copyOf(activations);
        nameStyle = nameStyle == null ? NameStyle.PLAIN : nameStyle;
        for (var activation : activations.entrySet()) {
            var definition = TraitRegistry.get(activation.getKey());
            if (!traits.contains(activation.getKey()) || definition == null || !definition.activationModes().contains(activation.getValue()))
                throw new IllegalArgumentException("Unsupported trait activation");
        }
        if (name.length() > 64 || name.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT))
            throw new IllegalArgumentException("Recipe name must be at most 64 characters without controls");
        if (traits.size() > 8 || traits.stream().distinct().count() != traits.size()
                || traits.stream().anyMatch(trait -> !ValidationPatterns.isTraitId(trait)))
            throw new IllegalArgumentException("Recipe traits must contain at most eight distinct trait IDs");
    }
}
