package dev.rocks.infinitecraft.core;

import java.util.Objects;

public final class PairKey {
    private PairKey() { }
    public static String of(String first, String second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!first.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || !second.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Recipe inputs must be namespaced item IDs");
        }
        return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
    }
}
