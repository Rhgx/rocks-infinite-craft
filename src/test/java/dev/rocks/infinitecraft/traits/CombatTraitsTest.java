package dev.rocks.infinitecraft.traits;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatTraitsTest {
    @Test
    void explosionChanceUsesTf2MeleeCurve() {
        assertEquals(0.1F, CombatTraits.explosionChance(0));
        assertEquals(0.3F, CombatTraits.explosionChance(400));
        assertEquals(0.5F, CombatTraits.explosionChance(800));
        assertEquals(0.5F, CombatTraits.explosionChance(2000));
    }
}
