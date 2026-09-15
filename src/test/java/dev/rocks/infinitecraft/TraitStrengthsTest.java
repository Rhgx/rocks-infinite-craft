package dev.rocks.infinitecraft;

import dev.rocks.infinitecraft.core.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TraitStrengthsTest {
    @Test void rejectsInvalidAndUnselectedStrengths() {
        for (double value : new double[] {-0.1,1.1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new RecipeResult("minecraft:stick",1,"",List.of("bouncy"),Map.of("bouncy",value)));
        assertThrows(IllegalArgumentException.class, () -> new RecipeResult("minecraft:stick",1,"",List.of("blocking"),Map.of("blocking",.5)));
        assertThrows(IllegalArgumentException.class, () -> new RecipeResult("minecraft:stick",1,"",List.of(),Map.of("bouncy",.5)));
    }
}
