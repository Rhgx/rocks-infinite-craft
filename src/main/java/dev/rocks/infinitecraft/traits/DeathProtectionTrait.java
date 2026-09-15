package dev.rocks.infinitecraft.traits;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DeathProtection;

import java.util.Set;

public final class DeathProtectionTrait implements TraitDefinition {
    @Override
    public String id() {
        return "death_protection";
    }

    @Override
    public String hint() {
        return "Protective";
    }

    @Override
    public ChatFormatting hintColor() {
        return ChatFormatting.BLUE;
    }

    @Override
    public Set<DataComponentType<?>> components() {
        return Set.of(DataComponents.DEATH_PROTECTION);
    }

    @Override
    public void apply(ItemStack output, double value) {
        output.set(DataComponents.DEATH_PROTECTION, DeathProtection.TOTEM_OF_UNDYING);
    }
}
