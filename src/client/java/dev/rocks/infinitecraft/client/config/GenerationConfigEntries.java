package dev.rocks.infinitecraft.client.config;

import dev.rocks.infinitecraft.ModConfig;
import dev.rocks.infinitecraft.client.config.ProviderEntry.Provider;
import dev.rocks.infinitecraft.provider.ProviderConfig;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

final class GenerationConfigEntries {
    private GenerationConfigEntries() {
    }

    static Supplier<ProviderConfig> add(ConfigCategory ai, ConfigCategory limits,
            ConfigEntryBuilder entries, ConfigEntries fields, ModConfig config) {
        var generation = entries
                .startBooleanToggle(Component.literal("Generate new recipes"), config.generationEnabled)
                .setDefaultValue(false)
                .setTooltip(Component.literal(
                        "Explicit and discovered recipes still work when disabled. Hosted APIs can charge per request."))
                .setSaveConsumer(value -> config.generationEnabled = value)
                .build();
        var provider = new ProviderEntry(Provider.valueOf(config.provider.provider().toUpperCase(Locale.ROOT)));
        var baseUrl = entries.startStrField(Component.literal("API base URL"), config.provider.baseUrl())
                .setDefaultValue("")
                .setTooltip(Component.literal(
                        "Clear to use this provider's default. Include /v1 when required; do not include an endpoint."))
                .build();
        var customModels = new EnumMap<Provider, me.shedaniel.clothconfig2.gui.entries.StringListEntry>(Provider.class);
        for (var choice : Provider.values()) {
            boolean local = choice == Provider.OLLAMA || choice == Provider.CODEX;
            var customModel = entries.startStrField(Component.literal(local ? "Custom model" : "Model ID"),
                            choice == provider.getValue() ? config.provider.model() : "")
                    .setDefaultValue("")
                    .build();
            customModels.put(choice, customModel);
        }
        var model = new ModelDropdownEntry(config.provider.model(), provider::getValue, () -> {
            if (provider.getValue() != Provider.OLLAMA) return null;
            return providerValue(provider.getValue(), config, baseUrl.getValue(), config.provider.baseUrl());
        }, () -> customModels.get(provider.getValue()).getValue());
        model.setDisplayRequirement(model::hasCatalog);
        customModels.forEach((choice, field) -> field.setDisplayRequirement(() ->
                provider.getValue() == choice && choice != Provider.DISABLED && model.isCustom()));
        var reasoning = new ReasoningEntry(config.provider.reasoning(), model::reasoningLevels);
        reasoning.setDisplayRequirement(() -> provider.getValue() == Provider.CODEX);
        var fast = entries.startBooleanToggle(Component.literal("Fast mode"), config.provider.fastMode())
                .setDefaultValue(false)
                .setTooltip(Component.literal("Priority processing when supported by your model and plan."))
                .build();
        fast.setDisplayRequirement(() -> provider.getValue() == Provider.CODEX);
        var key = new SecretEntry(config.provider.apiKey());
        var keyEnv = entries.startStrField(Component.literal("API key variable"), config.provider.apiKeyEnv()).setDefaultValue("")
                .setTooltip(Component.literal("Used when the API key is empty.")).build();
        var timeout = entries.startIntField(Component.literal("Timeout, seconds"), config.provider.timeoutSeconds())
                .setDefaultValue(60).setMin(1).setMax(300).build();
        Supplier<ProviderConfig> providerValue = () -> {
            Provider selected = provider.getValue();
            if (selected == null) throw new IllegalArgumentException("Choose a provider.");

            String selectedBaseUrl = providerValue(selected, config, baseUrl.getValue(), config.provider.baseUrl());
            String selectedKeyEnv = providerValue(selected, config, keyEnv.getValue(), config.provider.apiKeyEnv());
            String selectedKey = providerValue(selected, config, key.getValue(), config.provider.apiKey());
            return new ProviderConfig(
                    selected.name().toLowerCase(Locale.ROOT), selectedBaseUrl, model.getValue(), selectedKeyEnv,
                    timeout.getValue(), selectedKey, reasoning.getValue(), fast.getValue());
        };
        model.setErrorSupplier(() -> {
            try {
                ProviderConfig value = providerValue.get();
                if (generation.getValue() && value.provider().equals("disabled")) {
                    return Optional.of(Component.literal("Choose a provider to enable generation."));
                }
                return Optional.empty();
            } catch (IllegalArgumentException error) {
                return Optional.of(Component.literal(error.getMessage()));
            }
        });
        Supplier<Boolean> http = () -> provider.getValue() != Provider.CODEX && provider.getValue() != Provider.DISABLED;
        baseUrl.setDisplayRequirement(http::get);
        key.setDisplayRequirement(http::get);
        keyEnv.setDisplayRequirement(http::get);
        var connectionHeader = entries.startTextDescription(
                Component.literal("Connection").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build();
        connectionHeader.setDisplayRequirement(http::get);
        ai.addEntry(generation);
        ai.addEntry(provider);
        fields.section(ai, "Model");
        ai.addEntry(model);
        customModels.values().forEach(ai::addEntry);
        ai.addEntry(reasoning);
        ai.addEntry(fast);
        ai.addEntry(connectionHeader);
        ai.addEntry(key);
        var connectionTest = new ConnectionTestEntry(providerValue);
        connectionTest.setDisplayRequirement(() -> provider.getValue() != Provider.DISABLED);
        ai.addEntry(connectionTest);

        fields.section(limits, "Requests");
        limits.addEntry(timeout);
        fields.integer(limits, "Generation attempts", config.generationAttempts, 3, 1, 4,
                value -> config.generationAttempts = value);
        fields.integer(limits, "Generation threads", config.generationThreads, 1, 1, 8,
                "Recipes that may generate at the same time.", value -> config.generationThreads = value);
        var endpointHeader = entries.startTextDescription(
                Component.literal("Connection overrides").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build();
        endpointHeader.setDisplayRequirement(http::get);
        limits.addEntry(endpointHeader);
        limits.addEntry(baseUrl);
        limits.addEntry(keyEnv);
        fields.section(limits, "Scanning");
        fields.integer(limits, "Scan interval, ticks", config.scanIntervalTicks, 10, 1, 200,
                value -> config.scanIntervalTicks = value);
        fields.integer(limits, "Nearby item limit", config.maxNearbyItems, 64, 2, 256,
                value -> config.maxNearbyItems = value);
        fields.integer(limits, "Pending exchange limit", config.maxPending, 8, 1, 64,
                value -> config.maxPending = value);
        fields.integer(limits, "Candidate count", config.candidateLimit, 48, 1, 256,
                value -> config.candidateLimit = value);
        fields.integer(limits, "Cooldown, ticks", config.cooldownTicks, 100, 20, 12000,
                value -> config.cooldownTicks = value);

        return providerValue;
    }

    /** Keeps an edited override, but drops a stale value inherited from the previous provider. */
    private static String providerValue(Provider selected, ModConfig config, String current, String saved) {
        boolean sameProvider = selected.name().equalsIgnoreCase(config.provider.provider());
        return sameProvider || !current.equals(saved) ? current : "";
    }
}
