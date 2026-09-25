package dev.rocks.infinitecraft.client;

import dev.rocks.infinitecraft.client.mixin.CreativeModeInventoryScreenAccess;
import dev.rocks.infinitecraft.client.mixin.CreativeModeTabAccess;
import dev.rocks.infinitecraft.discovery.SpecialItemsTab;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStackLinkedSet;

import java.util.ArrayList;
import java.util.List;

final class SpecialItemsTabClient {
    private SpecialItemsTabClient() {}

    static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(SpecialItemsTab.Sync.TYPE, (payload, context) -> {
            SpecialItemsTab.replace(payload.items());
            var client = context.client();
            var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(SpecialItemsTab.KEY.identifier());
            if (tab == null) return;
            // Write the contents directly: ModernFix skips buildContents when its parameters are unchanged.
            var search = ItemStackLinkedSet.createTypeAndComponentsSet();
            search.addAll(payload.items());
            ((CreativeModeTabAccess) tab).infinitecraft$setDisplayItems(new ArrayList<>(search));
            ((CreativeModeTabAccess) tab).infinitecraft$setDisplayItemsSearchTab(search);
            if (client.gui.screen() instanceof CreativeModeInventoryScreen screen
                    && CreativeModeInventoryScreenAccess.infinitecraft$selectedTab() == tab) {
                ((CreativeModeInventoryScreenAccess) screen)
                        .infinitecraft$refreshCurrentTabContents(tab.getDisplayItems());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SpecialItemsTab.replace(List.of()));
    }
}
