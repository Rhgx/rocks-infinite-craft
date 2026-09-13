package dev.rocks.infinitecraft.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.network.chat.Component;

public final class InfiniteCraftModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            if (!FabricLoader.getInstance().isModLoaded("cloth-config"))
                return new AlertScreen(() -> Minecraft.getInstance().setScreenAndShow(parent),
                        Component.literal("Infinite Craft settings"), Component.literal("Install Cloth Config to edit settings in game."));
            return InfiniteCraftConfigScreen.create(parent);
        };
    }
}
