package dev.rocks.infinitecraft.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecipeQualityTest {
    @Test void rarityRaisesFloorSymmetricallyWithinPowerWithoutAddingTraits() {
        assertEquals(0, RecipeQuality.fromRarities(0, 0));
        assertEquals(25, RecipeQuality.fromRarities(1, 0));
        assertEquals(50, RecipeQuality.fromRarities(2, 0));
        assertEquals(75, RecipeQuality.fromRarities(3, 0));
        assertEquals(90, RecipeQuality.fromRarities(3, 3));
        assertEquals(RecipeQuality.fromRarities(1, 3), RecipeQuality.fromRarities(3, 1));
        var weak = new RecipeResult("minecraft:stone", 1, "", List.of("bouncy"), Map.of("bouncy", 0.0));
        var strong = new RecipeResult("minecraft:stone", 1, "", List.of("bouncy"), Map.of("bouncy", 1.0));
        for (int power : new int[]{0, 25, 50, 75, 100}) {
            var request = new GenerationRequest("minecraft:a", "minecraft:b", List.of(), List.of("bouncy"),
                    List.of(), 8, power, 50, Map.of(), 3, List.of(), 75);
            var boosted = RecipeQuality.apply(weak, request);
            assertEquals(.75 * power / 100.0, boosted.strengths().get("bouncy"), 1e-10);
            assertEquals(power / 100.0, RecipeQuality.apply(strong, request).strengths().get("bouncy"), 1e-10);
            assertEquals(weak.traits(), boosted.traits());
            assertTrue(RecipeQuality.apply(new RecipeResult("minecraft:stone", 1), request).traits().isEmpty());
        }
        assertSame(weak, RecipeQuality.apply(weak, new GenerationRequest("a", "b", List.of())));
    }
}
