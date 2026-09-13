package dev.rocks.infinitecraft.provider;

import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Queries the CLI's picker catalog without creating a conversation. */
public final class CodexModels {
    public record Model(String id, List<String> reasoningLevels, boolean isDefault) {
        public Model { reasoningLevels = List.copyOf(reasoningLevels); }
    }
    private CodexModels() {}

    public static List<Model> fetch() throws IOException, InterruptedException {
        return fetch(List.of(CodexRecipeGenerator.findExecutable().toString()), 15);
    }

    static List<Model> fetch(List<String> executable, int timeoutSeconds) throws IOException, InterruptedException {
        var command = new ArrayList<>(executable);
        command.addAll(List.of("app-server", "--listen", "stdio://"));
        var builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().remove("CODEX_THREAD_ID");
        builder.environment().remove("CODEX_TURN_ID");
        Process process = builder.start();
        var result = new CompletableFuture<List<Model>>();
        Thread.ofVirtual().name("infinitecraft-codex-models").start(() -> {
            try (var input = new BufferedInputStream(process.getInputStream());
                    var output = process.getOutputStream()) {
                // Bound the whole exchange, including notifications and paginated responses.
                int remaining = 1_048_576;
                var models = new LinkedHashMap<String, Model>();
                String request = "{\"id\":1,\"method\":\"initialize\",\"params\":{\"clientInfo\":{\"name\":\"infinitecraft\",\"version\":\"0.1.1\"}}}";
                for (int id = 1; id <= 11; id++) {
                    output.write((request + "\n").getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    JsonObject response;
                    while (true) {
                        var line = new ByteArrayOutputStream();
                        int next;
                        while ((next = input.read()) != -1 && next != '\n') {
                            if (--remaining <= 0) throw new IOException();
                            line.write(next);
                        }
                        if (--remaining <= 0 || next == -1) throw new IOException();
                        response = HttpRecipeGenerator.parseObject(line.toString(StandardCharsets.UTF_8));
                        if (response.has("id") && response.get("id").getAsString().equals(Integer.toString(id))) break;
                    }
                    if (response.has("error") || !response.has("result")) throw new IOException();
                    var params = new JsonObject();
                    params.addProperty("limit", 100);
                    params.addProperty("includeHidden", false);
                    if (id == 1) {
                        output.write("{\"method\":\"initialized\"}\n".getBytes(StandardCharsets.UTF_8));
                    } else {
                        var page = response.getAsJsonObject("result");
                        for (var entry : page.getAsJsonArray("data")) {
                            var model = entry.getAsJsonObject();
                            if (model.has("hidden") && model.get("hidden").getAsBoolean()) continue;
                            String name = model.get("model").getAsString();
                            if (!name.matches("[A-Za-z0-9_./:@-]{1,200}")) continue;
                            var levels = new ArrayList<String>();
                            if (model.has("supportedReasoningEfforts")) {
                                for (var effort : model.getAsJsonArray("supportedReasoningEfforts")) {
                                    String level = effort.getAsJsonObject().get("reasoningEffort").getAsString();
                                    if (ProviderConfig.REASONING_LEVELS.contains(level) && !levels.contains(level)) levels.add(level);
                                }
                            }
                            models.putIfAbsent(name, new Model(name, levels, model.has("isDefault") && model.get("isDefault").getAsBoolean()));
                        }
                        var cursor = page.get("nextCursor");
                        if (cursor == null || cursor.isJsonNull()) {
                            result.complete(List.copyOf(models.values()));
                            return;
                        }
                        params.add("cursor", cursor);
                    }
                    var message = new JsonObject();
                    message.addProperty("id", id + 1);
                    message.addProperty("method", "model/list");
                    message.add("params", params);
                    request = message.toString();
                }
                throw new IOException();
            } catch (Exception error) {
                result.completeExceptionally(new IOException("Codex model discovery failed"));
            }
        });
        try {
            return result.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException error) {
            throw new IOException("Codex model discovery timed out");
        } catch (ExecutionException error) {
            throw new IOException("Codex model discovery failed; check CLI login and version");
        } finally {
            CodexRecipeGenerator.stop(process);
        }
    }
}
