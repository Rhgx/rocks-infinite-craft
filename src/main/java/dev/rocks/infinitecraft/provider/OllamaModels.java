package dev.rocks.infinitecraft.provider;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Model inventory only. No authentication data is sent or included in errors. */
public final class OllamaModels {
    private OllamaModels() {}

    public static List<String> fetch(String baseUrl) throws IOException, InterruptedException {
        ProviderConfig config;
        try {
            config = new ProviderConfig("ollama", baseUrl, "discovery", "", 5);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid Ollama URL");
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var request = HttpRequest.newBuilder(URI.create(config.baseUrl() + "/api/tags"))
                .timeout(Duration.ofSeconds(5)).header("Accept", "application/json").GET().build();
        var pending = client.sendAsync(request,
                HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), 1_048_576));
        try {
            var response = pending.get(5, TimeUnit.SECONDS);
            if (response.statusCode() != 200) throw new IOException("Ollama model discovery returned HTTP " + response.statusCode());
            try {
                var root = JsonParser.parseString(response.body()).getAsJsonObject();
                var models = root.getAsJsonArray("models");
                if (models == null) throw new IllegalArgumentException();
                var names = new TreeSet<String>();
                for (var model : models) {
                    var name = model.getAsJsonObject().get("name");
                    if (name == null || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
                    var validated = new ProviderConfig("ollama", config.baseUrl(), name.getAsString(), "", 5).model();
                    if (!validated.equals(name.getAsString())) throw new IllegalArgumentException();
                    names.add(validated);
                }
                return List.copyOf(names);
            } catch (RuntimeException error) {
                throw new IOException("Ollama returned invalid model data");
            }
        } catch (InterruptedException error) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw error;
        } catch (TimeoutException error) {
            pending.cancel(true);
            throw new IOException("Ollama model discovery timed out");
        } catch (ExecutionException error) {
            throw new IOException("Ollama model discovery failed or exceeded the response limit");
        } finally {
            client.shutdownNow();
        }
    }
}
