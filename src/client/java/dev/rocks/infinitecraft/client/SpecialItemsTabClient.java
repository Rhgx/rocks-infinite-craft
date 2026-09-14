package dev.rocks.infinitecraft.client;

import dev.rocks.infinitecraft.SpecialItemsTab;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;

final class SpecialItemsTabClient {
    private SpecialItemsTabClient() {}

    static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(SpecialItemsTab.Sync.TYPE, (payload, context) -> {
            SpecialItemsTab.replace(payload.items());
            var client = context.client();
            if (client.level == null || client.player == null) return;
            var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(SpecialItemsTab.KEY.identifier());
            if (tab == null) return;
            tab.buildContents(new CreativeModeTab.ItemDisplayParameters(client.level.enabledFeatures(),
                    client.player.canUseGameMasterBlocks(), client.level.registryAccess()));
            if (client.gui.screen() instanceof CreativeModeInventoryScreen screen)
                screen.resize(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SpecialItemsTab.replace(java.util.List.of()));
    }
}
