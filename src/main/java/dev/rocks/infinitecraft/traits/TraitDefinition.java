package dev.rocks.infinitecraft.traits;

import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

/** One trait's metadata, compatibility rules and native component changes. */
public interface TraitDefinition {
    enum Phase {
        EQUIPMENT,
        EFFECT,
        FOOD
    }

    String id();

    default java.util.List<String> activationModes() { return java.util.List.of("auto"); }

    default boolean prepareActivation(ItemStack output, String activation) { return true; }

    default void apply(ItemStack output, double value, String activation) { apply(output, value); }

    default StrengthRange range() {
        return null;
    }

    default String hint() {
        return "";
    }

    default Set<String> conflicts() {
        return Set.of();
    }

    default Phase phase() {
        return Phase.EFFECT;
    }

    default boolean supports(ItemStack base) {
        return true;
    }

    /** Components this trait writes. Ordinary values carry over only when the inputs agree. */
    Set<DataComponentType<?>> components();

    /** Also checked after existing components are inherited during a later fusion. */
    default boolean validResult(ItemStack result) {
        return true;
    }

    /** Receives the mapped native value, or zero for an on/off trait. Mutate only this output copy. */
    void apply(ItemStack output, double value);
}
