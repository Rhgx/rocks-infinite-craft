package dev.rocks.infinitecraft.client.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Collection;
import java.util.Set;

/** Sets tab contents directly; ModernFix memoizes buildContents and skips same-parameter rebuilds. */
@Mixin(CreativeModeTab.class)
public interface CreativeModeTabAccess {
    @Accessor("displayItems")
    void infinitecraft$setDisplayItems(Collection<ItemStack> items);

    @Accessor("displayItemsSearchTab")
    void infinitecraft$setDisplayItemsSearchTab(Set<ItemStack> items);
}
