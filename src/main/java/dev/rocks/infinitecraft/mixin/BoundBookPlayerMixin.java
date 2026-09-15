package dev.rocks.infinitecraft.mixin;

import dev.rocks.infinitecraft.discovery.DiscoveryBook;
import dev.rocks.infinitecraft.fusion.FusionDrops;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class BoundBookPlayerMixin {
    @Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
    private void preventSelectedDrop(boolean entireStack, CallbackInfo ci) {
        var player = (ServerPlayer) (Object) this;
        if (dev.rocks.infinitecraft.InfiniteCraftMod.soulboundBook() && DiscoveryBook.isBook(player.getInventory().getSelectedItem())) {
            ci.cancel();
            player.inventoryMenu.sendAllDataToRemote();
        }
    }

    // Covers creative drop packets and other vanilla paths that bypass the selected-slot drop.
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void preventBookEntity(ItemStack stack, boolean random, boolean trace,
                                  CallbackInfoReturnable<ItemEntity> cir) {
        if (dev.rocks.infinitecraft.InfiniteCraftMod.soulboundBook() && DiscoveryBook.isBook(stack)) cir.setReturnValue(null);
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void vanishBook(DamageSource source, CallbackInfo ci) {
        if (dev.rocks.infinitecraft.InfiniteCraftMod.soulboundBook()) DiscoveryBook.remove((ServerPlayer) (Object) this);
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("RETURN"))
    private void rememberSingleDrop(ItemStack stack, boolean random, boolean trace, CallbackInfoReturnable<ItemEntity> callback) {
        FusionDrops.mark(callback.getReturnValue(), random, trace);
    }
}
