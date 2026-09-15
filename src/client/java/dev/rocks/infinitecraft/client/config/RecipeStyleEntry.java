package dev.rocks.infinitecraft.client.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Five named stops with the native slider's mouse, focus and narration behavior. */
final class RecipeStyleEntry extends AbstractConfigListEntry<Integer> {
    private final int original;
    private int selected;
    private final AbstractSliderButton slider;
    private final Button reset;

    RecipeStyleEntry(String title, int initial, List<String> labels, Consumer<Integer> save) {
        super(Component.literal(title), false);
        original = initial;
        selected = Math.round(initial / 25F) * 25;
        saveCallback = save;
        slider = new AbstractSliderButton(0, 0, 106, 20, Component.literal(labels.get(selected / 25)), selected / 100.0) {
            @Override
            protected void setValue(double next) {
                super.setValue(Math.round(Math.clamp(next, 0, 1) * 4) / 4.0);
            }
            @Override
            protected void applyValue() { selected = (int) Math.round(value * 4) * 25; }
            @Override
            protected void updateMessage() { setMessage(Component.literal(labels.get(selected / 25))); }
            @Override
            public boolean keyPressed(KeyEvent event) {
                if (active && canChangeValue && (event.key() == GLFW.GLFW_KEY_LEFT || event.key() == GLFW.GLFW_KEY_RIGHT)) {
                    setValue(value + (event.key() == GLFW.GLFW_KEY_LEFT ? -0.25 : 0.25));
                    return true;
                }
                return super.keyPressed(event);
            }
            @Override
            public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                // Reset changes the entry value; keep the handle and label synchronized.
                value = selected / 100.0;
                updateMessage();
                super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
            }
        };
        reset = Button.builder(Component.translatable("text.cloth-config.reset_value"), ignored -> selected = 50)
                .bounds(0, 0, 40, 20).build();
    }

    @Override
    public Integer getValue() { return selected; }
    @Override
    public Optional<Integer> getDefaultValue() { return Optional.of(50); }
    @Override
    public boolean isEdited() { return selected != original; }
    @Override
    public int getItemHeight() { return 26; }
    @Override
    public List<? extends GuiEventListener> children() { return List.of(slider, reset); }
    @Override
    public List<? extends NarratableEntry> narratables() { return List.of(slider, reset); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int index, int y, int x,
            int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
        super.extractRenderState(graphics, index, y, x, width, height, mouseX, mouseY, hovered, delta);
        int controlWidth = Math.min(150, Math.max(80, width / 2));
        slider.setX(x + width - controlWidth);
        slider.setY(y);
        slider.setWidth(controlWidth - 44);
        slider.active = isEditable();
        reset.setX(x + width - 40);
        reset.setY(y);
        reset.active = isEditable() && selected != 50;
        graphics.text(Minecraft.getInstance().font, getFieldName(), x, y + 6, getPreferredTextColor());
        slider.extractRenderState(graphics, mouseX, mouseY, delta);
        reset.extractRenderState(graphics, mouseX, mouseY, delta);
        for (int stop = 0; stop <= 4; stop++) {
            int marker = slider.getX() + 4 + Math.round((slider.getWidth() - 8) * stop / 4F);
            graphics.fill(marker, y + 21, marker + 1, y + 23, stop * 25 == selected ? 0xFFFFAA00 : 0xFF777777);
        }
    }
}
