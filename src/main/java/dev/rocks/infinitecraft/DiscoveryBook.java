package dev.rocks.infinitecraft;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.Holder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Vanilla knowledge book and dialogs; no custom item, screen, or packet registration. */
public final class DiscoveryBook {
    private static final java.util.regex.Pattern SEARCH_WORD_SEPARATOR =
            java.util.regex.Pattern.compile("[^a-z0-9]+");
    private static final String MARKER = "infinitecraft_discovery_book";
    private static final int PAGE_SIZE = 25;
    private static final int ROW_HEIGHT = 5 * 9 + 8; // Five font lines plus vanilla text-widget padding.
    private static final String EMPTY_ROW = " \n \n \n \n ";
    private static final net.minecraft.world.item.component.ItemLore SOULBOUND_LORE = new net.minecraft.world.item.component.ItemLore(List.of(
            Component.literal("Soulbound").withStyle(ChatFormatting.GRAY).withStyle(style -> style.withItalic(false))));
    private static final net.minecraft.world.item.component.TooltipDisplay SOULBOUND_TOOLTIP =
            net.minecraft.world.item.component.TooltipDisplay.DEFAULT.withHidden(DataComponents.ENCHANTMENTS, true);
    private static final java.util.Set<java.util.UUID> CREATIVE_CURSOR = new java.util.HashSet<>();
    private DiscoveryBook() {}

