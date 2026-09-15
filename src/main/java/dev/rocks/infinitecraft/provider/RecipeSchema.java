package dev.rocks.infinitecraft.provider;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import dev.rocks.infinitecraft.core.CatalogEntry;
import dev.rocks.infinitecraft.core.GenerationRequest;
import dev.rocks.infinitecraft.core.TraitStrengths;
import dev.rocks.infinitecraft.traits.TraitRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RecipeSchema {
    private static final Gson JSON = new Gson();

    private RecipeSchema() {
    }

    static JsonElement build(GenerationRequest request) {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("itemId", Map.of("type", "string", "enum", request.candidates().stream()
                .filter(CatalogEntry::craftable).map(CatalogEntry::id).distinct().toList()));
        properties.put("count", Map.of("type", "integer", "minimum", 1, "maximum", request.maxOutputCount()));
        var required = new ArrayList<>(List.of("itemId", "count"));
        if (!request.supportedTraits().isEmpty()) {
            if (!request.supportedPotions().isEmpty()) {
                var potions = new ArrayList<>(request.supportedPotions().keySet());
                potions.add("");
                properties.put("potion", Map.of("type", "string", "enum", potions));
            }
            var strengthProperties = new HashMap<String, Object>();
            request.supportedTraits().stream().filter(TraitStrengths.RANGES::containsKey).forEach(trait ->
                    strengthProperties.put(trait, Map.of("type", "number", "minimum", 0, "maximum", 1)));
            var activationProperties = new LinkedHashMap<String, Object>();
            request.supportedTraits().forEach(id -> activationProperties.put(id, Map.of("type", "string", "enum",
                    TraitRegistry.get(id).activationModes())));
            properties.put("activations", Map.of("type", "object", "additionalProperties", false,
                    "properties", activationProperties, "maxProperties", request.maxTraits()));
            var styleProperties = new LinkedHashMap<String, Object>();
            // JSON Schema: accept an empty color or one complete #RRGGBB value.
            styleProperties.put("color", Map.of("type", "string", "pattern", "^(#[0-9a-fA-F]{6})?$"));
            for (String flag : List.of("bold", "italic", "underlined", "strikethrough", "obfuscated"))
                styleProperties.put(flag, Map.of("type", "boolean"));
            var styleSchema = Map.of("type", "object", "additionalProperties", false,
                    "required", List.copyOf(styleProperties.keySet()), "properties", styleProperties);
            properties.put("nameStyle", styleSchema);
            properties.put("nameParts", Map.of("type", "array", "maxItems", 8, "items",
                    Map.of("type", "object", "additionalProperties", false, "required", List.of("text", "style"),
                            "properties", Map.of("text", Map.of("type", "string", "minLength", 1, "maxLength", 64),
                                    "style", styleSchema))));
            // JSON Schema: accept an empty model or one lowercase vanilla item model ID.
            properties.put("itemModel", Map.of("type", "string", "pattern", "^(minecraft:[a-z0-9/._-]+)?$"));
            // JSON Schema: use the same optional #RRGGBB format as name colors.
            properties.put("dyeColor", Map.of("type", "string", "pattern", "^(#[0-9a-fA-F]{6})?$"));
            properties.put("name", Map.of("type", "string", "maxLength", 64));
            properties.put("strengths", Map.of("type", "object", "additionalProperties", false,
                    "properties", strengthProperties, "maxProperties", request.maxTraits()));
            properties.put("traits", Map.of("type", "array", "maxItems", request.maxTraits(), "uniqueItems", true,
                    "items", Map.of("type", "string", "enum", request.supportedTraits())));
            required.addAll(List.of("name", "traits"));
        }
        var recipe = Map.of("type", "object", "additionalProperties", false,
                "required", required, "properties", properties);
        return JSON.toJsonTree(Map.of("type", "object", "additionalProperties", false,
                "required", List.of("results"), "properties", Map.of("results",
                        Map.of("type", "array", "minItems", 1, "maxItems", 5, "items", recipe))));
    }
}
