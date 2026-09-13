package dev.rocks.infinitecraft;

import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VanillaTraitsTest {
    @Test void traitPairCompatibilityAndCompositionDoNotDependOnRequestOrder() {
        var ops = lookup.createSerializationContext(JsonOps.INSTANCE);
        var ids = VanillaTraits.ids();
        for (int a = 0; a < ids.size(); a++) for (int b = a + 1; b < ids.size(); b++) {
            var forward = VanillaTraits.apply(new ItemStack(Items.STICK), List.of(ids.get(a), ids.get(b)), "");
            var reverse = VanillaTraits.apply(new ItemStack(Items.STICK), List.of(ids.get(b), ids.get(a)), "");
            assertEquals(forward.isEmpty(), reverse.isEmpty(), ids.get(a) + " + " + ids.get(b));
            if (!forward.isEmpty()) {
                // Hint order follows requested traits; compare the actual gameplay components.
                forward.remove(DataComponents.LORE);
                reverse.remove(DataComponents.LORE);
                assertEquals(ItemStack.CODEC.encodeStart(ops, forward).result(), ItemStack.CODEC.encodeStart(ops, reverse).result());
            }
        }
    }

    @Test void chosenActivationControlsSlotsConsumptionAndInheritance() {
        var parts = List.of(
                new dev.rocks.infinitecraft.core.NamePart("Lost ", new dev.rocks.infinitecraft.core.NameStyle("#FFAA00", true, false, true, false, true)),
                new dev.rocks.infinitecraft.core.NamePart("Rune", dev.rocks.infinitecraft.core.NameStyle.PLAIN));
        var named = VanillaTraits.apply(new ItemStack(Items.STICK), List.of(), "Lost Rune", java.util.Map.of(),
                java.util.Map.of(), dev.rocks.infinitecraft.core.NameStyle.PLAIN, parts);
        var name = named.get(DataComponents.CUSTOM_NAME);
        assertEquals("Lost Rune", name.getString());
        assertTrue(name.getSiblings().getFirst().getStyle().isObfuscated());
        assertTrue(name.getSiblings().getFirst().getStyle().isUnderlined());
        assertFalse(name.getSiblings().getLast().getStyle().isObfuscated());
        assertFalse(name.getSiblings().getLast().getStyle().isBold());
        var nameOps = lookup.createSerializationContext(JsonOps.INSTANCE);
        var roundTrip = ItemStack.CODEC.parse(nameOps, ItemStack.CODEC.encodeStart(nameOps, named).getOrThrow()).getOrThrow();
        assertTrue(ItemStack.isSameItemSameComponents(named, roundTrip));

        var plain = dev.rocks.infinitecraft.core.NameStyle.PLAIN;
        var stick = new ItemStack(Items.STICK);
        var offhand = VanillaTraits.apply(stick, List.of("speedy"), "", java.util.Map.of(),
                java.util.Map.of("speedy", "offhand"), plain);
        var modifiers = offhand.get(DataComponents.ATTRIBUTE_MODIFIERS);
        assertEquals(.1, modifiers.compute(Attributes.MOVEMENT_SPEED, .1, EquipmentSlot.MAINHAND), .00001);
        assertTrue(modifiers.compute(Attributes.MOVEMENT_SPEED, .1, EquipmentSlot.OFFHAND) > .1);
        var mainhand = VanillaTraits.apply(offhand, List.of("speedy"), "", java.util.Map.of(),
                java.util.Map.of("speedy", "mainhand"), plain);
        assertTrue(mainhand.get(DataComponents.LORE).lines().isEmpty());
        var inherited = ItemDataFusion.prepare(new ItemStack(Items.DIAMOND), offhand, stick, false);
        assertEquals(modifiers, inherited.get(DataComponents.ATTRIBUTE_MODIFIERS));
        var worn = VanillaTraits.apply(stick, List.of("bouncy"), "", java.util.Map.of(),
                java.util.Map.of("bouncy", "head"), plain);
        assertEquals(EquipmentSlot.HEAD, worn.get(DataComponents.EQUIPPABLE).slot());
        assertFalse(ItemDataFusion.prepare(new ItemStack(Items.DIAMOND), worn, stick, false).isEmpty());
        var eaten = VanillaTraits.apply(offhand, List.of("speedy"), "", java.util.Map.of("speedy", 1.0),
                java.util.Map.of("speedy", "consumed"), plain);
        assertFalse(eaten.isEmpty());
        assertTrue(eaten.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().isEmpty());
        assertEquals(List.of("Edible"), eaten.get(DataComponents.LORE).lines().stream().map(line -> line.getString()).toList());
        var effect = (net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect)
                eaten.get(DataComponents.CONSUMABLE).onConsumeEffects().getFirst();
        assertEquals(400, effect.effects().getFirst().getDuration());
        assertEquals(2, effect.effects().getFirst().getAmplifier());
        assertFalse(ItemDataFusion.prepare(new ItemStack(Items.DIAMOND), eaten, stick, false).isEmpty());
        var heldAgain = VanillaTraits.apply(eaten, List.of("speedy"), "", java.util.Map.of(),
                java.util.Map.of("speedy", "mainhand"), plain);
        assertTrue(heldAgain.get(DataComponents.CONSUMABLE).onConsumeEffects().isEmpty());
        assertTrue(VanillaTraits.apply(stick, List.of("bouncy", "speedy"), "", java.util.Map.of(),
                java.util.Map.of("bouncy", "head", "speedy", "consumed"), plain).isEmpty());
        var sharedSlot = VanillaTraits.apply(stick, List.of("bouncy", "speedy"), "", java.util.Map.of(),
                java.util.Map.of("bouncy", "head", "speedy", "feet"), plain);
        assertEquals(EquipmentSlot.HEAD, sharedSlot.get(DataComponents.EQUIPPABLE).slot());
        assertTrue(sharedSlot.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
                .allMatch(entry -> entry.slot() == EquipmentSlotGroup.HEAD));
        assertTrue(VanillaTraits.apply(stick, List.of("bouncy"), "", java.util.Map.of(),
                java.util.Map.of("bouncy", "consumed"), plain).isEmpty());
    }

    @Test void quantitiesSplitIntoValidStacksWithoutLosingSpecialComponents() {
        var special = VanillaTraits.apply(new ItemStack(Items.JUNGLE_PLANKS), List.of("bouncy"), "Jungle Oak Planks");
        assertEquals(64, special.getMaxStackSize());
        var inherited = ItemDataFusion.prepare(new ItemStack(Items.OAK_PLANKS), special, new ItemStack(Items.STICK), false);
        assertEquals(64, inherited.getMaxStackSize());
        assertEquals(special.get(DataComponents.ATTRIBUTE_MODIFIERS), inherited.get(DataComponents.ATTRIBUTE_MODIFIERS));
        for (var template : List.of(new ItemStack(Items.STONE), new ItemStack(Items.ENDER_PEARL),
                new ItemStack(Items.IRON_SWORD), special)) {
            var outputs = FusionRuntime.splitOutput(template, 37);
            assertEquals(37, outputs.stream().mapToInt(ItemStack::getCount).sum());
            for (var output : outputs) {
                assertTrue(ItemStack.validateStrict(output).result().isPresent());
                assertEquals(template.getComponents(), output.getComponents());
            }
            assertEquals(1, template.getCount());
        }
        assertEquals(1, FusionRuntime.splitOutput(special, 3).size());
        var durable = VanillaTraits.apply(new ItemStack(Items.STICK), List.of("gliding"), "Glider");
        assertEquals(3, FusionRuntime.splitOutput(durable, 3).size());
        assertThrows(IllegalArgumentException.class, () -> FusionRuntime.splitOutput(special, 65));
    }

    @Test void armorEnchantmentsMakeUnusualItemsWearableInOneRequiredSlot() {
        var enchantments = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        var registry = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        enchantments.set(registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.AQUA_AFFINITY), 1);
        var book = new ItemStack(Items.KNOWLEDGE_BOOK);
        book.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        var result = VanillaTraits.apply(book, List.of(), "Resonant Knowledge");
        assertEquals(EquipmentSlot.HEAD, result.get(DataComponents.EQUIPPABLE).slot());
        enchantments.set(registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FEATHER_FALLING), 1);
        book.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        var competing = VanillaTraits.apply(book, List.of(), "");
        assertEquals(EquipmentSlot.HEAD, competing.get(DataComponents.EQUIPPABLE).slot());
        assertEquals(2, competing.getEnchantments().keySet().size());
        assertTrue(ItemStack.validateStrict(competing).result().isPresent());
        assertFalse(book.has(DataComponents.EQUIPPABLE));
    }

    private static HolderLookup.Provider lookup;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        lookup = VanillaRegistries.createLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(pending -> pending.apply());
    }

    @Test void allTraitsRoundTripThroughNativeStackCodec() {
        var ops = lookup.createSerializationContext(JsonOps.INSTANCE);
        for (String trait : VanillaTraits.ids()) for (String activation :
                dev.rocks.infinitecraft.traits.TraitRegistry.get(trait).activationModes()) {
            var result = VanillaTraits.apply(new ItemStack(Items.STICK, 5), List.of(trait), "Test " + trait,
                    java.util.Map.of(), java.util.Map.of(trait, activation), new dev.rocks.infinitecraft.core.NameStyle("#55FF55", true, true));
            assertFalse(result.isEmpty(), trait);
            assertFalse(ItemDataFusion.prepare(new ItemStack(Items.STICK), result, new ItemStack(Items.COAL), false).isEmpty(), trait);
            assertTrue(result.getCustomName().getStyle().isBold());
            assertTrue(result.getCustomName().getStyle().isItalic());
            assertEquals(0x55FF55, result.getCustomName().getStyle().getColor().getValue());
            assertEquals(1, result.getCount());
            assertEquals(result.has(DataComponents.MAX_DAMAGE) ? 1 : 64, result.getMaxStackSize());
            var encoded = ItemStack.CODEC.encodeStart(ops, result);
            assertTrue(encoded.error().isEmpty(), () -> trait + ": " + encoded.error());
            var decoded = ItemStack.CODEC.parse(ops, encoded.result().orElseThrow());
            assertTrue(decoded.error().isEmpty(), () -> trait + ": " + decoded.error());
            // Registry holder-set wrappers can have distinct identities after decoding. Compare serialized meaning.
            var reencoded = ItemStack.CODEC.encodeStart(ops, decoded.result().orElseThrow());
            assertEquals(encoded.result().orElseThrow(), reencoded.result().orElseThrow(), trait);
        }
    }

    @Test void modifiersPreserveNativeWeaponAttributesAndDoNotStackOnReapply() {
        var base = new ItemStack(Items.IRON_SWORD);
        var original = base.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        var output = VanillaTraits.apply(base, List.of("strong", "speedy", "low_gravity"), null);
        var modifiers = output.get(DataComponents.ATTRIBUTE_MODIFIERS);
        assertTrue(modifiers.modifiers().containsAll(original.modifiers()));
        assertEquals(original.modifiers().size() + 3, modifiers.modifiers().size());
        assertEquals(.04, modifiers.compute(Attributes.GRAVITY, .08, EquipmentSlot.MAINHAND), .00001);
        assertEquals(original, base.get(DataComponents.ATTRIBUTE_MODIFIERS));
        assertEquals(modifiers.modifiers().size(), VanillaTraits.apply(output, List.of("strong"), null)
                .get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().size());
        var inherited = ItemDataFusion.prepare(new ItemStack(Items.STICK),
                VanillaTraits.apply(base, List.of("strong"), null), new ItemStack(Items.COAL), false);
        assertEquals(1, inherited.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().size());
        var reach = VanillaTraits.apply(base, List.of("long_reach"), null);
        assertNotNull(reach.get(DataComponents.ATTACK_RANGE));
        assertEquals(reach.get(DataComponents.ATTACK_RANGE), VanillaTraits.apply(reach, List.of("long_reach"), null).get(DataComponents.ATTACK_RANGE));
    }

    @Test void equipmentUsesActualSlotAndGliderCannotReplaceBootSlot() {
        var boots = VanillaTraits.apply(new ItemStack(Items.IRON_BOOTS), List.of("bouncy", "slippery"), null);
        assertTrue(boots.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
                .filter(entry -> entry.modifier().id().getNamespace().equals("infinitecraft"))
                .allMatch(entry -> entry.slot() == EquipmentSlotGroup.FEET));
        var glider = VanillaTraits.apply(new ItemStack(Items.STICK), List.of("gliding", "tiny"), null);
        assertEquals(EquipmentSlot.CHEST, glider.get(DataComponents.EQUIPPABLE).slot());
        assertTrue(glider.has(DataComponents.GLIDER));
        assertTrue(glider.isDamageableItem());
        assertTrue(VanillaTraits.apply(new ItemStack(Items.IRON_BOOTS), List.of("gliding"), null).isEmpty());
    }

    @Test void consumingCombinesEffectsWithoutReplacingNativeFoodNutrition() {
        var apple = new ItemStack(Items.APPLE);
        var output = VanillaTraits.apply(apple, List.of("edible_healing", "edible_teleport"), null);
        assertEquals(2, output.get(DataComponents.CONSUMABLE).onConsumeEffects().size());
        assertEquals(apple.get(DataComponents.FOOD).nutrition(), output.get(DataComponents.FOOD).nutrition());
        assertTrue(output.get(DataComponents.FOOD).canAlwaysEat());
        assertEquals(0, apple.get(DataComponents.CONSUMABLE).onConsumeEffects().size());
    }

    @Test void conflictingNativeTraitComponentsAreRejected() {
        var heal = VanillaTraits.apply(new ItemStack(Items.STICK), List.of("edible_healing"), null);
        var teleport = VanillaTraits.apply(new ItemStack(Items.STICK), List.of("edible_teleport"), null);
        assertTrue(ItemDataFusion.prepare(new ItemStack(Items.DIAMOND), heal, teleport, false).isEmpty());
        var glider = VanillaTraits.apply(new ItemStack(Items.STICK), List.of("gliding"), null);
        assertTrue(ItemDataFusion.prepare(new ItemStack(Items.IRON_BOOTS), glider, new ItemStack(Items.COAL), false).isEmpty());
    }
}
