package dev.rocks.infinitecraft.core;

/** One literal segment with its own visual style. */
public record NamePart(String text, NameStyle style) {
    public NamePart {
        if (text == null || text.isEmpty() || text.length() > 64
                || text.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT))
            throw new IllegalArgumentException("Invalid name segment");
        style = style == null ? NameStyle.PLAIN : style;
    }
}
