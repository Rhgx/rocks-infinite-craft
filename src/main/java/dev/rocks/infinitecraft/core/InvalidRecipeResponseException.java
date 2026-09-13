package dev.rocks.infinitecraft.core;

import java.io.IOException;

/** Model output failed recipe validation; transport and authentication errors use IOException instead. */
public final class InvalidRecipeResponseException extends IOException {
    public InvalidRecipeResponseException() { super("Provider returned an invalid recipe response"); }
}
