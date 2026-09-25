package dev.rocks.infinitecraft.traits;

import dev.rocks.infinitecraft.core.ValidationPatterns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.rocks.infinitecraft.traits.AttributeTrait.Mode.ADD;
import static dev.rocks.infinitecraft.traits.AttributeTrait.Mode.DECREASE_FRACTION;
import static dev.rocks.infinitecraft.traits.AttributeTrait.Mode.INCREASE_FRACTION;

/** Add definitions here; consumers derive their IDs, ranges, hints and component handling. */
public final class TraitRegistry {
    private static final List<TraitDefinition> DEFINITIONS = List.of(
            new AttributeTrait("bouncy", new StrengthRange(0, 1, .75),
                    () -> Attributes.BOUNCINESS, ADD),
            new AttributeTrait("slippery", new StrengthRange(0, .9, .75),
                    () -> Attributes.FRICTION_MODIFIER, DECREASE_FRACTION),
            new AttributeTrait("low_gravity", new StrengthRange(0, .75, .5),
                    () -> Attributes.GRAVITY, DECREASE_FRACTION),
            new AttributeTrait("tiny", new StrengthRange(0, .75, .5),
                    () -> Attributes.SCALE, DECREASE_FRACTION),
            new AttributeTrait("speedy", new StrengthRange(0, .5, .25),
                    () -> Attributes.MOVEMENT_SPEED, INCREASE_FRACTION, () -> MobEffects.SPEED),
            new LongReachTrait(),
            new AttributeTrait("strong", new StrengthRange(0, 6, 3),
                    () -> Attributes.ATTACK_DAMAGE, ADD, () -> MobEffects.STRENGTH),
            new AttributeTrait("attack_speed", new StrengthRange(0, 4, 2),
                    () -> Attributes.ATTACK_SPEED, ADD),
            new AttributeTrait("giant", new StrengthRange(0, 1, .5),
                    () -> Attributes.SCALE, INCREASE_FRACTION),
            new AttributeTrait("leaping", new StrengthRange(0, .6, .3),
                    () -> Attributes.JUMP_STRENGTH, INCREASE_FRACTION, () -> MobEffects.JUMP_BOOST),
            new AttributeTrait("knockback", new StrengthRange(0, 2, 1),
                    () -> Attributes.ATTACK_KNOCKBACK, ADD),
            new AttributeTrait("anchored", new StrengthRange(0, .8, .4),
                    () -> Attributes.KNOCKBACK_RESISTANCE, ADD),
            new AttributeTrait("armored", new StrengthRange(0, 8, 4),
                    () -> Attributes.ARMOR, ADD),
            new AttributeTrait("tough", new StrengthRange(0, 8, 4),
                    () -> Attributes.ARMOR_TOUGHNESS, ADD),
            new AttributeTrait("healthy", new StrengthRange(0, 20, 8),
                    () -> Attributes.MAX_HEALTH, ADD),
            new AttributeTrait("soft_landing", new StrengthRange(0, 12, 6),
                    () -> Attributes.SAFE_FALL_DISTANCE, ADD),
            new AttributeTrait("gilled", new StrengthRange(0, 4, 2),
                    () -> Attributes.OXYGEN_BONUS, ADD),
            new AttributeTrait("sweeping", new StrengthRange(0, 1, .5),
                    () -> Attributes.SWEEPING_DAMAGE_RATIO, ADD),
            new AttributeTrait("fast_mining", new StrengthRange(0, 1.5, .75),
                    () -> Attributes.BLOCK_BREAK_SPEED, INCREASE_FRACTION, () -> MobEffects.HASTE),
            new AttributeTrait("water_stride", new StrengthRange(0, 1, .5),
                    () -> Attributes.WATER_MOVEMENT_EFFICIENCY, ADD),
            new AttributeTrait("step_up", new StrengthRange(0, 1, .5),
                    () -> Attributes.STEP_HEIGHT, ADD),
            new TriggeredTrait("explosive", "☄ Volatile", ChatFormatting.RED),
            new TriggeredTrait("incendiary", "🔥 Burning", ChatFormatting.GOLD),
            new TriggeredTrait("vampiric", "♥ Bloodthirsty", ChatFormatting.DARK_RED),
            new TriggeredTrait("launching", "☁ Uplifting", ChatFormatting.LIGHT_PURPLE),
            new TriggeredTrait("frostbite", "❄ Chilly", ChatFormatting.AQUA),
            new TriggeredTrait("revealing", "☀ Exposing", ChatFormatting.YELLOW),
            new ReactiveArmorTrait("startled", "⚡ Jumpy", ChatFormatting.YELLOW,
                    "While worn, taking an attack grants Speed I for 3 seconds.", MobEffects.SPEED, 60, 1, false),
            new ReactiveArmorTrait("hardened", "⚓ Stubborn", ChatFormatting.GRAY,
                    "While worn, attacks have a 25% chance to grant Resistance I for 4 seconds.", MobEffects.RESISTANCE, 80, .25F, false),
            new ReactiveArmorTrait("cold_shoulder", "❄ Prickly", ChatFormatting.AQUA,
                    "While worn, attacks have a 30% chance to slow the attacker for 3 seconds.", MobEffects.SLOWNESS, 60, .3F, true),
            new EffectFoodTrait("airy_food", "☁ Airy", ChatFormatting.WHITE,
                    "Eating grants Slow Falling for 15 seconds.", MobEffects.SLOW_FALLING, 300, 1),
            new EffectFoodTrait("hearty_food", "♥ Hearty", ChatFormatting.GOLD,
                    "Eating grants Absorption I for 30 seconds.", MobEffects.ABSORPTION, 600, 1),
            new EffectFoodTrait("vanishing_food", "☯ Elusive", ChatFormatting.LIGHT_PURPLE,
                    "Eating grants Invisibility for 15 seconds.", MobEffects.INVISIBILITY, 300, 1),
            new NibbleableTrait(),
            new BlockingTrait(),
            new GlidingTrait(),
            new HealingFoodTrait(),
            new TeleportFoodTrait(),
            new DeathProtectionTrait(),
            new LuckyBlockTrait()
    );

    private static final Map<String, TraitDefinition> BY_ID = index(DEFINITIONS);

    private TraitRegistry() {
    }

    private static Map<String, TraitDefinition> index(List<TraitDefinition> definitions) {
        Map<String, TraitDefinition> indexed = new LinkedHashMap<>();
        for (var trait : definitions) {
            if (!ValidationPatterns.isTraitId(trait.id()) || indexed.putIfAbsent(trait.id(), trait) != null) {
                throw new IllegalArgumentException("Invalid or duplicate trait ID: " + trait.id());
            }
        }
        for (var trait : definitions) {
            if (!indexed.keySet().containsAll(trait.conflicts())) {
                throw new IllegalArgumentException("Unknown trait conflict: " + trait.id());
            }
        }
        return Collections.unmodifiableMap(indexed);
    }

    public static List<TraitDefinition> definitions() {
        return DEFINITIONS;
    }

    public static List<String> ids() {
        return List.copyOf(BY_ID.keySet());
    }

    public static TraitDefinition get(String id) {
        return BY_ID.get(id);
    }

    public static Set<DataComponentType<?>> components() {
        Set<DataComponentType<?>> components = new HashSet<>();
        for (var trait : DEFINITIONS) {
            components.addAll(trait.components());
        }
        return Set.copyOf(components);
    }

    public static boolean validResult(ItemStack output) {
        return DEFINITIONS.stream().allMatch(trait -> trait.validResult(output));
    }
}
