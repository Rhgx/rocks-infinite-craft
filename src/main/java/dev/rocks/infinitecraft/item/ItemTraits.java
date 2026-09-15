package dev.rocks.infinitecraft.item;

import dev.rocks.infinitecraft.traits.TraitRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;
import java.util.TreeSet;

/** Counts special traits, rather than vanilla equipment's individual attribute modifiers. */
public final class ItemTraits {
    public static final String KEY = "infinitecraft_traits";

    public static List<String> inherited(ItemStack... inputs) {
        var ids = new TreeSet<String>();
        for (var input : inputs) {
            var tag = input.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getListOrEmpty(KEY);
            for (int i = 0; i < tag.size(); i++) ids.add(tag.getStringOr(i, ""));
            for (var entry : input.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
                var id = entry.modifier().id();
                if (id.getNamespace().equals("infinitecraft") && TraitRegistry.get(id.getPath()) != null) ids.add(id.getPath());
            }
        }
        return List.copyOf(ids);
    }

    public static boolean apply(ItemStack output, ItemStack first, ItemStack second, List<String> chosen, int limit) {
        var ids = new TreeSet<>(inherited(first, second));
        ids.addAll(chosen);
        if (ids.size() > limit) return false;
        if (ids.isEmpty()) return true;
        var data = output.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var list = new ListTag();
        ids.forEach(id -> list.add(StringTag.valueOf(id)));
        data.put(KEY, list);
        output.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return true;
    }
}
