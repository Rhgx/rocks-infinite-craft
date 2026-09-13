package dev.rocks.infinitecraft.traits;

/** Native bounds and the amount used when the model omits a strength. */
public record StrengthRange(double minimum, double maximum, double defaultValue) {
    public StrengthRange {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || !Double.isFinite(defaultValue)
                || minimum > maximum || defaultValue < minimum || defaultValue > maximum) {
            throw new IllegalArgumentException("Strength range must have finite bounds and a default within them");
        }
    }
}
