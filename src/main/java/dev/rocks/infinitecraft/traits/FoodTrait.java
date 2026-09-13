package dev.rocks.infinitecraft.traits;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

/** Shared food composition preserves existing nutrition and unrelated consume effects. */
public abstract class FoodTrait implements TraitDefinition {
    @Override
    public List<String> activationModes() { return List.of("auto", "consumed"); }

    @Override
    public String hint() {
        return "Edible";
    }

    @Override
    public Phase phase() {
        return Phase.FOOD;
    }

    @Override
    public Set<String> conflicts() {
        return Set.of("blocking", "gliding");
    }

    @Override
    public Set<DataComponentType<?>> components() {
        return Set.of(DataComponents.FOOD, DataComponents.CONSUMABLE);
    }

    @Override
    public boolean supports(ItemStack base) {
        return TraitComponents.supportsUse(base)
                && !base.has(DataComponents.EQUIPPABLE)
                && !base.has(DataComponents.BLOCKS_ATTACKS);
    }

    @Override
    public boolean validResult(ItemStack result) {
        if (!result.has(DataComponents.CONSUMABLE)) {
            return true;
        }
        if (result.has(DataComponents.BLOCKS_ATTACKS) || result.has(DataComponents.EQUIPPABLE)) {
            return false;
        }
        return !TraitComponents.changed(result, DataComponents.CONSUMABLE) || TraitComponents.supportsUse(result);
    }

    protected abstract ConsumeEffect effect(double value);

    protected boolean replaces(ConsumeEffect effect) {
        return false;
    }

    @Override
    public void apply(ItemStack output, double value) {
        Consumable existing = output.get(DataComponents.CONSUMABLE);
        var effects = new ArrayList<ConsumeEffect>(existing == null ? List.of() : existing.onConsumeEffects());
        effects.removeIf(this::replaces);
        var effect = effect(value);
        if (!effects.contains(effect)) {
            effects.add(effect);
        }

        var template = existing == null ? Consumable.builder().consumeSeconds(1.6F).build() : existing;
        output.set(DataComponents.CONSUMABLE, new Consumable(
                Math.max(.8F, template.consumeSeconds()),
                template.animation(),
                template.sound(),
                template.hasConsumeParticles(),
                List.copyOf(effects)
        ));

        var food = output.get(DataComponents.FOOD);
        if (food == null) {
            food = new FoodProperties.Builder().nutrition(2).saturationModifier(.2F).alwaysEdible().build();
        } else {
            food = new FoodProperties(food.nutrition(), food.saturation(), true);
        }
        output.set(DataComponents.FOOD, food);
    }
}
