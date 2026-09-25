package dev.rocks.infinitecraft.discovery;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KnownRecipesTest {
    @Test
    void entriesSurviveTheNetworkCodec() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup())
                .forEach(pending -> pending.apply());
        var entry = new DiscoveryCollection.Entry(new ItemStack(Items.STONE), new ItemStack(Items.DIRT),
                new ItemStack(Items.DIAMOND), "Rocks", 7, List.of("Rocks", "Friend"));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        KnownRecipes.CODEC.encode(buffer, new KnownRecipes(List.of(entry), true));

        var decoded = KnownRecipes.CODEC.decode(buffer);
        assertTrue(decoded.replace());
        var copy = decoded.entries().getFirst();
        assertEquals(7, copy.id());
        assertEquals(List.of("Rocks", "Friend"), copy.discoverers());
        assertTrue(ItemStack.matches(entry.first(), copy.first()));
        assertTrue(ItemStack.matches(entry.second(), copy.second()));
        assertTrue(ItemStack.matches(entry.result(), copy.result()));
        assertEquals(0, buffer.readableBytes());
    }
}
