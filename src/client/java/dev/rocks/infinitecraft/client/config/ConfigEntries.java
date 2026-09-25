package dev.rocks.infinitecraft.client.config;

import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/** Adds entries to a category and returns them, so callers can attach requirements. */
final class ConfigEntries {
    private final ConfigEntryBuilder entries;

    ConfigEntries(ConfigEntryBuilder entries) {
        this.entries = entries;
    }

    void section(ConfigCategory category, String label) {
        var heading = Component.literal(label).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        category.addEntry(entries.startTextDescription(heading).build());
    }

    /** A heading followed by one short gray line explaining the section. */
    void section(ConfigCategory category, String label, String intro) {
        section(category, label);
        category.addEntry(entries.startTextDescription(Component.literal(intro).withStyle(ChatFormatting.GRAY)).build());
    }

    BooleanListEntry toggle(ConfigCategory category, String label, boolean value, boolean defaultValue,
            String tooltip, Consumer<Boolean> save) {
        var entry = entries.startBooleanToggle(Component.literal(label), value)
                .setDefaultValue(defaultValue)
                .setTooltip(Component.literal(tooltip))
                .setSaveConsumer(save)
                .build();
        category.addEntry(entry);
        return entry;
    }

    IntegerListEntry integer(ConfigCategory category, String label, int value, int defaultValue, int minimum,
            int maximum, String tooltip, Consumer<Integer> save) {
        var entry = entries.startIntField(Component.literal(label), value)
                .setDefaultValue(defaultValue)
                .setMin(minimum)
                .setMax(maximum)
                .setTooltip(Component.literal(tooltip + " Range: " + minimum + " to " + maximum + "."))
                .setSaveConsumer(save)
                .build();
        category.addEntry(entry);
        return entry;
    }

    IntegerSliderEntry slider(ConfigCategory category, String label, int value, int defaultValue, int minimum,
            int maximum, String tooltip, Function<Integer, Component> text, Consumer<Integer> save) {
        var entry = entries.startIntSlider(Component.literal(label), value, minimum, maximum)
                .setDefaultValue(defaultValue)
                .setTooltip(Component.literal(tooltip))
                .setTextGetter(text)
                .setSaveConsumer(save)
                .build();
        category.addEntry(entry);
        return entry;
    }

    static Component number(int value) {
        return Component.literal(Integer.toString(value));
    }

    /** Shows a tick count as seconds, the unit players think in. */
    static Component seconds(int ticks) {
        return Component.literal(ticks % 20 == 0 ? ticks / 20 + " s" : String.format(Locale.ROOT, "%.2f s", ticks / 20.0));
    }
}
