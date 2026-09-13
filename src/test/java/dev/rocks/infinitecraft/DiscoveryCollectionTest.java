package dev.rocks.infinitecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DiscoveryCollectionTest {
    private static HolderLookup.Provider lookup;
    @TempDir Path directory;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        lookup = VanillaRegistries.createLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(pending -> pending.apply());
    }

    @Test void worldFirstDiscoveriesPersistExactComponentsAndIgnoreQuantity() throws Exception {
        for (int count : new int[]{10, 25, 50, 100, 250, 500, 1000, 2500, 1_000_000_000})
            assertTrue(FusionRuntime.isMilestone(count));
        for (int count : new int[]{0, 1, 9, 26, 75, 200, 999, Integer.MAX_VALUE})
            assertFalse(FusionRuntime.isMilestone(count));
        var file = directory.resolve("discoveries.json");
        var collection = new DiscoveryCollection(file, lookup);
        var first = new ItemStack(Items.STICK);
        var second = new ItemStack(Items.COAL);
        var output = VanillaTraits.apply(new ItemStack(Items.JUNGLE_PLANKS), java.util.List.of("bouncy"), "Spring Planks");
        var ordinary = FusionRuntime.discoveryMessage(new ItemStack(Items.STONE), "Rocks");
        assertTrue(ordinary.getString().startsWith("[FIRST] Rocks found "));
        assertTrue(ordinary.getSiblings().getFirst().getStyle().isBold());
        assertEquals(0xFFAA00, ordinary.getSiblings().getFirst().getStyle().getColor().getValue());
        var special = FusionRuntime.discoveryMessage(output, "Rocks");
        assertTrue(special.getString().startsWith("[SPECIAL] Rocks found "));
        assertEquals(0xFF55FF, special.getSiblings().getFirst().getStyle().getColor().getValue());
        assertTrue(special.getSiblings().getFirst().getStyle().isBold());
        assertFalse(special.getSiblings().get(1).getStyle().isBold());
        assertEquals(0xAAAAAA, special.getSiblings().get(1).getStyle().getColor().getValue());
        assertNotNull(special.getSiblings().getLast().getStyle().getHoverEvent());
        assertTrue(collection.record(first, second, output, "Rocks"));
        assertFalse(collection.record(second, first, output.copyWithCount(4), "Friend"));
        collection.save(collection.snapshot());
        var reloaded = new DiscoveryCollection(file, lookup);
        assertEquals(1, reloaded.entries().size());
        assertEquals("Rocks", reloaded.entries().getFirst().discoverer());
        assertTrue(ItemStack.isSameItemSameComponents(output, reloaded.entries().getFirst().result()));
        assertFalse(reloaded.record(first, second, output, "Friend"));
        reloaded.entries().getFirst().result().set(DataComponents.CUSTOM_NAME, Component.literal("Changed"));
        assertEquals("Spring Planks", reloaded.entries().getFirst().result().getHoverName().getString());
        var pageSnapshot = reloaded.entries();
        var saveSnapshot = reloaded.snapshot();
        output.set(DataComponents.CUSTOM_NAME, Component.literal("Different discovery"));
        assertTrue(reloaded.record(first, second, output, "Friend"));
        assertEquals(1, pageSnapshot.size());
        assertEquals(1, saveSnapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> pageSnapshot.clear());
        assertEquals("Spring Planks", collection.entries().getFirst().result().getHoverName().getString());
        var rocks = java.util.UUID.randomUUID();
        var friend = java.util.UUID.randomUUID();
        assertFalse(reloaded.record(first, second, pageSnapshot.getFirst().result(), "Rocks", rocks));
        assertEquals(1, reloaded.entries(rocks, "Rocks").size());
        assertFalse(reloaded.record(first, second, pageSnapshot.getFirst().result(), "Friend", friend));
        assertEquals(2, reloaded.entries(friend, "Friend").size());
        assertEquals(0, reloaded.entries(java.util.UUID.randomUUID(), "Stranger").size());
        reloaded.save(reloaded.snapshot());
        var personalReload = new DiscoveryCollection(file, lookup);
        assertTrue(personalReload.owns(1, friend, "RenamedFriend"));
        assertEquals(1, personalReload.entries(rocks, "RenamedRocks").getFirst().id());
    }

    @Test void malformedCollectionIsNeverOverwrittenOnLoad() throws Exception {
        var file = directory.resolve("discoveries.json");
        Files.writeString(file, "not json");
        assertThrows(IOException.class, () -> new DiscoveryCollection(file, lookup));
        assertEquals("not json", Files.readString(file));
    }
}
