package dev.rocks.infinitecraft;

import dev.rocks.infinitecraft.core.TraitStrengths;
import dev.rocks.infinitecraft.core.NameStyle;
import dev.rocks.infinitecraft.traits.TraitDefinition;
import dev.rocks.infinitecraft.traits.TraitRegistry;
import java.util.HashSet;
import java.util.HashMap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Validates and composes registered traits on a copy, never on the input item. */
public final class VanillaTraits {
    private VanillaTraits() {}
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    public static List<String> ids() { return TraitRegistry.ids(); }

    public static ItemStack apply(ItemStack base, List<String> traits, String name) {
        return apply(base, traits, name, Map.of());
    }

    public static ItemStack apply(ItemStack base, List<String> traits, String name, Map<String, Double> strengths) {
        return apply(base, traits, name, strengths, Map.of(), NameStyle.PLAIN);
    }

    public static ItemStack apply(ItemStack base, List<String> traits, String name, Map<String, Double> strengths,
            Map<String, String> activations, NameStyle nameStyle) {
        return apply(base, traits, name, strengths, activations, nameStyle, List.of());
    }

    public static ItemStack apply(ItemStack base, List<String> traits, String name, Map<String, Double> strengths,
            Map<String, String> activations, NameStyle nameStyle, List<dev.rocks.infinitecraft.core.NamePart> nameParts) {
        if (activations == null || nameStyle == null) return ItemStack.EMPTY;
        if (base == null || base.isEmpty() || traits == null || strengths == null || traits.size() > 8
                || new HashSet<>(traits).size() != traits.size()
                || traits.stream().anyMatch(id -> id == null || TraitRegistry.get(id) == null)) return ItemStack.EMPTY;
        try {
            TraitStrengths.validate(traits, strengths);
            for (var entry : activations.entrySet())
                if (!traits.contains(entry.getKey()) || !TraitRegistry.get(entry.getKey()).activationModes().contains(entry.getValue()))
                    return ItemStack.EMPTY;
        }
        catch (IllegalArgumentException invalid) { return ItemStack.EMPTY; }
        if (name != null && (name.length() > 64 || name.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT))) return ItemStack.EMPTY;
        for (String id : traits) {
            var trait = TraitRegistry.get(id);
            if (!trait.supports(base) || trait.conflicts().stream().anyMatch(traits::contains)) return ItemStack.EMPTY;
        }
        // Keep an existing slot; otherwise choose consistently so candidate validation and spawning agree.
        var equipment = base.get(DataComponents.EQUIPPABLE);
        String chosenSlot = equipment == null ? null : equipment.slot().getName();
        if (chosenSlot == null) for (var slot : ARMOR_SLOTS) {
            if (activations.containsValue(slot.getName())) { chosenSlot = slot.getName(); break; }
        }
        if (chosenSlot != null) {
            var resolved = new HashMap<>(activations);
            for (var slot : ARMOR_SLOTS) for (var entry : resolved.entrySet())
                if (entry.getValue().equals(slot.getName())) entry.setValue(chosenSlot);
            activations = resolved;
        }
        ItemStack output = base.copy();
        if (!traits.isEmpty()) {
            output.setCount(1);
        }
        for (String id : traits)
            if (!TraitRegistry.get(id).prepareActivation(output, activations.getOrDefault(id, "auto"))) return ItemStack.EMPTY;
        if (name != null && !name.isBlank()) {
            var text = nameParts.isEmpty() ? Component.literal(name).withStyle(textStyle(nameStyle))
                    : Component.empty().withStyle(net.minecraft.network.chat.Style.EMPTY.withItalic(false));
            for (var part : nameParts) text.append(Component.literal(part.text()).withStyle(textStyle(part.style())));
            output.set(DataComponents.CUSTOM_NAME, text);

        }
        // Equipment precedes attributes; registry order keeps consume effects stable.
        for (var phase : TraitDefinition.Phase.values()) {
            for (var trait : TraitRegistry.definitions()) {
                if (traits.contains(trait.id()) && trait.phase() == phase) {
                    try { trait.apply(output, trait.range() == null ? 0 : TraitStrengths.value(trait.id(), strengths),
                            activations.getOrDefault(trait.id(), "auto")); }
                    catch (IllegalArgumentException invalid) { return ItemStack.EMPTY; }
                }
            }
        }
        if (!equipForArmorEffects(output)) return ItemStack.EMPTY;
        if (!TraitRegistry.validResult(output) || !TraitLore.add(output, traits, activations)) return ItemStack.EMPTY;
        return ItemStack.validateStrict(output).result().orElse(ItemStack.EMPTY);
    }
    private static net.minecraft.network.chat.Style textStyle(NameStyle style) {
        var result = net.minecraft.network.chat.Style.EMPTY.withBold(style.bold()).withItalic(style.italic())
                .withUnderlined(style.underlined()).withStrikethrough(style.strikethrough()).withObfuscated(style.obfuscated());
        return style.color().isEmpty() ? result : result.withColor(Integer.parseInt(style.color().substring(1), 16));
    }

    private static boolean equipForArmorEffects(ItemStack output) {
        if (output.has(DataComponents.EQUIPPABLE)) return true;
        var modifiers = output.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        var enchantments = output.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var slot : ARMOR_SLOTS) {
            boolean needed = modifiers.modifiers().stream().anyMatch(entry -> entry.slot().test(slot)
                    && !entry.slot().test(EquipmentSlot.MAINHAND) && !entry.slot().test(EquipmentSlot.OFFHAND))
                    || enchantments.keySet().stream().anyMatch(enchantment -> enchantment.value().matchingSlot(slot)
                    && !enchantment.value().matchingSlot(EquipmentSlot.MAINHAND)
                    && !enchantment.value().matchingSlot(EquipmentSlot.OFFHAND));
            if (!needed) continue;
            if (output.has(DataComponents.CONSUMABLE) || output.has(DataComponents.BLOCKS_ATTACKS)) return false;
            output.set(DataComponents.EQUIPPABLE, Equippable.builder(slot).build());
            return TraitLore.add(output, List.of(), Map.of("equipment", slot.getName()));
        }
        return true;
    }

}
