package dev.rocks.infinitecraft.mixin;

import dev.rocks.infinitecraft.item.ItemTraits;
import dev.rocks.infinitecraft.traits.NibbleableTrait;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Consumable.class)
abstract class NibbleableFoodMixin {
    @Redirect(method = "onConsume", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
    private void spendBite(ItemStack stack, int amount, LivingEntity eater) {
        if (!stack.isDamageableItem() || !ItemTraits.inherited(stack).contains("nibbleable")) {
            stack.consume(amount, eater);
            return;
        }
        if (eater.hasInfiniteMaterials()) return;
        NibbleableTrait.spendBite(stack);
    }
}
