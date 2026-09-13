package dev.rocks.infinitecraft.mixin;

import dev.rocks.infinitecraft.DiscoveryBook;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class BoundBookCreativeMixin {
    @Shadow public ServerPlayer player;

    @Redirect(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;setByPlayer(Lnet/minecraft/world/item/ItemStack;)V"))
    private void trackCreativeMove(Slot slot, ItemStack stack) {
        var previous = slot.getItem().copy();
        slot.setByPlayer(stack);
        DiscoveryBook.creativeSlotChanged(player, previous, stack);
    }

    @Inject(method = "handleContainerClose", at = @At("TAIL"))
    private void finishCreativeMove(ServerboundContainerClosePacket packet, CallbackInfo ci) {
        if (DiscoveryBook.forgetCreativeCursor(player.getUUID())) DiscoveryBook.sync(player);
    }
}