    public static void initialize(Function<ServerPlayer, List<DiscoveryCollection.Entry>> entries) {
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> sync(newPlayer));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (var player : server.getPlayerList().getPlayers()) {
                if (player.isAlive()) sync(player);
            }
        });
        UseItemCallback.EVENT.register((player, world, hand) -> use(player, hand, entries));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> use(player, hand, entries));
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(
                (player, world, hand, entity, hit) -> use(player, hand, entries));
    }

    private static InteractionResult use(Player player, InteractionHand hand,
                                         Function<ServerPlayer, List<DiscoveryCollection.Entry>> entries) {
        if (!isBook(player.getItemInHand(hand))) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            try {
                var discoveries = entries.apply(serverPlayer);
                if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
                        serverPlayer, DiscoveryScreenPayload.TYPE))
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer,
                            new DiscoveryScreenPayload(discoveries, InfiniteCraftMod.personalBook()));
                else show(serverPlayer, discoveries, 1);
            } catch (RuntimeException error) {
                InfiniteCraftMod.LOGGER.warn("Could not open discovery collection", error);
                serverPlayer.sendSystemMessage(Component.literal("Could not open collection."));
            } finally {
                // An unmodified client predicts knowledge-book consumption. Restore the authoritative inventory.
                serverPlayer.inventoryMenu.sendAllDataToRemote();
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean isBook(ItemStack stack) {
        return stack.is(Items.KNOWLEDGE_BOOK) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBooleanOr(MARKER, false);
    }

    public static ItemStack create(net.minecraft.core.HolderLookup.Provider registries) {
        return create(registries, true);
    }

    public static ItemStack create(net.minecraft.core.HolderLookup.Provider registries, boolean soulbound) {
        ItemStack stack = new ItemStack(Items.KNOWLEDGE_BOOK);
        var marker = new CompoundTag();
        marker.putBoolean(MARKER, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(
                List.of(), List.of(), List.of("rocks_discovery_book"), List.of()));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Discovery Book").withStyle(style -> style.withColor(0xFFAA00).withItalic(false)));
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        if (!soulbound) return stack;
        var enchantments = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        enchantments.set(registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.VANISHING_CURSE), 1);
        stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        stack.set(DataComponents.TOOLTIP_DISPLAY, SOULBOUND_TOOLTIP);
        stack.set(DataComponents.LORE, SOULBOUND_LORE);
        return stack;
    }

    /** Return false when inventory space is unavailable; do not silently drop the book. */
    public static boolean give(ServerPlayer player) {
        if (!InfiniteCraftMod.fusionEnabled(player)) return false;
        if (!InfiniteCraftMod.soulboundBook()) {
            boolean added = player.getInventory().add(create(player.registryAccess(), false));
            if (added) player.inventoryMenu.sendAllDataToRemote();
            return added;
        }
        if (CREATIVE_CURSOR.contains(player.getUUID())) return true;
        if (retainBook(player.getInventory(), player.containerMenu)) {
            player.inventoryMenu.broadcastChanges();
            return true;
        }
        boolean added = player.getInventory().add(create(player.registryAccess(), InfiniteCraftMod.soulboundBook()));
        if (added) player.inventoryMenu.sendAllDataToRemote();
        return added;
    }

    /** Creative clients send changed slots without transmitting their cursor stack. */
    public static void creativeSlotChanged(ServerPlayer player, ItemStack previous, ItemStack current) {
        if (!InfiniteCraftMod.soulboundBook()) return;
        if (isBook(current) || (isBook(previous) && retainBook(player.getInventory(), true)))
            CREATIVE_CURSOR.remove(player.getUUID());
        else if (isBook(previous)) CREATIVE_CURSOR.add(player.getUUID());
    }

    public static boolean forgetCreativeCursor(java.util.UUID player) {
        return CREATIVE_CURSOR.remove(player);
    }

    public static void sync(ServerPlayer player) {
        if (InfiniteCraftMod.soulboundBook() && InfiniteCraftMod.fusionEnabled(player)) {
            java.util.stream.IntStream.concat(Inventory.EQUIPMENT_SLOT_MAPPING.keySet().intStream(),
                    java.util.stream.IntStream.range(0, player.getInventory().getContainerSize())).distinct()
                    .forEach(slot -> bind(player.getInventory().getItem(slot), player.registryAccess()));
            bind(player.containerMenu.getCarried(), player.registryAccess());
        }
        if (!InfiniteCraftMod.soulboundBook()) {
            forgetCreativeCursor(player.getUUID());
            var slots = java.util.stream.IntStream.concat(Inventory.EQUIPMENT_SLOT_MAPPING.keySet().intStream(),
                    java.util.stream.IntStream.range(0, player.getInventory().getContainerSize())).distinct();
            slots.forEach(slot -> unbind(player.getInventory().getItem(slot)));
            unbind(player.containerMenu.getCarried());
            player.containerMenu.broadcastChanges();
            return;
        }
        if (InfiniteCraftMod.fusionEnabled(player)) give(player);
        else remove(player);
    }

    private static void unbind(ItemStack stack) {
        if (!isBook(stack)) return;
        stack.remove(DataComponents.ENCHANTMENTS);
        stack.remove(DataComponents.TOOLTIP_DISPLAY);
        stack.remove(DataComponents.LORE);
    }

    static void bind(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
        if (!isBook(stack)) return;
        var enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        var curse = registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.VANISHING_CURSE);
        if (enchantments.size() == 1 && enchantments.getLevel(curse) == 1
                && SOULBOUND_LORE.equals(stack.get(DataComponents.LORE))
                && SOULBOUND_TOOLTIP.equals(stack.get(DataComponents.TOOLTIP_DISPLAY))) return;
        var binding = create(registries);
        stack.set(DataComponents.ENCHANTMENTS, binding.get(DataComponents.ENCHANTMENTS));
        stack.set(DataComponents.LORE, binding.get(DataComponents.LORE));
        stack.set(DataComponents.TOOLTIP_DISPLAY, binding.get(DataComponents.TOOLTIP_DISPLAY));
    }

    static boolean retainBook(Inventory inventory, AbstractContainerMenu menu) {
        if (isBook(menu.getCarried())) {
            menu.getCarried().setCount(1);
            retainBook(inventory, false);
            return true;
        }
        return retainBook(inventory, true);
    }

    /** Let vanilla perform inventory moves, but not transfers out of the player's inventory. */
    public static boolean blocksMove(AbstractContainerMenu menu, Inventory inventory,
                                     int slotId, int button, ContainerInput input) {
        if (!InfiniteCraftMod.soulboundBook()) return false;
        var slot = slotId >= 0 && slotId < menu.slots.size() ? menu.slots.get(slotId) : null;
        var target = slot == null ? ItemStack.EMPTY : slot.getItem();
        var carried = menu.getCarried();
        boolean sourceBook = isBook(target);
        boolean cursorBook = isBook(carried);
        boolean swappedBook = input == ContainerInput.SWAP && button >= 0
                && (button < inventory.getContainerSize() || Inventory.EQUIPMENT_SLOT_MAPPING.containsKey(button))
                && isBook(inventory.getItem(button));
        if (!sourceBook && !cursorBook && !swappedBook) return false;
        boolean inventorySlot = slot != null && slot.container == inventory;
        return switch (input) {
            case PICKUP -> !inventorySlot || target.has(DataComponents.BUNDLE_CONTENTS)
                    || carried.has(DataComponents.BUNDLE_CONTENTS);
            case SWAP -> !inventorySlot || cursorBook;
            case QUICK_CRAFT -> !cursorBook || (AbstractContainerMenu.getQuickcraftHeader(button) == 1
                    && (!inventorySlot || target.has(DataComponents.BUNDLE_CONTENTS)));
            default -> true;
        };
    }

    /** Never let vanilla's full-inventory fallback drop a cursor-held book. */
    public static void returnCursorBook(AbstractContainerMenu menu, Inventory inventory) {
        if (!InfiniteCraftMod.soulboundBook()) return;
        var book = menu.getCarried();
        if (!isBook(book)) return;
        menu.setCarried(ItemStack.EMPTY);
        if (retainBook(inventory, true)) return;
        int slot = inventory.getFreeSlot();
        if (slot < 0 && inventory.getItem(Inventory.SLOT_OFFHAND).isEmpty()) slot = Inventory.SLOT_OFFHAND;
        if (slot >= 0) inventory.setItem(slot, book.copyWithCount(1));
        // If all slots filled while moving it, the normal delivery check restores it when space opens.
    }

    /** Equipment slots have separate indices outside getContainerSize() in 26.2. */
    static boolean retainBook(net.minecraft.world.entity.player.Inventory inventory, boolean keepOne) {
        boolean found = false;
        var slots = java.util.stream.IntStream.concat(
                net.minecraft.world.entity.player.Inventory.EQUIPMENT_SLOT_MAPPING.keySet().intStream(),
                java.util.stream.IntStream.range(0, inventory.getContainerSize())).distinct().toArray();
        for (int slot : slots) {
            var stack = inventory.getItem(slot);
            if (!isBook(stack)) continue;
            if (!keepOne || found) inventory.setItem(slot, ItemStack.EMPTY);
            else {
                stack.setCount(1);
                found = true;
            }
        }
        return found;
    }

    /** Also clear equipment and cursor copies when disabling fusion or dying with keepInventory. */
    public static void remove(ServerPlayer player) {
        forgetCreativeCursor(player.getUUID());
        retainBook(player.getInventory(), false);
        if (isBook(player.containerMenu.getCarried())) player.containerMenu.setCarried(ItemStack.EMPTY);
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }

    /** Pages are one-based. Clamp stale page requests if the collection changed since the last view. */
    public static void show(ServerPlayer player, List<DiscoveryCollection.Entry> entries, int page) {
        player.openDialog(Holder.direct(createDialog(entries, page, InfiniteCraftMod.personalBook())));
    }

    static net.minecraft.server.dialog.NoticeDialog createDialog(List<DiscoveryCollection.Entry> entries, int requestedPage) {
        return createDialog(entries, requestedPage, false);
    }

    static net.minecraft.server.dialog.NoticeDialog createDialog(List<DiscoveryCollection.Entry> entries, int requestedPage, boolean personal) {
        return createDialog(entries, requestedPage, personal, 0, 1);
    }

    static net.minecraft.server.dialog.NoticeDialog createDialog(List<DiscoveryCollection.Entry> entries, int requestedPage,
            boolean personal, int itemId, int returnPage) {
        return createDialog(entries, requestedPage, personal, itemId, returnPage, null);
    }

    static net.minecraft.server.dialog.NoticeDialog createDialog(List<DiscoveryCollection.Entry> entries, int requestedPage,
            boolean personal, int itemId, int returnPage, String pageCommandOverride) {
        var groups = DiscoveryCollection.groupResults(entries);
        var selected = itemId > 0 ? entries.stream().filter(entry -> entry.id() == itemId).findFirst()
                : Optional.<DiscoveryCollection.Entry>empty();
        var alternatives = selected.map(entry -> groups.get(new DiscoveryCollection.ResultKey(entry.result()))).orElse(List.of());
        boolean detail = itemId > 0 && alternatives.size() > 1;
        entries = detail ? alternatives : groups.values().stream().map(List::getFirst).toList();
        entries = entries.reversed();
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        int start = (page - 1) * PAGE_SIZE;
        List<DialogBody> body = new ArrayList<>();
        String pageCommand = detail ? "/fusion collection item " + itemId + " " + returnPage + " "
                : pageCommandOverride == null ? "/fusion collection " : pageCommandOverride;
        body.add(new PlainMessage(Component.literal(entries.size() + (detail ? (entries.size() == 1 ? " recipe" : " recipes") : (entries.size() == 1 ? " discovery" : " discoveries"))
                + "  ·  Page " + page + " of " + pages).withStyle(ChatFormatting.GRAY), 320));
        var navigation = new PlainMessage(Component.empty()
                .append(pageArrow("<", "Previous page", pageCommand + (page - 1), page > 1))
                .append(Component.literal("   "))
                .append(searchButton())
                .append(Component.literal("   "))
                .append(pageArrow(">", "Next page", pageCommand + (page + 1), page < pages)), 320);
        body.add(navigation);
        for (int index = start; index < Math.min(entries.size(), start + PAGE_SIZE); index++) {
            var entry = entries.get(index);
            int discoveryId = entry.id() > 0 ? entry.id() : entries.size() - index;
            var resultRecipes = groups.get(new DiscoveryCollection.ResultKey(entry.result()));
            int recipeCount = resultRecipes.size();
            var name = itemName(entry.result(), false).copy();
            var description = Component.empty()
                    .append(name)
                    .append(Component.literal("\n\n"))
                    .append(itemName(entry.first(), true))
                    .append(Component.literal(" + ").withStyle(ChatFormatting.GRAY))
                    .append(itemName(entry.second(), true));
            if (!personal) description.append(Component.literal("\n\n"))
                    .append(discovererLine(DiscoveryCollection.discoverers(detail ? List.of(entry) : resultRecipes)));
            else description.append(Component.literal("\n\n"));
            if (!detail && recipeCount > 1) {
                if (!personal) description.append(Component.literal(" · ").withStyle(ChatFormatting.GRAY));
                description.append(Component.literal("[+" + (recipeCount - 1) + "]").withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.RunCommand("/fusion collection item " + discoveryId + " " + page + " 1"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("View recipes")))));
                if (personal) description.append(Component.literal(" · ").withStyle(ChatFormatting.GRAY));
            }
            if (personal) description.append(Component.literal("[Share]").withStyle(style -> style.withColor(ChatFormatting.GOLD)
                            .withClickEvent(new ClickEvent.RunCommand("/fusion share " + discoveryId))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Share recipe in chat")))));
            body.add(new ItemBody(ItemStackTemplate.fromNonEmptyStack(displayStack(entry.result())),
                    Optional.of(new PlainMessage(description, 280)), false, true, 32, ROW_HEIGHT));
        }
        // Reserve every row so short pages keep the same viewport and navigation positions.
        while (body.size() < PAGE_SIZE + 2) {
            String text = entries.isEmpty() && body.size() == 2
                    ? "No discoveries yet." + "\n \n \n \n " : EMPTY_ROW;
            body.add(new PlainMessage(Component.literal(text).withStyle(ChatFormatting.GRAY), 320));
        }
        body.add(navigation);
        var common = new CommonDialogData(Component.literal("Discovery Book").withStyle(style -> style.withColor(0xFFAA00).withItalic(false)),
                Optional.empty(), true, false, DialogAction.NONE, List.copyOf(body), List.of());
        var close = new ActionButton(new CommonButtonData(Component.literal(detail ? "Back" : "Close"), 150),
                Optional.of(new StaticAction(new ClickEvent.RunCommand(detail ? "/fusion collection back " + returnPage : "/fusion collection close"))));
        return new net.minecraft.server.dialog.NoticeDialog(common, close);
    }

    static net.minecraft.server.dialog.ConfirmationDialog createSearchDialog() {
        var common = new CommonDialogData(Component.literal("Search discoveries")
                .withStyle(style -> style.withColor(0xFFAA00).withItalic(false)), Optional.empty(), true, false,
                DialogAction.NONE, List.of(), List.of(new Input("query",
                        new TextInput(280, Component.literal("Search"), false, "", 80, Optional.empty()))));
        var template = ParsedTemplate.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                new com.google.gson.JsonPrimitive("/fusion collection query $(query)")).getOrThrow();
        var search = new ActionButton(new CommonButtonData(Component.literal("Search"), 150),
                Optional.of(new CommandTemplate(template)));
        var back = new ActionButton(new CommonButtonData(Component.literal("Back"), 150),
                Optional.of(new StaticAction(new ClickEvent.RunCommand("/fusion collection back 1"))));
        return new net.minecraft.server.dialog.ConfirmationDialog(common, search, back);
    }

    public static List<DiscoveryCollection.Entry> search(List<DiscoveryCollection.Entry> entries, String query) {
        var terms = java.util.Arrays.stream(query.toLowerCase(java.util.Locale.ROOT).trim().split("\\s+"))
                .filter(term -> !term.isEmpty()).toList();
        if (terms.isEmpty()) return entries;
        return entries.stream().filter(entry -> {
            var fields = List.of(entry.result().getHoverName().getString(), entry.first().getHoverName().getString(),
                    entry.second().getHoverName().getString(), entry.discoverer(), entry.result().getItem().toString(),
                    entry.first().getItem().toString(), entry.second().getItem().toString()).stream()
                    .map(text -> text.toLowerCase(java.util.Locale.ROOT)).toList();
            var words = fields.stream().flatMap(field -> java.util.Arrays.stream(SEARCH_WORD_SEPARATOR.split(field))).toList();
            return terms.stream().allMatch(term -> fields.stream().anyMatch(field -> field.contains(term))
                    || term.length() >= 4 && words.stream().anyMatch(word -> fuzzyMatch(word, term)));
        }).toList();
    }

    private static boolean fuzzyMatch(String word, String query) {
        int tolerance = Math.max(1, Math.max(word.length(), query.length()) / 3);
        if (Math.abs(word.length() - query.length()) > tolerance) return false;
        int[] previous = java.util.stream.IntStream.rangeClosed(0, query.length()).toArray();
        for (int row = 1; row <= word.length(); row++) {
            int[] current = new int[query.length() + 1];
            current[0] = row;
            for (int column = 1; column <= query.length(); column++) current[column] = Math.min(
                    Math.min(current[column - 1] + 1, previous[column] + 1),
                    previous[column - 1] + (word.charAt(row - 1) == query.charAt(column - 1) ? 0 : 1));
            previous = current;
        }
        return previous[query.length()] <= tolerance;
    }

    private static Component searchButton() {
        return Component.literal("[ ")
                .append(Component.literal("🔎").withStyle(style -> style.withColor(ChatFormatting.AQUA)))
                .append(Component.literal(" ]"))
                .withStyle(style -> style.withColor(ChatFormatting.GRAY)
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Search discoveries")))
                        .withClickEvent(new ClickEvent.RunCommand("/fusion collection search")));
    }

    private static Component pageArrow(String glyph, String tooltip, String command, boolean available) {
        return Component.literal("[ ")
                .append(Component.literal(glyph).withStyle(style -> style.withBold(true)
                        .withColor(available ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)))
                .append(Component.literal(" ]"))
                .withStyle(style -> style
                .withColor(available ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(tooltip)))
                .withClickEvent(available ? new ClickEvent.RunCommand(command) : null));
    }

    private static Component itemName(ItemStack stack, boolean ingredient) {
        var name = stack.getHoverName();
        var label = ingredient
                ? Component.literal(shortText(name.getString(), 20)).withStyle(ChatFormatting.GRAY)
                : name.getString().length() > 32
                        ? Component.literal(shortText(name.getString(), 32)).setStyle(name.getStyle()) : name.copy();
        return label.withStyle(style -> style.withHoverEvent(
                new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(displayStack(stack)))));
    }

    public static ItemStack displayStack(ItemStack stack) {
        return FusionOrigin.strip(stack);
    }

    public static Component discovererLine(List<String> names) {
        return discovererLine(names, 2);
    }

    public static Component discovererLine(List<String> names, int visibleNames) {
        if (names.isEmpty()) return Component.empty();
        var line = Component.empty()
                .append(Component.literal("By ").withStyle(ChatFormatting.DARK_GRAY));
        int shown = Math.min(Math.max(1, visibleNames), names.size());
        for (int index = 0; index < shown; index++) {
            if (index > 0) line.append(Component.literal(names.size() == 2 ? " and " : ", ")
                    .withStyle(ChatFormatting.DARK_GRAY));
            String name = names.get(index);
            line.append(Component.object(new net.minecraft.network.chat.contents.objects.PlayerSprite(
                    net.minecraft.world.item.component.ResolvableProfile.createUnresolved(name), true), Component.empty()))
                    .append(Component.literal(" " + name).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (names.size() > shown) {
            var more = Component.empty();
            for (int index = shown; index < names.size(); index++) {
                if (index > shown) more.append("\n");
                String name = names.get(index);
                more.append(Component.object(new net.minecraft.network.chat.contents.objects.PlayerSprite(
                        net.minecraft.world.item.component.ResolvableProfile.createUnresolved(name), true), Component.empty()))
                        .append(Component.literal(" " + name).withStyle(ChatFormatting.GRAY));
            }
            int remaining = names.size() - shown;
            line.append(Component.literal(" and " + remaining + " more").withStyle(style -> style
                    .withColor(ChatFormatting.AQUA).withHoverEvent(new HoverEvent.ShowText(more))));
        }
        return line;
    }

    private static String shortText(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit - 1) + "…";
    }
}
