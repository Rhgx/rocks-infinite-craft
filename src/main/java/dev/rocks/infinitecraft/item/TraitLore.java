package dev.rocks.infinitecraft.item;

import dev.rocks.infinitecraft.fusion.FusionCount;
import dev.rocks.infinitecraft.traits.TraitRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Hint at how an item can be used without revealing its effect. */
public final class TraitLore {
    private static final Map<String, Component> LINES = TraitRegistry.definitions().stream()
            .filter(trait -> !trait.hint().isEmpty())
            .collect(java.util.stream.Collectors.toUnmodifiableMap(trait -> trait.id(), trait -> line(trait.hint())));

    private static Component line(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false));
    }

    public static boolean add(ItemStack stack, List<String> traits) {
        return add(stack, traits, Map.of());
    }

    public static boolean add(ItemStack stack, List<String> traits, Map<String, String> activations) {
        List<Component> lines = new ArrayList<>(deduplicatedLines(stack));
        boolean offhand = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().stream()
                .anyMatch(entry -> entry.slot() == net.minecraft.world.entity.EquipmentSlotGroup.OFFHAND);
        if (!offhand) lines.remove(line("Offhand"));
        for (String trait : traits) {
            Component line = LINES.get(trait);
            if (line != null && !lines.contains(line)) lines.add(line);
        }
        for (String mode : activations.values()) {
            String hint = switch (mode) {
                case "consumed", "consumed_brief", "consumed_long", "consumed_intense" -> "Edible";
                case "head", "chest", "legs", "feet" -> "Wearable";
                case "offhand" -> "Offhand";
                default -> "";
            };
            if (!hint.isEmpty() && !lines.contains(line(hint))) lines.add(line(hint));
        }
        if (lines.size() > ItemLore.MAX_LINES) return false;
        if (!lines.isEmpty() || stack.has(DataComponents.LORE)) stack.set(DataComponents.LORE, new ItemLore(lines));
        return true;
    }

    public static boolean merge(ItemStack result, ItemStack first, ItemStack second) {
        var a = deduplicatedLines(first);
        var b = deduplicatedLines(second);
        var customA = a.stream().filter(line -> !isHint(line)).toList();
        var customB = b.stream().filter(line -> !isHint(line)).toList();
        if (!customA.isEmpty() && !customB.isEmpty() && !customA.equals(customB)) return false;
        List<Component> merged = new ArrayList<>(customA.isEmpty() ? customB : customA);
        for (var source : List.of(a, b)) for (var line : source)
            if (isHint(line) && !merged.contains(line)) merged.add(line);
        if (merged.size() > ItemLore.MAX_LINES) return false;
        if (!merged.isEmpty()) result.set(DataComponents.LORE, new ItemLore(merged));
        return true;
    }

    private static boolean isHint(Component value) {
        return LINES.containsValue(value) || List.of(line("Offhand"), line("Edible"), line("Wearable")).contains(value);
    }

    private static List<Component> deduplicatedLines(ItemStack stack) {
        List<Component> result = new ArrayList<>();
        for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
            if (FusionCount.get(stack) >= 0 && FusionCount.isCounterLine(line)) continue;
            if (!isHint(line) || !result.contains(line)) result.add(line);
        }
        return result;
    }
}
