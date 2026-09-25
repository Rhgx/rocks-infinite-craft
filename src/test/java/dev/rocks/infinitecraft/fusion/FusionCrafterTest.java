package dev.rocks.infinitecraft.fusion;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FusionCrafterTest {
    private static HolderLookup.Provider lookup;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        lookup = VanillaRegistries.createLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(pending -> pending.apply());
    }

    @Test void recipeProducesVanillaItemAndStationSurvivesSaving() throws Exception {
        var json = JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/rocks_infinite_craft/recipe/fusion_crafter.json")));
        var recipe = (CraftingRecipe) Recipe.CODEC.parse(lookup.createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
        var stack = recipe.assemble(CraftingInput.of(3, 1, List.of(new ItemStack(Items.CRAFTER),
                new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.COPPER_INGOT))));
        assertTrue(stack.is(Items.CRAFTER));
        assertEquals("Fusion Crafter", stack.getHoverName().getString());
        assertFalse(stack.get(DataComponents.CUSTOM_NAME).getStyle().isItalic());
        var block = new CrafterBlockEntity(BlockPos.ZERO, Blocks.CRAFTER.defaultBlockState());
        assertFalse(FusionCrafterBlock.marked(block));
        block.applyComponentsFromItemStack(stack);
        assertTrue(FusionCrafterBlock.marked(block));
        var owner = java.util.UUID.randomUUID();
        FusionCrafterBlock.setOwner(block, owner);
        assertEquals(owner, FusionCrafterBlock.owner(block));
        assertTrue(FusionCrafterBlock.repeats(block));
        FusionCrafterBlock.setMode(block, false, true);
        assertTrue(FusionCrafterBlock.paused(block));
        block.setItem(0, new ItemStack(Items.COAL, 4));
        var saved = block.saveWithFullMetadata(lookup);
        var restored = new CrafterBlockEntity(BlockPos.ZERO, Blocks.CRAFTER.defaultBlockState());
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, lookup, saved));
        assertTrue(FusionCrafterBlock.marked(restored));
        assertFalse(FusionCrafterBlock.repeats(restored));
        assertTrue(FusionCrafterBlock.paused(restored));
        FusionCrafterBlock.setMode(restored, true, false);
        assertTrue(FusionCrafterBlock.repeats(restored));
        assertFalse(FusionCrafterBlock.paused(restored));
        assertEquals(4, restored.getItem(0).getCount());
        assertEquals(owner, FusionCrafterBlock.owner(restored));
        assertEquals(stack.get(DataComponents.CUSTOM_MODEL_DATA), restored.collectComponents().get(DataComponents.CUSTOM_MODEL_DATA));
    }

    @Test void stationLocksSevenSlotsAndPreviewCannotBeTaken() {
        var block = new CrafterBlockEntity(BlockPos.ZERO, Blocks.CRAFTER.defaultBlockState());
        var marker = new net.minecraft.nbt.CompoundTag();
        marker.putBoolean(FusionCrafterBlock.MARKER, true);
        block.setComponents(net.minecraft.core.component.DataComponentMap.builder()
                .set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(marker)).build());
        block.setSlotState(3, false);
        assertTrue(block.canPlaceItem(3, new ItemStack(Items.STONE)));
        var inventory = new net.minecraft.world.entity.player.Inventory(null, new net.minecraft.world.entity.EntityEquipment());
        var menu = new net.minecraft.world.inventory.CrafterMenu(1, inventory, block,
                new net.minecraft.world.inventory.SimpleContainerData(10));
        for (int slot = 0; slot < 9; slot++) {
            boolean input = slot == 3 || slot == 5;
            assertEquals(!input, block.isSlotDisabled(slot));
            assertEquals(!input, menu.isSlotDisabled(slot));
            assertEquals(input, block.canPlaceItem(slot, new ItemStack(Items.STONE)));
            menu.setSlotState(slot, !input);
            assertEquals(!input, menu.isSlotDisabled(slot));
        }
        assertFalse(menu.getSlot(45).mayPickup(null));
        assertFalse(menu.getSlot(45).mayPlace(new ItemStack(Items.STONE)));
        // Existing items in newly locked slots remain removable.
        block.setItem(0, new ItemStack(Items.COAL, 4));
        assertEquals(4, block.removeItem(0, 4).getCount());
        assertTrue(block.isSlotDisabled(0));
    }

    @Test void twoSlotsAllowIdenticalInputsButNotOneBulkStackOrThreeInputs() {
        var items = new ArrayList<ItemStack>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        items.set(2, new ItemStack(Items.STONE, 64));
        assertTrue(FusionCrafter.inputSlots(items).isEmpty());
        items.set(8, new ItemStack(Items.STONE));
        assertEquals(List.of(2, 8), FusionCrafter.inputSlots(items));
        items.set(0, new ItemStack(Items.STICK));
        assertTrue(FusionCrafter.inputSlots(items).isEmpty());
    }

    @Test void cancellationSnapshotDetectsQuantityComponentsAndSlotChanges() {
        var items = List.of(new ItemStack(Items.STONE, 2), new ItemStack(Items.STICK));
        var snapshot = items.stream().map(ItemStack::copy).toList();
        assertTrue(FusionCrafter.sameItems(snapshot, items));
        items.getFirst().shrink(1);
        assertFalse(FusionCrafter.sameItems(snapshot, items));
        items.getFirst().setCount(2);
        items.getFirst().set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Changed"));
        assertFalse(FusionCrafter.sameItems(snapshot, items));
        assertFalse(FusionCrafter.sameItems(snapshot, List.of(snapshot.getLast(), snapshot.getFirst())));
    }
}

