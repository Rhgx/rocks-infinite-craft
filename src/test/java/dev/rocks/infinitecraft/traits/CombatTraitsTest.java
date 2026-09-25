package dev.rocks.infinitecraft.traits;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatTraitsTest {
    @Test
    void lifestealRequiresDamageAndRespectsMissingHealth() {
        assertEquals(0, CombatTraits.healingAmount(0, 10));
        assertEquals(0, CombatTraits.healingAmount(-1, 10));
        assertEquals(1, CombatTraits.healingAmount(2, 10));
        assertEquals(2, CombatTraits.healingAmount(8, 10));
        assertEquals(4, CombatTraits.healingAmount(100, 10));
        assertEquals(0.5F, CombatTraits.healingAmount(8, 0.5F));
        assertEquals(0, CombatTraits.healingAmount(8, 0));
    }

    @Test
    void explosionChanceUsesTf2MeleeCurve() {
        assertEquals(0.1F, CombatTraits.explosionChance(0));
        assertEquals(0.3F, CombatTraits.explosionChance(400));
        assertEquals(0.5F, CombatTraits.explosionChance(800));
        assertEquals(0.5F, CombatTraits.explosionChance(2000));
    }
}
