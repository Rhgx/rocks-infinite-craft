package dev.rocks.infinitecraft.client.mixin;

import java.util.Collection;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenAccess {
    @Accessor("selectedTab")
    static CreativeModeTab infinitecraft$selectedTab() {
        throw new AssertionError();
    }

    @Invoker("refreshCurrentTabContents")
    void infinitecraft$refreshCurrentTabContents(Collection<ItemStack> items);
}
