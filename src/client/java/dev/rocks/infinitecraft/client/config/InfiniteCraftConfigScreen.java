package dev.rocks.infinitecraft.client.config;

import dev.rocks.infinitecraft.InfiniteCraftMod;
import dev.rocks.infinitecraft.ModConfig;
import dev.rocks.infinitecraft.provider.ProviderConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import net.minecraft.network.chat.FormattedText;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

public final class InfiniteCraftConfigScreen {

    public static Screen create(Screen parent) {
        Minecraft client = Minecraft.getInstance();
        Path path = FabricLoader.getInstance().getConfigDir().resolve("infinitecraft.json");
        ModConfig config;
        try {
            config = ModConfig.load(path);
        } catch (IOException error) {
            return new AlertScreen(() -> client.setScreenAndShow(parent), Component.literal("Cannot load Infinite Craft settings"),
                    Component.literal("Check config/infinitecraft.json."));
        }
        var builder = ConfigBuilder.create().setParentScreen(parent).setTitle(Component.literal("Rocks' Infinite Craft").withStyle(ChatFormatting.GOLD));
        var entries = builder.entryBuilder();
        var fields = new ConfigEntries(entries);
        // Tabs follow a first-time setup: how fusion plays, where recipes come from, then refinements.
        var gameplay = category(builder, "Gameplay", "Fusion methods, recipe style and the Discovery Book.");
        var generation = category(builder, "AI Generation", "The provider and model that write new recipes.");
        var special = category(builder, "Special Items", "Rare results with traits, and how they chain.");
        var traits = category(builder, "Traits", "Which traits new recipes may use.");
        var effects = category(builder, "Feedback", "Particles, sounds and messages.");
        var advanced = category(builder, "Advanced", "Request limits and performance tuning.");

        BookControls books = GameplayConfigEntries.add(gameplay, fields, config);
        GameplayConfigEntries.addSpecial(special, fields, config);
        EffectsConfigEntries.add(effects, fields, config);
        TraitConfigEntries.add(traits, entries, config);
        Supplier<ProviderConfig> providerValue = GenerationConfigEntries.add(
                generation, advanced, entries, fields, config);

        builder.setSavingRunnable(() -> save(parent, client, path, config, books, providerValue));
        return builder.build();
    }

    private static ConfigCategory category(ConfigBuilder builder, String name, String description) {
        var category = builder.getOrCreateCategory(Component.literal(name));
        category.setDescription(new FormattedText[]{Component.literal(description)});
        return category;
    }

    private static void save(Screen parent, Minecraft client, Path path, ModConfig config, BookControls books,
            Supplier<ProviderConfig> provider) {
        try {
            config.provider = provider.get();
            config.soulboundBook = books.ownership().getValue();
            config.personalBook = books.visibility().getValue();
            config.save(path);
            var server = client.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> InfiniteCraftMod.applyConfigToIntegratedServer(server, config));
            }
        } catch (IOException | RuntimeException error) {
            // Run after Cloth closes the form so its parent transition cannot hide this error.
            client.schedule(() -> client.setScreenAndShow(new AlertScreen(
                    () -> client.setScreenAndShow(create(parent)), Component.literal("Settings could not be saved"),
                    Component.literal("Check your settings and file permissions."))));
        }
    }

}
