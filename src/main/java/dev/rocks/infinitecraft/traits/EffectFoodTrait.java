package dev.rocks.infinitecraft.traits;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

/** Uses vanilla consumption effects, including their native probability field. */
final class EffectFoodTrait extends FoodTrait {
    private final String id;
    private final String hint;
    private final String description;
    private final int color;
    private final Holder<MobEffect> effect;
    private final int duration;
    private final float chance;

    EffectFoodTrait(String id, String hint, int color, String description,
            Holder<MobEffect> effect, int duration, float chance) {
        this.id = id;
        this.hint = hint;
        this.color = color;
        this.description = description;
        this.effect = effect;
        this.duration = duration;
        this.chance = chance;
    }

    @Override public String id() { return id; }
    @Override public String hint() { return hint; }
    @Override public int hintColor() { return color; }
    @Override public String description() { return description; }
    @Override public float triggerChance() { return chance; }
    @Override protected ConsumeEffect effect(double value) {
        return new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(effect, duration, 0), TraitSettings.chance(id));
    }
}
