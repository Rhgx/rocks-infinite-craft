package dev.rocks.infinitecraft.engine;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

/** Player-facing failures must never expose provider responses or credentials. */
public final class GenerationFailure {
    private GenerationFailure() {}

    public static String message(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        if (failure instanceof BlockedRecipeException) return "No valid result. Combination blocked.";
        if (failure instanceof CancellationException) return "Fusion cancelled.";
        if (failure instanceof RejectedExecutionException) return "Fusion queue full. Try again shortly.";
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        return switch (message) {
            case "Recipe generation is disabled" -> "Generation is off. Only saved recipes can fuse.";
            case "Provider returned HTTP 429" -> "Provider limit reached. Try again later.";
            case "Provider returned HTTP 401", "Provider returned HTTP 403",
                    "Provider API key is missing; enter a key or set its environment variable",
                    "Provider API key contains invalid characters" -> "Provider access failed. Ask the host to check settings.";
            case "Provider request timed out", "Codex request timed out" -> "Generation timed out. You can retry this combination.";
            default -> failure instanceof IOException
                    ? "Provider unavailable. You can retry this combination." : "Fusion failed. You can retry this combination.";
        };
    }
}
