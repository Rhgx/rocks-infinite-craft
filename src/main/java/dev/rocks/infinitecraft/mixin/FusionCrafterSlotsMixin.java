package dev.rocks.infinitecraft.mixin;

import dev.rocks.infinitecraft.InfiniteCraftMod;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrafterBlockEntity.class)
abstract class FusionCrafterSlotsMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    protected net.minecraft.world.inventory.ContainerData containerData;

    @Inject(method = "setSlotState", at = @At("HEAD"), cancellable = true)
    private void keepFixedLayout(int slot, boolean enabled, CallbackInfo ci) {
        var block = (CrafterBlockEntity) (Object) this;
        if (!InfiniteCraftMod.isFusionCrafter(block)) return;
        if (block.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            for (var player : level.getServer().getPlayerList().getPlayers()) {
                if (!(player.containerMenu instanceof net.minecraft.world.inventory.CrafterMenu menu)
                        || menu.getContainer() != block) continue;
                var access = (ContainerMenuAccess) menu;
                var synchronizer = access.fusionSynchronizer();
                if (synchronizer == null) continue;
                for (int input = 0; input < 9; input++) {
                    int value = InfiniteCraftMod.fusionInputSlot(input) ? 0 : 1;
                    access.fusionRemoteDataSlots().set(input, value);
                    synchronizer.sendDataChange(menu, input, value);
                }
            }
        }
        ci.cancel();
    }

    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void rejectLockedSlot(int slot, net.minecraft.world.item.ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!InfiniteCraftMod.isFusionCrafter((BlockEntity) (Object) this)) return;
        boolean input = InfiniteCraftMod.fusionInputSlot(slot);
        containerData.set(slot, input ? 0 : 1);
        if (!input) cir.setReturnValue(false);
    }

    @Inject(method = "isSlotDisabled", at = @At("HEAD"), cancellable = true)
    private void fixedFusionInputs(int slot, CallbackInfoReturnable<Boolean> cir) {
        if (InfiniteCraftMod.isFusionCrafter((BlockEntity) (Object) this))
            cir.setReturnValue(!InfiniteCraftMod.fusionInputSlot(slot));
    }
}
