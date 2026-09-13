package dev.rocks.infinitecraft.core;

@FunctionalInterface
public interface RecipeGenerator {
    RecipeResult generate(GenerationRequest request) throws Exception;

    default java.util.List<RecipeResult> generateCandidates(GenerationRequest request) throws Exception {
        RecipeResult result = generate(request);
        return result == null ? java.util.List.of() : java.util.List.of(result);
    }
}
