package dev.rocks.infinitecraft.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ItemDisplayWidget;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Native dialogs reserve a larger box but always draw items at 16 pixels. */
@Mixin(ItemDisplayWidget.class)
abstract class DiscoveryItemDisplayMixin {
    @Redirect(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void drawDiscoveryItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int seed) {
        var screen = Minecraft.getInstance().gui.screen();
        if (!(screen instanceof DialogScreen<?>) || !screen.getTitle().getString().equals("Discovery Book")) {
            graphics.item(stack, x, y, seed);
            return;
        }
        // Scale only the item draw, leaving focus outlines and tooltip coordinates untouched.
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y).scale(2);
            graphics.item(stack, 0, 0, seed);
        } finally {
            graphics.pose().popMatrix();
        }
    }
}
