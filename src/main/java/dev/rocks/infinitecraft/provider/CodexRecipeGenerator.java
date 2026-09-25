package dev.rocks.infinitecraft.provider;

import dev.rocks.infinitecraft.core.GenerationRequest;
import dev.rocks.infinitecraft.core.InvalidRecipeResponseException;
import dev.rocks.infinitecraft.core.RecipeGenerator;
import dev.rocks.infinitecraft.core.RecipeResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** One ephemeral CLI invocation per generation, with model tools constrained by a read-only sandbox. */
public final class CodexRecipeGenerator implements RecipeGenerator {
    private static final com.google.gson.Gson JSON = new com.google.gson.Gson();
    private static final int MAX_BYTES = 1_048_576;
    private final ProviderConfig config;
    private final List<String> command;

    public CodexRecipeGenerator(ProviderConfig config) { this(config, List.of()); }
    CodexRecipeGenerator(ProviderConfig config, List<String> command) {
        this.config = config;
        this.command = List.copyOf(command);
    }

    @Override
    public RecipeResult generate(GenerationRequest request) throws Exception { return generateCandidates(request).getFirst(); }

    @Override
    public List<RecipeResult> generateCandidates(GenerationRequest request) throws Exception {
        return generateCandidates(request, "");
    }

    @Override
    public List<RecipeResult> generateCandidates(GenerationRequest request, String feedback) throws Exception {
        byte[] prompt = ("Answer only from the supplied data. Do not use tools, read files, browse, or modify anything. "
                + RecipePrompt.build(request, true, feedback)).getBytes(StandardCharsets.UTF_8);
        if (prompt.length > MAX_BYTES) throw new IOException("Codex request exceeds size limit");
        List<String> executable = command.isEmpty() ? List.of(findExecutable().toString()) : command;
        Path scratch = Files.createTempDirectory("infinitecraft-codex-");
        Path schema = scratch.resolve("schema.json");
        Process process = null;
        Exception failure = null;
        try {
            Files.writeString(schema, schema(request).toString());
            var builder = new ProcessBuilder(arguments(executable, config, scratch, schema)).directory(scratch.toFile());
            // Prevent inherited desktop session routing from turning a CLI request into app work.
            builder.environment().remove("CODEX_THREAD_ID");
            builder.environment().remove("CODEX_TURN_ID");
            try {
                process = builder.start();
            } catch (IOException error) {
                throw new IOException("Codex CLI could not start; check its executable path");
            }
            Process child = process;
            var stdout = read(child.getInputStream(), child);
            var stderr = read(child.getErrorStream(), child);
            var input = new CompletableFuture<Void>();
            Thread.ofVirtual().name("infinitecraft-codex-input").start(() -> {
                try (var stream = child.getOutputStream()) {
                    stream.write(prompt);
                    input.complete(null);
                }
                catch (IOException error) {
                    input.completeExceptionally(new IOException("Codex input failed"));
                }
            });
            long deadline = System.nanoTime() + Duration.ofSeconds(config.timeoutSeconds()).toNanos();
            try {
                if (!child.waitFor(config.timeoutSeconds(), TimeUnit.SECONDS)) throw new TimeoutException();
                byte[] output = stdout.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                stderr.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                input.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (child.exitValue() != 0) throw new IOException("Codex CLI failed; check login, model access, and CLI version");
                return parseCandidates(new String(output, StandardCharsets.UTF_8), request);
            } catch (TimeoutException error) {
                throw new IOException("Codex request timed out");
            } catch (ExecutionException error) {
                throw new IOException("Codex output exceeded its limit or the process connection failed");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("Codex request interrupted");
            }
        } catch (Exception error) {
            failure = error;
            throw error;
        } finally {
            if (process != null) stop(process);
            // These are the only files created by this adapter; never recursively remove an unknown tree.
            try {
                Files.deleteIfExists(schema);
                Files.deleteIfExists(scratch);
            } catch (IOException error) {
                var cleanup = new IOException("Codex temporary files could not be removed");
                if (failure != null) failure.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }

    static List<String> arguments(List<String> executable, ProviderConfig config, Path scratch, Path schema) {
        var args = new ArrayList<>(executable);
        args.addAll(List.of("exec", "--ephemeral", "--ignore-user-config", "--ignore-rules", "--skip-git-repo-check",
                "--sandbox", "read-only", "--color", "never",
                "--cd", scratch.toString(), "--output-schema", schema.toString(), "-c", "approval_policy=\"never\"",
                "-c", "web_search=\"disabled\"", "-c", "history.persistence=\"none\"", "-c", "project_doc_max_bytes=0",
                "-c", "mcp_servers={}"));
        if (!config.model().isEmpty()) args.addAll(List.of("--model", config.model()));
        if (config.fastMode()) args.addAll(List.of("-c", "service_tier=\"fast\""));
        if (!config.reasoning().equals("default"))
            args.addAll(List.of("-c", "model_reasoning_effort=\"" + config.reasoning() + "\""));
        for (String feature : List.of("shell_tool", "unified_exec", "hooks", "plugins", "apps", "multi_agent",
                "browser_use", "computer_use", "image_generation", "view_image", "code_mode", "code_mode_host",
                "memories", "goals", "skill_search", "skill_mcp_dependency_install")) args.addAll(List.of("--disable", feature));
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"))
            args.addAll(List.of("-c", "windows.sandbox=\"unelevated\""));
        args.add("-");
        return List.copyOf(args);
    }

    static com.google.gson.JsonObject schema(GenerationRequest request) {
        var schema = RecipeSchema.build(request).getAsJsonObject();
        if (request.supportedTraits().isEmpty()) return schema;
        var recipe = schema.getAsJsonObject("properties").getAsJsonObject("results").getAsJsonObject("items");
        var properties = recipe.getAsJsonObject("properties");
        for (var style : List.of(properties.getAsJsonObject("nameStyle"), properties.getAsJsonObject("nameParts")
                .getAsJsonObject("items").getAsJsonObject("properties").getAsJsonObject("style"))) {
            style.add("required", JSON.toJsonTree(style.getAsJsonObject("properties").keySet()));
        }
        // Required arrays stay sparse even when the catalog supports many traits.
        recipe.add("required", JSON.toJsonTree(List.of("itemId", "count", "name", "traits", "strengths", "activations", "nameStyle", "dyeColor", "itemModel", "nameParts")));
        if (properties.has("potion")) recipe.getAsJsonArray("required").add("potion");
        properties.getAsJsonObject("name").add("type", JSON.toJsonTree(List.of("string", "null")));
        var strengthTraits = properties.getAsJsonObject("strengths").getAsJsonObject("properties").keySet();
        properties.add("strengths", controlSchema(strengthTraits, request.maxTraits(), "value",
                JSON.toJsonTree(Map.of("type", "number", "minimum", 0, "maximum", 1))));
        var activationProperties = properties.getAsJsonObject("activations").getAsJsonObject("properties");
        var modes = new LinkedHashSet<String>();
        activationProperties.entrySet().forEach(entry -> entry.getValue().getAsJsonObject().getAsJsonArray("enum")
                .forEach(mode -> modes.add(mode.getAsString())));
        properties.add("activations", controlSchema(activationProperties.keySet(), request.maxTraits(), "mode",
                JSON.toJsonTree(Map.of("type", "string", "enum", modes))));
        properties.getAsJsonObject("traits").remove("uniqueItems");
        return schema;
    }

    private static com.google.gson.JsonElement controlSchema(Set<String> traits, int limit, String valueKey,
            com.google.gson.JsonElement valueSchema) {
        return JSON.toJsonTree(Map.of("type", "array", "maxItems", traits.isEmpty() ? 0 : limit,
                "items", Map.of("type", "object", "additionalProperties", false,
                        "required", List.of("trait", valueKey), "properties", Map.of(
                                "trait", Map.of("type", "string", "enum", traits.isEmpty() ? List.of("") : traits),
                                valueKey, valueSchema))));
    }

    static List<RecipeResult> parseCandidates(String text, GenerationRequest request) throws InvalidRecipeResponseException {
        try {
            var payload = RecipeResponseParser.parseObject(text);
            if (payload.has("results") && payload.get("results").isJsonArray()) {
                var results = payload.getAsJsonArray("results");
                for (int i = 0; i < Math.min(5, results.size()); i++) {
                    var item = results.get(i);
                    if (!item.isJsonObject()) continue;
                    try {
                        normalizeControls(item.getAsJsonObject());
                    } catch (IllegalArgumentException | IllegalStateException error) {
                        // Keep its position so a malformed entry cannot admit a sixth proposal.
                        results.set(i, com.google.gson.JsonNull.INSTANCE);
                    }
                }
            } else normalizeControls(payload);
            return RecipeResponseParser.parseCandidates(payload.toString(), request);
        } catch (IOException | RuntimeException error) {
            throw new InvalidRecipeResponseException();
        }
    }

    private static void normalizeControls(com.google.gson.JsonObject recipe) {
        if (recipe.has("name") && recipe.get("name").isJsonNull()) recipe.addProperty("name", "");
        for (String key : List.of("strengths", "activations")) {
            if (!recipe.has(key)) continue;
            if (!recipe.get(key).isJsonArray()) throw new IllegalArgumentException();
            var entries = recipe.getAsJsonArray(key);
            if (entries.size() > 3) throw new IllegalArgumentException();
            String valueKey = key.equals("strengths") ? "value" : "mode";
            var values = new com.google.gson.JsonObject();
            for (var entry : entries) {
                if (!entry.isJsonObject()) throw new IllegalArgumentException();
                var object = entry.getAsJsonObject();
                if (!object.keySet().equals(Set.of("trait", valueKey))
                        || !object.get("trait").isJsonPrimitive()
                        || !object.getAsJsonPrimitive("trait").isString()) throw new IllegalArgumentException();
                String trait = object.get("trait").getAsString();
                if (values.has(trait)) throw new IllegalArgumentException();
                values.add(trait, object.get(valueKey));
            }
            recipe.add(key, values);
        }
    }

    static Path findExecutable() throws IOException {
        String configured = System.getenv("CODEX_CLI_PATH");
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured).toAbsolutePath();
            if (Files.isRegularFile(path) && !configured.endsWith(".cmd") && !configured.endsWith(".ps1")) return path;
            throw new IOException("Codex executable path must name its native executable");
        }
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        String searchPath = System.getenv("PATH");
        // PATH uses the platform separator, semicolon on Windows and colon on Unix-like systems.
        for (String directory : (searchPath == null ? "" : searchPath).split(java.io.File.pathSeparator)) {
            if (directory.isBlank()) continue;
            Path base = Path.of(directory.replace("\"", ""));
            Path direct = base.resolve(windows ? "codex.exe" : "codex");
            if (Files.isRegularFile(direct)) return direct.toAbsolutePath();
            if (windows) {
                for (String arch : List.of("x64", "arm64")) {
                    String target = arch.equals("x64") ? "x86_64" : "aarch64";
                    Path npm = base.resolve("node_modules/@openai/codex/node_modules/@openai/codex-win32-" + arch
                            + "/vendor/" + target + "-pc-windows-msvc/bin/codex.exe");
                    if (Files.isRegularFile(npm)) return npm.toAbsolutePath();
                }
            }
        }
        throw new IOException("Codex CLI was not found; install it or set CODEX_CLI_PATH");
    }

    private static CompletableFuture<byte[]> read(InputStream stream, Process child) {
        var result = new CompletableFuture<byte[]>();
        Thread.ofVirtual().name("infinitecraft-codex-output").start(() -> {
            try (stream) {
                byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) {
                    stop(child);
                    throw new IOException("Codex output limit exceeded");
                }
                result.complete(bytes);
            } catch (IOException error) {
                result.completeExceptionally(new IOException("Codex process output failed"));
            }
        });
        return result;
    }

    static void stop(Process process) {
        process.descendants().forEach(child -> { if (child.isAlive()) child.destroyForcibly(); });
        if (process.isAlive()) process.destroyForcibly();
        boolean interrupted = Thread.interrupted();
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            interrupted = true;
        }
        finally { if (interrupted) Thread.currentThread().interrupt(); }
    }
}
