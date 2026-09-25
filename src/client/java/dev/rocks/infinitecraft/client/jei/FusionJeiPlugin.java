package dev.rocks.infinitecraft.client.jei;

import dev.rocks.infinitecraft.discovery.DiscoveryCollection;
import dev.rocks.infinitecraft.fusion.FusionCrafterBlock;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.List;

/** Shows the world's discovered fusions in JEI, kept in step with the server as new ones are found. */
public final class FusionJeiPlugin implements IModPlugin {
    static final IRecipeType<DiscoveryCollection.Entry> TYPE =
            IRecipeType.create("rocks_infinite_craft", "fusion", DiscoveryCollection.Entry.class);
    private static IJeiRuntime runtime;
    // What JEI currently lists, so updates only add new entries and hide replaced ones.
    private static List<DiscoveryCollection.Entry> shown = List.of();

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath("rocks_infinite_craft", "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new FusionCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(TYPE, FusionCrafterBlock.item());
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        shown = KnownRecipesClient.all();
        registration.addRecipes(TYPE, shown);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime available) {
        runtime = available;
        KnownRecipesClient.listen(FusionJeiPlugin::refresh);
        refresh();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    private static void refresh() {
        if (runtime == null) return;
        var known = KnownRecipesClient.all();
        var current = new HashSet<>(known);
        var listed = new HashSet<>(shown);
        var recipes = runtime.getRecipeManager();
        recipes.hideRecipes(TYPE, shown.stream().filter(entry -> !current.contains(entry)).toList());
        recipes.addRecipes(TYPE, known.stream().filter(entry -> !listed.contains(entry)).toList());
        shown = known;
    }
}
