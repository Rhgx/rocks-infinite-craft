package dev.rocks.infinitecraft.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** A vanilla button showing a swappable 10x10 icon; the message is kept for narration only. */
public final class IconButton extends Button {
    private Identifier icon;

    public IconButton(int x, int y, int size, Identifier icon, OnPress onPress) {
        super(x, y, size, size, Component.empty(), onPress, DEFAULT_NARRATION);
        this.icon = icon;
    }

    public void setIcon(Identifier icon) {
        this.icon = icon;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractDefaultSprite(graphics);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, getX() + (width - 10) / 2, getY() + (height - 10) / 2,
                10, 10, active ? 0xFFFFFFFF : 0xFF808080);
    }
}
