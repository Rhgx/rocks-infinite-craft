package dev.rocks.infinitecraft.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.rocks.infinitecraft.core.GenerationRequest;
import dev.rocks.infinitecraft.core.InvalidRecipeResponseException;
import dev.rocks.infinitecraft.core.RecipeGenerator;
import dev.rocks.infinitecraft.core.RecipeResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public final class HttpRecipeGenerator implements RecipeGenerator {
    private static final Gson JSON = new Gson();
    private static final int MAX_BODY = 1_048_576;
    private final ProviderConfig config;
    private final HttpClient client;
    private final Function<String, String> environment;

    public HttpRecipeGenerator(ProviderConfig config) { this(config, System::getenv); }

    HttpRecipeGenerator(ProviderConfig config, Function<String, String> environment) {
        this.config = config;
        this.environment = environment;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(config.timeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public RecipeResult generate(GenerationRequest request) throws Exception {
        return generateCandidates(request).getFirst();
    }

    @Override
    public List<RecipeResult> generateCandidates(GenerationRequest request) throws Exception {
        return generateCandidates(request, "");
    }

    @Override
    public List<RecipeResult> generateCandidates(GenerationRequest request, String feedback) throws Exception {
        if (config.provider().equals("disabled")) throw new IOException("Recipe generation is disabled");
        if (request.candidates().isEmpty()) throw new IOException("No crafting candidates available");
        String key = config.apiKey();
        if (key.isEmpty()) key = config.apiKeyEnv().isEmpty() ? "" : environment.apply(config.apiKeyEnv());
        if ((key == null || key.isBlank()) && !allowsNoKey(config.provider()))
            throw new IOException("Provider API key is missing; enter a key or set its environment variable");
        if (key != null && key.chars().anyMatch(c -> c < 33 || c > 126))
            throw new IOException("Provider API key contains invalid characters");
        JsonObject body = body(request, feedback);
        byte[] encoded = JSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_BODY) throw new IOException("Provider request exceeds size limit");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.baseUrl() + path()))
                .timeout(Duration.ofSeconds(config.timeoutSeconds())).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(encoded));
        if (key != null && !key.isBlank()) builder.header(switch (config.provider()) {
            case "anthropic" -> "x-api-key";
            case "gemini" -> "x-goog-api-key";
            default -> "Authorization";
        }, (config.provider().equals("anthropic") || config.provider().equals("gemini") ? "" : "Bearer ") + key);
        if (config.provider().equals("anthropic")) builder.header("anthropic-version", "2023-06-01");
        CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(builder.build(), ignored -> new LimitedBody());
        HttpResponse<byte[]> response;
        try {
            response = pending.get(config.timeoutSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Provider request interrupted");
        } catch (TimeoutException e) {
            pending.cancel(true); throw new IOException("Provider request timed out");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof HttpTimeoutException) throw new IOException("Provider request timed out");
            throw new IOException("Provider connection failed or response exceeded size limit");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("Provider returned HTTP " + response.statusCode());
        try {
            JsonObject envelope = RecipeResponseParser.parseObject(new String(response.body(), StandardCharsets.UTF_8));
            if (config.provider().equals("ollama")) {
                org.slf4j.LoggerFactory.getLogger(HttpRecipeGenerator.class).debug(
                        "Ollama timing: load={} ns, prompt={} ns, output={} ns, promptTokens={}, cachedTokens={}, outputTokens={}",
                        envelope.get("load_duration"), envelope.get("prompt_eval_duration"), envelope.get("eval_duration"),
                        envelope.get("prompt_eval_count"), envelope.get("prompt_eval_cached_count"), envelope.get("eval_count"));
            }
            return RecipeResponseParser.parseCandidates(extract(envelope), request);
        } catch (RuntimeException | IOException e) {
            throw new InvalidRecipeResponseException();
        }
    }

    private static boolean allowsNoKey(String provider) { return provider.equals("ollama") || provider.equals("compatible"); }

    private String path() {
        return switch (config.provider()) {
            case "ollama" -> "/api/chat";
            case "openai" -> "/responses";
            case "anthropic" -> "/messages";
            // Gemini accepts the model with or without its conventional "models/" prefix.
            case "gemini" -> "/models/" + config.model().replaceFirst("^models/", "") + ":generateContent";
            default -> "/chat/completions";
        };
    }

    private JsonObject body(GenerationRequest request, String feedback) {
        String prompt = RecipePrompt.build(request, false, feedback);
        JsonObject body = new JsonObject();
        if (!config.provider().equals("gemini")) body.addProperty("model", config.model());
        switch (config.provider()) {
            case "openai" -> {
                body.addProperty("input", prompt);
                body.addProperty("store", false);
                body.addProperty("max_output_tokens", 2048);
                body.add("text", JSON.toJsonTree(java.util.Map.of("format", java.util.Map.of("type", "json_object"))));
            }
            case "gemini" -> {
                body.add("contents", JSON.toJsonTree(List.of(java.util.Map.of("role", "user", "parts", List.of(java.util.Map.of("text", prompt))))));
                body.add("generationConfig", JSON.toJsonTree(java.util.Map.of("responseMimeType", "application/json", "maxOutputTokens", 2048)));
            }
            default -> {
                body.add("messages", JSON.toJsonTree(List.of(java.util.Map.of("role", "user", "content", prompt))));
                if (config.provider().equals("ollama")) {
                    body.addProperty("stream", false);
                    body.addProperty("keep_alive", -1);
                    // Recipe selection needs a short answer; thinking can exhaust the output budget.
                    body.addProperty("think", false);
                    body.add("format", RecipeSchema.build(request));
                    body.add("options", JSON.toJsonTree(java.util.Map.of("num_predict", 2048, "temperature", 0.2, "presence_penalty", 0.0, "num_ctx", 8192)));
                } else { body.addProperty("max_tokens", 2048); }
            }
        }
        return body;
    }

    private String extract(JsonObject body) {
        return switch (config.provider()) {
            case "ollama" -> body.getAsJsonObject("message").get("content").getAsString();
            case "openai" -> {
                StringBuilder text = new StringBuilder();
                for (JsonElement output : body.getAsJsonArray("output")) {
                    JsonObject item = output.getAsJsonObject();
                    if (item.has("content")) for (JsonElement part : item.getAsJsonArray("content")) {
                        JsonObject p = part.getAsJsonObject();
                        if (p.has("type") && p.get("type").getAsString().equals("output_text")) text.append(p.get("text").getAsString());
                    }
                }
                yield text.toString();
            }
            case "anthropic" -> textParts(body.getAsJsonArray("content"));
            case "gemini" -> textParts(body.getAsJsonArray("candidates").get(0).getAsJsonObject().getAsJsonObject("content").getAsJsonArray("parts"));
            default -> body.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
        };
    }

    private static String textParts(JsonArray parts) {
        StringBuilder text = new StringBuilder();
        for (JsonElement part : parts) {
            JsonObject p = part.getAsJsonObject();
            if (p.has("text") && (!p.has("thought") || !p.get("thought").getAsBoolean())) text.append(p.get("text").getAsString());
        }
        return text.toString();
    }

    // Cancel while receiving instead of buffering an unbounded provider response.
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_BODY - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("Response too large"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
