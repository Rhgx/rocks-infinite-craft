package dev.rocks.infinitecraft.traits;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/** Attribute suppliers defer registry access until an item is actually being generated. */
public record AttributeTrait(
        String id,
        StrengthRange range,
        Supplier<Holder<Attribute>> attribute,
        Mode mode,
        Supplier<Holder<MobEffect>> consumedEffect
) implements TraitDefinition {
    public AttributeTrait(String id, StrengthRange range, Supplier<Holder<Attribute>> attribute, Mode mode) {
        this(id, range, attribute, mode, null);
    }

    @Override public List<String> activationModes() {
        var modes = new ArrayList<>(List.of("auto", "mainhand", "offhand", "head", "chest", "legs", "feet"));
        if (consumedEffect != null) modes.add("consumed");
        return List.copyOf(modes);
    }

    @Override public boolean prepareActivation(ItemStack output, String activation) {
        if (!Set.of("head", "chest", "legs", "feet").contains(activation)) return true;
        var slot = EquipmentSlot.valueOf(activation.toUpperCase(Locale.ROOT));
        var equipment = output.get(DataComponents.EQUIPPABLE);
        if (equipment != null) return equipment.slot() == slot;
        if (!TraitComponents.supportsUse(output)) return false;
        output.set(DataComponents.EQUIPPABLE, Equippable.builder(slot).build());
        return true;
    }
    public enum Mode {
        ADD,
        INCREASE_FRACTION,
        DECREASE_FRACTION
    }

    @Override
    public Set<DataComponentType<?>> components() {
        return consumedEffect == null ? Set.of(DataComponents.ATTRIBUTE_MODIFIERS, DataComponents.EQUIPPABLE)
                : Set.of(DataComponents.ATTRIBUTE_MODIFIERS, DataComponents.EQUIPPABLE, DataComponents.FOOD, DataComponents.CONSUMABLE);
    }

    @Override
    public void apply(ItemStack output, double value) {
        apply(output, value, "auto");
    }

    @Override public void apply(ItemStack output, double value, String activation) {
        if (activation.equals("consumed")) {
            if (consumedEffect == null) throw new IllegalArgumentException("No consumed form for this trait");
            var modifiers = output.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            var modifierId = Identifier.fromNamespaceAndPath("infinitecraft", id);
            if (modifiers.modifiers().stream().anyMatch(entry -> entry.modifier().id().equals(modifierId)))
                output.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(modifiers.modifiers().stream()
                        .filter(entry -> !entry.modifier().id().equals(modifierId)).toList()));
            var food = new FoodTrait() {
                public String id() { return AttributeTrait.this.id; }
                protected ConsumeEffect effect(double ignored) {
                    double fraction = (value - range.minimum()) / (range.maximum() - range.minimum());
                    return new ApplyStatusEffectsConsumeEffect(
                            new MobEffectInstance(consumedEffect.get(), 400,
                                    Math.min(2, (int) Math.floor(fraction * 3))));
                }
                protected boolean replaces(ConsumeEffect effect) {
                    return effect instanceof ApplyStatusEffectsConsumeEffect status
                            && status.effects().size() == 1 && status.effects().getFirst().getEffect().equals(consumedEffect.get());
                }
            };
            if (!food.supports(output)) throw new IllegalArgumentException("This item cannot consume an attribute effect");
            food.apply(output, value);
            return;
        }
        // Retuning a consumed trait to an equipment slot must not retain both forms.
        var consumable = output.get(DataComponents.CONSUMABLE);
        if (consumedEffect != null && consumable != null) {
            var effects = consumable.onConsumeEffects().stream().filter(effect ->
                    !(effect instanceof ApplyStatusEffectsConsumeEffect status && status.effects().size() == 1
                            && status.effects().getFirst().getEffect().equals(consumedEffect.get()))).toList();
            if (effects.size() != consumable.onConsumeEffects().size()) output.set(DataComponents.CONSUMABLE,
                    new Consumable(consumable.consumeSeconds(), consumable.animation(), consumable.sound(),
                            consumable.hasConsumeParticles(), effects));
        }
        double amount = mode == Mode.DECREASE_FRACTION ? -value : value;
        var operation = mode == Mode.ADD
                ? AttributeModifier.Operation.ADD_VALUE
                : AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        if (activation.equals("auto")) TraitComponents.modifier(output, id, attribute.get(), amount, operation);
        else {
            var slot = EquipmentSlotGroup.bySlot(
                    EquipmentSlot.valueOf(activation.toUpperCase(Locale.ROOT)));
            var modifiers = output.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            output.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers.withModifierAdded(attribute.get(),
                    new AttributeModifier(Identifier.fromNamespaceAndPath("infinitecraft", id), amount, operation), slot));
        }
    }
}
