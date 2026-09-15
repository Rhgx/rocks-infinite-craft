package dev.rocks.infinitecraft.core;

import java.util.regex.Pattern;

/** Shared validation for identifiers exchanged with providers or stored on disk. */
public final class ValidationPatterns {
    // A lowercase namespaced resource ID such as "minecraft:iron_ingot".
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    // A vanilla item model ID. Custom namespaces are deliberately rejected here.
    private static final Pattern VANILLA_ITEM_MODEL = Pattern.compile("minecraft:[a-z0-9/._-]+");
    // An optional six-digit RGB color written as #RRGGBB.
    private static final Pattern OPTIONAL_HEX_COLOR = Pattern.compile("(#[0-9a-fA-F]{6})?");
    // A short lowercase trait ID, optionally namespaced with a colon.
    private static final Pattern TRAIT_ID = Pattern.compile("[a-z0-9_.:-]{1,64}");
    // A provider model name containing only CLI- and URL-safe identifier characters.
    private static final Pattern MODEL_ID = Pattern.compile("[A-Za-z0-9_./:@-]{1,200}");
    // A conventional environment-variable name that starts with a letter or underscore.
    private static final Pattern ENVIRONMENT_VARIABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private ValidationPatterns() {
    }

    public static boolean isResourceId(String value) {
        return value != null && RESOURCE_ID.matcher(value).matches();
    }

    public static boolean isVanillaItemModel(String value) {
        return VANILLA_ITEM_MODEL.matcher(value).matches();
    }

    public static boolean isOptionalHexColor(String value) {
        return OPTIONAL_HEX_COLOR.matcher(value).matches();
    }

    public static boolean isTraitId(String value) {
        return TRAIT_ID.matcher(value).matches();
    }

    public static boolean isModelId(String value) {
        return MODEL_ID.matcher(value).matches();
    }

    public static boolean isEnvironmentVariable(String value) {
        return ENVIRONMENT_VARIABLE.matcher(value).matches();
    }
}
