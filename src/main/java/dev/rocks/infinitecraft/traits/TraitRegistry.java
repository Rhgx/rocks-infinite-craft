package dev.rocks.infinitecraft.traits;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffects;

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
            new AttributeTrait("fast_mining", new StrengthRange(0, 1.5, .75),
                    () -> Attributes.BLOCK_BREAK_SPEED, INCREASE_FRACTION, () -> MobEffects.HASTE),
            new AttributeTrait("water_stride", new StrengthRange(0, 1, .5),
                    () -> Attributes.WATER_MOVEMENT_EFFICIENCY, ADD),
            new AttributeTrait("step_up", new StrengthRange(0, 1, .5),
                    () -> Attributes.STEP_HEIGHT, ADD),
            new BlockingTrait(),
            new GlidingTrait(),
            new HealingFoodTrait(),
            new TeleportFoodTrait(),
            new DeathProtectionTrait()
    );

    private static final Map<String, TraitDefinition> BY_ID = index(DEFINITIONS);

    private TraitRegistry() {
    }

    private static Map<String, TraitDefinition> index(List<TraitDefinition> definitions) {
        Map<String, TraitDefinition> indexed = new LinkedHashMap<>();
        for (var trait : definitions) {
            if (!trait.id().matches("[a-z0-9_.:-]{1,64}") || indexed.putIfAbsent(trait.id(), trait) != null) {
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
