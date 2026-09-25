package dev.rocks.infinitecraft.fusion;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Mode and activity of the Fusion Crafter a player has open, for the mod's crafter screen. */
public record CrafterStatePayload(int containerId, boolean repeat, boolean paused, boolean working)
        implements CustomPacketPayload {
    public static final Type<CrafterStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
            "rocks_infinite_craft", "crafter_state"));
    public static final StreamCodec<FriendlyByteBuf, CrafterStatePayload> CODEC = StreamCodec.of(
            (buffer, state) -> {
                buffer.writeVarInt(state.containerId);
                buffer.writeByte((state.repeat ? 1 : 0) | (state.paused ? 2 : 0) | (state.working ? 4 : 0));
            },
            buffer -> {
                int id = buffer.readVarInt();
                int flags = buffer.readByte();
                return new CrafterStatePayload(id, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
            });

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
