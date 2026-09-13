package dev.rocks.infinitecraft.traits;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

public final class HealingFoodTrait extends FoodTrait {
    @Override
    public String id() {
        return "edible_healing";
    }

    @Override
    protected ConsumeEffect effect(double value) {
        return new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 0));
    }
}
