package dev.rocks.infinitecraft.engine;

public final class BlockedRecipeException extends IllegalStateException {
    public BlockedRecipeException() {
        super("Combination is blocked after invalid recipe generation. An operator can set or forget its recipe.");
    }
}
