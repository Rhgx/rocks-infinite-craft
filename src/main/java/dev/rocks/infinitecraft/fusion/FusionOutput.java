package dev.rocks.infinitecraft.fusion;

import dev.rocks.infinitecraft.ModConfig;
import dev.rocks.infinitecraft.core.RecipeResult;
import dev.rocks.infinitecraft.item.FusionOrigin;
import dev.rocks.infinitecraft.item.ItemDataFusion;
import dev.rocks.infinitecraft.item.ItemTraits;
import dev.rocks.infinitecraft.item.PotionFusion;
import dev.rocks.infinitecraft.item.VanillaTraits;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.Set;
import java.util.function.Predicate;

/** Builds and strictly validates the concrete item produced by a resolved recipe. */
final class FusionOutput {
    private FusionOutput() {
    }

    static ItemStack create(RecipeResult result, ItemStack first, ItemStack second,
            ItemStack dataFirst, ItemStack dataSecond, MinecraftServer server, ModConfig config,
            Set<String> allowed, Predicate<ItemStack> special) {
        if (result.traits().size() > config.maxTraits) return ItemStack.EMPTY;

        Identifier id = Identifier.tryParse(result.itemId());
        if (id == null || !allowed.contains(result.itemId()) || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        ItemStack output = BuiltInRegistries.ITEM.getValue(id).getDefaultInstance();
        if (output.isEmpty() || result.count() < 1 || result.count() > 64) return ItemStack.EMPTY;
        output.setCount(1);

        var brewed = config.allowItemData
                ? ItemDataFusion.brew(server.potionBrewing(), dataFirst, dataSecond)
                : ItemStack.EMPTY;
        boolean applyBrewing = !brewed.isEmpty() && brewed.is(output.getItem());
        output = ItemDataFusion.prepare(applyBrewing ? brewed : output, dataFirst, dataSecond, applyBrewing);
        if (output.isEmpty() || !PotionFusion.applyChoice(output, result.potion())) return ItemStack.EMPTY;

        output = VanillaTraits.apply(output, result.traits(), result.name(), result.strengths(),
                result.activations(), result.nameStyle(), result.nameParts());
        if (output.isEmpty() || !applyDye(output, result.dyeColor()) || !applyModel(output, result.itemModel())) {
            return ItemStack.EMPTY;
        }
        if (!FusionCount.apply(output, first, second, special, config.specialCombinationLimit)) {
            return ItemStack.EMPTY;
        }
        if (!ItemTraits.apply(output, dataFirst, dataSecond, result.traits(), config.maxTraits)) return ItemStack.EMPTY;
        if (!FusionOrigin.apply(output, first, second)) return ItemStack.EMPTY;
        if (output.getCount() > output.getMaxStackSize()) return ItemStack.EMPTY;
        return ItemStack.validateStrict(output).result().orElse(ItemStack.EMPTY);
    }

    private static boolean applyDye(ItemStack output, String color) {
        if (color.isEmpty()) return true;
        if (!output.is(ItemTags.CAULDRON_CAN_REMOVE_DYE)) return false;
        output.set(DataComponents.DYED_COLOR, new DyedItemColor(Integer.parseInt(color.substring(1), 16)));
        return true;
    }

    private static boolean applyModel(ItemStack output, String itemModel) {
        if (itemModel.isEmpty()) return true;
        Identifier id = Identifier.parse(itemModel);
        if (!id.getNamespace().equals("minecraft") || !BuiltInRegistries.ITEM.containsKey(id)) return false;

        var model = BuiltInRegistries.ITEM.getValue(id).getDefaultInstance().get(DataComponents.ITEM_MODEL);
        if (model == null || !model.getNamespace().equals("minecraft")) return false;
        output.set(DataComponents.ITEM_MODEL, model);
        return true;
    }
}
