package dev.rocks.infinitecraft.fusion;

import dev.rocks.infinitecraft.item.FusionOrigin;
import dev.rocks.infinitecraft.item.ItemDataFusion;
import dev.rocks.infinitecraft.item.ItemTraits;
import dev.rocks.infinitecraft.traits.TraitRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.Predicate;

/** A bounded lineage counter. Other custom data remains unsupported. */
public final class FusionCount {
    public static final int LIMIT = 5;
    private static final String KEY = "infinitecraft_combinations";

    public static int get(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return -1;
        var tag = data.copyTag();
        if (!FusionOrigin.valid(tag)) return -1;
        tag.remove(FusionOrigin.FIRST);
        tag.remove(FusionOrigin.SECOND);
        if (tag.size() != (tag.contains(ItemTraits.KEY) ? 2 : 1) || !(tag.get(KEY) instanceof NumericTag number)) return -1;
        if (tag.contains(ItemTraits.KEY)) {
            var traits = tag.getList(ItemTraits.KEY).orElse(null);
            if (traits == null || traits.size() > 8) return -1;
            var seen = new HashSet<String>();
            for (int i = 0; i < traits.size(); i++) {
                String id = traits.getStringOr(i, "");
                if (TraitRegistry.get(id) == null || !seen.add(id)) return -1;
            }
        }
        int count = tag.getIntOr(KEY, -1);
        // JSON-backed world tools can round-trip a small integer as a byte; reject fractional numbers.
        return count >= 0 && number.doubleValue() == count ? count : -1;
    }

    public static boolean supportedData(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return true;
        var tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        if (!FusionOrigin.valid(tag)) return false;
        tag.remove(FusionOrigin.FIRST);
        tag.remove(FusionOrigin.SECOND);
        return tag.isEmpty() || get(stack) >= 0;
    }

    public static boolean exhausted(ItemStack stack) {
        return exhausted(stack, LIMIT);
    }

    public static boolean exhausted(ItemStack stack, int limit) {
        return limit > 0 && get(stack) >= limit;
    }

    public static boolean isCounterLine(Component line) {
        return line.getString().startsWith("Combinations: ");
    }

    private static Component line(int count) {
        return line(count, LIMIT);
    }

    private static Component line(int count, int limit) {
        double progress = limit == 0 ? 0 : (double) count / limit;
        int color = progress < 0.6 ? 0xFFFFFF : progress < 1 ? 0xFFFF55 : 0xFF5555;
        String value = limit == 0 ? Integer.toString(count) : count + "/" + limit;
        return Component.literal("Combinations: " + value)
                .withStyle(style -> style.withColor(color).withItalic(false));
    }

    public static boolean apply(ItemStack output, ItemStack first, ItemStack second) {
        return apply(output, first, second, ItemDataFusion::specialIngredient, LIMIT);
    }

    public static boolean apply(ItemStack output, ItemStack first, ItemStack second, Predicate<ItemStack> special) {
        return apply(output, first, second, special, LIMIT);
    }

    public static boolean apply(ItemStack output, ItemStack first, ItemStack second,
            Predicate<ItemStack> special, int limit) {
        if (exhausted(first, limit) || exhausted(second, limit) || !supportedData(output)) return false;
        int firstCount = get(first), secondCount = get(second);
        boolean inherited = firstCount >= 0 || secondCount >= 0
                || special.test(first) || special.test(second);
        if (!inherited && !special.test(output)) return true;
        if (Math.max(firstCount, secondCount) == Integer.MAX_VALUE) return false;
        int count = inherited ? Math.max(0, Math.max(firstCount, secondCount)) + 1 : 0;
        var lines = new ArrayList<>(output.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        lines.removeIf(FusionCount::isCounterLine);
        if (lines.size() >= ItemLore.MAX_LINES) return false;
        lines.add(line(count, limit));
        var marker = new CompoundTag();
        marker.putInt(KEY, count);
        output.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        output.set(DataComponents.LORE, new ItemLore(lines));
        return true;
    }
}
