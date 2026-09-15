package dev.rocks.infinitecraft.mixin;

import dev.rocks.infinitecraft.discovery.DiscoveryBook;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
abstract class BoundBookMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void protectBook(int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer)) return;
        var menu = (AbstractContainerMenu) (Object) this;
        if (DiscoveryBook.blocksMove(menu, player.getInventory(), slotId, button, input)) {
            // The packet handler records client predictions and reconciles them after clicked returns.
            // Sending a full snapshot here interrupts that normal synchronization sequence.
            ci.cancel();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void returnBookOnClose(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.isAlive())
            DiscoveryBook.returnCursorBook((AbstractContainerMenu) (Object) this, player.getInventory());
    }
}
