package dev.rocks.infinitecraft.mixin;

import dev.rocks.infinitecraft.InfiniteCraftMod;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrafterMenu.class)
abstract class FusionCrafterMenuMixin extends AbstractContainerMenu {
    @Shadow @Final private CraftingContainer container;
    @Shadow @Final private ContainerData containerData;
    @Shadow @Final private ResultContainer resultContainer;

    protected FusionCrafterMenuMixin(MenuType<?> type, int id) { super(type, id); }

    private boolean fusionMenu() {
        return container instanceof CrafterBlockEntity block && InfiniteCraftMod.isFusionCrafter(block);
    }

    private void updateFusionPreview() {
        for (int slot = 0; slot < 9; slot++)
            containerData.set(slot, InfiniteCraftMod.fusionInputSlot(slot) ? 0 : 1);
        var preview = InfiniteCraftMod.crafterPreview((CrafterBlockEntity) container);
        if (!ItemStack.matches(resultContainer.getItem(0), preview)) resultContainer.setItem(0, preview);
    }

    @Inject(method = "refreshRecipeResult", at = @At("HEAD"), cancellable = true)
    private void fusionResult(CallbackInfo ci) {
        if (!fusionMenu()) return;
        updateFusionPreview();
        ci.cancel();
    }

    @Override
    public void broadcastChanges() {
        if (fusionMenu()) updateFusionPreview();
        super.broadcastChanges();
    }
}
