package dev.rocks.infinitecraft.traits;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Set;

/** Rolled once per incoming hit, even when several equipped pieces share the trait. */
record ReactiveArmorTrait(String id, String hint, ChatFormatting hintColor, String description,
        Holder<MobEffect> effect, int duration, float chance, boolean retaliation) implements TraitDefinition {
    @Override public float triggerChance() { return chance; }
    @Override public Phase phase() { return Phase.EQUIPMENT; }
    @Override public Set<DataComponentType<?>> components() { return Set.of(DataComponents.EQUIPPABLE); }
    @Override public Set<String> conflicts() { return Set.of("edible_healing", "edible_teleport", "nibbleable", "airy_food", "hearty_food", "vanishing_food"); }

    @Override
    public boolean supports(ItemStack base) {
        var equipment = base.get(DataComponents.EQUIPPABLE);
        return !base.has(DataComponents.CONSUMABLE) && (equipment == null || equipment.slot().isArmor());
    }

    @Override
    public void apply(ItemStack output, double value) {
        if (!output.has(DataComponents.EQUIPPABLE)) {
            output.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).build());
        }
    }

    void onHit(LivingEntity wearer, LivingEntity attacker) {
        LivingEntity recipient = retaliation ? attacker : wearer;
        if (recipient != null && recipient.isAlive() && wearer.getRandom().nextFloat() < TraitSettings.chance(id)) {
            recipient.addEffect(new MobEffectInstance(effect, duration, 0), wearer);
        }
    }
}
