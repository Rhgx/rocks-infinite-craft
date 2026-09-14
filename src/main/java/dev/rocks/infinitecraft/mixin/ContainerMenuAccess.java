package dev.rocks.infinitecraft.mixin;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerMenu.class)
public interface ContainerMenuAccess {
    @Accessor("synchronizer") ContainerSynchronizer fusionSynchronizer();
    @Accessor("remoteDataSlots") IntList fusionRemoteDataSlots();
}
