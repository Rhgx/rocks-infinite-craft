package dev.rocks.infinitecraft.client.jei;

import dev.rocks.infinitecraft.discovery.DiscoveryBook;
import dev.rocks.infinitecraft.discovery.DiscoveryCollection;
import dev.rocks.infinitecraft.fusion.FusionCrafterBlock;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;

/** One fusion: two dropped items and what they made, with who found it underneath. */
final class FusionCategory extends AbstractRecipeCategory<DiscoveryCollection.Entry> {
    FusionCategory(IGuiHelper gui) {
        super(FusionJeiPlugin.TYPE, Component.literal("Item Fusion"),
                gui.createDrawableItemStack(FusionCrafterBlock.item()), 110, 32);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, DiscoveryCollection.Entry recipe, IFocusGroup focuses) {
        builder.addInputSlot(1, 1).setStandardSlotBackground().add(DiscoveryBook.displayStack(recipe.first()));
        builder.addInputSlot(38, 1).setStandardSlotBackground().add(DiscoveryBook.displayStack(recipe.second()));
        builder.addOutputSlot(92, 1).setStandardSlotBackground().add(DiscoveryBook.displayStack(recipe.result()));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, DiscoveryCollection.Entry recipe, IFocusGroup focuses) {
        builder.addRecipePlusSignWidget().setPosition(21, 3);
        builder.addRecipeArrowWidget().setPosition(61, 1);
        int others = recipe.discoverers().size() - 1;
        builder.addText(Component.literal("By " + recipe.discoverer() + (others > 0 ? " +" + others : "")), 110, 10)
                .setPosition(0, 22)
                .setColor(0xFF404040);
    }
}
