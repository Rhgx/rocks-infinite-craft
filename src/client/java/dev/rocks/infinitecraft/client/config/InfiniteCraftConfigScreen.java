package dev.rocks.infinitecraft.client.config;

import dev.rocks.infinitecraft.InfiniteCraftMod;
import dev.rocks.infinitecraft.ModConfig;
import dev.rocks.infinitecraft.provider.ProviderConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
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
        var gameplay = builder.getOrCreateCategory(Component.literal("Gameplay"));
        var generation = builder.getOrCreateCategory(Component.literal("Generation"));
        var effects = builder.getOrCreateCategory(Component.literal("Effects"));
        var advanced = builder.getOrCreateCategory(Component.literal("Advanced"));

        BookControls books = GameplayConfigEntries.add(gameplay, fields, config);
        EffectsConfigEntries.add(effects, fields, config);
        Supplier<ProviderConfig> providerValue = GenerationConfigEntries.add(
                generation, advanced, entries, fields, config);

        builder.setSavingRunnable(() -> save(parent, client, path, config, books, providerValue));
        return builder.build();
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
