package dev.rocks.infinitecraft.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodexModelsTest {
    @TempDir Path directory;

    @Test void discoversPagesAndFiltersHiddenInvalidAndDuplicateModels() throws Exception {
        var models = fetch("valid", 5);
        assertEquals(List.of("test-one", "test-two"), models.stream().map(CodexModels.Model::id).toList());
        assertEquals(List.of("low", "high"), models.getFirst().reasoningLevels());
        assertTrue(models.getFirst().isDefault());
        assertTrue(models.getLast().reasoningLevels().isEmpty());
    }

    @Test void rejectsMalformedAndOversizedResponsesWithoutDiagnostics() {
        for (String mode : List.of("invalid", "large", "error")) {
            var error = assertThrows(IOException.class, () -> fetch(mode, 5));
            assertFalse(error.toString().contains("private-secret"));
        }
    }

    @Test void timeoutStopsDiscoveryProcess() throws Exception {
        assertThrows(IOException.class, () -> fetch("sleep", 1));
        long pid = Long.parseLong(Files.readString(directory.resolve("sleep.pid")));
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    }

    private List<CodexModels.Model> fetch(String mode, int timeout) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classes = Path.of(FakeServer.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        return CodexModels.fetch(List.of(java, "-cp", classes, FakeServer.class.getName(), mode,
                directory.resolve(mode + ".pid").toString()), timeout);
    }

    public static final class FakeServer {
        public static void main(String[] args) throws Exception {
            Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
            var input = new BufferedReader(new InputStreamReader(System.in));
            if (!input.readLine().contains("initialize")) System.exit(1);
            switch (args[0]) {
                case "sleep" -> { Thread.sleep(10_000); return; }
                case "invalid" -> { System.out.println("private-secret"); return; }
                case "large" -> { System.out.print("x".repeat(1_048_600)); return; }
                case "error" -> { System.out.println("{\"id\":1,\"error\":{\"message\":\"private-secret\"}}"); return; }
            }
            System.out.println("{\"id\":1,\"result\":{}}");
            if (!input.readLine().contains("initialized") || !input.readLine().contains("model/list")) System.exit(2);
            System.out.println("{\"method\":\"notification\"}");
            System.out.println("{\"id\":2,\"result\":{\"data\":[{\"model\":\"test-one\",\"isDefault\":true,\"supportedReasoningEfforts\":[{\"reasoningEffort\":\"low\"},{\"reasoningEffort\":\"high\"},{\"reasoningEffort\":\"low\"},{\"reasoningEffort\":\"invalid\"}]},{\"model\":\"hidden\",\"hidden\":true},{\"model\":\"bad model\"}],\"nextCursor\":\"page2\"}}");
            String page = input.readLine();
            if (!page.contains("model/list") || !page.contains("page2")) System.exit(3);
            System.out.println("{\"id\":3,\"result\":{\"data\":[{\"model\":\"test-one\"},{\"model\":\"test-two\"}],\"nextCursor\":null}}");
        }
    }
}
