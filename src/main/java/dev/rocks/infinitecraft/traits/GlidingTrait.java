package dev.rocks.infinitecraft.traits;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Set;

public final class GlidingTrait implements TraitDefinition {
    @Override
    public String id() {
        return "gliding";
    }

    @Override
    public String hint() {
        return "Wearable";
    }

    @Override
    public Phase phase() {
        return Phase.EQUIPMENT;
    }

    @Override
    public Set<DataComponentType<?>> components() {
        return Set.of(DataComponents.EQUIPPABLE, DataComponents.GLIDER, DataComponents.MAX_DAMAGE, DataComponents.DAMAGE);
    }

    @Override
    public boolean supports(ItemStack base) {
        var equipped = base.get(DataComponents.EQUIPPABLE);
        return equipped == null || equipped.slot() == EquipmentSlot.CHEST;
    }

    @Override
    public boolean validResult(ItemStack result) {
        if (!result.has(DataComponents.GLIDER)) {
            return true;
        }
        var equipment = result.get(DataComponents.EQUIPPABLE);
        return equipment != null && equipment.slot() == EquipmentSlot.CHEST;
    }

    @Override
    public void apply(ItemStack output, double value) {
        if (!output.has(DataComponents.EQUIPPABLE)) {
            output.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).build());
        }
        output.set(DataComponents.GLIDER, Unit.INSTANCE);
        TraitComponents.ensureDurability(output, 432);
    }
}
