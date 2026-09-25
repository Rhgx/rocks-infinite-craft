package dev.rocks.infinitecraft.traits;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** A trait whose behavior is supplied by a server-side gameplay event. */
record TriggeredTrait(String id, String hint, int hintColor) implements TraitDefinition {
    @Override public float triggerChance() { return id.equals("explosive") ? .1F : 1; }
    @Override
    public String description() {
        return switch (id) {
            case "explosive" -> "Melee or projectile hits can cause an explosion that spares the wielder.";
            case "incendiary" -> "Melee or projectile hits ignite the target.";
            case "vampiric" -> "Melee or projectile damage heals the wielder; zero-damage hits do not heal.";
            case "launching" -> "Melee or projectile hits briefly levitate the target.";
            case "frostbite" -> "Melee or projectile hits briefly slow the target.";
            case "revealing" -> "Melee or projectile hits make the target glow through walls.";
            default -> "";
        };
    }

    @Override
    public Set<DataComponentType<?>> components() {
        return Set.of();
    }

    @Override
    public void apply(ItemStack output, double value) {
    }
}
