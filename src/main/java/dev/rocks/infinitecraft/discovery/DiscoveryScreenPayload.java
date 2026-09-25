package dev.rocks.infinitecraft.discovery;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/** Full collection data is sent only to clients that advertise this payload. */
public record DiscoveryScreenPayload(List<DiscoveryCollection.Entry> entries, boolean personal, Set<Integer> favorites)
        implements CustomPacketPayload {
    public static final Type<DiscoveryScreenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
            "rocks_infinite_craft", "open_discovery_book_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DiscoveryScreenPayload> CODEC = new StreamCodec<>() {
        @Override
        public DiscoveryScreenPayload decode(RegistryFriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            if (size < 0 || size > 100_000) throw new IllegalArgumentException("Invalid discovery count");
            var entries = new ArrayList<DiscoveryCollection.Entry>(size);
            for (int i = 0; i < size; i++) {
                var first = ItemStack.STREAM_CODEC.decode(buffer);
                var second = ItemStack.STREAM_CODEC.decode(buffer);
                var result = ItemStack.STREAM_CODEC.decode(buffer);
                String discoverer = buffer.readUtf(64);
                int id = buffer.readVarInt();
                int names = buffer.readVarInt();
                if (names < 0 || names > 1_000) throw new IllegalArgumentException("Invalid discoverer count");
                var discoverers = new ArrayList<String>(names);
                for (int name = 0; name < names; name++) discoverers.add(buffer.readUtf(64));
                entries.add(new DiscoveryCollection.Entry(first, second, result, discoverer, id, discoverers));
            }
            boolean personal = buffer.readBoolean();
            int count = buffer.readVarInt();
            if (count < 0 || count > size) throw new IllegalArgumentException("Invalid favorite count");
            var favorites = new HashSet<Integer>();
            for (int i = 0; i < count; i++) favorites.add(buffer.readVarInt());
            return new DiscoveryScreenPayload(entries, personal, favorites);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, DiscoveryScreenPayload payload) {
            buffer.writeVarInt(payload.entries.size());
            for (var entry : payload.entries) {
                ItemStack.STREAM_CODEC.encode(buffer, entry.first());
                ItemStack.STREAM_CODEC.encode(buffer, entry.second());
                ItemStack.STREAM_CODEC.encode(buffer, entry.result());
                buffer.writeUtf(entry.discoverer(), 64);
                buffer.writeVarInt(entry.id());
                buffer.writeVarInt(entry.discoverers().size());
                entry.discoverers().forEach(name -> buffer.writeUtf(name, 64));
            }
            buffer.writeBoolean(payload.personal);
            buffer.writeVarInt(payload.favorites.size());
            payload.favorites.forEach(buffer::writeVarInt);
        }
    };

    public DiscoveryScreenPayload {
        entries = List.copyOf(entries);
        favorites = Set.copyOf(favorites);
    }

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().registerLarge(TYPE, CODEC, 16 * 1024 * 1024);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
