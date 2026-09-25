package dev.rocks.infinitecraft.item;

import com.google.gson.JsonParser;
import dev.rocks.infinitecraft.core.RecipeResult;
import dev.rocks.infinitecraft.fusion.FusionRuntime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComponentPairKeyTest {
    @Test void stableForIngredientAndObjectOrderButSeparatesVariants() {
        var a = JsonParser.parseString("{\"id\":\"minecraft:potion\",\"components\":{\"potion\":\"healing\"}}");
        var aReordered = JsonParser.parseString("{\"components\":{\"potion\":\"healing\"},\"id\":\"minecraft:potion\"}");
        var b = JsonParser.parseString("{\"id\":\"minecraft:stick\"}");
        String key = ComponentPairKey.of("minecraft:potion", "minecraft:stick", a, b);
        assertEquals(key, ComponentPairKey.of("minecraft:stick", "minecraft:potion", b, aReordered));
        assertNotEquals(key, ComponentPairKey.of("minecraft:potion", "minecraft:stick", JsonParser.parseString("{\"potion\":\"poison\"}"), b));
        assertFalse(key.contains("healing"));
        boolean sawFirst = false, sawSecond = false;
        for (long seed = 0; seed < 100; seed++) {
            boolean firstWins = ComponentPairKey.firstWins(a, b, seed);
            assertEquals(firstWins, ComponentPairKey.firstWins(aReordered, b, seed));
            assertNotEquals(firstWins, ComponentPairKey.firstWins(b, a, seed));
            sawFirst |= firstWins;
            sawSecond |= !firstWins;
        }
        assertTrue(sawFirst && sawSecond);
        var base = new RecipeResult("minecraft:stone", 1);
        var variant = new RecipeResult("minecraft:diamond", 1);
        assertSame(variant, FusionRuntime.cachedVariantOrBase(variant, base, ignored -> false));
        assertSame(base, FusionRuntime.cachedVariantOrBase(null, base, ignored -> true));
        assertNull(FusionRuntime.cachedVariantOrBase(null, base, ignored -> false));
    }
}
