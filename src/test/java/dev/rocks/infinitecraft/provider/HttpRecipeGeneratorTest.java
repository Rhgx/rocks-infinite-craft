package dev.rocks.infinitecraft.provider;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import dev.rocks.infinitecraft.core.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class HttpRecipeGeneratorTest {
    @Test void modelQuantityUsesRequestBoundsForPlainAndSpecialResults() throws Exception {
        var request = new GenerationRequest(REQUEST.first(), REQUEST.second(), REQUEST.candidates(), List.of("speedy"), List.of(), 12);
        String response = "{\"itemId\":\"minecraft:cobblestone\",\"traits\":[\"speedy\"],\"count\":";
        assertEquals(12, candidates(response + "12}", request).getFirst().count());
        assertEquals(8, candidates(response + "8}", REQUEST).getFirst().count());
        for (String value : List.of("0", "-1", "13", "1.5", "2147483649", "\"3\""))
            assertThrows(InvalidRecipeResponseException.class, () -> candidates(response + value + "}", request));
        var schema = CodexRecipeGenerator.schema(request).getAsJsonObject("properties").getAsJsonObject("results")
                .getAsJsonObject("items").getAsJsonObject("properties").getAsJsonObject("count");
        assertEquals(12, schema.get("maximum").getAsInt());
        assertFalse(schema.has("const"));
        var limited = new GenerationRequest(REQUEST.first(), REQUEST.second(), REQUEST.candidates(),
                List.of("speedy", "bouncy"), List.of(), 12, 50, 50, Map.of(), 1, List.of());
        String dyed = "{\"itemId\":\"minecraft:cobblestone\",\"dyeColor\":\"#4A8BFF\",\"traits\":[\"speedy\"]}";
        assertEquals("#4A8BFF", candidates(dyed, limited).getFirst().dyeColor());
        String modeled = dyed.replace("#4A8BFF", "").replace("\"dyeColor\":\"\"", "\"itemModel\":\"minecraft:diamond\"");
        assertEquals("minecraft:diamond", candidates(modeled, limited).getFirst().itemModel());
        String segmented = "{\"itemId\":\"minecraft:cobblestone\",\"name\":\"Lost Rune\",\"nameParts\":[{\"text\":\"Lost \",\"style\":{\"underlined\":true}},{\"text\":\"Rune\",\"style\":{\"obfuscated\":true}}]}";
        var named = candidates(segmented, limited).getFirst();
        assertTrue(named.nameParts().getFirst().style().underlined());
        assertTrue(named.nameParts().getLast().style().obfuscated());
        assertFalse(named.nameParts().getLast().style().underlined());
        var gson = new Gson();
        assertEquals(named, gson.fromJson(gson.toJson(named), RecipeResult.class));
        assertThrows(InvalidRecipeResponseException.class, () -> candidates(segmented.replace("Lost Rune", "Mismatch"), limited));
        assertThrows(InvalidRecipeResponseException.class, () -> candidates(segmented.replace("true", "\"true\""), limited));

        assertThrows(InvalidRecipeResponseException.class, () -> candidates(modeled.replace("minecraft:diamond", "custom:missing"), limited));

        assertThrows(InvalidRecipeResponseException.class, () -> candidates(dyed.replace("#4A8BFF", "blue"), limited));
        assertThrows(InvalidRecipeResponseException.class, () -> candidates(dyed.replace("[\"speedy\"]", "[\"speedy\",\"bouncy\"]"), limited));

    }

    @Test void potionChoiceUsesTheSuppliedRegistryListAcrossProviderSchemas() throws Exception {
        var request = new GenerationRequest("minecraft:potion", "minecraft:sugar", List.of(
                new CatalogEntry("minecraft:potion", "item", "minecraft", "Potion", List.of(), true, "")),
                List.of("speedy"), List.of(List.of("Base potion: minecraft:water; no effects"), List.of()),
                8, 50, 50, Map.of("minecraft:swiftness", List.of("minecraft:speed level 1, 3600 ticks")));
        String response = "{\"itemId\":\"minecraft:potion\",\"potion\":\"minecraft:swiftness\"}";
        assertEquals("minecraft:swiftness", candidates(response, request).getFirst().potion());
        assertThrows(InvalidRecipeResponseException.class, () -> candidates(response.replace("swiftness", "missing"), request));
        assertTrue(HttpRecipeGenerator.prompt(request).contains("Names alone do not give potion effects"));
        var schema = CodexRecipeGenerator.schema(request).getAsJsonObject("properties").getAsJsonObject("results").getAsJsonObject("items");
        assertTrue(schema.getAsJsonArray("required").contains(new JsonPrimitive("potion")));
        assertEquals(2, schema.getAsJsonObject("properties").getAsJsonObject("potion").getAsJsonArray("enum").size());
        assertEquals("minecraft:swiftness", CodexRecipeGenerator.parseCandidates("{\"results\":[" + response + "]}", request).getFirst().potion());
    }

    private static final Gson JSON = new Gson();
    private static final GenerationRequest REQUEST = new GenerationRequest("minecraft:stone", "minecraft:stone",
            List.of(new CatalogEntry("minecraft:cobblestone", "item", "minecraft", "Cobblestone", List.of(), true, "")));

    @Test void allProvidersUseTheirNativeProtocol() throws Exception {
        for (boolean enteredKey : List.of(false, true)) {
        for (String provider : List.of("ollama", "openai", "anthropic", "gemini", "openrouter", "compatible")) {
            AtomicReference<String> path = new AtomicReference<>();
            AtomicReference<JsonObject> request = new AtomicReference<>();
            AtomicReference<String> key = new AtomicReference<>();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                path.set(exchange.getRequestURI().getPath());
                request.set(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
                key.set(exchange.getRequestHeaders().getFirst(switch (provider) {
                    case "anthropic" -> "x-api-key"; case "gemini" -> "x-goog-api-key"; default -> "Authorization";
                }));
                String recipe = "{\"itemId\":\"minecraft:cobblestone\",\"count\":1}";
                byte[] bytes = envelope(provider, enteredKey ? "{\"results\":[" + recipe + "]}" : recipe).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.start();
            try {
                var config = new ProviderConfig(provider, "http://127.0.0.1:" + server.getAddress().getPort(), "test-model", "TEST_KEY", 3,
                        enteredKey ? "test-secret" : "");
                assertEquals(new RecipeResult("minecraft:cobblestone", 1), new HttpRecipeGenerator(config, ignored -> {
                    assertFalse(enteredKey, "An entered key must take precedence over the environment");
                    return "test-secret";
                }).generate(REQUEST));
                assertEquals(switch (provider) {
                    case "ollama" -> "/api/chat"; case "openai" -> "/responses"; case "anthropic" -> "/messages";
                    case "gemini" -> "/models/test-model:generateContent"; default -> "/chat/completions";
                }, path.get());
                assertEquals(provider.equals("anthropic") || provider.equals("gemini") ? "test-secret" : "Bearer test-secret", key.get());
                assertTrue(request.get().toString().contains("minecraft:cobblestone"));
                assertFalse(request.get().toString().contains("supportedTraits"));
                assertFalse(request.get().toString().contains("adjustableTraits"));
                assertFalse(request.get().toString().contains("test-secret"));
                if (provider.equals("openai")) assertFalse(request.get().get("store").getAsBoolean());
                if (provider.equals("ollama")) {
                    assertFalse(request.get().get("stream").getAsBoolean());
                    assertFalse(request.get().get("think").getAsBoolean());
                    var schema = request.get().getAsJsonObject("format");
                    assertFalse(schema.get("additionalProperties").getAsBoolean());
                    var results = schema.getAsJsonObject("properties").getAsJsonObject("results");
                    assertEquals(5, results.get("maxItems").getAsInt());
                    var properties = results.getAsJsonObject("items").getAsJsonObject("properties");
                    assertEquals(1, properties.getAsJsonObject("count").get("minimum").getAsInt());
                    assertEquals("minecraft:cobblestone", properties.getAsJsonObject("itemId").getAsJsonArray("enum").get(0).getAsString());
                    assertEquals(java.util.Set.of("itemId", "count"), properties.keySet());
                    assertEquals(JSON.toJsonTree(List.of("itemId", "count")), results.getAsJsonObject("items").get("required"));
                }
                if (provider.equals("gemini")) assertTrue(request.get().has("contents"));
                else assertEquals("test-model", request.get().get("model").getAsString());
            } finally { server.stop(0); }
        }
        }
    }

    @Test void invalidRepliesAndHttpErrorsAreSafe() throws Exception {
        for (String reply : List.of("broken", "{}", envelope("ollama", "{}"),
                envelope("ollama", "{itemId:'minecraft:cobblestone'}"), "[".repeat(40) + "0" + "]".repeat(40),
                envelope("ollama", "{\"itemId\":\"mod:unknown\"}"),
                envelope("ollama", "{\"itemId\":\"minecraft:cobblestone\",\"count\":1.5}"),
                envelope("ollama", "{\"itemId\":\"minecraft:cobblestone\",\"count\":\"1\"}"),
                "x".repeat(1_048_577))) {
            IOException error = callFailure(reply, 200, 0);
            assertFalse(error.getMessage().contains(reply.substring(0, Math.min(40, reply.length()))));
            assertNull(error.getCause());
        }
        assertEquals("Provider returned HTTP 429", callFailure("test-secret upstream diagnostics", 429, 0).getMessage());
    }

    @Test void timesOutWhileBodyIsStalled() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 100);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                Thread.sleep(1500);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var config = new ProviderConfig("ollama", "http://127.0.0.1:" + server.getAddress().getPort(), "test-model", "", 1);
            assertEquals("Provider request timed out", assertThrows(IOException.class,
                    () -> new HttpRecipeGenerator(config).generate(REQUEST)).getMessage());
        } finally { server.stop(0); }
    }

    @Test void enteredKeysAreRedactedAndInvalidKeysAreRejectedWithoutEchoingThem() {
        var environment = new ProviderConfig("openai", "", "chosen-model", "TEST_KEY", 60);
        for (String key : List.of("", "test-secret\nInjected: value")) {
            IOException error = assertThrows(IOException.class,
                    () -> new HttpRecipeGenerator(environment, ignored -> key).generate(REQUEST));
            assertFalse(error.toString().contains("test-secret"));
            assertNull(error.getCause());
        }
        for (String url : List.of("http://remote.example", "https://user:secret@example.com", "https://example.com?key=secret", "https://example.com/#secret"))
            assertThrows(IllegalArgumentException.class, () -> new ProviderConfig("ollama", url, "model", "", 60));
        var message = ProviderConnectionTest.failureMessage(new IOException("secret-key https://private-host"));
        assertFalse(message.contains("secret"));
        assertFalse(message.contains("private-host"));
        var config = new ProviderConfig("openai", "", "chosen-model", "", 60, "test-secret");
        assertEquals("test-secret", config.apiKey());
        assertFalse(config.toString().contains("test-secret"));
        assertTrue(config.toString().contains("apiKey=<redacted>"));
        assertEquals("", new ProviderConfig("openai", "", "chosen-model", "", 60).apiKey());
        assertEquals("", new ProviderConfig("openai", "", "chosen-model", "", 60, null).apiKey());
        for (String key : List.of("test-secret\nInjected: value", "test-secret\r", "test secret", "test-secret\u007f")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new ProviderConfig("openai", "", "chosen-model", "", 60, key));
            assertFalse(error.toString().contains("test-secret"));
            assertNull(error.getCause());
        }
    }

    @Test void skipsInvalidCandidatesAndPreservesAllowedNamesAndTraits() throws Exception {
        var request = new GenerationRequest(REQUEST.first(), REQUEST.second(), REQUEST.candidates(), List.of("glowing", "durable"));
        String valid = "{\"itemId\":\"minecraft:cobblestone\",\"count\":1,\"name\":\"Moon Stone\",\"traits\":[\"glowing\"]}";
        String reply = "{\"results\":[null,{\"itemId\":\"missing:item\"},"
                + "{\"itemId\":\"minecraft:cobblestone\",\"traits\":[\"not_allowed\"]},"
                + "{\"itemId\":\"minecraft:cobblestone\",\"name\":123}," + valid + "]}";
        assertEquals(List.of(new RecipeResult("minecraft:cobblestone", 1, "Moon Stone", List.of("glowing"))), candidates(reply, request));
        assertEquals(5, candidates("{\"results\":[" + String.join(",", java.util.Collections.nCopies(6, valid)) + "]}", request).size());
        assertEquals(List.of(new RecipeResult("minecraft:cobblestone", 1)), candidates(valid, REQUEST),
                "Plain requests must strip unsolicited names and traits without rejecting a usable item");
        assertTrue(HttpRecipeGenerator.prompt(REQUEST).contains("Return ordinary items"));
    }

    @Test void invalidMetadataAndMalformedPayloadsHaveTypedFailure() throws Exception {
        for (String metadata : List.of("\"name\":\"" + "x".repeat(65) + "\"", "\"name\":\"line\\nbreak\"",
                "\"traits\":[\"unsupported\"]", "\"traits\":null", "\"traits\":[1]")) {
            assertThrows(InvalidRecipeResponseException.class,
                    () -> candidates("{\"results\":[{\"itemId\":\"minecraft:cobblestone\"," + metadata + "}]}",
                            new GenerationRequest(REQUEST.first(), REQUEST.second(), REQUEST.candidates(), List.of("bouncy"))));
        }
        for (String payload : List.of("broken", "{}", "{\"results\":[]}", "{\"results\":{}}"))
            assertThrows(InvalidRecipeResponseException.class, () -> candidates(payload, REQUEST));
        assertInstanceOf(InvalidRecipeResponseException.class, callFailure("malformed JSON", 200, 0));
        assertFalse(callFailure("private upstream error", 401, 0) instanceof InvalidRecipeResponseException);
        assertFalse(callFailure("private upstream error", 429, 0) instanceof InvalidRecipeResponseException);
        var timeout = callFailure("{}", 200, 1500);
        assertFalse(timeout instanceof InvalidRecipeResponseException);
        assertEquals("Provider request timed out", timeout.getMessage());
    }

    private static List<RecipeResult> candidates(String payload, GenerationRequest request) throws Exception {
        return HttpRecipeGenerator.parseCandidates(payload, request);
    }

    private static IOException callFailure(String reply, int status, long delay) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                if (delay > 0) Thread.sleep(delay);
                byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var config = new ProviderConfig("ollama", "http://127.0.0.1:" + server.getAddress().getPort(), "test-model", "", 1, "test-secret");
            return assertThrows(IOException.class, () -> new HttpRecipeGenerator(config).generate(REQUEST));
        } finally { server.stop(0); }
    }

    private static String envelope(String provider, String text) {
        return JSON.toJson(switch (provider) {
            case "ollama" -> Map.of("message", Map.of("content", text));
            case "openai" -> Map.of("output", List.of(Map.of("type", "reasoning"), Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", text)))));
            case "anthropic" -> Map.of("content", List.of(Map.of("type", "text", "text", text)));
            case "gemini" -> Map.of("candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", text))))));
            default -> Map.of("choices", List.of(Map.of("message", Map.of("content", text))));
        });
    }
}
