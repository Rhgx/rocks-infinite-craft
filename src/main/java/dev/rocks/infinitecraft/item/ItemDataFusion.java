package dev.rocks.infinitecraft.item;

import dev.rocks.infinitecraft.fusion.FusionCount;
import dev.rocks.infinitecraft.traits.TraitRegistry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic component handling on the server thread, never model-authored item data. */
public final class ItemDataFusion {
    private static final Set<DataComponentType<?>> SHARED_COMPONENTS = Set.of(DataComponents.ENCHANTMENTS,
            DataComponents.STORED_ENCHANTMENTS, DataComponents.POTION_CONTENTS, DataComponents.SUSPICIOUS_STEW_EFFECTS, DataComponents.CUSTOM_NAME,
            DataComponents.LORE, DataComponents.DAMAGE, DataComponents.REPAIR_COST,
            DataComponents.MAX_STACK_SIZE, DataComponents.MAX_DAMAGE, DataComponents.ATTRIBUTE_MODIFIERS,
            DataComponents.EQUIPPABLE, DataComponents.DYED_COLOR, DataComponents.ITEM_MODEL);
    private static final Set<DataComponentType<?>> SUPPORTED;
    private static final List<DataComponentType<?>> TRAIT_VALUES;
    static {
        var supported = new HashSet<>(SHARED_COMPONENTS);
        supported.add(DataComponents.CUSTOM_DATA);
        supported.addAll(TraitRegistry.components());
        SUPPORTED = Set.copyOf(supported);
        // Shared components have dedicated merge rules below. Other declared values use equality merging.
        var copied = new HashSet<>(TraitRegistry.components());
        copied.removeAll(SHARED_COMPONENTS);
        copied.remove(DataComponents.CUSTOM_DATA);
        copied.add(DataComponents.MAX_DAMAGE);
        TRAIT_VALUES = List.copyOf(copied);
    }

    public static boolean supported(ItemStack stack) {
        return FusionCount.supportedData(stack)
                && (changed(stack, DataComponents.MAX_STACK_SIZE) == null || stack.getMaxStackSize() == 1)
                && stack.getComponentsPatch().entrySet().stream()
                .allMatch(entry -> SUPPORTED.contains(entry.getKey()) && entry.getValue().isPresent());
    }

    public static boolean specialIngredient(ItemStack stack) {
        return specialIngredient(stack, true, true, true, true);
    }

