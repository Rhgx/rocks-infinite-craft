package dev.rocks.infinitecraft.traits;

import dev.rocks.infinitecraft.core.PairKey;
import dev.rocks.infinitecraft.fusion.FusionCount;
import dev.rocks.infinitecraft.item.ItemTraits;
import dev.rocks.infinitecraft.item.VanillaTraits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TriggeredTraitsTest {
    @Test
    void luckyCooldownDoesNotLockOutAResetPlayerClock() {
        assertFalse(LuckyBlockTrait.coolingDown(0, null));
        assertTrue(LuckyBlockTrait.coolingDown(1000, 1000));
        assertTrue(LuckyBlockTrait.coolingDown(1004, 1000));
        assertFalse(LuckyBlockTrait.coolingDown(1005, 1000));
        assertFalse(LuckyBlockTrait.coolingDown(0, 1000));
        assertFalse(LuckyBlockTrait.coolingDown(20, 1000));
    }
    @Test
    void luckyMessagesUseRainbowAndDarkRed() {
        var rainbow = LuckyBlockTrait.message(0, "♦ Immortal!");
        assertEquals("♦ Immortal!", rainbow.getString());
        var colors = rainbow.getSiblings().stream().filter(part -> !part.getString().isBlank())
                .map(part -> part.getStyle().getColor()).toList();
        assertEquals(10, colors.size());
        for (int i = 1; i < colors.size(); i++) assertNotEquals(colors.get(i - 1), colors.get(i));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.DARK_RED),
                LuckyBlockTrait.message(1, "Unlucky.").getStyle().getColor());
    }

    @Test
    void starThemeLoopsInTimeAndStaysInNoteBlockRange() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        assertEquals(64, LuckyBlockEffects.STAR_THEME.size());
        int notes = 0;
        for (var tick : LuckyBlockEffects.STAR_THEME) {
            for (var note : tick) {
                notes++;
                assertTrue(note.pitch() >= 0.5f && note.pitch() <= 2.0f, "pitch " + note.pitch());
            }
        }
        // Per bar: seven three-note chords, three fill notes and five bass notes.
        assertEquals(2 * (7 * 3 + 3 + 5), notes);
    }

    @Test
    void luckyRollsAreStableAndCoverThirteenOutcomes() {
        var outcomes = new HashSet<Integer>();
        for (int roll = 0; roll < 100; roll++) outcomes.add(LuckyBlockTrait.outcome(roll));
        assertEquals(13, outcomes.size());
        assertEquals(12, LuckyBlockTrait.outcome(99));
        assertEquals(0, LuckyBlockTrait.outcome(0));
        assertEquals(0, LuckyBlockTrait.outcome(3));
        assertEquals(1, LuckyBlockTrait.outcome(4));
        assertEquals(1, LuckyBlockTrait.outcome(7));
        assertEquals(2, LuckyBlockTrait.outcome(8));
        var counts = new int[13];
        for (int roll = 0; roll < 100; roll++) counts[LuckyBlockTrait.outcome(roll)]++;
        for (int outcome = 2; outcome < 13; outcome++) assertTrue(counts[outcome] >= 8 && counts[outcome] <= 9);
        String pair = PairKey.of("minecraft:stone", "minecraft:dirt");
        assertEquals(LuckyBlockTrait.appears(pair, 17), LuckyBlockTrait.appears(
                PairKey.of("minecraft:dirt", "minecraft:stone"), 17));
        int lucky = 0;
        for (int seed = 0; seed < 10000; seed++) if (LuckyBlockTrait.appears(pair, seed)) lucky++;
        assertTrue(lucky > 350 && lucky < 650);
        assertEquals(100, TraitSettings.defaultChance("hearty_food"));
        assertEquals(100, TraitSettings.defaultChance("vanishing_food"));
    }

    @Test
    void rainbowDisplayCopiesSettingsWithoutChangingTheRealTeam() {
        var scoreboard = new net.minecraft.world.scores.Scoreboard();
        var original = scoreboard.addPlayerTeam("friends");
        scoreboard.addPlayerToTeam("Rocks", original);
        original.setPlayerPrefix(net.minecraft.network.chat.Component.literal("[Friends] "));
        original.setColor(Optional.of(net.minecraft.world.scores.TeamColor.BLUE));
        original.setAllowFriendlyFire(false);
        original.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.NEVER);
        var display = new net.minecraft.world.scores.PlayerTeam(new net.minecraft.world.scores.Scoreboard(), "star");
        LuckyBlockEffects.copySettings(original, display);
        display.setColor(Optional.of(net.minecraft.world.scores.TeamColor.RED));
        assertSame(original, scoreboard.getPlayersTeam("Rocks"));
        assertEquals(Optional.of(net.minecraft.world.scores.TeamColor.BLUE), original.getColor());
        assertEquals(original.getPlayerPrefix(), display.getPlayerPrefix());
        assertFalse(display.isAllowFriendlyFire());
        assertEquals(original.getNameTagVisibility(), display.getNameTagVisibility());
        LuckyBlockEffects.copySettings(null, display);
        assertEquals(net.minecraft.world.scores.Team.Visibility.ALWAYS, display.getNameTagVisibility());
        assertEquals("", display.getPlayerPrefix().getString());
    }

    @Test
    void foodAndArmorTraitsProduceValidVanillaComponents() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup())
                .forEach(pending -> pending.apply());
        var lucky = VanillaTraits.apply(new ItemStack(Items.STONE), List.of("lucky_block"), "");
        assertFalse(lucky.isEmpty());
        assertTrue(FusionCount.apply(lucky, ItemStack.EMPTY, ItemStack.EMPTY,
                stack -> stack == lucky, FusionCount.LIMIT));
        assertTrue(ItemTraits.apply(lucky, ItemStack.EMPTY, ItemStack.EMPTY,
                List.of("lucky_block"), 1));
        assertTrue(FusionCount.supportedData(lucky));
        assertTrue(ItemStack.validateStrict(lucky).result().isPresent());
        var apple = new ItemStack(Items.APPLE);
        var food = VanillaTraits.apply(apple, List.of("nibbleable", "hearty_food"), "", Map.of("nibbleable", 0.0));
        assertFalse(food.isEmpty());
        assertEquals(3, food.getMaxDamage());
        assertEquals(1, food.getMaxStackSize());
        assertEquals(apple.get(DataComponents.FOOD).nutrition(), food.get(DataComponents.FOOD).nutrition());
        var effect = (ApplyStatusEffectsConsumeEffect) food.get(DataComponents.CONSUMABLE).onConsumeEffects().getFirst();
        assertEquals(1F, effect.probability());
        assertTrue(ItemStack.validateStrict(food).result().isPresent());
        NibbleableTrait.spendBite(food);
        assertEquals(1, food.getDamageValue());
        assertEquals(1, food.getCount());
        NibbleableTrait.spendBite(food);
        assertEquals(2, food.getDamageValue());
        NibbleableTrait.spendBite(food);
        assertTrue(food.isEmpty());

        var armor = VanillaTraits.apply(new ItemStack(Items.IRON_HELMET), List.of("startled", "hardened"), "", Map.of());
        assertFalse(armor.isEmpty());
        assertEquals(EquipmentSlot.HEAD, armor.get(DataComponents.EQUIPPABLE).slot());
        assertTrue(VanillaTraits.apply(apple, List.of("hardened", "nibbleable"), "", Map.of()).isEmpty());
    }
}
