package dev.rocks.infinitecraft.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CrafterSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A clearer two-input layout layered over the same vanilla menu and slot protocol. */
@Mixin(CrafterScreen.class)
abstract class FusionCrafterScreenMixin extends AbstractContainerScreen<CrafterMenu> {
    protected FusionCrafterScreenMixin(CrafterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private boolean fusionScreen() {
        for (int slot = 0; slot < 9; slot++)
            if (menu.isSlotDisabled(slot) == (slot == 3 || slot == 5)) return false;
        return true;
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
        if (!fusionScreen()) return;
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        ci.cancel();
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void simplifyGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!fusionScreen()) return;
        int gridX = leftPos + 25;
        int gridY = topPos + 16;
        graphics.fill(gridX, gridY, gridX + 54, gridY + 54, 0xFFC6C6C6);
        for (int slot : new int[]{3, 5}) {
            int x = gridX + slot % 3 * 18;
            int y = gridY + slot / 3 * 18;
            graphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
            graphics.outline(x, y, 18, 18, 0xFF373737);
        }
        graphics.centeredText(font, "+", gridX + 27, gridY + 23, 0xFF555555);
        graphics.centeredText(font, "→", leftPos + 105, gridY + 23, 0xFF555555);
    }
}
