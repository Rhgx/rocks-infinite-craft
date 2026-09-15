package dev.rocks.infinitecraft.discovery;

import dev.rocks.infinitecraft.fusion.FusionCount;
import dev.rocks.infinitecraft.fusion.FusionCrafterBlock;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** The server owns the list; this class only mirrors it into modded creative inventories. */
public final class SpecialItemsTab {
    public static final ResourceKey<CreativeModeTab> ITEMS_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath("rocks_infinite_craft", "items"));
    public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath("rocks_infinite_craft", "special_discoveries"));
    private static volatile List<ItemStack> items = List.of();

    public record Sync(List<ItemStack> items) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
                "rocks_infinite_craft", "special_discoveries"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC =
                ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(Sync::new, Sync::items);
        public Sync { items = items.stream().map(stack -> stack.copyWithCount(1)).toList(); }
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private SpecialItemsTab() {}

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().registerLarge(Sync.TYPE, Sync.CODEC, 16 * 1024 * 1024);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ITEMS_KEY,
                FabricCreativeModeTab.builder()
                        .title(Component.literal("Rocks' Infinite Craft"))
                        .icon(FusionCrafterBlock::item)
                        .displayItems((parameters, output) -> {
                            output.accept(DiscoveryBook.create(parameters.holders(), false));
                            output.accept(FusionCrafterBlock.item());
                        })
                        .build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY,
                FabricCreativeModeTab.builder()
                        .title(Component.literal("Special Discoveries"))
                        .icon(() -> items.isEmpty() ? new ItemStack(Items.ENCHANTED_BOOK) : items.getFirst().copy())
                        .displayItems((parameters, output) -> items.forEach(output::accept))
                        .build());
    }

    public static List<ItemStack> from(List<DiscoveryCollection.Entry> discoveries) {
        return DiscoveryCollection.uniqueResults(discoveries).stream()
                .map(DiscoveryCollection.Entry::result)
                .filter(stack -> FusionCount.get(stack) >= 0)
                .map(stack -> stack.copyWithCount(1))
                .toList();
    }

    public static void send(ServerPlayer player, List<DiscoveryCollection.Entry> discoveries) {
        if (ServerPlayNetworking.canSend(player, Sync.TYPE))
            ServerPlayNetworking.send(player, new Sync(from(discoveries)));
    }

    public static void sendAll(net.minecraft.server.MinecraftServer server,
                               List<DiscoveryCollection.Entry> discoveries) {
        for (var player : server.getPlayerList().getPlayers()) send(player, discoveries);
    }

    public static void replace(List<ItemStack> replacement) {
        items = replacement.stream().map(stack -> stack.copyWithCount(1)).toList();
    }
}
