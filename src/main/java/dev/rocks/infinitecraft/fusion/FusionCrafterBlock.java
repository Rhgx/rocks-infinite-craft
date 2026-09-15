package dev.rocks.infinitecraft.fusion;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;

import java.util.List;
import java.util.UUID;

/** Identifies the vanilla Crafter instances and item stacks used as Fusion Crafters. */
public final class FusionCrafterBlock {
    public static final String MARKER = "rocks_fusion_crafter";
    public static final String OWNER = "rocks_fusion_owner";
    private static final CompoundTag MARKER_PATTERN = markerTag();

    private FusionCrafterBlock() {
    }

    public static boolean marked(CrafterBlockEntity block) {
        var data = block.components().get(DataComponents.CUSTOM_DATA);
        return data != null && data.matchedBy(MARKER_PATTERN);
    }

    public static void setOwner(CrafterBlockEntity block, UUID owner) {
        var tag = block.components().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(MARKER, true);
        tag.putString(OWNER, owner.toString());
        block.setComponents(DataComponentMap.builder()
                .addAll(block.components())
                .set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
                .build());
        block.setChanged();
    }

    public static UUID owner(CrafterBlockEntity block) {
        var data = block.components().get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String value = data.copyTag().getStringOr(OWNER, "");
        try {
            return value.isEmpty() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean inputSlot(int slot) {
        return slot == 3 || slot == 5;
    }

    public static ItemStack item() {
        var stack = new ItemStack(Items.CRAFTER);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("Fusion Crafter").withStyle(style -> style.withColor(0xFFAA00).withItalic(false)));
        var marker = markerTag();
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of("rocks_fusion_crafter"), List.of()));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Place items in two slots to fuse.")
                .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)))));
        return stack;
    }

    private static CompoundTag markerTag() {
        var marker = new CompoundTag();
        marker.putBoolean(MARKER, true);
        return marker;
    }
}
