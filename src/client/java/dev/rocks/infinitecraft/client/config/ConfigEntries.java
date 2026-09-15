package dev.rocks.infinitecraft.client.config;

import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;

final class ConfigEntries {
    private final ConfigEntryBuilder entries;

    ConfigEntries(ConfigEntryBuilder entries) {
        this.entries = entries;
    }

    void section(ConfigCategory category, String label) {
        var heading = Component.literal(label).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        category.addEntry(entries.startTextDescription(heading).build());
    }

    void toggle(ConfigCategory category, String label, boolean value, boolean defaultValue,
            Consumer<Boolean> save) {
        category.addEntry(toggleEntry(label, value, defaultValue, save));
    }

    BooleanListEntry toggleEntry(String label, boolean value, boolean defaultValue, Consumer<Boolean> save) {
        return entries.startBooleanToggle(Component.literal(label), value)
                .setDefaultValue(defaultValue)
                .setSaveConsumer(save)
                .build();
    }

    BooleanListEntry toggleEntry(String label, boolean value, boolean defaultValue, String tooltip,
            Consumer<Boolean> save) {
        return entries.startBooleanToggle(Component.literal(label), value)
                .setDefaultValue(defaultValue)
                .setTooltip(Component.literal(tooltip))
                .setSaveConsumer(save)
                .build();
    }

    void toggle(ConfigCategory category, String label, boolean value, boolean defaultValue,
            String tooltip, Consumer<Boolean> save) {
        category.addEntry(entries.startBooleanToggle(Component.literal(label), value)
                .setDefaultValue(defaultValue)
                .setTooltip(Component.literal(tooltip))
                .setSaveConsumer(save)
                .build());
    }

    void integer(ConfigCategory category, String label, int value, int defaultValue, int minimum, int maximum,
            Consumer<Integer> save) {
        category.addEntry(entries.startIntField(Component.literal(label), value)
                .setDefaultValue(defaultValue)
                .setMin(minimum)
                .setMax(maximum)
                .setSaveConsumer(save)
                .build());
    }

    void integer(ConfigCategory category, String label, int value, int defaultValue, int minimum, int maximum,
            String tooltip, Consumer<Integer> save) {
        category.addEntry(entries.startIntField(Component.literal(label), value)
                .setDefaultValue(defaultValue)
                .setMin(minimum)
                .setMax(maximum)
                .setTooltip(Component.literal(tooltip))
                .setSaveConsumer(save)
                .build());
    }

    void slider(ConfigCategory category, String label, int value, int defaultValue, int minimum, int maximum,
            Function<Integer, Component> text, Consumer<Integer> save) {
        category.addEntry(sliderEntry(label, value, defaultValue, minimum, maximum, text, save));
    }

    IntegerSliderEntry sliderEntry(String label, int value, int defaultValue, int minimum, int maximum,
            Function<Integer, Component> text, Consumer<Integer> save) {
        return entries.startIntSlider(Component.literal(label), value, minimum, maximum)
                .setDefaultValue(defaultValue)
                .setTextGetter(text)
                .setSaveConsumer(save)
                .build();
    }

    IntegerSliderEntry sliderEntry(String label, int value, int defaultValue, int minimum, int maximum,
            String tooltip, Function<Integer, Component> text, Consumer<Integer> save) {
        return entries.startIntSlider(Component.literal(label), value, minimum, maximum)
                .setDefaultValue(defaultValue)
                .setTooltip(Component.literal(tooltip))
                .setTextGetter(text)
                .setSaveConsumer(save)
                .build();
    }
}
