package dev.rocks.infinitecraft.item;

import dev.rocks.infinitecraft.fusion.FusionCount;
import dev.rocks.infinitecraft.traits.TraitDefinition;
import dev.rocks.infinitecraft.traits.TraitRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Hint at how an item can be used without revealing its effect. */
public final class TraitLore {
    public static final String EDIBLE = "🍎 Edible";
    public static final int EDIBLE_COLOR = 0x55FF55;
    public static final String WEARABLE = "👕 Wearable";
    public static final int WEARABLE_COLOR = 0x6FB7FF;
    private static final Map<String, Component> LINES = TraitRegistry.definitions().stream()
            .filter(trait -> !trait.hint().isEmpty())
            .collect(Collectors.toUnmodifiableMap(
                    TraitDefinition::id, trait -> line(trait.hint(), trait.hintColor())));
    private static final Map<String, Component> ACTIVATION_LINES = Map.of(
            "Edible", line(EDIBLE, EDIBLE_COLOR),
            "Wearable", line(WEARABLE, WEARABLE_COLOR),
            "Offhand", line("✋ Offhand", 0xFFB38A));

    private static Component line(String text, int color) {
        return Component.literal(text).withStyle(style -> style.withColor(color).withItalic(false));
    }

    /** Hint text without its leading icon, so lines from older versions still match. */
    private static String label(String text) {
        int space = text.indexOf(' ');
        return space > 0 && !Character.isLetter(text.codePointAt(0)) ? text.substring(space + 1) : text;
    }

    public static boolean add(ItemStack stack, List<String> traits) {
        return add(stack, traits, Map.of());
    }

    public static boolean add(ItemStack stack, List<String> traits, Map<String, String> activations) {
        List<Component> lines = new ArrayList<>(deduplicatedLines(stack));
        boolean offhand = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().stream()
                .anyMatch(entry -> entry.slot() == net.minecraft.world.entity.EquipmentSlotGroup.OFFHAND);
        if (!offhand) lines.removeIf(line -> label(line.getString()).equals("Offhand"));
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
            Component line = ACTIVATION_LINES.get(hint);
            if (line != null && !lines.contains(line)) lines.add(line);
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
        return normalizedHint(value) != null;
    }

    private static List<Component> deduplicatedLines(ItemStack stack) {
        List<Component> result = new ArrayList<>();
        for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
            if (FusionCount.get(stack) >= 0 && FusionCount.isCounterLine(line)) continue;
            Component normalized = normalizedHint(line);
            if (normalized == null || !result.contains(normalized)) result.add(normalized == null ? line : normalized);
        }
        return result;
    }

    private static Component normalizedHint(Component line) {
        String label = label(line.getString());
        Component activation = ACTIVATION_LINES.get(label);
        if (activation != null) return activation;
        // Existing discoveries can carry the same hint with an older glyph and color, or none.
        return LINES.values().stream().filter(hint -> label(hint.getString()).equals(label))
                .findFirst().orElse(null);
    }
}
