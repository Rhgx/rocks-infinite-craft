package dev.rocks.infinitecraft.provider;

import dev.rocks.infinitecraft.core.RecipeGenerator;

public final class RecipeGenerators {
    private RecipeGenerators() {}

    public static RecipeGenerator create(ProviderConfig config) {
        return config.provider().equals("codex") ? new CodexRecipeGenerator(config) : new HttpRecipeGenerator(config);
    }
}
