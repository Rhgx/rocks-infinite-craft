package dev.rocks.infinitecraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;

/** Adds visible immediate-parent provenance without carrying older provenance into another recipe. */
public final class FusionOrigin {
    public static final String FIRST = "infinitecraft_origin_first";
    public static final String SECOND = "infinitecraft_origin_second";
    private static final String PREFIX = "Made from ";

    private FusionOrigin() {}

    public static boolean valid(net.minecraft.nbt.CompoundTag tag) {
        return tag.contains(FIRST) == tag.contains(SECOND)
                && (!tag.contains(FIRST) || tag.getString(FIRST).isPresent() && tag.getString(SECOND).isPresent());
    }

    public static ItemStack strip(ItemStack source) {
        ItemStack stack = source.copy();
        var data = stack.get(DataComponents.CUSTOM_DATA);
        boolean hasOrigin = data != null && data.copyTag().contains(FIRST) && valid(data.copyTag());
        if (!hasOrigin) return stack;
        if (data != null) {
            var tag = data.copyTag();
            tag.remove(FIRST);
            tag.remove(SECOND);
            if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
            else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        var lore = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        lore.removeIf(line -> line.getString().startsWith(PREFIX));
        if (lore.isEmpty()) {
            var defaultLore = stack.getItem().components().get(DataComponents.LORE);
            if (defaultLore == null) stack.remove(DataComponents.LORE);
            else stack.set(DataComponents.LORE, defaultLore);
        }
        else stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    public static boolean apply(ItemStack output, ItemStack first, ItemStack second) {
        var lore = new ArrayList<>(output.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        lore.removeIf(line -> line.getString().startsWith(PREFIX));
        if (lore.size() >= ItemLore.MAX_LINES) return false;
        String firstName = first.getHoverName().getString();
        String secondName = second.getHoverName().getString();
        lore.add(Component.literal(PREFIX + firstName + " + " + secondName)
                .withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY).withItalic(false)));
        output.set(DataComponents.LORE, new ItemLore(lore));
        var tag = output.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(FIRST, firstName);
        tag.putString(SECOND, secondName);
        output.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return true;
    }
}
