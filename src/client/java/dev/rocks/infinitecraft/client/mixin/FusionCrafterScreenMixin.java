package dev.rocks.infinitecraft.client.mixin;

import dev.rocks.infinitecraft.client.CrafterStateClient;
import dev.rocks.infinitecraft.client.IconButton;
import dev.rocks.infinitecraft.fusion.CrafterStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CrafterSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A two-input fusion panel with mode controls, layered over the same vanilla menu and slot protocol. */
@Mixin(CrafterScreen.class)
abstract class FusionCrafterScreenMixin extends AbstractContainerScreen<CrafterMenu> {
    @Unique private static final Identifier FURNACE = Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
    @Unique private static final Identifier ARROW = Identifier.withDefaultNamespace("container/furnace/burn_progress");
    @Unique private static final Identifier START = icon("start");
    @Unique private static final Identifier STOP = icon("stop");
    @Unique private static final Identifier ONCE = icon("once");
    @Unique private static final Identifier REPEAT = icon("repeat");
    @Unique private static final int ARROW_X = 92;
    @Unique private static final int ARROW_Y = 35;
    @Unique private IconButton fusionModeButton;
    @Unique private IconButton fusionRunButton;
    @Unique private Component shownStatus;
    @Unique private String shownKey;

    protected FusionCrafterScreenMixin(CrafterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Unique
    private static Identifier icon(String name) {
        return Identifier.fromNamespaceAndPath("rocks_infinite_craft", "crafter/" + name);
    }

    @Unique
    private boolean fusionScreen() {
        for (int slot = 0; slot < 9; slot++)
            if (menu.isSlotDisabled(slot) == (slot == 3 || slot == 5)) return false;
        return true;
    }

    @Unique
    private void command(String action) {
        if (minecraft.player != null) minecraft.player.connection.sendCommand("fusion crafter " + action);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addFusionControls(CallbackInfo ci) {
        shownKey = null;
        // Two 14px buttons in the top-right corner, clear of the centered title: mode, then start/stop.
        int right = leftPos + imageWidth - 8;
        fusionModeButton = addRenderableWidget(new IconButton(right - 30, topPos + 5, 14, REPEAT, button -> {
            var state = CrafterStateClient.state(menu.containerId);
            if (state != null) command(state.repeat() ? "mode once" : "mode repeat");
        }));
        fusionRunButton = addRenderableWidget(new IconButton(right - 14, topPos + 5, 14, STOP, button -> {
            var state = CrafterStateClient.state(menu.containerId);
            if (state != null) command(state.paused() ? "start" : "stop");
        }));
        updateFusionControls();
    }

    @Unique
    private MutableComponent status(CrafterStatePayload state) {
        if (state == null) return Component.literal("Connecting...").withStyle(ChatFormatting.GRAY);
        if (state.working()) return Component.literal("Fusing" + ".".repeat(1 + (int) (Util.getMillis() / 400 % 3)))
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        if (state.paused()) return Component.literal("Stopped").withStyle(ChatFormatting.RED);
        if (!menu.getSlot(3).hasItem() || !menu.getSlot(5).hasItem())
            return Component.literal("Waiting for two items").withStyle(ChatFormatting.GRAY);
        return Component.literal(state.repeat() ? "Ready, repeating" : "Ready").withStyle(ChatFormatting.GREEN);
    }

    @Unique
    private static MutableComponent line(String text) {
        return Component.literal("\n" + text).withStyle(ChatFormatting.GRAY);
    }

    @Unique
    private void updateFusionControls() {
        var state = CrafterStateClient.state(menu.containerId);
        fusionModeButton.visible = fusionRunButton.visible = fusionScreen();
        fusionModeButton.active = fusionRunButton.active = state != null;
        shownStatus = status(state);
        String key = state + "|" + shownStatus.getString();
        if (key.equals(shownKey)) return;
        shownKey = key;
        boolean repeat = state == null || state.repeat();
        boolean paused = state != null && state.paused();
        fusionModeButton.setIcon(repeat ? REPEAT : ONCE);
        fusionModeButton.setMessage(Component.literal(repeat ? "Mode: Repeat" : "Mode: Once"));
        fusionModeButton.setTooltip(Tooltip.create(Component.literal(repeat ? "Mode: Repeat" : "Mode: Once")
                .append(line(repeat ? "Keeps fusing while both inputs have items." : "Fuses one pair, then stops."))
                .append(line(repeat ? "Click to switch to Once." : "Click to switch to Repeat."))));
        fusionRunButton.setIcon(paused ? START : STOP);
        fusionRunButton.setMessage(Component.literal(paused ? "Start" : "Stop"));
        fusionRunButton.setTooltip(Tooltip.create(Component.literal(paused ? "Start" : "Stop")
                .append(line(paused ? "Fuse the two input items. A redstone pulse also starts it."
                        : state != null && state.working() ? "Stops after the current fusion finishes."
                        : "Stop fusing until started again. A redstone pulse also stops it."))));
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void fixedSlots(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (!fusionScreen() || !(slot instanceof CrafterSlot)) return;
        if (slot.index == 3 || slot.index == 5) super.slotClicked(slot, slotId, button, input);
        ci.cancel();
    }

    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void fixedSlotRendering(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!fusionScreen() || !(slot instanceof CrafterSlot)) return;
        if (slot.index == 3 || slot.index == 5) super.extractSlot(graphics, slot, mouseX, mouseY);
        ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void removeCrafterControls(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (fusionModeButton != null) updateFusionControls();
        if (!fusionScreen()) return;
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int x = leftPos + ARROW_X, y = topPos + ARROW_Y;
        if (mouseX >= x && mouseX < x + 24 && mouseY >= y && mouseY < y + 16)
            graphics.setTooltipForNextFrame(font, font.split(Component.empty().append(shownStatus)
                    .append(line("Inputs on the left fuse into the result on the right.")), 200), mouseX, mouseY);
        ci.cancel();
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void fusionPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!fusionScreen()) return;
        int gridX = leftPos + 25;
        int gridY = topPos + 16;
        graphics.fill(gridX, gridY, gridX + 54, gridY + 54, 0xFFC6C6C6);
        for (int slot : new int[]{3, 5}) {
            int x = gridX + slot % 3 * 18;
            int y = gridY + slot / 3 * 18;
            // Vanilla slot bevel: dark top-left, white bottom-right.
            graphics.fill(x, y, x + 18, y + 18, 0xFF373737);
            graphics.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        }
        // Plus sign centered between the two inputs.
        int plusX = gridX + 27, plusY = gridY + 27;
        graphics.fill(plusX - 4, plusY - 1, plusX + 4, plusY + 1, 0xFF8B8B8B);
        graphics.fill(plusX - 1, plusY - 4, plusX + 1, plusY + 4, 0xFF8B8B8B);

        // The furnace's empty arrow, filled with its progress sprite while generating.
        int arrowX = leftPos + ARROW_X, arrowY = topPos + ARROW_Y;
        graphics.blit(RenderPipelines.GUI_TEXTURED, FURNACE, arrowX, arrowY, 79, 34, 24, 16, 256, 256);
        var state = CrafterStateClient.state(menu.containerId);
        if (state != null && state.working()) {
            // Generation time is unknown, so the arrow loops rather than showing real progress.
            int filled = (int) (Util.getMillis() / 40 % 25);
            if (filled > 0) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, 24, 16, 0, 0, arrowX, arrowY, filled, 16);
        }
    }
}
