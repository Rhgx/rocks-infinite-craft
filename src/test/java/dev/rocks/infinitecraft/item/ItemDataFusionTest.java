package dev.rocks.infinitecraft.item;

import dev.rocks.infinitecraft.fusion.FusionCount;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.enchantment.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemDataFusionTest {
    @Test void conflictingDataCanPreserveEitherIngredientWithoutChangingInputs() {
        var helmet = new ItemStack(Items.IRON_HELMET);
        helmet.setDamageValue(1);
        var potion = new ItemStack(Items.POTION);
        potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER_BREATHING));
        assertTrue(ItemDataFusion.prepare(new ItemStack(Items.POTION), helmet, potion, false).isEmpty());
        assertTrue(ItemDataFusion.prepare(new ItemStack(Items.IRON_HELMET), helmet, potion, false).isEmpty());
        var helmetWins = ItemDataFusion.prepare(new ItemStack(Items.IRON_HELMET), helmet, ItemStack.EMPTY, false);
        assertFalse(helmetWins.isEmpty());
        assertEquals(1, helmetWins.getDamageValue());
        assertFalse(helmetWins.has(DataComponents.POTION_CONTENTS));
        var potionWins = ItemDataFusion.prepare(new ItemStack(Items.POTION), ItemStack.EMPTY, potion, false);
        assertFalse(potionWins.isEmpty());
        assertEquals(potion.get(DataComponents.POTION_CONTENTS), potionWins.get(DataComponents.POTION_CONTENTS));
        helmet.set(DataComponents.CUSTOM_NAME, Component.literal("Helmet"));
        potion.set(DataComponents.CUSTOM_NAME, Component.literal("Potion"));
        assertEquals("Helmet", ItemDataFusion.prepare(new ItemStack(Items.IRON_HELMET), helmet, ItemStack.EMPTY, false).getHoverName().getString());
        assertEquals("Potion", ItemDataFusion.prepare(new ItemStack(Items.POTION), ItemStack.EMPTY, potion, false).getHoverName().getString());
        assertEquals(1, helmet.getDamageValue());
        assertEquals(new PotionContents(Potions.WATER_BREATHING), potion.get(DataComponents.POTION_CONTENTS));
    }
    @Test void specialFusionCounterStartsAtZeroAndSurvivesPlainDescendants() {
        var untouched = new ItemStack(Items.STONE);
        assertTrue(ItemStack.matches(untouched, FusionOrigin.strip(untouched)));
        assertTrue(ItemDataFusion.supported(FusionOrigin.strip(untouched)));
        assertEquals(-1, FusionCount.get(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)));
        assertEquals(-1, FusionCount.get(new ItemStack(Items.ENCHANTED_BOOK)));
        var vanillaPotion = new ItemStack(Items.POTION);
        vanillaPotion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER_BREATHING));
        assertEquals(-1, FusionCount.get(vanillaPotion));
        var plain = new ItemStack(Items.STONE);
        var special = new ItemStack(Items.STICK);
        special.set(DataComponents.CUSTOM_NAME, Component.literal("Special"));
        assertTrue(FusionCount.apply(special, plain, plain));
        assertEquals(0, FusionCount.get(special));
        assertTrue(ItemTraits.apply(special, plain, plain, java.util.List.of("bouncy"), 1));
        assertEquals(java.util.List.of("bouncy"), ItemTraits.inherited(special));
        assertEquals(0, FusionCount.get(special));
        var retuned = special.copy();
        assertTrue(ItemTraits.apply(retuned, special, plain, java.util.List.of("bouncy"), 1));
        assertFalse(ItemTraits.apply(retuned, special, plain, java.util.List.of("speedy"), 1));
        assertTrue(FusionCount.supportedData(retuned));
        var dyed = new ItemStack(Items.LEATHER_CHESTPLATE);
        dyed.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x4A8BFF));
        assertTrue(ItemDataFusion.specialIngredient(dyed));
        assertTrue(ItemDataFusion.supported(dyed));
        var modeled = new ItemStack(Items.STICK);
        modeled.set(DataComponents.ITEM_MODEL, new ItemStack(Items.DIAMOND).get(DataComponents.ITEM_MODEL));
        assertTrue(ItemDataFusion.supported(modeled));
        assertTrue(ItemDataFusion.specialIngredient(modeled));


        assertTrue(ItemDataFusion.supported(special));
        var origin = new ItemStack(Items.TORCH);
        assertTrue(FusionOrigin.apply(origin, new ItemStack(Items.STICK), new ItemStack(Items.COAL)));
        assertEquals("Made from Stick + Coal", origin.get(DataComponents.LORE).lines().getLast().getString());
        assertTrue(ItemDataFusion.supported(origin));
        var strippedOrigin = FusionOrigin.strip(origin);
        assertFalse(strippedOrigin.has(DataComponents.CUSTOM_DATA));
        assertTrue(strippedOrigin.getOrDefault(DataComponents.LORE,
                net.minecraft.world.item.component.ItemLore.EMPTY).lines().isEmpty());
        assertTrue(strippedOrigin.getComponentsPatch().isEmpty());
        var chestBoat = new ItemStack(Items.BIRCH_CHEST_BOAT);
        assertTrue(FusionOrigin.apply(chestBoat, new ItemStack(Items.BIRCH_STAIRS), new ItemStack(Items.BIRCH_SLAB)));
        var strippedChestBoat = FusionOrigin.strip(chestBoat);
        assertTrue(ItemDataFusion.supported(strippedChestBoat));
        assertTrue(strippedChestBoat.getComponentsPatch().isEmpty());
        var lineage = new java.util.ArrayList<ItemStack>();
        var descendant = special;
        for (int count = 1; count <= 5; count++) {
            var next = new ItemStack(Items.STONE);
            assertTrue(FusionCount.apply(next, descendant, plain));
            assertEquals(count, FusionCount.get(next));
            assertEquals("Combinations: " + count + "/5", next.get(DataComponents.LORE).lines().getLast().getString());
            assertTrue(ItemDataFusion.supported(next));
            lineage.add(next);
            descendant = next;
        }
        assertTrue(FusionCount.exhausted(descendant));
        assertFalse(FusionCount.apply(new ItemStack(Items.STONE), descendant, plain));
        assertTrue(ItemDataFusion.prepare(new ItemStack(Items.STONE), descendant, plain, false).isEmpty());
        assertEquals(0, FusionCount.get(special));
        var merged = ItemDataFusion.prepare(new ItemStack(Items.STONE), lineage.get(0), lineage.get(1), false);
        assertFalse(merged.isEmpty());
        assertTrue(FusionCount.apply(merged, lineage.get(0), lineage.get(1)));
        assertEquals(3, FusionCount.get(merged));
        assertEquals(1, merged.get(DataComponents.LORE).lines().size());
        assertEquals(0xFFFF55, merged.get(DataComponents.LORE).lines().getFirst().getStyle().getColor().getValue());
        var ops = net.minecraft.data.registries.VanillaRegistries.createLookup()
                .createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var restored = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, merged).getOrThrow()).getOrThrow();
        assertEquals(3, FusionCount.get(restored));
    }

    @Test void foreignAndMalformedCustomDataRemainUnsupported() {
        for (int count : new int[] {-1, 6}) {
            var stack = new ItemStack(Items.STONE);
            var marker = new net.minecraft.nbt.CompoundTag();
            marker.putInt("infinitecraft_combinations", count);
            stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(marker));
            assertFalse(ItemDataFusion.supported(stack));
        }
        var stack = new ItemStack(Items.KNOWLEDGE_BOOK);
        var marker = new net.minecraft.nbt.CompoundTag();
        marker.putBoolean("infinitecraft_discovery_book", true);
        marker.putInt("infinitecraft_combinations", 0);
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(marker));
        assertFalse(ItemDataFusion.supported(stack));
    }
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(pending -> pending.apply());
    }

    @Test void effectlessBottlesCanReceiveModelSelectedPotionEffects() {
        var water = new ItemStack(Items.POTION);
        water.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
        assertTrue(ItemDataFusion.specialIngredient(water));
        assertTrue(PotionFusion.describe(water, new ItemStack(Items.SUGAR)).getFirst().getFirst().contains("no effects"));
        assertTrue(PotionFusion.options().containsKey("minecraft:swiftness"));
        var output = ItemDataFusion.prepare(new ItemStack(Items.POTION), water, new ItemStack(Items.SUGAR), false);
        assertFalse(output.isEmpty());
        assertTrue(PotionFusion.applyChoice(output, "minecraft:swiftness"));
        assertTrue(output.get(DataComponents.POTION_CONTENTS).getAllEffects().iterator().hasNext());
        assertFalse(water.get(DataComponents.POTION_CONTENTS).getAllEffects().iterator().hasNext());
        assertTrue(ItemStack.validateStrict(output).result().isPresent());
        assertFalse(PotionFusion.applyChoice(new ItemStack(Items.STONE), "minecraft:swiftness"));
        assertFalse(PotionFusion.applyChoice(output, "minecraft:missing"));
        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var ops = lookup.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var restored = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, output).getOrThrow()).getOrThrow();
        assertTrue(restored.get(DataComponents.POTION_CONTENTS).getAllEffects().iterator().hasNext());
    }

    @Test void carriesNamesAndDamageWithoutMutatingInputs() {
        var first = new ItemStack(Items.IRON_SWORD);
        assertFalse(ItemDataFusion.specialIngredient(first));
        first.setDamageValue(50);
        assertFalse(ItemDataFusion.specialIngredient(first));
        assertFalse(ItemDataFusion.specialIngredient(new ItemStack(Items.IRON_CHESTPLATE)));
        assertFalse(ItemDataFusion.specialIngredient(new ItemStack(Items.BREAD)));
        assertTrue(ItemDataFusion.specialIngredient(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)));
        var rare = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
        assertFalse(ItemDataFusion.specialIngredient(rare, false, false, false, false));
        assertTrue(ItemDataFusion.specialIngredient(rare, true, false, false, false));
        for (var rarity : net.minecraft.world.item.Rarity.values()) {
            var item = new ItemStack(Items.STONE);
            item.set(DataComponents.RARITY, rarity);
            assertEquals(rarity != net.minecraft.world.item.Rarity.COMMON, ItemDataFusion.specialIngredient(item));
        }
        first.set(DataComponents.CUSTOM_NAME, Component.literal("Keeper"));
        assertTrue(ItemDataFusion.specialIngredient(first));
        first.setDamageValue(50);
        var result = ItemDataFusion.prepare(new ItemStack(Items.DIAMOND_SWORD), first, new ItemStack(Items.DIAMOND), false);
        assertEquals(first.getCustomName(), result.getCustomName());
        assertTrue(result.getDamageValue() > 0);
        assertEquals(50, first.getDamageValue());
        assertTrue(ItemDataFusion.prepare(new ItemStack(Items.STONE), first, new ItemStack(Items.DIAMOND), false).isEmpty());
    }

    @Test void conflictingNamesAndUnknownComponentsAreRejected() {
        var first = new ItemStack(Items.STICK);
        var second = new ItemStack(Items.COAL);
        first.set(DataComponents.CUSTOM_NAME, Component.literal("One"));
        second.set(DataComponents.CUSTOM_NAME, Component.literal("Two"));
        assertTrue(ItemDataFusion.prepare(new ItemStack(Items.TORCH), first, second, false).isEmpty());
        first.set(DataComponents.MAX_STACK_SIZE, 32);
        assertFalse(ItemDataFusion.supported(first));
        var removed = new ItemStack(Items.IRON_SWORD);
        removed.remove(DataComponents.DAMAGE);
        assertFalse(ItemDataFusion.supported(removed));
    }

    @Test void potionsMergeEffectsWithoutEscalationOrInputMutation() {
        var healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
        assertTrue(ItemDataFusion.specialIngredient(healing));
        var result = ItemDataFusion.prepare(new ItemStack(Items.SPLASH_POTION), healing, new ItemStack(Items.GUNPOWDER), false);
        assertEquals(healing.get(DataComponents.POTION_CONTENTS), result.get(DataComponents.POTION_CONTENTS));
        assertTrue(ItemDataFusion.prepare(new ItemStack(Items.STONE), healing, new ItemStack(Items.STONE), false).isEmpty());
        var poison = PotionContents.createItemStack(Items.POTION, Potions.POISON);
        var mixed = ItemDataFusion.prepare(new ItemStack(Items.POTION), healing, poison, false);
        assertFalse(mixed.isEmpty());
        var effects = mixed.get(DataComponents.POTION_CONTENTS).customEffects();
        assertEquals(2, effects.size());
        assertEquals(mixed.get(DataComponents.POTION_CONTENTS),
                ItemDataFusion.prepare(new ItemStack(Items.POTION), poison, healing, false).get(DataComponents.POTION_CONTENTS));
        assertEquals(mixed.get(DataComponents.POTION_CONTENTS),
                ItemDataFusion.prepare(new ItemStack(Items.POTION), mixed, mixed, false).get(DataComponents.POTION_CONTENTS));
        var strong = PotionContents.createItemStack(Items.POTION, Potions.STRONG_POISON);
        var longPoison = PotionContents.createItemStack(Items.POTION, Potions.LONG_POISON);
        var duplicate = ItemDataFusion.prepare(new ItemStack(Items.LINGERING_POTION), strong, longPoison, false);
        var retained = duplicate.get(DataComponents.POTION_CONTENTS).customEffects().getFirst();
        assertEquals(1, retained.getAmplifier());
        assertEquals(strong.get(DataComponents.POTION_CONTENTS).getAllEffects().iterator().next().getDuration(), retained.getDuration());
        assertTrue(healing.get(DataComponents.POTION_CONTENTS).is(Potions.HEALING));
        assertFalse(PotionFusion.describe(healing, poison).getFirst().isEmpty());
    }

    @Test void stewCarriesPotionLevelsAndItsOwnEffectsAcrossFurtherFusion() {
        var stew = new ItemStack(Items.SUSPICIOUS_STEW);
        stew.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, new net.minecraft.world.item.component.SuspiciousStewEffects(
                java.util.List.of(new net.minecraft.world.item.component.SuspiciousStewEffects.Entry(
                        net.minecraft.world.effect.MobEffects.BLINDNESS, 160))));
        assertTrue(ItemDataFusion.supported(stew));
        assertTrue(ItemDataFusion.specialIngredient(stew));
        var potion = PotionContents.createItemStack(Items.POTION, Potions.STRONG_SWIFTNESS);
        var result = ItemDataFusion.prepare(new ItemStack(Items.SUSPICIOUS_STEW), stew, potion, false);
        assertFalse(result.isEmpty());
        assertEquals(2, result.get(DataComponents.POTION_CONTENTS).customEffects().size());
        assertTrue(result.get(DataComponents.POTION_CONTENTS).customEffects().stream()
                .anyMatch(effect -> effect.is(net.minecraft.world.effect.MobEffects.SPEED) && effect.getAmplifier() == 1));
        assertTrue(result.get(DataComponents.SUSPICIOUS_STEW_EFFECTS).effects().isEmpty());
        assertEquals(result.get(DataComponents.POTION_CONTENTS), ItemDataFusion.prepare(
                new ItemStack(Items.SUSPICIOUS_STEW), potion, stew, false).get(DataComponents.POTION_CONTENTS));
        assertFalse(ItemDataFusion.prepare(new ItemStack(Items.SUSPICIOUS_STEW), result, stew, false).isEmpty());
        assertFalse(ItemDataFusion.prepare(new ItemStack(Items.SPLASH_POTION), stew, potion, false).isEmpty());
        assertTrue(ItemDataFusion.prepare(new ItemStack(Items.STONE), stew, potion, false).isEmpty());
        var ops = net.minecraft.data.registries.VanillaRegistries.createLookup().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var encoded = ItemStack.CODEC.encodeStart(ops, result).getOrThrow();
        assertEquals(result.get(DataComponents.POTION_CONTENTS),
                ItemStack.CODEC.parse(ops, encoded).getOrThrow().get(DataComponents.POTION_CONTENTS));
        assertEquals(1, stew.get(DataComponents.SUSPICIOUS_STEW_EFFECTS).effects().size());
    }

    @Test void brewingChangesPotionUsingVanillaRulesInEitherOrder() {
        var brewing = PotionBrewing.bootstrap(FeatureFlags.VANILLA_SET);
        var healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
        var glowstone = new ItemStack(Items.GLOWSTONE_DUST);
        var brewed = ItemDataFusion.brew(brewing, healing, glowstone);
        assertTrue(brewed.get(DataComponents.POTION_CONTENTS).is(Potions.STRONG_HEALING));
        assertTrue(ItemStack.matches(brewed, ItemDataFusion.brew(brewing, glowstone, healing)));
        var result = ItemDataFusion.prepare(brewed, healing, glowstone, true);
        assertTrue(result.get(DataComponents.POTION_CONTENTS).is(Potions.STRONG_HEALING));
        assertTrue(healing.get(DataComponents.POTION_CONTENTS).is(Potions.HEALING));
        var custom = new PotionContents(java.util.Optional.of(Potions.HEALING), java.util.Optional.of(0x123456),
                java.util.List.of(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 200)),
                java.util.Optional.of("custom"));
        healing.set(DataComponents.POTION_CONTENTS, custom);
        var customResult = ItemDataFusion.prepare(ItemDataFusion.brew(brewing, healing, glowstone), healing, glowstone, true);
        assertEquals(custom.customEffects(), customResult.get(DataComponents.POTION_CONTENTS).customEffects());
        assertEquals(custom.customColor(), customResult.get(DataComponents.POTION_CONTENTS).customColor());
        glowstone.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
        assertTrue(ItemDataFusion.prepare(brewed, healing, glowstone, true).isEmpty());
    }

    @Test void enchantmentsMergeWithinLimitsOnUnusualOutputs() {
        var sword = new ItemStack(Items.IRON_SWORD);
        var enchantment = Holder.direct(new Enchantment(Component.literal("Test"),
                Enchantment.definition(HolderSet.direct(sword.typeHolder()), 1, 3,
                        Enchantment.constantCost(1), Enchantment.constantCost(1), 1, EquipmentSlotGroup.MAINHAND),
                HolderSet.direct(), DataComponentMap.EMPTY));
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment, 2);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        sword.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        assertTrue(ItemDataFusion.specialIngredient(sword));
        assertTrue(ItemDataFusion.specialIngredient(book));
        var rareBook = book.copy();
        rareBook.set(DataComponents.RARITY, net.minecraft.world.item.Rarity.EPIC);
        assertTrue(ItemDataFusion.specialIngredient(rareBook, true, false, false, false));
        assertTrue(ItemDataFusion.specialIngredient(rareBook, false, true, false, false));
        assertFalse(ItemDataFusion.specialIngredient(rareBook, false, false, false, false));
        var result = ItemDataFusion.prepare(new ItemStack(Items.IRON_SWORD), sword, book, false);
        assertEquals(3, result.getEnchantments().getLevel(enchantment));
        assertEquals(2, sword.getEnchantments().getLevel(enchantment));
        var enchantedBlock = ItemDataFusion.prepare(new ItemStack(Items.STONE), sword, book, false);
        assertFalse(enchantedBlock.isEmpty());
        assertFalse(enchantment.value().isSupportedItem(enchantedBlock));
        assertEquals(3, enchantedBlock.getEnchantments().getLevel(enchantment));
        assertTrue(ItemDataFusion.prepare(new ItemStack(Items.IRON_SWORD, 2), sword, book, false).isEmpty());
        var capped = ItemDataFusion.prepare(new ItemStack(Items.IRON_SWORD), result, result, false);
        assertEquals(3, capped.getEnchantments().getLevel(enchantment));
        enchantments.set(enchantment, 4);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        assertTrue(ItemDataFusion.prepare(new ItemStack(Items.IRON_SWORD), sword, book, false).isEmpty());

        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var fire = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.FIRE_ASPECT);
        var fireBook = new ItemStack(Items.ENCHANTED_BOOK);
        var fireEnchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        fireEnchantments.set(fire, 1);
        fireBook.set(DataComponents.STORED_ENCHANTMENTS, fireEnchantments.toImmutable());
        var fireBlock = ItemDataFusion.prepare(new ItemStack(Items.STONE), fireBook, new ItemStack(Items.STONE), false);
        assertTrue(fire.value().matchingSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
        assertFalse(fire.value().getEffects(EnchantmentEffectComponents.POST_ATTACK).isEmpty());
        assertEquals(1, fireBlock.getEnchantments().getLevel(fire));
        var ops = lookup.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var encoded = ItemStack.CODEC.encodeStart(ops, fireBlock).getOrThrow();
        assertEquals(1, ItemStack.CODEC.parse(ops, encoded).getOrThrow().getEnchantments().getLevel(fire));
        var storedAgain = ItemDataFusion.prepare(new ItemStack(Items.ENCHANTED_BOOK), fireBlock, new ItemStack(Items.BOOK), false);
        assertEquals(1, storedAgain.get(DataComponents.STORED_ENCHANTMENTS).getLevel(fire));
    }
}
