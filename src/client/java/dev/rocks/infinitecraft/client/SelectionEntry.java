package dev.rocks.infinitecraft.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A selection-only dropdown. Its choices use the config list's normal layout and scrolling. */
class SelectionEntry<T> extends AbstractConfigListEntry<T> {
    private final T original;
    private final T defaultValue;
    private final Function<T, String> label;
    private T value;
    private boolean expanded;
    private final Button toggle;
    private final List<Button> choices = new ArrayList<>();
    private List<T> options = List.of();

    SelectionEntry(String title, T initial, T defaultValue, List<T> options, Function<T, String> label) {
        super(Component.literal(title), false);
        original = initial;
        value = initial;
        this.defaultValue = defaultValue;
        this.label = label;
        toggle = new Button(0, 0, 150, 20, Component.literal(label.apply(initial)),
                ignored -> expanded = !expanded, supplier -> supplier.get()) {
            @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                extractDefaultSprite(graphics);
                var font = Minecraft.getInstance().font;
                String arrow = expanded ? "\u25B2" : "\u25BC";
                int arrowX = getX() + getWidth() - 6 - font.width(arrow);
                int left = getX() + labelInset();
                int available = Math.max(0, arrowX - 6 - left);
                String text = getMessage().getString().strip();
                if (font.width(text) > available) {
                    text = font.plainSubstrByWidth(text, Math.max(0, available - font.width("..."))) + "...";
                }
                var visible = Component.literal(text).setStyle(getMessage().getStyle());
                int color = active ? 0xFFFFFFFF : 0xFFA0A0A0;
                int textY = getY() + (getHeight() - 8) / 2;
                graphics.text(font, visible, left + Math.max(0, (available - font.width(text)) / 2), textY, color);
                graphics.text(font, Component.literal(arrow), arrowX, textY, color);
            }
        };
        setOptions(options);
    }

    protected final void setOptions(List<T> options) {
        expanded = false;
        setFocused((GuiEventListener) null);
        choices.clear();
        this.options = List.copyOf(options);
        for (T option : options) {
            choices.add(Button.builder(Component.literal(label.apply(option)), ignored -> {
                value = option;
                expanded = false;
                setFocused(toggle);
            }).bounds(0, 0, 150, 20).build());
        }
    }

    protected Tooltip tooltip() { return null; }
    protected int labelInset() { return 6; }
    protected int buttonHeight() { return 20; }
    protected int buttonWidth(int width) { return Math.min(150, Math.max(80, width / 2)); }
    protected Component optionText(T option) { return Component.literal(label.apply(option)); }
    protected void drawOption(GuiGraphicsExtractor graphics, Button button, T option, int index, int labelX) {}

    protected final void setValue(T value) { this.value = value; }
    @Override public T getValue() { return value; }
    @Override public Optional<T> getDefaultValue() { return Optional.of(defaultValue); }
    @Override public boolean isEdited() { return !Objects.equals(getValue(), original); }
    @Override public int getItemHeight() { return buttonHeight() + 4 + (expanded ? choices.size() * (buttonHeight() + 2) : 0); }

    private List<Button> controls() {
        if (!expanded) return List.of(toggle);
        var buttons = new ArrayList<Button>();
        buttons.add(toggle);
        buttons.addAll(choices);
        return buttons;
    }

    @Override public List<? extends GuiEventListener> children() { return controls(); }
    @Override public List<? extends NarratableEntry> narratables() { return controls(); }

    @Override public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) expanded = false;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);
        if (handled && !expanded) setFocused(toggle);
        return handled;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int index, int y, int x,
            int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
        super.extractRenderState(graphics, index, y, x, width, height, mouseX, mouseY, hovered, delta);
        int buttonWidth = buttonWidth(width);
        graphics.text(Minecraft.getInstance().font, getFieldName(), x, y + (buttonHeight() - 8) / 2, getPreferredTextColor());
        toggle.setMessage(optionText(value));
        toggle.setTooltip(tooltip());
        var buttons = controls();
        for (int i = 0; i < buttons.size(); i++) {
            var button = buttons.get(i);
            button.setX(x + width - buttonWidth);
            button.setY(y + (i == 0 ? 0 : buttonHeight() + 4 + (i - 1) * (buttonHeight() + 2)));
            button.setHeight(buttonHeight());
            T option = i == 0 ? value : options.get(i - 1);
            if (i > 0) button.setMessage(optionText(option));
            button.setWidth(buttonWidth);
            button.active = isEditable();
            button.extractRenderState(graphics, mouseX, mouseY, delta);
            drawOption(graphics, button, option, i - 1, x);
        }
    }
}
