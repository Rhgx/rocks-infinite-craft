package dev.rocks.infinitecraft.traits;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** A trait whose behavior is supplied by a server-side gameplay event. */
record TriggeredTrait(String id, String hint, ChatFormatting hintColor) implements TraitDefinition {
    @Override
    public Set<DataComponentType<?>> components() {
        return Set.of();
    }

    @Override
    public void apply(ItemStack output, double value) {
    }
}
