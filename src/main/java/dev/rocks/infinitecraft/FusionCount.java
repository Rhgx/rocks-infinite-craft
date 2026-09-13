package dev.rocks.infinitecraft;

import java.util.ArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/** A bounded lineage counter. Other custom data remains unsupported. */
final class FusionCount {
    static final int LIMIT = 5;
    private static final String KEY = "infinitecraft_combinations";

    static int get(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return -1;
        var tag = data.copyTag();
        if (tag.size() != (tag.contains(ItemTraits.KEY) ? 2 : 1) || !(tag.get(KEY) instanceof NumericTag number)) return -1;
        if (tag.contains(ItemTraits.KEY)) {
            var traits = tag.getList(ItemTraits.KEY).orElse(null);
            if (traits == null || traits.size() > 8) return -1;
            var seen = new java.util.HashSet<String>();
            for (int i = 0; i < traits.size(); i++) {
                String id = traits.getStringOr(i, "");
                if (dev.rocks.infinitecraft.traits.TraitRegistry.get(id) == null || !seen.add(id)) return -1;
            }
        }
        int count = tag.getIntOr(KEY, -1);
        // JSON-backed world tools can round-trip a small integer as a byte; reject fractional numbers.
        return count >= 0 && count <= LIMIT && number.doubleValue() == count ? count : -1;
    }

    static boolean supportedData(ItemStack stack) {
        return !stack.has(DataComponents.CUSTOM_DATA) || get(stack) >= 0;
    }

    static boolean exhausted(ItemStack stack) { return get(stack) == LIMIT; }

    static boolean isCounterLine(Component line) {
        for (int count = 0; count <= LIMIT; count++) if (line.equals(line(count))) return true;
        return false;
    }

    private static Component line(int count) {
        int color = count <= 2 ? 0xFFFFFF : count <= 4 ? 0xFFFF55 : 0xFF5555;
        return Component.literal("Combinations: " + count + "/" + LIMIT)
                .withStyle(style -> style.withColor(color).withItalic(false));
    }

    static boolean apply(ItemStack output, ItemStack first, ItemStack second) {
        return apply(output, first, second, ItemDataFusion::specialIngredient);
    }

    static boolean apply(ItemStack output, ItemStack first, ItemStack second, java.util.function.Predicate<ItemStack> special) {
        if (exhausted(first) || exhausted(second) || !supportedData(output)) return false;
        int firstCount = get(first), secondCount = get(second);
        boolean inherited = firstCount >= 0 || secondCount >= 0
                || special.test(first) || special.test(second);
        if (!inherited && !special.test(output)) return true;
        int count = inherited ? Math.max(0, Math.max(firstCount, secondCount)) + 1 : 0;
        var lines = new ArrayList<>(output.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        lines.removeIf(FusionCount::isCounterLine);
        if (lines.size() >= ItemLore.MAX_LINES) return false;
        lines.add(line(count));
        var marker = new CompoundTag();
        marker.putInt(KEY, count);
        output.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        output.set(DataComponents.LORE, new ItemLore(lines));
        return true;
    }
}
