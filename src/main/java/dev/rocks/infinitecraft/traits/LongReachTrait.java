package dev.rocks.infinitecraft.traits;

import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class LongReachTrait implements TraitDefinition {
    @Override
    public String id() {
        return "long_reach";
    }

    @Override
    public StrengthRange range() {
        return new StrengthRange(0, 3, 2);
    }

    @Override
    public Set<DataComponentType<?>> components() {
        return Set.of(DataComponents.ATTRIBUTE_MODIFIERS, DataComponents.ATTACK_RANGE);
    }

    @Override
    public void apply(ItemStack output, double reach) {
        var modifiers = output.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double previousReach = modifiers.modifiers().stream()
                .filter(entry -> entry.modifier().id().toString().equals("infinitecraft:long_reach_entity"))
                .mapToDouble(entry -> entry.modifier().amount())
                .findFirst()
                .orElse(0);

        // Replace the old reach contribution when this item is retuned.
        var range = output.getOrDefault(DataComponents.ATTACK_RANGE, new AttackRange(0, 3, 0, 5, 0, 1));
        double change = reach - previousReach;
        float survivalReach = (float) Math.clamp(range.maxReach() + change, range.minReach(), 64);
        float creativeReach = (float) Math.clamp(range.maxCreativeReach() + change, range.minCreativeReach(), 64);
        output.set(DataComponents.ATTACK_RANGE, new AttackRange(
                range.minReach(), survivalReach,
                range.minCreativeReach(), creativeReach,
                range.hitboxMargin(), range.mobFactor()
        ));

        TraitComponents.modifier(output, id() + "_block", Attributes.BLOCK_INTERACTION_RANGE,
                reach, AttributeModifier.Operation.ADD_VALUE);
        TraitComponents.modifier(output, id() + "_entity", Attributes.ENTITY_INTERACTION_RANGE,
                reach, AttributeModifier.Operation.ADD_VALUE);
    }
}
