package dev.rocks.infinitecraft.mixin;

import dev.rocks.infinitecraft.InfiniteCraftMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntity.class)
abstract class FusionCrafterDataMixin {
    @Inject(method = "setChanged()V", at = @At("TAIL"))
    private void wakeFusion(CallbackInfo ci) {
        InfiniteCraftMod.crafterChanged((BlockEntity) (Object) this);
    }

    @Inject(method = {"applyComponents", "loadWithComponents"}, at = @At("TAIL"))
    private void updateFusionAppearance(CallbackInfo ci) {
        var block = (BlockEntity) (Object) this;
        if (!(block instanceof net.minecraft.world.level.block.entity.CrafterBlockEntity)) return;
        InfiniteCraftMod.crafterChanged(block);
        var level = block.getLevel();
        if (level != null) level.sendBlockUpdated(block.getBlockPos(), block.getBlockState(), block.getBlockState(), 3);
    }

    @Inject(method = "getUpdateTag", at = @At("HEAD"), cancellable = true)
    private void syncFusionAppearance(HolderLookup.Provider lookup, CallbackInfoReturnable<CompoundTag> cir) {
        var block = (BlockEntity) (Object) this;
        if (!InfiniteCraftMod.isFusionCrafter(block)) return;
        // Chunk loads and placement updates need only the marker, never the station's inventory.
        var components = DataComponentMap.builder()
                .set(DataComponents.CUSTOM_DATA, block.components().get(DataComponents.CUSTOM_DATA)).build();
        var tag = new CompoundTag();
        tag.put("components", DataComponentMap.CODEC.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE),
                components).getOrThrow());
        cir.setReturnValue(tag);
    }

    @Inject(method = "getUpdatePacket", at = @At("HEAD"), cancellable = true)
    private void fusionUpdatePacket(CallbackInfoReturnable<Packet<ClientGamePacketListener>> cir) {
        var block = (BlockEntity) (Object) this;
        if (InfiniteCraftMod.isFusionCrafter(block)) cir.setReturnValue(ClientboundBlockEntityDataPacket.create(block));
    }
}
