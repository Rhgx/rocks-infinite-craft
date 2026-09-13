package dev.rocks.infinitecraft.provider;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

public record ProviderConfig(String provider, String baseUrl, String model, String apiKeyEnv, int timeoutSeconds, String apiKey, String reasoning, boolean fastMode) {
    public static final java.util.List<String> REASONING_LEVELS = java.util.List.of("minimal", "low", "medium", "high", "xhigh");

    public ProviderConfig(String provider, String baseUrl, String model, String apiKeyEnv, int timeoutSeconds, String apiKey, String reasoning) {
        this(provider, baseUrl, model, apiKeyEnv, timeoutSeconds, apiKey, reasoning, false);
    }

    public ProviderConfig(String provider, String baseUrl, String model, String apiKeyEnv, int timeoutSeconds, String apiKey) {
        this(provider, baseUrl, model, apiKeyEnv, timeoutSeconds, apiKey, "low");
    }
    public ProviderConfig(String provider, String baseUrl, String model, String apiKeyEnv, int timeoutSeconds) {
        this(provider, baseUrl, model, apiKeyEnv, timeoutSeconds, "");
    }

    public ProviderConfig {
        reasoning = reasoning == null ? "low" : reasoning.trim().toLowerCase(Locale.ROOT);
        if (!reasoning.equals("default") && !REASONING_LEVELS.contains(reasoning))
            throw new IllegalArgumentException("Unknown reasoning level");
        apiKey = apiKey == null ? "" : apiKey;
        if (apiKey.chars().anyMatch(c -> c < 33 || c > 126))
            throw new IllegalArgumentException("Provider API key contains invalid characters");
        provider = provider == null ? "disabled" : provider.toLowerCase(Locale.ROOT).trim();
        if (!Set.of("disabled", "ollama", "openai", "anthropic", "gemini", "openrouter", "compatible", "codex").contains(provider))
            throw new IllegalArgumentException("Unknown recipe provider");
        model = model == null ? "" : model.trim();
        if (!provider.equals("disabled") && !(provider.equals("codex") && model.isEmpty())
                && (model.isEmpty() || !model.matches("[A-Za-z0-9_./:@-]{1,200}")))
            throw new IllegalArgumentException("An explicit provider model is required");
        if (timeoutSeconds < 1 || timeoutSeconds > 300)
            throw new IllegalArgumentException("Provider timeout must be between 1 and 300 seconds");
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = switch (provider) {
            case "ollama" -> "http://localhost:11434";
            case "openai" -> "https://api.openai.com/v1";
            case "anthropic" -> "https://api.anthropic.com/v1";
            case "gemini" -> "https://generativelanguage.googleapis.com/v1beta";
            case "openrouter" -> "https://openrouter.ai/api/v1";
            case "disabled", "codex" -> "http://localhost";
            default -> throw new IllegalArgumentException("Compatible provider requires baseUrl");
        };
        baseUrl = baseUrl.trim().replaceAll("/+$", "");
        URI uri;
        try { uri = URI.create(baseUrl); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid provider baseUrl"); }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Provider baseUrl must have a host and no credentials, query, or fragment");
        boolean loopback = Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
        if (!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) && loopback))
            throw new IllegalArgumentException("Provider requires HTTPS except on loopback");
        if (apiKeyEnv == null || apiKeyEnv.isBlank()) apiKeyEnv = switch (provider) {
            case "openai" -> "OPENAI_API_KEY";
            case "anthropic" -> "ANTHROPIC_API_KEY";
            case "gemini" -> "GEMINI_API_KEY";
            case "openrouter" -> "OPENROUTER_API_KEY";
            default -> "";
        };
        if (!apiKeyEnv.isEmpty() && !apiKeyEnv.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalArgumentException("apiKeyEnv must name an environment variable");
    }

    public static ProviderConfig defaults() { return new ProviderConfig("disabled", "", "", "", 60); }

    @Override public String toString() {
        return "ProviderConfig[provider=" + provider + ", baseUrl=" + baseUrl + ", model=" + model
                + ", apiKeyEnv=" + apiKeyEnv + ", timeoutSeconds=" + timeoutSeconds + ", apiKey=<redacted>]";
    }
}
