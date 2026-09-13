package dev.rocks.infinitecraft.mixin;

import dev.rocks.infinitecraft.DiscoveryBook;
import dev.rocks.infinitecraft.InfiniteCraftMod;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the recipe vanilla-compatible while the server controls whether it can be crafted. */
@Mixin(ShapelessRecipe.class)
abstract class DiscoveryBookRecipeMixin {
    @Shadow @Final private ItemStackTemplate result;

    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
            at = @At("HEAD"), cancellable = true)
    private void checkBookMode(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> callback) {
        if (level instanceof net.minecraft.server.level.ServerLevel && InfiniteCraftMod.soulboundBook()
                && result.item().value() == Items.KNOWLEDGE_BOOK && DiscoveryBook.isBook(result.create()))
            callback.setReturnValue(false);
    }
}
