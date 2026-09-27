package dev.rocks.infinitecraft.traits;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolTraitsTest {
    @Test
    void veinSpreadsInRingsFollowsDiagonalsSkipsGapsAndStopsAtTheLimit() {
        var ore = Set.of(new BlockPos(1, 0, 0), new BlockPos(2, 1, 1), new BlockPos(4, 1, 1), new BlockPos(0, 0, 0));
        var vein = ToolTraits.vein(BlockPos.ZERO, ore::contains, 64);
        assertEquals(List.of(List.of(new BlockPos(1, 0, 0)), List.of(new BlockPos(2, 1, 1))), vein);

        var column = ToolTraits.vein(BlockPos.ZERO, pos -> pos.getX() == 0 && pos.getZ() == 0, 5);
        assertEquals(5, column.stream().mapToInt(List::size).sum());
        assertEquals(2, column.getFirst().size());
        assertTrue(column.stream().flatMap(List::stream).allMatch(pos -> Math.abs(pos.getY()) <= 3));
    }

    @Test
    void toolTraitsOnlyAppearOnFittingTools() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var stick = new ItemStack(Items.STICK);
        for (var id : List.of("self_repairing", "auto_smelt", "vein_miner")) assertFalse(TraitRegistry.get(id).supports(stick), id);
        assertTrue(TraitRegistry.get("vein_miner").supports(new ItemStack(Items.IRON_AXE)));
        assertFalse(TraitRegistry.get("vein_miner").supports(new ItemStack(Items.IRON_SHOVEL)));
        assertTrue(TraitRegistry.get("auto_smelt").supports(new ItemStack(Items.FISHING_ROD)));
        assertTrue(TraitRegistry.get("auto_smelt").supports(new ItemStack(Items.SHEARS)));
        assertFalse(TraitRegistry.get("auto_smelt").supports(new ItemStack(Items.IRON_SWORD)));
        assertTrue(TraitRegistry.get("self_repairing").supports(new ItemStack(Items.IRON_SWORD)));
    }
}
