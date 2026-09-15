package dev.rocks.infinitecraft.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.rocks.infinitecraft.core.GenerationRequest;
import dev.rocks.infinitecraft.core.InvalidRecipeResponseException;
import dev.rocks.infinitecraft.core.NamePart;
import dev.rocks.infinitecraft.core.NameStyle;
import dev.rocks.infinitecraft.core.RecipeResult;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

final class RecipeResponseParser {
    // Accepts one complete JSON Markdown fence and captures only its contents.
    private static final Pattern JSON_FENCE = Pattern.compile("\\A```(?:json)?\\r?\\n([\\s\\S]*)\\r?\\n```\\z");

    private RecipeResponseParser() {}

    static List<RecipeResult> parseCandidates(String text, GenerationRequest request) throws InvalidRecipeResponseException {
        try {
            String output = text.strip();
            var fence = JSON_FENCE.matcher(output);
            JsonObject payload = parseObject(fence.matches() ? fence.group(1) : output);
            JsonArray choices;
            if (payload.has("results")) choices = payload.getAsJsonArray("results");
            else {
                choices = new JsonArray();
                choices.add(payload);
            }
            List<RecipeResult> valid = new ArrayList<>();
            for (int index = 0; index < Math.min(5, choices.size()); index++) {
                try {
                    valid.add(parseRecipe(choices.get(index).getAsJsonObject(), request));
                } catch (RuntimeException invalidCandidate) {
                    // One malformed proposal must not hide a usable later proposal in this response.
                }
            }
            if (valid.isEmpty()) throw new InvalidRecipeResponseException();
            return List.copyOf(valid);
        } catch (RuntimeException | IOException e) {
            throw new InvalidRecipeResponseException();
        }
    }

    private static RecipeResult parseRecipe(JsonObject result, GenerationRequest request) {
        String id = string(result, "itemId");
        if (request.candidates().stream().noneMatch(c -> c.craftable() && c.id().equals(id))) throw new IllegalArgumentException();
        int count = 1;
        if (result.has("count")) {
            if (!result.get("count").isJsonPrimitive() || !result.getAsJsonPrimitive("count").isNumber()) throw new IllegalArgumentException();
            count = result.get("count").getAsBigDecimal().intValueExact();
        }
        if (count < 1 || count > request.maxOutputCount()) throw new IllegalArgumentException();
        // The server's rarity roll is authoritative, even when a provider ignores the plain-item prompt.
        if (request.supportedTraits().isEmpty()) return new RecipeResult(id, count);
        String name = result.has("name") ? string(result, "name") : "";
        List<String> traits = new ArrayList<>();
        if (result.has("traits")) {
            for (JsonElement trait : result.getAsJsonArray("traits")) {
                if (!trait.isJsonPrimitive() || !trait.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
                traits.add(trait.getAsString());
                if (traits.size() > request.maxTraits() || !request.supportedTraits().contains(trait.getAsString())) throw new IllegalArgumentException();
            }
        }
        var strengths = new HashMap<String, Double>();
        if (result.has("strengths")) {
            for (var entry : result.getAsJsonObject("strengths").entrySet()) {
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
                strengths.put(entry.getKey(), entry.getValue().getAsDouble());
            }
        }
        var activations = new HashMap<String, String>();
        if (result.has("activations")) {
            for (var entry : result.getAsJsonObject("activations").entrySet())
                activations.put(entry.getKey(), string(result.getAsJsonObject("activations"), entry.getKey()));
        }
        if (activations.containsValue("consumed_intense") && request.rarityQuality() < 75)
            throw new IllegalArgumentException();
        NameStyle nameStyle = result.has("nameStyle") ? parseNameStyle(result.getAsJsonObject("nameStyle")) : NameStyle.PLAIN;
        var nameParts = new ArrayList<NamePart>();
        if (result.has("nameParts")) {
            for (var element : result.getAsJsonArray("nameParts")) {
                var part = element.getAsJsonObject();
                nameParts.add(new NamePart(string(part, "text"),
                        parseNameStyle(part.getAsJsonObject("style"))));
            }
        }
        String potion = result.has("potion") ? string(result, "potion") : "";
        if (!potion.isEmpty() && !request.supportedPotions().containsKey(potion)) throw new IllegalArgumentException();
        return new RecipeResult(id, count, name, traits, strengths, activations, nameStyle, potion,
                result.has("dyeColor") ? string(result, "dyeColor") : "",
                result.has("itemModel") ? string(result, "itemModel") : "", nameParts);
    }

    private static NameStyle parseNameStyle(JsonObject style) {
        return new NameStyle(style.has("color") ? string(style, "color") : "",
                flag(style, "bold"), flag(style, "italic"), flag(style, "underlined"),
                flag(style, "strikethrough"), flag(style, "obfuscated"));
    }

    private static boolean flag(JsonObject object, String key) {
        if (!object.has(key)) return false;
        if (!object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isBoolean()) throw new IllegalArgumentException();
        return object.get(key).getAsBoolean();
    }

    private static String string(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException();
        return object.get(key).getAsString();
    }

    static JsonObject parseObject(String text) throws IOException {
        // Check nesting before Gson builds a tree, so hostile responses cannot exhaust the stack.
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            int depth = 0;
            while (reader.peek() != JsonToken.END_DOCUMENT) {
                switch (reader.peek()) {
                    case BEGIN_ARRAY -> { reader.beginArray(); depth++; }
                    case BEGIN_OBJECT -> { reader.beginObject(); depth++; }
                    case END_ARRAY -> { reader.endArray(); depth--; }
                    case END_OBJECT -> { reader.endObject(); depth--; }
                    case NAME -> reader.nextName();
                    case STRING, NUMBER -> reader.nextString();
                    case BOOLEAN -> reader.nextBoolean();
                    case NULL -> reader.nextNull();
                    default -> throw new IOException("Invalid JSON");
                }
                if (depth > 32) throw new IOException("JSON nesting exceeds limit");
            }
        }
        return JsonParser.parseString(text).getAsJsonObject();
    }

}
