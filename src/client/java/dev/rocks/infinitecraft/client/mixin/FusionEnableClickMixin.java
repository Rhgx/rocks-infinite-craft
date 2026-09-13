package dev.rocks.infinitecraft.client.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
abstract class FusionEnableClickMixin {
    @Inject(method = "clickCommandAction", at = @At("HEAD"), cancellable = true)
    private static void enableFusion(LocalPlayer player, String command, Screen screen, CallbackInfo callback) {
        // Only this fixed action skips confirmation. Server-side host permissions still apply.
        if (!command.equals("/fusion enable")) return;
        player.connection.sendCommand("fusion enable");
        callback.cancel();
    }
}
