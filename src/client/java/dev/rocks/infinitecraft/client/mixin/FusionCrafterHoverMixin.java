package dev.rocks.infinitecraft.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.world.inventory.CrafterSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hidden Fusion Crafter slots must not participate in generic container highlighting. */
@Mixin(AbstractContainerScreen.class)
abstract class FusionCrafterHoverMixin {
    @Shadow protected Slot hoveredSlot;

    private boolean hiddenFusionSlot() {
        if (!((Object) this instanceof CrafterScreen screen) || !(hoveredSlot instanceof CrafterSlot)) return false;
        for (int slot = 0; slot < 9; slot++)
            if (screen.getMenu().isSlotDisabled(slot) == (slot == 3 || slot == 5)) return false;
        return hoveredSlot.index != 3 && hoveredSlot.index != 5;
    }

    @Inject(method = "extractSlotHighlightBack", at = @At("HEAD"), cancellable = true)
    private void hideBackHighlight(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (hiddenFusionSlot()) ci.cancel();
    }

    @Inject(method = "extractSlotHighlightFront", at = @At("HEAD"), cancellable = true)
    private void hideFrontHighlight(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (hiddenFusionSlot()) ci.cancel();
    }
}
