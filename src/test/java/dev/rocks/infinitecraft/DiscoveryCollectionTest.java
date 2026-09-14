package dev.rocks.infinitecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
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
        assertEquals(0, FusionRuntime.milestoneTier(10));
        assertEquals(4, FusionRuntime.milestoneTier(250));
        assertEquals(6, FusionRuntime.milestoneTier(1000));
        assertEquals(50, FusionRuntime.milestoneExperience(0));
        assertEquals(500, FusionRuntime.milestoneExperience(99));
        var largeMilestone = FusionRuntime.milestoneMessage(1000, 6, "Rocks");
        assertEquals("Rocks reached 1000 discoveries!", largeMilestone.getString());
        assertTrue(largeMilestone.getStyle().isBold());
        assertEquals(0xFF55FF, largeMilestone.getSiblings().getFirst().getStyle().getColor().getValue());
        assertTrue(largeMilestone.getSiblings().getFirst().getStyle().isBold());
        assertTrue(largeMilestone.getSiblings().getLast().getStyle().isBold());
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
        assertEquals(List.of("Rocks", "Friend"), reloaded.entries().getFirst().discoverers());
        assertEquals(2, reloaded.entries(friend, "Friend").size());
        assertEquals(0, reloaded.entries(java.util.UUID.randomUUID(), "Stranger").size());
        reloaded.save(reloaded.snapshot());
        var personalReload = new DiscoveryCollection(file, lookup);
        assertTrue(personalReload.owns(1, friend, "RenamedFriend"));
        assertEquals(1, personalReload.entries(rocks, "RenamedRocks").getFirst().id());
        var originalOutput = pageSnapshot.getFirst().result();
        assertFalse(personalReload.record(new ItemStack(Items.DIRT), second, originalOutput, "Friend", friend));
        assertEquals(3, personalReload.entries().size());
        assertEquals(2, DiscoveryCollection.uniqueResults(personalReload.entries()).size());
        assertEquals(2, personalReload.outputCount());
        assertEquals(2, DiscoveryCollection.groupResults(personalReload.entries())
                .get(new DiscoveryCollection.ResultKey(originalOutput.copyWithCount(12))).size());
        var alternateOrigin = originalOutput.copy();
        assertTrue(FusionOrigin.apply(alternateOrigin, new ItemStack(Items.BOOK), new ItemStack(Items.DIRT)));
        assertEquals(new DiscoveryCollection.ResultKey(originalOutput), new DiscoveryCollection.ResultKey(alternateOrigin));
        var plainSteak = new ItemStack(Items.COOKED_BEEF);
        var originatedSteak = plainSteak.copy();
        assertTrue(FusionOrigin.apply(originatedSteak, new ItemStack(Items.BEEF), new ItemStack(Items.CAMPFIRE)));
        assertTrue(DiscoveryCollection.samePair(plainSteak, plainSteak, plainSteak, originatedSteak));
        assertEquals(1, DiscoveryCollection.groupResults(personalReload.entries(rocks, "RenamedRocks"))
                .get(new DiscoveryCollection.ResultKey(originalOutput)).size());
        assertFalse(personalReload.record(second, new ItemStack(Items.DIRT), originalOutput, "Friend", friend));
        assertEquals(3, personalReload.entries().size());
        assertEquals(1, personalReload.entries(rocks, "RenamedRocks").size());
        personalReload.save(personalReload.snapshot());
        var finalReload = new DiscoveryCollection(file, lookup);
        assertEquals(3, finalReload.entries().size());
        assertEquals(List.of("Rocks", "Friend"), finalReload.entries().getFirst().discoverers());
    }

    @Test void malformedCollectionIsNeverOverwrittenOnLoad() throws Exception {
        var file = directory.resolve("discoveries.json");
        Files.writeString(file, "not json");
        assertThrows(IOException.class, () -> new DiscoveryCollection(file, lookup));
        assertEquals("not json", Files.readString(file));
    }

    @Test void queuedSavesCoalesceAndKeepTheLatestSnapshot() throws Exception {
        var file = directory.resolve("coalesced.json");
        var collection = new DiscoveryCollection(file, lookup);
        var jobs = new java.util.ArrayDeque<Runnable>();
        for (int i = 0; i < 100; i++) {
            var output = new ItemStack(Items.STONE);
            output.set(DataComponents.CUSTOM_NAME, Component.literal("Result " + i));
            collection.record(new ItemStack(Items.STICK), new ItemStack(Items.COAL), output, "Rocks");
            collection.saveAsync(jobs::add, error -> fail(error));
        }
        assertEquals(1, jobs.size());
        jobs.remove().run();
        assertEquals(100, new DiscoveryCollection(file, lookup).outputCount());
        collection.record(new ItemStack(Items.DIRT), new ItemStack(Items.COAL), new ItemStack(Items.DIAMOND), "Rocks");
        collection.saveAsync(jobs::add, error -> fail(error));
        assertEquals(1, jobs.size());
        jobs.remove().run();
        assertEquals(101, new DiscoveryCollection(file, lookup).outputCount());
    }
}
