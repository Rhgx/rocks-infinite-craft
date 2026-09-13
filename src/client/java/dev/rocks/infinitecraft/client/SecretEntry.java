package dev.rocks.infinitecraft.client;

import java.util.List;
import java.util.Optional;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Keep the actual key out of both rendered text and screen-reader narration. */
final class SecretEntry extends AbstractConfigListEntry<String> {
    private final String original;
    private final EditBox field;
    private final Button clear;

    SecretEntry(String value) {
        super(Component.literal("API key"), false);
        original = value;
        field = new EditBox(Minecraft.getInstance().font, 0, 0, 148, 18, Component.literal("API key")) {
            @Override protected MutableComponent createNarrationMessage() {
                return Component.literal(getValue().isEmpty() ? "API key, empty" : "API key, hidden");
            }
        };
        field.setMaxLength(4096);
        field.addFormatter((text, offset) -> Component.literal("*".repeat(text.length())).getVisualOrderText());
        field.setValue(value);
        field.setTooltip(Tooltip.create(Component.literal("Saved in plaintext in this instance's local config. Leave empty to use the environment variable.")));
        clear = Button.builder(Component.literal("Clear"), button -> field.setValue("")).bounds(0, 0, 44, 20).build();
    }

    @Override public String getValue() { return field.getValue(); }
    @Override public Optional<String> getDefaultValue() { return Optional.of(""); }
    @Override public boolean isEdited() { return !original.equals(getValue()); }
    @Override public int getItemHeight() { return 24; }
    @Override public List<? extends GuiEventListener> children() { return List.of(field, clear); }
    @Override public List<? extends NarratableEntry> narratables() { return List.of(field, clear); }

    @Override public void updateSelected(boolean selected) {
        if (!selected) { setFocused(null); field.setFocused(false); clear.setFocused(false); }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int index, int y, int x,
            int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
        super.extractRenderState(graphics, index, y, x, width, height, mouseX, mouseY, hovered, delta);
        int fieldWidth = Math.max(40, Math.min(148, width - 110));
        clear.setX(x + width - 44);
        clear.setY(y);
        clear.active = isEditable() && !getValue().isEmpty();
        field.setX(clear.getX() - fieldWidth - 4);
        field.setY(y + 1);
        field.setWidth(fieldWidth);
        field.setEditable(isEditable());
        field.active = isEditable();
        graphics.text(Minecraft.getInstance().font, getDisplayedFieldName(), x, y + 6, getPreferredTextColor());
        field.extractRenderState(graphics, mouseX, mouseY, delta);
        clear.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
