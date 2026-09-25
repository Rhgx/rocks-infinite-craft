package dev.rocks.infinitecraft.mixin;

import dev.rocks.infinitecraft.InfiniteCraftMod;
import dev.rocks.infinitecraft.fusion.CrafterStatePayload;
import dev.rocks.infinitecraft.fusion.FusionCrafterBlock;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrafterMenu.class)
abstract class FusionCrafterMenuMixin extends AbstractContainerMenu {
    @Shadow @Final private CraftingContainer container;
    @Shadow @Final private ContainerData containerData;
    @Shadow @Final private ResultContainer resultContainer;
    @Shadow @Final private Player player;
    @Unique private CrafterStatePayload sentState;

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

    @Unique
    private void sendFusionState() {
        // Only clients with the mod register this payload; vanilla clients use the /fusion crafter dialog.
        if (!(player instanceof ServerPlayer viewer) || !ServerPlayNetworking.canSend(viewer, CrafterStatePayload.TYPE)) return;
        var block = (CrafterBlockEntity) container;
        var state = new CrafterStatePayload(containerId, FusionCrafterBlock.repeats(block),
                FusionCrafterBlock.paused(block), InfiniteCraftMod.crafterWorking(block));
        if (state.equals(sentState)) return;
        sentState = state;
        ServerPlayNetworking.send(viewer, state);
    }

    @Override
    public void broadcastChanges() {
        if (fusionMenu()) {
            updateFusionPreview();
            sendFusionState();
        }
        super.broadcastChanges();
    }
}
