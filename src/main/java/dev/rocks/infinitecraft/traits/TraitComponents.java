package dev.rocks.infinitecraft.traits;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.Objects;

/** Minecraft-specific operations shared by trait implementations. */
public final class TraitComponents {
    private TraitComponents() {
    }

    public static boolean supportsUse(ItemStack stack) {
        return stack.getItem().getClass() == Item.class || stack.getItem().getClass() == BlockItem.class
                || stack.is(Items.SHIELD);
    }

    public static boolean changed(ItemStack stack, DataComponentType<?> component) {
        return !Objects.equals(stack.get(component), stack.getItem().components().get(component));
    }

    public static void ensureDurability(ItemStack stack, int durability) {
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        if (!stack.has(DataComponents.MAX_DAMAGE)) {
            stack.set(DataComponents.MAX_DAMAGE, durability);
            stack.set(DataComponents.DAMAGE, 0);
        }
    }

    public static void modifier(ItemStack stack, String id, Holder<Attribute> attribute,
                                double amount, AttributeModifier.Operation operation) {
        var equipment = stack.get(DataComponents.EQUIPPABLE);
        var slot = equipment == null ? EquipmentSlotGroup.MAINHAND : EquipmentSlotGroup.bySlot(equipment.slot());
        var previous = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        var modifier = new AttributeModifier(Identifier.fromNamespaceAndPath("infinitecraft", id), amount, operation);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, previous.withModifierAdded(attribute, modifier, slot));
    }
}
