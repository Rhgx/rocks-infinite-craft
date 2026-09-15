package dev.rocks.infinitecraft.client;

import dev.rocks.infinitecraft.client.mixin.CreativeModeInventoryScreenAccess;
import dev.rocks.infinitecraft.discovery.SpecialItemsTab;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;

import java.util.List;

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
            if (client.gui.screen() instanceof CreativeModeInventoryScreen screen
                    && CreativeModeInventoryScreenAccess.infinitecraft$selectedTab() == tab) {
                ((CreativeModeInventoryScreenAccess) screen)
                        .infinitecraft$refreshCurrentTabContents(tab.getDisplayItems());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SpecialItemsTab.replace(List.of()));
    }
}
