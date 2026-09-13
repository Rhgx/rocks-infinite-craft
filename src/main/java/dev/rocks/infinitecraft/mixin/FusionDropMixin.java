package dev.rocks.infinitecraft.mixin;

import dev.rocks.infinitecraft.FusionDrops;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
abstract class FusionDropMixin {
    @Inject(method = "isMergable", at = @At("HEAD"), cancellable = true)
    private void keepFusionIngredientsSeparate(CallbackInfoReturnable<Boolean> callback) {
        if (FusionDrops.intentional((ItemEntity) (Object) this)) callback.setReturnValue(false);
    }
}
