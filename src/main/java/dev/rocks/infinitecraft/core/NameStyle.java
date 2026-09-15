package dev.rocks.infinitecraft.core;

/** Only visual formatting is accepted, never arbitrary chat components or click events. */
public record NameStyle(String color, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated) {
    public NameStyle(String color, boolean bold, boolean italic) {
        this(color, bold, italic, false, false, false);
    }
    public static final NameStyle PLAIN = new NameStyle("", false, false);

    public NameStyle {
        color = color == null ? "" : color;
        if (!color.isEmpty() && !ValidationPatterns.isOptionalHexColor(color))
            throw new IllegalArgumentException("Name color must be #RRGGBB");
    }
}
