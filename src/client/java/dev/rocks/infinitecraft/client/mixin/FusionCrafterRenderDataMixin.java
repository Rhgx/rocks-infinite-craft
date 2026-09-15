package dev.rocks.infinitecraft.client.mixin;

import dev.rocks.infinitecraft.InfiniteCraftMod;
import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CrafterBlockEntity.class)
abstract class FusionCrafterRenderDataMixin implements RenderDataBlockEntity {
    @Override
    public Object getRenderData() {
        // Immutable snapshot for the terrain renderer's worker threads.
        return InfiniteCraftMod.isFusionCrafter((BlockEntity) (Object) this);
    }
}
