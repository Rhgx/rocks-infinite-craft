package dev.rocks.infinitecraft.client.config;

import dev.rocks.infinitecraft.ModConfig;
import dev.rocks.infinitecraft.traits.TraitDefinition;
import dev.rocks.infinitecraft.traits.TraitRegistry;
import dev.rocks.infinitecraft.traits.TraitSettings;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

final class TraitConfigEntries {
    private TraitConfigEntries() {}

    static void add(ConfigCategory category, ConfigEntryBuilder entries, ModConfig config) {
        for (String group : List.of("Attributes", "Weapons", "Armor", "Food", "Utility")) {
            var section = entries.startSubCategory(Component.literal(group))
                    .setExpanded(false)
                    .setTooltip(Component.literal("Choose which traits can appear in new recipes. Turning off combat traits also stops their triggers."));
            for (var trait : TraitRegistry.definitions()) {
                if (!group.equals(group(trait))) continue;
                var toggle = entries.startBooleanToggle(Component.literal(label(trait)),
                                !config.disabledTraits.contains(trait.id()))
                        .setDefaultValue(true)
                        .setSaveConsumer(enabled -> {
                            var disabled = new HashSet<>(config.disabledTraits);
                            if (enabled) disabled.remove(trait.id()); else disabled.add(trait.id());
                            config.disabledTraits = disabled;
                        });
                if (!trait.description().isEmpty()) toggle.setTooltip(Component.literal(trait.description()));
                section.add(toggle.build());
                if (!TraitSettings.CHANCE_TRAITS.contains(trait.id())) continue;

                int defaultChance = TraitSettings.defaultChance(trait.id());
                boolean explosive = trait.id().equals("explosive");
                section.add(entries.startIntSlider(Component.literal(explosive ? "  Base chance" : "  Chance")
                                        .withStyle(ChatFormatting.GRAY),
                                config.traitChances.getOrDefault(trait.id(), defaultChance), 0, 100)
                        .setDefaultValue(defaultChance)
                        .setTextGetter(value -> Component.literal(value + "%"))
                        .setTooltip(Component.literal(explosive
                                ? "Increases with recent damage, up to five times this chance (maximum 100%)."
                                : trait.phase() == TraitDefinition.Phase.FOOD
                                        ? "Applies to newly created food. Existing food keeps its chance."
                                        : "Applies to existing and newly created items."))
                        .setSaveConsumer(value -> {
                            var chances = new HashMap<>(config.traitChances);
                            if (value == defaultChance) chances.remove(trait.id()); else chances.put(trait.id(), value);
                            config.traitChances = chances;
                        })
                        .build());
            }
            category.addEntry(section.build());
        }
    }

    private static String group(TraitDefinition trait) {
        if (trait.phase() == TraitDefinition.Phase.FOOD) return "Food";
        return switch (trait.id()) {
            case "strong", "attack_speed", "knockback", "sweeping", "explosive", "incendiary",
                    "vampiric", "launching", "frostbite", "revealing" -> "Weapons";
            case "anchored", "armored", "tough", "healthy", "startled", "hardened", "cold_shoulder" -> "Armor";
            case "blocking", "gliding", "death_protection", "lucky_block" -> "Utility";
            default -> "Attributes";
        };
    }

    private static String label(TraitDefinition trait) {
        return switch (trait.id()) {
            case "airy_food" -> "Airy";
            case "hearty_food" -> "Hearty";
            case "vanishing_food" -> "Vanishing";
            case "edible_healing" -> "Healing food";
            case "edible_teleport" -> "Teleporting food";
            default -> {
                String words = trait.id().replace('_', ' ');
                yield Character.toUpperCase(words.charAt(0)) + words.substring(1);
            }
        };
    }
}