    public static boolean specialIngredient(ItemStack stack, boolean rarity, boolean enchantments, boolean potions, boolean custom) {
        if (rarity && stack.getOrDefault(DataComponents.RARITY, net.minecraft.world.item.Rarity.COMMON)
                != net.minecraft.world.item.Rarity.COMMON) return true;
        if (enchantments && (!stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
                || !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty())) return true;
        if (potions && (stack.has(DataComponents.POTION_CONTENTS)
                || !stack.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS,
                        net.minecraft.world.item.component.SuspiciousStewEffects.EMPTY).effects().isEmpty())) return true;
        if (!custom) return false;
        if (changed(stack, DataComponents.ITEM_MODEL) != null || stack.has(DataComponents.DYED_COLOR)
                || stack.has(DataComponents.CUSTOM_NAME)) return true;
        // Compare against the item's defaults so ordinary armor, tools and food do not qualify.
        return TraitRegistry.components().stream()
                .filter(type -> type != DataComponents.DAMAGE && type != DataComponents.MAX_DAMAGE
                        && type != DataComponents.MAX_STACK_SIZE && type != DataComponents.RARITY
                        && type != DataComponents.ENCHANTMENTS && type != DataComponents.STORED_ENCHANTMENTS
                        && type != DataComponents.POTION_CONTENTS && type != DataComponents.SUSPICIOUS_STEW_EFFECTS)
                .anyMatch(type -> changed(stack, type) != null);
    }

    public static ItemStack brew(PotionBrewing brewing, ItemStack first, ItemStack second) {
        if (brewing.hasMix(first, second)) return brewing.mix(second, first).copyWithCount(1);
        if (brewing.hasMix(second, first)) return brewing.mix(first, second).copyWithCount(1);
        return ItemStack.EMPTY;
    }

    /** Empty means data would be lost or incompatible. Inputs are never mutated. */
    public static ItemStack prepare(ItemStack target, ItemStack first, ItemStack second, boolean brewed) {
        if (!supported(first) || !supported(second) || FusionCount.exhausted(first) || FusionCount.exhausted(second)) return ItemStack.EMPTY;
        ItemStack result = target.copy();
        if ((first.has(DataComponents.DYED_COLOR) || second.has(DataComponents.DYED_COLOR))
                && result.is(net.minecraft.tags.ItemTags.CAULDRON_CAN_REMOVE_DYE)) {
            var aColor = first.get(DataComponents.DYED_COLOR);
            var bColor = second.get(DataComponents.DYED_COLOR);
            if (aColor != null || bColor != null) {
                int a = (aColor != null ? aColor : bColor).rgb();
                int b = (bColor != null ? bColor : aColor).rgb();
                int mixed = ((((a >> 16) & 255) + ((b >> 16) & 255)) / 2 << 16)
                        | ((((a >> 8) & 255) + ((b >> 8) & 255)) / 2 << 8) | ((a & 255) + (b & 255)) / 2;
                result.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(mixed));
            }
        }
        for (var type : TRAIT_VALUES) if (!mergeChanged(result, first, second, type)) return ItemStack.EMPTY;
        var equipmentA = changed(first, DataComponents.EQUIPPABLE);
        var equipmentB = changed(second, DataComponents.EQUIPPABLE);
        if (equipmentA != null && equipmentB != null && !equipmentA.equals(equipmentB)) return ItemStack.EMPTY;
        var equipment = equipmentA != null ? equipmentA : equipmentB;
        if (equipment != null) {
            var nativeEquipment = result.get(DataComponents.EQUIPPABLE);
            if (nativeEquipment != null && nativeEquipment.slot() != equipment.slot()) return ItemStack.EMPTY;
            if (nativeEquipment == null) result.set(DataComponents.EQUIPPABLE, equipment);
        }
        if (!TraitRegistry.validResult(result)) return ItemStack.EMPTY;
        if (!mergeTraitModifiers(result, first, second) || !TraitRegistry.validResult(result)) return ItemStack.EMPTY;
        if (result.has(DataComponents.MAX_DAMAGE)) result.set(DataComponents.MAX_STACK_SIZE, 1);
        if (result.getCount() > result.getMaxStackSize()) return ItemStack.EMPTY;
        if (result.has(DataComponents.MAX_DAMAGE) && !result.has(DataComponents.DAMAGE)) result.set(DataComponents.DAMAGE, 0);
        if (!mergeValue(result, first, second, DataComponents.CUSTOM_NAME)
                || !TraitLore.merge(result, first, second)) return ItemStack.EMPTY;
        var a = first.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        var b = second.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        if (brewed) {
            if (!a.equals(PotionContents.EMPTY) && !b.equals(PotionContents.EMPTY)) return ItemStack.EMPTY;
            var contents = result.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            if (contents.potion().isEmpty()) return ItemStack.EMPTY;
            result.set(DataComponents.POTION_CONTENTS,
                    (a.equals(PotionContents.EMPTY) ? b : a).withPotion(contents.potion().orElseThrow()));
        } else if (!PotionFusion.merge(result, first, second)) return ItemStack.EMPTY;
        var merged = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (ItemStack input : List.of(first, second)) {
            for (var type : List.of(DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS)) {
                for (var entry : input.getOrDefault(type, ItemEnchantments.EMPTY).entrySet()) {
                    var enchantment = entry.getKey();
                    int level = entry.getIntValue();
                    if (level < 1 || level > enchantment.value().getMaxLevel()) return ItemStack.EMPTY;
                    // supported_items controls normal application, not whether vanilla executes the effect.
                    // Preserve enchantments on unusual items; native slots and effect conditions still apply.
                    for (var existing : merged.keySet()) {
                        if (!existing.equals(enchantment) && !Enchantment.areCompatible(existing, enchantment)) return ItemStack.EMPTY;
                    }
                    int previous = merged.getLevel(enchantment);
                    merged.set(enchantment, previous == level ? Math.min(level + 1, enchantment.value().getMaxLevel()) : Math.max(previous, level));
                }
            }
        }
        if (!merged.keySet().isEmpty()) {
            if (result.getCount() != 1) return ItemStack.EMPTY;
            result.set(result.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, merged.toImmutable());
        }
        double damage = 0;
        for (ItemStack input : List.of(first, second)) {
            if (input.isDamaged()) damage = Math.max(damage, (double) input.getDamageValue() / input.getMaxDamage());
        }
        if (damage > 0) {
            if (!result.isDamageableItem()) return ItemStack.EMPTY;
            result.setDamageValue(Math.min(result.getMaxDamage(), (int) Math.ceil(damage * result.getMaxDamage())));
        }
        int repairCost = Math.max(first.getOrDefault(DataComponents.REPAIR_COST, 0), second.getOrDefault(DataComponents.REPAIR_COST, 0));
        if (repairCost > 0) result.set(DataComponents.REPAIR_COST, repairCost);
        return ItemStack.validateStrict(result).result().orElse(ItemStack.EMPTY);
    }

    private static <T> T changed(ItemStack stack, DataComponentType<T> type) {
        T value = stack.get(type);
        return Objects.equals(value, stack.getItem().components().get(type)) ? null : value;
    }

    private static <T> boolean mergeChanged(ItemStack result, ItemStack first, ItemStack second, DataComponentType<T> type) {
        T a = changed(first, type), b = changed(second, type);
        if (a != null && b != null && !a.equals(b)) return false;
        if (a != null || b != null) result.set(type, a != null ? a : b);
        return true;
    }

    private static boolean mergeTraitModifiers(ItemStack result, ItemStack first, ItemStack second) {
        Map<net.minecraft.resources.Identifier, ItemAttributeModifiers.Entry> traits = new LinkedHashMap<>();
        for (var input : List.of(first, second)) {
            var modifiers = changed(input, DataComponents.ATTRIBUTE_MODIFIERS);
            if (modifiers == null) continue;
            var nativeModifiers = input.getItem().components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            if (!modifiers.modifiers().containsAll(nativeModifiers.modifiers())) return false;
            for (var entry : modifiers.modifiers()) {
                if (nativeModifiers.modifiers().contains(entry)) continue;
                if (!entry.modifier().id().getNamespace().equals("infinitecraft")) return false;
                var sourceEquipment = input.get(DataComponents.EQUIPPABLE);
                if (sourceEquipment != null && entry.slot() == EquipmentSlotGroup.bySlot(sourceEquipment.slot())) {
                    var targetEquipment = result.get(DataComponents.EQUIPPABLE);
                    if (targetEquipment != null && targetEquipment.slot() != sourceEquipment.slot()) return false;
                    if (targetEquipment == null) {
                        if (!dev.rocks.infinitecraft.traits.TraitComponents.supportsUse(result)) return false;
                        result.set(DataComponents.EQUIPPABLE, sourceEquipment);
                    }
                }
                var previous = traits.putIfAbsent(entry.modifier().id(), entry);
            if (previous != null && (!previous.attribute().equals(entry.attribute())
                    || !previous.modifier().equals(entry.modifier()) || previous.slot() != entry.slot())) {
                return false;
            }
            }
        }
        var modifiers = result.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (var entry : traits.values()) modifiers = modifiers.withModifierAdded(entry.attribute(), entry.modifier(), entry.slot());
        if (!traits.isEmpty()) {
            result.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
        }
        return true;
    }

    private static <T> boolean mergeValue(ItemStack result, ItemStack first, ItemStack second, DataComponentType<T> type) {
        T a = first.get(type), b = second.get(type);
        if (a != null && b != null && !a.equals(b)) return false;
        if (a != null || b != null) result.set(type, a != null ? a : b);
        return true;
    }
}
