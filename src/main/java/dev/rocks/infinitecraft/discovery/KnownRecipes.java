package dev.rocks.infinitecraft.discovery;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The discoveries a player can see, mirrored to clients that show them in a recipe viewer.
 * Only clients that registered a receiver get them, so other clients pay nothing.
 */
public record KnownRecipes(List<DiscoveryCollection.Entry> entries, boolean replace) implements CustomPacketPayload {
    public static final Type<KnownRecipes> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
            "rocks_infinite_craft", "known_recipes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, KnownRecipes> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.replace);
                DiscoveryScreenPayload.writeEntries(buffer, payload.entries);
            },
            buffer -> {
                boolean replace = buffer.readBoolean();
                return new KnownRecipes(DiscoveryScreenPayload.readEntries(buffer), replace);
            });

    public KnownRecipes {
        entries = List.copyOf(entries);
    }

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().registerLarge(TYPE, CODEC, 16 * 1024 * 1024);
    }

    /** Sends the whole visible list when replacing, otherwise entries to add or update by id. */
    public static void send(ServerPlayer player, List<DiscoveryCollection.Entry> entries, boolean replace) {
        if (ServerPlayNetworking.canSend(player, TYPE)) ServerPlayNetworking.send(player, new KnownRecipes(entries, replace));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
