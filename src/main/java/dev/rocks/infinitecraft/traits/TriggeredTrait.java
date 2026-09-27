package dev.rocks.infinitecraft.traits;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Set;

/** A trait whose behavior is supplied by a server-side gameplay event. */
record TriggeredTrait(String id, String hint, int hintColor) implements TraitDefinition {
    @Override public float triggerChance() {
        return switch (id) {
            case "explosive" -> .1F;
            case "thundering" -> .05F;
            case "launching" -> .3F;
            case "self_repairing", "auto_smelt", "vein_miner" -> -1;
            default -> 1;
        };
    }
    @Override
    public String description() {
        return switch (id) {
            case "explosive" -> "Melee or projectile hits can cause an explosion that spares the wielder.";
            case "incendiary" -> "Melee or projectile hits ignite the target.";
            case "vampiric" -> "Melee or projectile damage heals the wielder; zero-damage hits do not heal.";
            case "launching" -> "Melee or projectile hits lift the target about 6 blocks on a short updraft.";
            case "frostbite" -> "Melee or projectile hits briefly slow the target.";
            case "revealing" -> "Melee or projectile hits make the target glow through walls.";
            case "magnetic" -> "Melee or projectile hits pull the target toward the wielder.";
            case "thundering" -> "Melee or projectile hits can call lightning onto the target, more often after heavy damage.";
            case "self_repairing" -> "Slowly repairs itself anywhere in the inventory. Requires an item with durability.";
            case "auto_smelt" -> "Blocks broken and fish caught with this tool drop already smelted; sneak to keep them raw. Requires a pickaxe, axe, shovel, hoe, shears or fishing rod.";
            case "vein_miner" -> "Breaking an ore or log also breaks up to 24 connected blocks of the same kind; sneak to break one. Requires a pickaxe or axe.";
            default -> "";
        };
    }

    @Override
    public boolean supports(ItemStack base) {
        return switch (id) {
            case "self_repairing" -> base.has(DataComponents.MAX_DAMAGE);
            case "auto_smelt" -> base.is(Items.SHEARS) || base.is(Items.FISHING_ROD) || mines(base, BlockTags.MINEABLE_WITH_PICKAXE,
                    BlockTags.MINEABLE_WITH_AXE, BlockTags.MINEABLE_WITH_SHOVEL, BlockTags.MINEABLE_WITH_HOE);
            case "vein_miner" -> mines(base, BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_AXE);
            default -> true;
        };
    }

    /** Reads the tool's own mining rules, so modded tools qualify and item tags need not be loaded. */
    @SafeVarargs
    private static boolean mines(ItemStack base, TagKey<Block>... blocks) {
        var tool = base.get(DataComponents.TOOL);
        return tool != null && tool.rules().stream().anyMatch(rule ->
                rule.blocks().unwrapKey().filter(List.of(blocks)::contains).isPresent());
    }

    @Override
    public Set<DataComponentType<?>> components() {
        return Set.of();
    }

    @Override
    public void apply(ItemStack output, double value) {
    }
}
