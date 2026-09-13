package dev.rocks.infinitecraft.client;

import dev.rocks.infinitecraft.provider.ProviderConfig;
import dev.rocks.infinitecraft.provider.ProviderConnectionTest;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;


final class ConnectionTestEntry extends AbstractConfigListEntry<Boolean> {
    private static final List<Identifier> PINGING = java.util.stream.IntStream.rangeClosed(1, 5)
            .mapToObj(frame -> Identifier.withDefaultNamespace("server_list/pinging_" + frame)).toList();
    private final Button button;
    private String status = "Connection";
    private int statusColor = 0xFFAAAAAA;
    private Identifier icon = Identifier.withDefaultNamespace("icon/ping_unknown");
    private boolean running;

    ConnectionTestEntry(Supplier<ProviderConfig> currentSettings) {
        super(Component.literal("Test connection"), false);
        button = Button.builder(Component.literal("Test connection"), ignored -> test(currentSettings)).bounds(0, 0, 150, 20).build();
        button.setTooltip(Tooltip.create(Component.literal("Test current settings. Hosted APIs may charge.")));
    }

    private void test(Supplier<ProviderConfig> currentSettings) {
        if (running) return;
        ProviderConfig config;
        try { config = currentSettings.get(); }
        catch (RuntimeException error) {
            setStatus("Check settings", "Check provider, model, URL, key and timeout.", false);
            return;
        }
        running = true;
        button.active = false;
        button.setMessage(Component.literal("Testing..."));
        status = "Testing...";
        statusColor = 0xFFFFCC66;

        ProviderConnectionTest.start(config).thenAccept(result -> Minecraft.getInstance().execute(() -> {
            running = false;
            button.setMessage(Component.literal("Test connection"));
            setStatus(result.success() ? "Connected" : "Test failed", result.message(), result.success());
        }));
    }

    private void setStatus(String value, String detail, boolean success) {
        status = value;
        statusColor = success ? 0xFF99DD88 : 0xFFFF8888;
        icon = Identifier.withDefaultNamespace(success ? "icon/ping_5" : "icon/ping_unknown");
        button.setTooltip(Tooltip.create(Component.literal(detail)));
    }

    @Override public Boolean getValue() { return false; }
    @Override public Optional<Boolean> getDefaultValue() { return Optional.of(false); }
    @Override public boolean isEdited() { return false; }
    @Override public int getItemHeight() { return 24; }
    @Override public List<? extends GuiEventListener> children() { return List.of(button); }
    @Override public List<? extends NarratableEntry> narratables() { return List.of(button); }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int index, int y, int x,
            int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
        super.extractRenderState(graphics, index, y, x, width, height, mouseX, mouseY, hovered, delta);
        var font = Minecraft.getInstance().font;
        button.setWidth(Math.min(150, Math.max(80, width / 2)));
        // GUI sprites are available at the title screen before item components are bound.
        // Match the server list's 100ms frames, sweeping forward and back.
        int frame = (int) (net.minecraft.util.Util.getMillis() / 100L & 7L);
        var statusIcon = running ? PINGING.get(frame > 4 ? 8 - frame : frame) : icon;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, statusIcon, x + 3, y + 6, 10, 8);
        graphics.text(font, Component.literal(font.plainSubstrByWidth(status, Math.max(0, width - button.getWidth() - 30))), x + 22, y + 6, statusColor);
        button.setX(x + width - button.getWidth());
        button.setY(y);
        button.active = !running && isEditable();
        button.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
