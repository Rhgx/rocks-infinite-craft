package dev.rocks.infinitecraft.provider;

import dev.rocks.infinitecraft.core.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CodexRecipeGeneratorTest {
    @TempDir Path directory;
    private static final GenerationRequest REQUEST = new GenerationRequest("minecraft:stone", "minecraft:stone",
            List.of(new CatalogEntry("minecraft:cobblestone", "item", "minecraft", "Cobblestone", List.of(), true, "")));

    @Test void oneShotSubprocessReceivesPromptAndParsesCandidates() throws Exception {
        var generator = fake("valid", 5);
        assertEquals(List.of(new RecipeResult("minecraft:cobblestone", 1)), generator.generateCandidates(REQUEST));
    }

    @Test void subprocessFailureDoesNotExposeDiagnostics() throws Exception {
        IOException error = assertThrows(IOException.class, () -> fake("fail", 5).generate(REQUEST));
        assertFalse(error instanceof InvalidRecipeResponseException);
        assertFalse(error.toString().contains("private-secret"));
        assertThrows(InvalidRecipeResponseException.class, () -> fake("invalid", 5).generate(REQUEST));
        assertThrows(IOException.class, () -> fake("large", 5).generate(REQUEST));
    }

    @Test void timeoutStopsTheExactProcess() throws Exception {
        IOException error = assertThrows(IOException.class, () -> fake("sleep", 1).generate(REQUEST));
        assertEquals("Codex request timed out", error.getMessage());
        long pid = Long.parseLong(Files.readString(directory.resolve("sleep.pid")));
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    }

    @Test void commandUsesEphemeralReadOnlyModeAndNeverAShell() {
        var config = new ProviderConfig("codex", "", "", "", 30);
        var args = CodexRecipeGenerator.arguments(List.of("C:/native/codex.exe"), config, Path.of("scratch"), Path.of("schema.json"));
        assertEquals("C:/native/codex.exe", args.getFirst());
        for (String flag : List.of("--ephemeral", "--ignore-user-config", "--ignore-rules", "read-only", "--output-schema", "approval_policy=\"never\""))
            assertTrue(args.contains(flag));
        assertFalse(args.contains("--model"));
        assertTrue(args.contains("model_reasoning_effort=\"low\""));
        assertFalse(args.stream().anyMatch(value -> value.startsWith("service_tier=")));
        assertFalse(args.contains("--dangerously-bypass-approvals-and-sandbox"));
        assertEquals("-", args.getLast());
        assertInstanceOf(CodexRecipeGenerator.class, RecipeGenerators.create(config));
        var fast = new ProviderConfig("codex", "", "", "", 30, "", "high", true);
        var fastArgs = CodexRecipeGenerator.arguments(List.of("codex"), fast, Path.of("scratch"), Path.of("schema.json"));
        assertTrue(fastArgs.contains("model_reasoning_effort=\"high\""));
        assertTrue(fastArgs.contains("service_tier=\"fast\""));
    }

    @Test void strictSchemaUsesSparseControlsAndRejectsInvalidEntries() throws Exception {
        var plain = CodexRecipeGenerator.schema(REQUEST).getAsJsonObject("properties")
                .getAsJsonObject("results").getAsJsonObject("items");
        assertEquals(java.util.Set.of("itemId", "count"), plain.getAsJsonObject("properties").keySet());
        assertEquals(2, plain.getAsJsonArray("required").size());
        var request = new GenerationRequest(REQUEST.first(), REQUEST.second(), REQUEST.candidates(), List.of("bouncy", "speedy"));
        var schema = CodexRecipeGenerator.schema(request);
        var recipe = schema.getAsJsonObject("properties").getAsJsonObject("results").getAsJsonObject("items");
        assertEquals(10, recipe.getAsJsonArray("required").size());
        var strength = recipe.getAsJsonObject("properties").getAsJsonObject("strengths");
        assertEquals("array", strength.get("type").getAsString());
        assertEquals(3, strength.get("maxItems").getAsInt());
        var fields = strength.getAsJsonObject("items").getAsJsonObject("properties");
        assertEquals(2, fields.getAsJsonObject("trait").getAsJsonArray("enum").size());
        assertEquals(0, fields.getAsJsonObject("value").get("minimum").getAsInt());
        assertEquals(1, fields.getAsJsonObject("value").get("maximum").getAsInt());
        var activation = recipe.getAsJsonObject("properties").getAsJsonObject("activations");
        assertEquals(3, activation.get("maxItems").getAsInt());
        assertTrue(activation.getAsJsonObject("items").getAsJsonObject("properties").getAsJsonObject("mode")
                .getAsJsonArray("enum").contains(new com.google.gson.JsonPrimitive("consumed")));
        String base = "{\"itemId\":\"minecraft:cobblestone\",\"count\":1,\"name\":null,\"traits\":[\"bouncy\"],";
        String valid = base + "\"strengths\":[{\"trait\":\"bouncy\",\"value\":0.5}],\"activations\":[{\"trait\":\"bouncy\",\"mode\":\"head\"}]}";
        var result = CodexRecipeGenerator.parseCandidates("{\"results\":[" + valid + "]}", request).getFirst();
        assertEquals(java.util.Map.of("bouncy", .5), result.strengths());
        assertEquals(java.util.Map.of("bouncy", "head"), result.activations());
        assertEquals("", result.name());
        var empty = CodexRecipeGenerator.parseCandidates(base + "\"strengths\":[],\"activations\":[]}", request).getFirst();
        assertTrue(empty.strengths().isEmpty());
        assertTrue(empty.activations().isEmpty());
        for (String controls : List.of(
                "\"strengths\":[{\"trait\":\"bouncy\",\"value\":0.5},{\"trait\":\"bouncy\",\"value\":0.6}]",
                "\"strengths\":[{\"trait\":\"bouncy\",\"value\":1.1}]",
                "\"strengths\":[{\"trait\":\"speedy\",\"value\":0.5}]",
                "\"strengths\":[{\"trait\":\"bouncy\",\"value\":null}]",
                "\"strengths\":[{\"trait\":\"bouncy\",\"value\":\"0.5\"}]",
                "\"strengths\":[{\"trait\":\"bouncy\",\"value\":0.5,\"extra\":true}]",
                "\"strengths\":[{\"trait\":\"bouncy\"}]",
                "\"strengths\":[{},{},{},{}]",
                "\"activations\":[{\"trait\":\"bouncy\",\"mode\":\"consumed\"}]",
                "\"activations\":[{\"trait\":\"bouncy\",\"mode\":\"auto\"},{\"trait\":\"bouncy\",\"mode\":\"head\"}]",
                "\"activations\":[{\"trait\":\"unknown\",\"mode\":\"auto\"}]")) {
            String invalid = base + controls + "}";
            assertThrows(InvalidRecipeResponseException.class, () -> CodexRecipeGenerator.parseCandidates(invalid, request), controls);
            assertEquals(List.of(result), CodexRecipeGenerator.parseCandidates("{\"results\":[" + invalid + "," + valid + "]}", request), controls);
        }
        String invalid = base + "\"strengths\":[{}]}";
        assertThrows(InvalidRecipeResponseException.class, () -> CodexRecipeGenerator.parseCandidates(
                "{\"results\":[" + (invalid + ",").repeat(5) + valid + "]}", request));
        var noAdjustable = new GenerationRequest(REQUEST.first(), REQUEST.second(), REQUEST.candidates(), List.of("gliding"));
        assertEquals(0, CodexRecipeGenerator.schema(noAdjustable).getAsJsonObject("properties")
                .getAsJsonObject("results").getAsJsonObject("items").getAsJsonObject("properties")
                .getAsJsonObject("strengths").get("maxItems").getAsInt());
    }

    private CodexRecipeGenerator fake(String mode, int timeout) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classes = Path.of(FakeCodex.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        return new CodexRecipeGenerator(new ProviderConfig("codex", "", "test-model", "", timeout),
                List.of(java, "-cp", classes, FakeCodex.class.getName(), mode, directory.resolve(mode + ".pid").toString()));
    }

    public static final class FakeCodex {
        public static void main(String[] args) throws Exception {
            List<String> command = Arrays.asList(args);
            if (!command.contains("--ephemeral") || !command.contains("read-only") || !command.contains("--ignore-user-config")) System.exit(10);
            String schema = Files.readString(Path.of(args[command.indexOf("--output-schema") + 1]));
            String prompt = new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (!schema.contains("results") || !prompt.contains("minecraft:cobblestone")) System.exit(11);
            Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
            switch (args[0]) {
                case "sleep" -> Thread.sleep(10_000);
                case "fail" -> { System.err.println("private-secret confidential diagnostics"); System.exit(2); }
                case "invalid" -> System.out.println("not valid JSON");
                case "large" -> System.out.print("x".repeat(1_048_600));
                default -> System.out.println("{\"results\":[{\"itemId\":\"minecraft:cobblestone\",\"count\":1}]}");
            }
        }
    }
}
