package dev.rocks.infinitecraft.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;

final class ProviderEntry extends SelectionEntry<ProviderEntry.Provider> {
    enum Provider {
        OLLAMA("Ollama", 0xEEEEEE, "On this device"), CODEX("Codex CLI", 0xEEEEEE, ""),
        OPENAI("OpenAI", 0xEEEEEE, "Hosted APIs"), ANTHROPIC("Anthropic", 0xE5B899, ""),
        GEMINI("Gemini", 0x99B9FF, ""), OPENROUTER("OpenRouter", 0xEEEEEE, ""),
        COMPATIBLE("Compatible API", 0x98D9DC, "Other"), DISABLED("Disabled", 0xAAAAAA, "");

        final String label;
        final int color;
        final String group;
        final Identifier icon;

        Provider(String label, int color, String group) {
            this.label = label;
            this.color = color;
            this.group = group;
            icon = Identifier.fromNamespaceAndPath("rocks_infinite_craft",
                    "textures/gui/providers/" + name().toLowerCase(Locale.ROOT) + ".png");
        }
    }

    ProviderEntry(Provider initial) {
        super("Provider", initial, Provider.DISABLED, List.of(Provider.values()), provider -> provider.label);
    }

    @Override
    protected int labelInset() { return 36; }
    @Override
    protected int buttonHeight() { return 32; }
    @Override
    protected Component optionText(Provider option) {
        return Component.literal("    " + option.label).withColor(option.color);
    }

    @Override
    protected void drawOption(GuiGraphicsExtractor graphics, Button button, Provider option, int index, int labelX) {
        int size = option == Provider.OLLAMA ? 24 : 16;
        graphics.blit(RenderPipelines.GUI_TEXTURED, option.icon,
                button.getX() + 6 + (24 - size) / 2, button.getY() + (button.getHeight() - size) / 2,
                0, 0, size, size, size, size);
        if (index >= 0 && !option.group.isEmpty()) {
            var font = Minecraft.getInstance().font;
            var caption = font.plainSubstrByWidth(option.group, Math.max(0, button.getX() - labelX - 6));
            graphics.text(font, Component.literal(caption), labelX, button.getY() + (button.getHeight() - 8) / 2, 0xFFAAAAAA);
        }
    }

    @Override
    protected Tooltip tooltip() {
        return getValue() == Provider.CODEX ? Tooltip.create(Component.literal("Uses your Codex CLI login.")) : null;
    }
}
