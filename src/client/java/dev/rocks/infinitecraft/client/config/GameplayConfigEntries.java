package dev.rocks.infinitecraft.client.config;

import dev.rocks.infinitecraft.ModConfig;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.Requirement;
import net.minecraft.network.chat.Component;

import java.util.List;

final class GameplayConfigEntries {
    private GameplayConfigEntries() {
    }

    static BookControls add(ConfigCategory category, ConfigEntries fields, ModConfig config) {
        fields.section(category, "Fusion", "How players combine items.");
        var enabled = fields.toggle(category, "Enable item fusion", config.enabled, true,
                "Master switch. When off, nothing fuses anywhere.", value -> config.enabled = value);
        var fusionOn = Requirement.isTrue(enabled);
        fields.toggle(category, "Ground fusion", config.groundFusion, true,
                "Drop two items next to each other to fuse them.", value -> config.groundFusion = value)
                .setRequirement(fusionOn);
        fields.toggle(category, "Fusion Crafter", config.crafterFusion, true,
                "Fuse items placed in the two input slots of a Fusion Crafter.", value -> config.crafterFusion = value)
                .setRequirement(fusionOn);
        fields.toggle(category, "Allow modded items", config.allowModdedItems, true,
                "Let items from other mods be ingredients and results.", value -> config.allowModdedItems = value)
                .setRequirement(fusionOn);
        fields.toggle(category, "Keep item data", config.allowItemData, true,
                "Carry enchantments, potions, names and durability into the result.", value -> config.allowItemData = value)
                .setRequirement(fusionOn);

        fields.section(category, "Recipe style", "The tone new recipes are generated with.");
        category.addEntry(new RecipeStyleEntry("Power", config.power,
                List.of("Gentle", "Mild", "Balanced", "Powerful", "Overpowered"), value -> config.power = value));
        category.addEntry(new RecipeStyleEntry("Silliness", config.silliness,
                List.of("Sensible", "Playful", "Silly", "Absurd", "Ridiculous"), value -> config.silliness = value));

        fields.section(category, "Output");
        fields.slider(category, "Maximum traits", config.maxTraits, 3, 0, 8,
                "Most traits a single result can have.", ConfigEntries::number, value -> config.maxTraits = value);
        fields.slider(category, "Maximum quantity", config.maxOutputCount, 8, 1, 64,
                "Largest stack one fusion can produce.", ConfigEntries::number, value -> config.maxOutputCount = value);

        fields.section(category, "Discovery Book", "The book that records every recipe players find.");
        var ownership = new SelectionEntry<>("Book ownership", config.soulboundBook, true,
                List.of(true, false), value -> value ? "Soulbound" : "Craftable");
        var visibility = new SelectionEntry<>("Book visibility", config.personalBook, false,
                List.of(false, true), value -> value ? "Personal" : "Global");
        category.addEntry(ownership);
        category.addEntry(visibility);
        return new BookControls(ownership, visibility);
    }

    static void addSpecial(ConfigCategory category, ConfigEntries fields, ModConfig config) {
        fields.section(category, "Special results", "Rare results with generated traits and effects.");
        var special = fields.toggle(category, "Special results", config.generatedTraits, true,
                "Allow fusions to create special items.", value -> config.generatedTraits = value);
        var specialOn = Requirement.isTrue(special);
        fields.slider(category, "Random chance", config.specialResultChance, 5, 0, 100,
                "Chance that any fusion gives a special result.",
                value -> Component.literal(value + "%"), value -> config.specialResultChance = value)
                .setRequirement(specialOn);
        var triggers = fields.toggle(category, "Item triggers", config.specialIngredientTriggers, true,
                "Ingredients of the types below always give a special result.",
                value -> config.specialIngredientTriggers = value);
        triggers.setRequirement(specialOn);

        fields.section(category, "Special item types", "What counts as a special item, for triggers and the combination limit.");
        fields.toggle(category, "Rarity", config.specialRarity, true,
                "Uncommon, Rare and Epic items. Also enables rarity quality boosts.", value -> config.specialRarity = value);
        fields.toggle(category, "Enchantments", config.specialEnchantments, true,
                "Enchanted items and books.", value -> config.specialEnchantments = value);
        fields.toggle(category, "Potions and stew", config.specialPotions, true,
                "Potions, tipped arrows and suspicious stew.", value -> config.specialPotions = value);
        fields.toggle(category, "Custom item data", config.specialCustomData, true,
                "Names, dyes, models and modified traits.", value -> config.specialCustomData = value);

        fields.section(category, "Chaining");
        fields.toggle(category, "Combine special items", config.combineSpecialItems, false,
                "Allow two crafted special items to fuse together.", value -> config.combineSpecialItems = value);
        fields.slider(category, "Combination limit", config.specialCombinationLimit, 5, 0, 64,
                "Most fusions a special item's lineage can go through.",
                value -> Component.literal(value == 0 ? "Unlimited" : Integer.toString(value)),
                value -> config.specialCombinationLimit = value);
    }
}
