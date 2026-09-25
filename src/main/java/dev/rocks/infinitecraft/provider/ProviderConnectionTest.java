package dev.rocks.infinitecraft.provider;

import dev.rocks.infinitecraft.core.CatalogEntry;
import dev.rocks.infinitecraft.core.GenerationRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Uses the real recipe protocol without reading or modifying a world or saved settings. */
public final class ProviderConnectionTest {
    public record Result(boolean success, String message) {}
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final GenerationRequest REQUEST = new GenerationRequest("minecraft:stone", "minecraft:stone",
            List.of(new CatalogEntry("minecraft:cobblestone", "item", "minecraft", "Cobblestone", List.of(), true, "")));

    private ProviderConnectionTest() {}

    public static CompletableFuture<Result> start(ProviderConfig config) {
        if (!RUNNING.compareAndSet(false, true))
            return CompletableFuture.completedFuture(new Result(false, "A connection test is already running. Wait for it to finish."));
        var result = new CompletableFuture<Result>();
        Thread worker = Thread.ofVirtual().name("infinite-craft-connection-test").unstarted(() -> {
            Result outcome;
            try {
                RecipeGenerators.create(config).generate(REQUEST);
                outcome = new Result(true, "Connected. The selected model returned a valid test recipe.");
            } catch (Exception error) {
                outcome = new Result(false, failureMessage(error));
            } finally {
                RUNNING.set(false);
            }
            result.complete(outcome);
        });
        result.whenComplete((ignored, error) -> {
            if (result.isCancelled()) worker.interrupt();
        });
        worker.start();
        return result;
    }

    static String failureMessage(Exception error) {
        // Only fixed messages are displayed; never show provider bodies, request URLs, or exception chains.
        String message = error.getMessage() == null ? "" : error.getMessage();
        return switch (message) {
            case "Codex CLI was not found; install it or set CODEX_CLI_PATH" -> "Install Codex CLI or set CODEX_CLI_PATH to its native executable.";
            case "Codex CLI failed; check login, model access, and CLI version" -> "Codex failed. Run codex login and check the model and CLI version.";
            case "Codex request timed out" -> "Codex timed out. Check connectivity or increase the timeout.";
            case "Recipe generation is disabled" -> "Choose a provider before testing the connection.";
            case "Provider API key is missing; enter a key or set its environment variable" -> "Enter an API key or set the configured environment variable.";
            case "Provider API key contains invalid characters" -> "The API key contains invalid characters. Check for spaces or line breaks.";
            case "Provider request timed out" -> "Request timed out. Check the server, model availability, or increase the timeout.";
            case "Provider request interrupted" -> "Connection test was interrupted. Try again.";
            case "Provider returned HTTP 401", "Provider returned HTTP 403" -> "Authentication failed. Check the API key and model access permissions.";
            case "Provider returned HTTP 404" -> "Endpoint or model was not found. Check the base URL and exact model name.";
            case "Provider returned HTTP 429" -> "Provider limit reached. Check account credits and rate limits before retrying.";
            case "Provider returned HTTP 400", "Provider returned HTTP 422" -> "Provider rejected the request. Check the model and API compatibility.";
            case "Provider returned an invalid recipe response" -> "Provider responded, but the model did not return a valid recipe. Try another model.";
            case "Provider connection failed or response exceeded size limit" -> "Connection failed. Check the server address, TLS, and network availability.";
            default -> "Connection test failed. Check the provider, model, and server availability.";
        };
    }
}
