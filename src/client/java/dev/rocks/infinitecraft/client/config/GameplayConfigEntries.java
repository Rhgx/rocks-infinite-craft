package dev.rocks.infinitecraft.client.config;

import dev.rocks.infinitecraft.ModConfig;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import net.minecraft.network.chat.Component;

import java.util.List;

final class GameplayConfigEntries {
    private GameplayConfigEntries() {
    }

    static BookControls add(ConfigCategory category, ConfigEntries fields, ModConfig config) {
        fields.section(category, "Fusion");
        fields.toggle(category, "Enable item fusion", config.enabled, true, value -> config.enabled = value);
        fields.toggle(category, "Ground fusion", config.groundFusion, true, value -> config.groundFusion = value);
        fields.toggle(category, "Fusion Crafter", config.crafterFusion, true,
                "Fuses items placed in two Crafter slots.", value -> config.crafterFusion = value);
        fields.toggle(category, "Allow modded items", config.allowModdedItems, true,
                value -> config.allowModdedItems = value);
        fields.toggle(category, "Item data fusion", config.allowItemData, true,
                "Preserve enchantments, potions, names and durability.", value -> config.allowItemData = value);

        fields.section(category, "Recipe style");
        category.addEntry(new RecipeStyleEntry("Power", config.power,
                List.of("Gentle", "Mild", "Balanced", "Powerful", "Overpowered"), value -> config.power = value));
        category.addEntry(new RecipeStyleEntry("Silliness", config.silliness,
                List.of("Sensible", "Playful", "Silly", "Absurd", "Ridiculous"), value -> config.silliness = value));

        fields.section(category, "Output");
        fields.slider(category, "Maximum traits", config.maxTraits, 3, 0, 8,
                value -> Component.literal(Integer.toString(value)), value -> config.maxTraits = value);
        fields.slider(category, "Maximum quantity", config.maxOutputCount, 8, 1, 64,
                value -> Component.literal(Integer.toString(value)), value -> config.maxOutputCount = value);

        fields.section(category, "Special items");
        var special = fields.toggleEntry("Special results", config.generatedTraits, true,
                value -> config.generatedTraits = value);
        category.addEntry(special);
        fields.toggle(category, "Combine special items", config.combineSpecialItems, false,
                "Allow two crafted special items to fuse together.", value -> config.combineSpecialItems = value);
        var chance = fields.sliderEntry("Random chance", config.specialResultChance, 5, 0, 100,
                "Chance of a special result.",
                value -> Component.literal(value + "%"), value -> config.specialResultChance = value);
        chance.setDisplayRequirement(special::getValue);
        category.addEntry(chance);
        var triggers = fields.toggleEntry("Item triggers", config.specialIngredientTriggers, true,
                "Selected item types bypass the chance roll.",
                value -> config.specialIngredientTriggers = value);
        triggers.setDisplayRequirement(special::getValue);
        category.addEntry(triggers);

        fields.section(category, "Special item types");
        fields.toggle(category, "Rarity", config.specialRarity, true,
                "Uncommon, Rare and Epic items. Also enables rarity quality boosts.", value -> config.specialRarity = value);
        fields.toggle(category, "Enchantments", config.specialEnchantments, true,
                "Enchanted items and books.", value -> config.specialEnchantments = value);
        fields.toggle(category, "Potions and stew", config.specialPotions, true,
                value -> config.specialPotions = value);
        fields.toggle(category, "Custom item data", config.specialCustomData, true,
                "Names, dyes, models and modified traits.", value -> config.specialCustomData = value);

        fields.section(category, "Discovery Book");
        var ownership = new SelectionEntry<>("Book ownership", config.soulboundBook, true,
                List.of(true, false), value -> value ? "Soulbound" : "Craftable");
        var visibility = new SelectionEntry<>("Book visibility", config.personalBook, false,
                List.of(false, true), value -> value ? "Personal" : "Global");
        category.addEntry(ownership);
        category.addEntry(visibility);
        return new BookControls(ownership, visibility);
    }
}
