package dev.rocks.infinitecraft;

import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DiscoveryBookTest {
    private static HolderLookup.Provider lookup;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        lookup = VanillaRegistries.createLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(pending -> pending.apply());
    }

    @Test void boundBookEncodesWithHiddenVanishingAndSoulBoundHint() throws Exception {
        for (var permissions : List.of(net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS,
                net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS)) {
            var probe = net.minecraft.commands.Commands.createCompilationContext(permissions);
            assertNull(probe.getServer());
            assertFalse(InfiniteCraftMod.canControlFusion(probe));
        }
        // Loading the targets also verifies that the server-side protection mixins can apply.
        assertDoesNotThrow(() -> Class.forName("net.minecraft.server.level.ServerPlayer"));
        assertDoesNotThrow(() -> Class.forName("net.minecraft.world.inventory.AbstractContainerMenu"));
        assertDoesNotThrow(() -> Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl"));
        var output = DiscoveryBook.create(lookup);
        var ops = lookup.createSerializationContext(JsonOps.INSTANCE);
        var encoded = ItemStack.CODEC.encodeStart(ops, output).getOrThrow();
        assertTrue(ItemStack.isSameItemSameComponents(output, ItemStack.CODEC.parse(ops, encoded).getOrThrow()));
        assertTrue(DiscoveryBook.isBook(output));
        assertEquals(1, output.getMaxStackSize());
        assertEquals("Discovery Book", output.getHoverName().getString());
        assertFalse(output.getHoverName().getStyle().isItalic());
        assertEquals(0xFFAA00, output.getHoverName().getStyle().getColor().getValue());
        var hint = output.get(DataComponents.LORE).lines().getFirst();
        assertEquals("Soulbound", hint.getString());
        assertEquals(0xAAAAAA, hint.getStyle().getColor().getValue());
        assertFalse(hint.getStyle().isItalic());
        assertEquals(1, output.get(DataComponents.ENCHANTMENTS).getLevel(lookup
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.VANISHING_CURSE)));
        assertTrue(output.get(DataComponents.TOOLTIP_DISPLAY).hiddenComponents().contains(DataComponents.ENCHANTMENTS));
        assertFalse(DiscoveryBook.isBook(new ItemStack(Items.KNOWLEDGE_BOOK)));
        var craftable = DiscoveryBook.create(lookup, false);
        assertTrue(DiscoveryBook.isBook(craftable));
        assertTrue(craftable.getOrDefault(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).isEmpty());
        assertTrue(craftable.getOrDefault(DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY).lines().isEmpty());
        var rebound = craftable.copy();
        DiscoveryBook.bind(rebound, lookup);
        assertTrue(ItemStack.isSameItemSameComponents(output, rebound));
        var enchantments = rebound.get(DataComponents.ENCHANTMENTS);
        DiscoveryBook.bind(rebound, lookup);
        assertSame(enchantments, rebound.get(DataComponents.ENCHANTMENTS));
        var recipeJson = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/data/rocks_infinite_craft/recipe/discovery_book.json")));
        var recipe = (net.minecraft.world.item.crafting.ShapelessRecipe) net.minecraft.world.item.crafting.Recipe.CODEC
                .parse(ops, recipeJson).getOrThrow();
        assertTrue(ItemStack.isSameItemSameComponents(craftable, recipe.assemble(
                net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(new ItemStack(Items.BOOK), new ItemStack(Items.COBBLESTONE))))));
    }

    @Test void offhandBookIsRetainedWithoutDuplicatesAndDisabledFusionClearsBoth() {
        var equipment = new net.minecraft.world.entity.EntityEquipment();
        var inventory = new net.minecraft.world.entity.player.Inventory(null, equipment);
        var offhand = DiscoveryBook.create(lookup);
        equipment.set(net.minecraft.world.entity.EquipmentSlot.OFFHAND, offhand);
        inventory.setItem(0, DiscoveryBook.create(lookup));
        inventory.setItem(1, new ItemStack(Items.DIAMOND));
        assertTrue(DiscoveryBook.retainBook(inventory, true));
        assertSame(offhand, equipment.get(net.minecraft.world.entity.EquipmentSlot.OFFHAND));
        assertTrue(inventory.getItem(0).isEmpty());
        assertTrue(DiscoveryBook.retainBook(inventory, true));
        inventory.setItem(2, DiscoveryBook.create(lookup));
        assertFalse(DiscoveryBook.retainBook(inventory, false));
        assertTrue(equipment.get(net.minecraft.world.entity.EquipmentSlot.OFFHAND).isEmpty());
        assertTrue(inventory.getItem(2).isEmpty());
        assertTrue(inventory.getItem(1).is(Items.DIAMOND));
        var menu = net.minecraft.world.inventory.ChestMenu.oneRow(0, inventory);
        var input = net.minecraft.world.inventory.ContainerInput.PICKUP;
        int source = 9; // First player-inventory slot after the chest's nine slots.
        menu.getSlot(source).set(offhand);
        assertFalse(DiscoveryBook.blocksMove(menu, inventory, source, 0, input));
        menu.getSlot(source).set(ItemStack.EMPTY);
        menu.setCarried(offhand);
        assertTrue(DiscoveryBook.retainBook(inventory, menu));
        assertSame(offhand, menu.getCarried());
        assertTrue(menu.getSlot(source).getItem().isEmpty());
        assertFalse(DiscoveryBook.blocksMove(menu, inventory, source + 1, 0, input));
        assertTrue(DiscoveryBook.blocksMove(menu, inventory, 0, 0, input));
        assertTrue(DiscoveryBook.blocksMove(menu, inventory, -999, 0, input));
        menu.getSlot(source + 1).set(new ItemStack(Items.BUNDLE));
        assertTrue(DiscoveryBook.blocksMove(menu, inventory, source + 1, 1, input));
        assertFalse(DiscoveryBook.blocksMove(menu, inventory, source, 1,
                net.minecraft.world.inventory.ContainerInput.QUICK_CRAFT));
        assertTrue(DiscoveryBook.blocksMove(menu, inventory, 0, 1,
                net.minecraft.world.inventory.ContainerInput.QUICK_CRAFT));
        DiscoveryBook.returnCursorBook(menu, inventory);
        assertTrue(menu.getCarried().isEmpty());
        assertTrue(DiscoveryBook.retainBook(inventory, true));
        DiscoveryBook.retainBook(inventory, false);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) inventory.setItem(slot, new ItemStack(Items.STONE, 64));
        equipment.set(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.DIAMOND));
        menu.setCarried(offhand);
        DiscoveryBook.returnCursorBook(menu, inventory);
        assertTrue(menu.getCarried().isEmpty());
        assertEquals(64, inventory.getItem(0).getCount());
        assertTrue(equipment.get(net.minecraft.world.entity.EquipmentSlot.OFFHAND).is(Items.DIAMOND));
    }

    @Test void paginatedDialogEncodesActualOutputComponentsAndClampsPages() {
        var entries = new ArrayList<DiscoveryCollection.Entry>();
        for (int index = 0; index < 27; index++) {
            var output = new ItemStack(Items.DIAMOND);
            output.set(DataComponents.CUSTOM_NAME, Component.literal("Discovery " + index));
            entries.add(new DiscoveryCollection.Entry(new ItemStack(Items.STICK), new ItemStack(Items.COAL), output, "Rocks"));
        }
        var dialog = DiscoveryBook.createDialog(entries, 999);
        assertEquals("Discovery Book", dialog.common().title().getString());
        var firstPage = DiscoveryBook.createDialog(entries, 1);
        var empty = DiscoveryBook.createDialog(List.of(), 1);
        assertEquals("Discovery 26", ((ItemBody) firstPage.common().body().get(2)).item().create().getHoverName().getString());
        assertEquals(25, firstPage.common().body().stream().filter(ItemBody.class::isInstance).count());
        for (var state : List.of(firstPage, dialog, empty)) {
            assertEquals(28, state.common().body().size());
            assertTrue(state.common().inputs().isEmpty());
            assertEquals("Close", state.action().button().label().getString());
            assertEquals(net.minecraft.server.dialog.DialogAction.NONE, state.common().afterAction());
            assertTrue(state.action().action().isPresent());
            for (var row : state.common().body().subList(2, 27)) {
                if (row instanceof ItemBody item) assertEquals(53, item.height());
                else assertEquals(5, ((net.minecraft.server.dialog.body.PlainMessage) row).contents().getString().split("\n", -1).length);
            }
        }
        for (var state : List.of(firstPage, dialog, empty)) {
            var top = ((net.minecraft.server.dialog.body.PlainMessage) state.common().body().get(1)).contents();
            var bottom = ((net.minecraft.server.dialog.body.PlainMessage) state.common().body().getLast()).contents();
            assertEquals(top, bottom);
            assertEquals(state == dialog, top.getSiblings().get(0).getStyle().getClickEvent() != null);
            assertEquals(new net.minecraft.network.chat.ClickEvent.RunCommand("/fusion collection search"),
                    top.getSiblings().get(2).getStyle().getClickEvent());
            assertEquals(state == firstPage, top.getSiblings().get(4).getStyle().getClickEvent() != null);
        }
        assertTrue(((net.minecraft.server.dialog.body.PlainMessage) dialog.common().body().getFirst()).contents().getString().endsWith("Page 2 of 2"));
        assertEquals(2, dialog.common().body().stream().filter(ItemBody.class::isInstance).count());
        var preview = (ItemBody) dialog.common().body().get(2);
        assertEquals("Discovery 1", preview.item().create().getHoverName().getString());
        assertTrue(preview.showTooltip());
        var labels = preview.description().orElseThrow().contents().getSiblings();
        for (int position : new int[]{0, 2, 4}) {
            var hover = assertInstanceOf(net.minecraft.network.chat.HoverEvent.ShowItem.class,
                    labels.get(position).getStyle().getHoverEvent());
            var expected = position == 0 ? entries.get(1).result()
                    : position == 2 ? entries.get(1).first() : entries.get(1).second();
            assertTrue(ItemStack.isSameItemSameComponents(expected, hover.item().create()));
        }
        assertNull(labels.get(3).getStyle().getHoverEvent());
        assertFalse(preview.description().orElseThrow().contents().getString().contains("[Share]"));
        assertNull(labels.getLast().getStyle().getClickEvent());
        var encoded = Dialog.DIRECT_CODEC.encodeStart(lookup.createSerializationContext(JsonOps.INSTANCE), dialog);
        assertTrue(encoded.error().isEmpty(), () -> encoded.error().toString());
        var decoded = (net.minecraft.server.dialog.NoticeDialog) Dialog.DIRECT_CODEC.parse(
                lookup.createSerializationContext(JsonOps.INSTANCE), encoded.result().orElseThrow()).getOrThrow();
        var decodedNav = ((net.minecraft.server.dialog.body.PlainMessage) decoded.common().body().get(1)).contents();
        assertEquals(new net.minecraft.network.chat.ClickEvent.RunCommand("/fusion collection 1"),
                decodedNav.getSiblings().getFirst().getStyle().getClickEvent());

        assertTrue(encoded.result().orElseThrow().toString().contains("/fusion collection 1"));
        var search = DiscoveryBook.createSearchDialog();
        assertEquals("Search discoveries", search.common().title().getString());
        assertEquals("query", search.common().inputs().getFirst().key());
        assertEquals("Search", search.yesButton().button().label().getString());
        assertEquals("Back", search.noButton().button().label().getString());
        assertEquals(new net.minecraft.network.chat.ClickEvent.RunCommand("/fusion collection back 1"),
                ((net.minecraft.server.dialog.action.StaticAction) search.noButton().action().orElseThrow()).value());
        assertTrue(Dialog.DIRECT_CODEC.encodeStart(lookup.createSerializationContext(JsonOps.INSTANCE), search).isSuccess());
        assertEquals(entries, DiscoveryBook.search(entries, ""));
        assertEquals(List.of(entries.get(26)), DiscoveryBook.search(entries, "dscvry 26"));
        assertTrue(DiscoveryBook.search(entries, "missing").isEmpty());
        assertTrue(DiscoveryBook.search(entries, "dirt cake").isEmpty());
        assertEquals("Discovery Book", DiscoveryBook.createDialog(List.of(), -1).common().title().getString());
        var personalEntry = new DiscoveryCollection.Entry(new ItemStack(Items.STICK), new ItemStack(Items.COAL),
                new ItemStack(Items.TORCH), "OtherName", 42);
        var personalDialog = DiscoveryBook.createDialog(List.of(personalEntry), 1, true);
        var personalText = ((ItemBody) personalDialog.common().body().get(2)).description().orElseThrow().contents();
        assertFalse(personalText.getString().contains("By "));
        assertFalse(personalText.getString().contains("OtherName"));
        assertEquals(new net.minecraft.network.chat.ClickEvent.RunCommand("/fusion share 42"), personalText.getSiblings().getLast().getStyle().getClickEvent());
        assertNull(personalText.getSiblings().getFirst().getStyle().getClickEvent());
        assertFalse(personalText.getString().contains("[+"));
        assertEquals("Close", DiscoveryBook.createDialog(List.of(personalEntry), 1, true, 42, 1).action().button().label().getString());
        var alternative = new DiscoveryCollection.Entry(new ItemStack(Items.STICK), new ItemStack(Items.CHARCOAL),
                new ItemStack(Items.TORCH), "Friend", 43);
        var recipes = List.of(personalEntry, alternative);
        var multiple = ((ItemBody) DiscoveryBook.createDialog(recipes, 1).common().body().get(2)).description().orElseThrow().contents();
        assertTrue(multiple.getString().contains("By "));
        assertTrue(multiple.getString().contains("OtherName"));
        assertTrue(multiple.getString().contains("Friend"));
        assertTrue(multiple.getString().endsWith(" · [+1]"));
        assertEquals("Torch", multiple.getSiblings().getFirst().getString());
        assertEquals(0x55FFFF, multiple.getSiblings().getLast().getStyle().getColor().getValue());
        assertNull(multiple.getSiblings().getFirst().getStyle().getClickEvent());
        assertInstanceOf(net.minecraft.network.chat.HoverEvent.ShowItem.class,
                multiple.getSiblings().getFirst().getStyle().getHoverEvent());
        assertEquals(new net.minecraft.network.chat.ClickEvent.RunCommand("/fusion collection item 42 1 1"),
                multiple.getSiblings().getLast().getStyle().getClickEvent());
        assertEquals(1, DiscoveryBook.createDialog(recipes, 1).common().body().stream().filter(ItemBody.class::isInstance).count());
        var details = DiscoveryBook.createDialog(recipes, 1, true, 42, 2);
        assertEquals(2, details.common().body().stream().filter(ItemBody.class::isInstance).count());
        assertEquals("Back", details.action().button().label().getString());
        var detailJson = Dialog.DIRECT_CODEC.encodeStart(lookup.createSerializationContext(JsonOps.INSTANCE), details).getOrThrow();
        assertTrue(detailJson.toString().contains("/fusion collection back 2"));
        assertNotNull(Dialog.DIRECT_CODEC.parse(lookup.createSerializationContext(JsonOps.INSTANCE), detailJson).getOrThrow());
        var originated = new ItemStack(Items.TORCH);
        assertTrue(FusionOrigin.apply(originated, new ItemStack(Items.STICK), new ItemStack(Items.COAL)));
        assertFalse(DiscoveryBook.displayStack(originated).getOrDefault(DataComponents.LORE,
                net.minecraft.world.item.component.ItemLore.EMPTY).lines().stream()
                .anyMatch(line -> line.getString().startsWith("Made from ")));
        var byline = DiscoveryBook.discovererLine(List.of("Rocks", "Friend", "Third"));
        assertTrue(byline.getString().contains("Rocks"));
        assertTrue(byline.getString().contains("Friend"));
        assertTrue(byline.getString().endsWith("and 1 more"));
        var moreNames = assertInstanceOf(net.minecraft.network.chat.HoverEvent.ShowText.class,
                byline.getSiblings().getLast().getStyle().getHoverEvent());
        assertTrue(moreNames.value().getString().contains("Third"));
        var compactByline = DiscoveryBook.discovererLine(
                List.of("Only16Characters", "Only16Characters", "Third"), 1);
        assertTrue(compactByline.getString().contains("Only16Characters and 2 more"));
        assertFalse(compactByline.getString().contains(","));
    }
}
