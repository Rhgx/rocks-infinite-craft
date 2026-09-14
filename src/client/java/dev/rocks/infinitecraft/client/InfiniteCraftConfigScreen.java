package dev.rocks.infinitecraft.client;

import dev.rocks.infinitecraft.InfiniteCraftMod;
import dev.rocks.infinitecraft.ModConfig;
import dev.rocks.infinitecraft.provider.ProviderConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import dev.rocks.infinitecraft.client.ProviderEntry.Provider;
import java.util.Optional;
import java.util.function.Supplier;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

final class InfiniteCraftConfigScreen {

    static Screen create(Screen parent) {
        Minecraft client = Minecraft.getInstance();
        Path path = FabricLoader.getInstance().getConfigDir().resolve("infinitecraft.json");
        ModConfig config;
        try { config = ModConfig.load(path); }
        catch (IOException error) {
            return new AlertScreen(() -> client.setScreenAndShow(parent), Component.literal("Cannot load Infinite Craft settings"),
                    Component.literal("Check config/infinitecraft.json."));
        }
        var builder = ConfigBuilder.create().setParentScreen(parent).setTitle(Component.literal("Rocks' Infinite Craft").withStyle(ChatFormatting.GOLD));
        var entries = builder.entryBuilder();
        var general = builder.getOrCreateCategory(Component.literal("Gameplay"));
        var ai = builder.getOrCreateCategory(Component.literal("Generation"));
        var effects = builder.getOrCreateCategory(Component.literal("Effects"));
        var limits = builder.getOrCreateCategory(Component.literal("Advanced"));
        general.addEntry(entries.startTextDescription(Component.literal("Fusion").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Enable item fusion"), config.enabled)
                .setDefaultValue(true).setSaveConsumer(value -> config.enabled = value).build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Ground fusion"), config.groundFusion)
                .setDefaultValue(true).setSaveConsumer(value -> config.groundFusion = value).build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Fusion Crafter"), config.crafterFusion)
                .setDefaultValue(true).setTooltip(Component.literal("Fuses items placed in two Crafter slots."))
                .setSaveConsumer(value -> config.crafterFusion = value).build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Allow modded items"), config.allowModdedItems)
                .setDefaultValue(true).setSaveConsumer(value -> config.allowModdedItems = value).build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Item data fusion"), config.allowItemData)
                .setDefaultValue(true).setTooltip(Component.literal("Preserve enchantments, potions, names and durability."))
                .setSaveConsumer(value -> config.allowItemData = value).build());
        general.addEntry(entries.startTextDescription(Component.literal("Recipe style").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build());
        general.addEntry(new RecipeStyleEntry("Power", config.power,
                java.util.List.of("Gentle", "Mild", "Balanced", "Powerful", "Overpowered"), value -> config.power = value));
        general.addEntry(new RecipeStyleEntry("Silliness", config.silliness,
                java.util.List.of("Sensible", "Playful", "Silly", "Absurd", "Ridiculous"), value -> config.silliness = value));
        general.addEntry(entries.startTextDescription(Component.literal("Output").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build());
        general.addEntry(entries.startIntSlider(Component.literal("Maximum traits"), config.maxTraits, 0, 8)
                .setDefaultValue(3).setTextGetter(value -> Component.literal(Integer.toString(value)))
                .setSaveConsumer(value -> config.maxTraits = value).build());
        general.addEntry(entries.startIntSlider(Component.literal("Maximum quantity"), config.maxOutputCount, 1, 64)
                .setDefaultValue(8).setTextGetter(value -> Component.literal(Integer.toString(value))).setSaveConsumer(value -> config.maxOutputCount = value).build());

        effects.addEntry(entries.startTextDescription(Component.literal("Particles").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build());
        effects.addEntry(entries.startBooleanToggle(Component.literal("Combining particles"), config.combiningParticles)
                .setDefaultValue(true).setSaveConsumer(value -> config.combiningParticles = value).build());
        effects.addEntry(entries.startBooleanToggle(Component.literal("Failure particles"), config.failureParticles)
                .setDefaultValue(true).setSaveConsumer(value -> config.failureParticles = value).build());
        effects.addEntry(entries.startBooleanToggle(Component.literal("Success particles"), config.successParticles)
                .setDefaultValue(true).setSaveConsumer(value -> config.successParticles = value).build());
        effects.addEntry(entries.startTextDescription(Component.literal("Sound").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build());
        effects.addEntry(entries.startBooleanToggle(Component.literal("Success sound"), config.successSound)
                .setDefaultValue(true).setSaveConsumer(value -> config.successSound = value).build());
        effects.addEntry(entries.startBooleanToggle(Component.literal("Failure sound"), config.failureSound)
                .setDefaultValue(true).setSaveConsumer(value -> config.failureSound = value).build());

        general.addEntry(entries.startTextDescription(Component.literal("Special items").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build());
        var special = entries.startBooleanToggle(Component.literal("Special results"), config.generatedTraits)
                .setDefaultValue(true).setSaveConsumer(value -> config.generatedTraits = value).build();
        general.addEntry(special);
        general.addEntry(entries.startBooleanToggle(Component.literal("Combine special items"), config.combineSpecialItems)
                .setDefaultValue(false).setTooltip(Component.literal("Allow two crafted special items to fuse together."))
                .setSaveConsumer(value -> config.combineSpecialItems = value).build());
        var chance = entries.startIntSlider(Component.literal("Random chance"), config.specialResultChance, 0, 100)
                .setDefaultValue(5).setTextGetter(value -> Component.literal(value + "%")).setTooltip(Component.literal("Chance of a special result."))
                .setSaveConsumer(value -> config.specialResultChance = value).build();
        chance.setDisplayRequirement(special::getValue);
        general.addEntry(chance);
        var triggers = entries.startBooleanToggle(Component.literal("Item triggers"), config.specialIngredientTriggers)
                .setDefaultValue(true).setTooltip(Component.literal("Selected item types bypass the chance roll."))
                .setSaveConsumer(value -> config.specialIngredientTriggers = value).build();
        triggers.setDisplayRequirement(special::getValue);
        general.addEntry(triggers);
        general.addEntry(entries.startTextDescription(Component.literal("Special item types").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Rarity"), config.specialRarity)
                .setDefaultValue(true).setTooltip(Component.literal("Uncommon, Rare and Epic items. Also enables rarity quality boosts."))
                .setSaveConsumer(value -> config.specialRarity = value).build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Enchantments"), config.specialEnchantments)
                .setDefaultValue(true).setTooltip(Component.literal("Enchanted items and books."))
                .setSaveConsumer(value -> config.specialEnchantments = value).build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Potions and stew"), config.specialPotions)
                .setDefaultValue(true).setSaveConsumer(value -> config.specialPotions = value).build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Custom item data"), config.specialCustomData)
                .setDefaultValue(true).setTooltip(Component.literal("Names, dyes, models and modified traits."))
                .setSaveConsumer(value -> config.specialCustomData = value).build());
        general.addEntry(entries.startTextDescription(Component.literal("Discovery Book").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build());
        var bookOwnership = new SelectionEntry<>("Book ownership", config.soulboundBook, true,
                java.util.List.of(true, false), value -> value ? "Soulbound" : "Craftable");
        var bookVisibility = new SelectionEntry<>("Book visibility", config.personalBook, false,
                java.util.List.of(false, true), value -> value ? "Personal" : "Global");
        general.addEntry(bookOwnership);
        general.addEntry(bookVisibility);
        effects.addEntry(entries.startTextDescription(Component.literal("Messages").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build());
        effects.addEntry(entries.startBooleanToggle(Component.literal("Join message"), config.joinMessage)
                .setDefaultValue(true).setSaveConsumer(value -> config.joinMessage = value).build());
        effects.addEntry(entries.startBooleanToggle(Component.literal("First discovery messages"), config.firstDiscoveryMessage)
                .setDefaultValue(true).setSaveConsumer(value -> config.firstDiscoveryMessage = value).build());
        effects.addEntry(entries.startBooleanToggle(Component.literal("Special discovery messages"), config.specialDiscoveryMessage)
                .setDefaultValue(true).setSaveConsumer(value -> config.specialDiscoveryMessage = value).build());
        effects.addEntry(entries.startBooleanToggle(Component.literal("Discovery milestones"), config.milestoneMessages)
                .setDefaultValue(true).setSaveConsumer(value -> config.milestoneMessages = value).build());
        effects.addEntry(entries.startBooleanToggle(Component.literal("Generation queue feedback"), config.queueFeedback)
                .setDefaultValue(true).setSaveConsumer(value -> config.queueFeedback = value).build());
        var generation = entries.startBooleanToggle(Component.literal("Generate new recipes"), config.generationEnabled)
                .setDefaultValue(false).setTooltip(Component.literal("Explicit and discovered recipes still work when disabled. Hosted APIs can charge per request."))
                .setSaveConsumer(value -> config.generationEnabled = value).build();
        var provider = new ProviderEntry(Provider.valueOf(config.provider.provider().toUpperCase(Locale.ROOT)));
        var baseUrl = entries.startStrField(Component.literal("API base URL"), config.provider.baseUrl()).setDefaultValue("")
                .setTooltip(Component.literal("Clear to use this provider's default. Include /v1 when required; do not include /responses or /chat/completions.")).build();
        var customModels = new java.util.EnumMap<Provider, me.shedaniel.clothconfig2.gui.entries.StringListEntry>(Provider.class);
        for (var choice : Provider.values()) {
            boolean local = choice == Provider.OLLAMA || choice == Provider.CODEX;
            customModels.put(choice, entries.startStrField(Component.literal(local ? "Custom model" : "Model ID"),
                    choice == provider.getValue() ? config.provider.model() : "").setDefaultValue("").build());
        }
        var model = new ModelDropdownEntry(config.provider.model(), provider::getValue, () -> {
            if (provider.getValue() != Provider.OLLAMA) return null;
            return config.provider.provider().equals("ollama") || !baseUrl.getValue().equals(config.provider.baseUrl())
                    ? baseUrl.getValue() : "";
        }, () -> customModels.get(provider.getValue()).getValue());
        model.setDisplayRequirement(model::hasCatalog);
        customModels.forEach((choice, field) -> field.setDisplayRequirement(() ->
                provider.getValue() == choice && choice != Provider.DISABLED && model.isCustom()));
        var reasoning = new ReasoningEntry(config.provider.reasoning(), model::reasoningLevels);
        reasoning.setDisplayRequirement(() -> provider.getValue() == Provider.CODEX);
        var fast = entries.startBooleanToggle(Component.literal("Fast mode"), config.provider.fastMode())
                .setDefaultValue(false).setTooltip(Component.literal("Priority processing when supported by your model and plan.")).build();
        fast.setDisplayRequirement(() -> provider.getValue() == Provider.CODEX);
        var key = new SecretEntry(config.provider.apiKey());
        var keyEnv = entries.startStrField(Component.literal("API key variable"), config.provider.apiKeyEnv()).setDefaultValue("")
                .setTooltip(Component.literal("Used when the API key is empty.")).build();
        var timeout = entries.startIntField(Component.literal("Timeout, seconds"), config.provider.timeoutSeconds())
                .setDefaultValue(60).setMin(1).setMax(300).build();
        Supplier<ProviderConfig> providerValue = () -> {
            if (provider.getValue() == null) throw new IllegalArgumentException("Choose a provider.");
            return new ProviderConfig(provider.getValue().name().toLowerCase(Locale.ROOT),
                // A stale built-in URL/key variable should follow a newly chosen provider.
                provider.getValue().name().equalsIgnoreCase(config.provider.provider()) || !baseUrl.getValue().equals(config.provider.baseUrl())
                        ? baseUrl.getValue() : "",
                model.getValue(),
                provider.getValue().name().equalsIgnoreCase(config.provider.provider()) || !keyEnv.getValue().equals(config.provider.apiKeyEnv())
                        ? keyEnv.getValue() : "",
                timeout.getValue(), provider.getValue().name().equalsIgnoreCase(config.provider.provider())
                        || !key.getValue().equals(config.provider.apiKey()) ? key.getValue() : "", reasoning.getValue(), fast.getValue());
        };
        model.setErrorSupplier(() -> {
            try {
                ProviderConfig value = providerValue.get();
                if (generation.getValue() && value.provider().equals("disabled"))
                    return Optional.of(Component.literal("Choose a provider to enable generation."));
                return Optional.empty();
            } catch (IllegalArgumentException error) { return Optional.of(Component.literal(error.getMessage())); }
        });
        Supplier<Boolean> http = () -> provider.getValue() != Provider.CODEX && provider.getValue() != Provider.DISABLED;
        baseUrl.setDisplayRequirement(http::get); key.setDisplayRequirement(http::get); keyEnv.setDisplayRequirement(http::get);
        var connectionHeader = entries.startTextDescription(Component.literal("Connection").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build();
        connectionHeader.setDisplayRequirement(http::get);
        ai.addEntry(generation);
        ai.addEntry(provider);
        ai.addEntry(entries.startTextDescription(Component.literal("Model").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build());
        ai.addEntry(model);
        customModels.values().forEach(ai::addEntry);
        ai.addEntry(reasoning); ai.addEntry(fast);
        ai.addEntry(connectionHeader); ai.addEntry(key);
        var connectionTest = new ConnectionTestEntry(providerValue);
        connectionTest.setDisplayRequirement(() -> provider.getValue() != Provider.DISABLED);
        ai.addEntry(connectionTest);

        limits.addEntry(entries.startTextDescription(Component.literal("Requests").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build());
        limits.addEntry(timeout);
        limits.addEntry(entries.startIntField(Component.literal("Generation attempts"), config.generationAttempts)
                .setDefaultValue(3).setMin(1).setMax(4).setSaveConsumer(value -> config.generationAttempts = value).build());
        limits.addEntry(entries.startIntField(Component.literal("Generation threads"), config.generationThreads)
                .setDefaultValue(1).setMin(1).setMax(8)
                .setTooltip(Component.literal("Recipes that may generate at the same time."))
                .setSaveConsumer(value -> config.generationThreads = value).build());
        var endpointHeader = entries.startTextDescription(Component.literal("Connection overrides").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build();
        endpointHeader.setDisplayRequirement(http::get);
        limits.addEntry(endpointHeader);
        limits.addEntry(baseUrl); limits.addEntry(keyEnv);
        limits.addEntry(entries.startTextDescription(Component.literal("Scanning").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build());
        limits.addEntry(entries.startIntField(Component.literal("Scan interval, ticks"), config.scanIntervalTicks).setDefaultValue(10)
                .setMin(1).setMax(200).setSaveConsumer(value -> config.scanIntervalTicks = value).build());
        limits.addEntry(entries.startIntField(Component.literal("Nearby item limit"), config.maxNearbyItems).setDefaultValue(64)
                .setMin(2).setMax(256).setSaveConsumer(value -> config.maxNearbyItems = value).build());
        limits.addEntry(entries.startIntField(Component.literal("Pending exchange limit"), config.maxPending).setDefaultValue(8)
                .setMin(1).setMax(64).setSaveConsumer(value -> config.maxPending = value).build());
        limits.addEntry(entries.startIntField(Component.literal("Candidate count"), config.candidateLimit).setDefaultValue(48)
                .setMin(1).setMax(256).setSaveConsumer(value -> config.candidateLimit = value).build());
        limits.addEntry(entries.startIntField(Component.literal("Cooldown, ticks"), config.cooldownTicks).setDefaultValue(100)
                .setMin(20).setMax(12000).setSaveConsumer(value -> config.cooldownTicks = value).build());

        builder.setSavingRunnable(() -> {
            try {
                config.provider = providerValue.get();
                config.soulboundBook = bookOwnership.getValue();
                config.personalBook = bookVisibility.getValue();
                config.save(path);
                var server = client.getSingleplayerServer();
                if (server != null) server.execute(() -> InfiniteCraftMod.applyConfigToIntegratedServer(server, config));
            } catch (IOException | RuntimeException error) {
                // Schedule after Cloth closes the form so its parent-screen transition cannot hide this error.
                client.schedule(() -> client.setScreenAndShow(new AlertScreen(() -> client.setScreenAndShow(create(parent)),
                        Component.literal("Settings could not be saved"), Component.literal("Check your settings and file permissions."))));
            }
        });
        return builder.build();
    }
}
