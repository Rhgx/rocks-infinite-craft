package dev.rocks.infinitecraft.mixin;

import dev.rocks.infinitecraft.InfiniteCraftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrafterBlock.class)
abstract class FusionCrafterBlockMixin {
    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
    private void fusionInsteadOfCrafting(BlockState state, ServerLevel level, BlockPos pos, CallbackInfo ci) {
        // Marked stations never fall through to ordinary crafting, even with fusion disabled.
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.CrafterBlockEntity block
                && InfiniteCraftMod.isFusionCrafter(block)) {
            InfiniteCraftMod.triggerCrafter(block);
            ci.cancel();
        }
    }

    @Inject(method = "setPlacedBy", at = @At("TAIL"))
    private void rememberPlacer(Level level, BlockPos pos, BlockState state, LivingEntity placer,
            ItemStack stack, CallbackInfo ci) {
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.CrafterBlockEntity block)
            InfiniteCraftMod.crafterPlaced(block, placer);
    }
}
