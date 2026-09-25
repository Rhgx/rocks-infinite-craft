package dev.rocks.infinitecraft.core;

import java.util.List;

@FunctionalInterface
public interface RecipeGenerator {
    RecipeResult generate(GenerationRequest request) throws Exception;

    default List<RecipeResult> generateCandidates(GenerationRequest request, String feedback) throws Exception {
        return generateCandidates(request);
    }

    default List<RecipeResult> generateCandidates(GenerationRequest request) throws Exception {
        RecipeResult result = generate(request);
        return result == null ? List.of() : List.of(result);
    }
}
