package dev.rocks.infinitecraft.discovery;

import dev.rocks.infinitecraft.InfiniteCraftMod;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ConfirmationDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;

/** Vanilla knowledge book and dialogs; no custom item, screen, or packet registration. */
public final class DiscoveryBook {
    private static final String MARKER = "infinitecraft_discovery_book";
    private static final ItemLore SOULBOUND_LORE = new ItemLore(List.of(
            Component.literal("Soulbound").withStyle(ChatFormatting.GRAY).withStyle(style -> style.withItalic(false))));
    private static final TooltipDisplay SOULBOUND_TOOLTIP =
            TooltipDisplay.DEFAULT.withHidden(DataComponents.ENCHANTMENTS, true);
    private static final Set<UUID> CREATIVE_CURSOR = new HashSet<>();

    private DiscoveryBook() {
    }

    public static void initialize(Function<ServerPlayer, List<DiscoveryCollection.Entry>> entries) {
        ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> sync(newPlayer));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (var player : server.getPlayerList().getPlayers()) {
                if (player.isAlive()) sync(player);
            }
        });
        UseItemCallback.EVENT.register((player, world, hand) -> use(player, hand, entries));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> use(player, hand, entries));
        UseEntityCallback.EVENT.register(
                (player, world, hand, entity, hit) -> use(player, hand, entries));
    }

    private static InteractionResult use(Player player, InteractionHand hand,
                                         Function<ServerPlayer, List<DiscoveryCollection.Entry>> entries) {
        if (!isBook(player.getItemInHand(hand))) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            try {
                var discoveries = entries.apply(serverPlayer);
                if (ServerPlayNetworking.canSend(serverPlayer, DiscoveryScreenPayload.TYPE))
                    ServerPlayNetworking.send(serverPlayer,
                            new DiscoveryScreenPayload(discoveries, InfiniteCraftMod.personalBook(), InfiniteCraftMod.runtime().favoriteIds(serverPlayer)));
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

    public static ItemStack create(HolderLookup.Provider registries) {
        return create(registries, true);
    }

    public static ItemStack create(HolderLookup.Provider registries, boolean soulbound) {
        ItemStack stack = new ItemStack(Items.KNOWLEDGE_BOOK);
        var marker = new CompoundTag();
        marker.putBoolean(MARKER, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                List.of(), List.of(), List.of("rocks_discovery_book"), List.of()));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Discovery Book").withStyle(style -> style.withColor(0xFFAA00).withItalic(false)));
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        if (!soulbound) return stack;
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(registries.lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.VANISHING_CURSE), 1);
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

    public static boolean forgetCreativeCursor(UUID player) {
        return CREATIVE_CURSOR.remove(player);
    }

    public static void sync(ServerPlayer player) {
        if (InfiniteCraftMod.soulboundBook() && InfiniteCraftMod.fusionEnabled(player)) {
            IntStream.concat(Inventory.EQUIPMENT_SLOT_MAPPING.keySet().intStream(),
                    IntStream.range(0, player.getInventory().getContainerSize())).distinct()
                    .forEach(slot -> bind(player.getInventory().getItem(slot), player.registryAccess()));
            bind(player.containerMenu.getCarried(), player.registryAccess());
        }
        if (!InfiniteCraftMod.soulboundBook()) {
            forgetCreativeCursor(player.getUUID());
            var slots = IntStream.concat(Inventory.EQUIPMENT_SLOT_MAPPING.keySet().intStream(),
                    IntStream.range(0, player.getInventory().getContainerSize())).distinct();
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

    public static void bind(ItemStack stack, HolderLookup.Provider registries) {
        if (!isBook(stack)) return;
        var enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        var curse = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.VANISHING_CURSE);
        if (enchantments.size() == 1 && enchantments.getLevel(curse) == 1
                && SOULBOUND_LORE.equals(stack.get(DataComponents.LORE))
                && SOULBOUND_TOOLTIP.equals(stack.get(DataComponents.TOOLTIP_DISPLAY))) return;
        var binding = create(registries);
        stack.set(DataComponents.ENCHANTMENTS, binding.get(DataComponents.ENCHANTMENTS));
        stack.set(DataComponents.LORE, binding.get(DataComponents.LORE));
        stack.set(DataComponents.TOOLTIP_DISPLAY, binding.get(DataComponents.TOOLTIP_DISPLAY));
    }

    public static boolean retainBook(Inventory inventory, AbstractContainerMenu menu) {
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
    public static boolean retainBook(Inventory inventory, boolean keepOne) {
        boolean found = false;
        var slots = IntStream.concat(Inventory.EQUIPMENT_SLOT_MAPPING.keySet().intStream(),
                IntStream.range(0, inventory.getContainerSize())).distinct().toArray();
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
        player.openDialog(Holder.direct(DiscoveryDialogs.createDialog(entries, page, InfiniteCraftMod.personalBook(),
                0, 1, null, InfiniteCraftMod.runtime().favoriteIds(player), false)));
    }

    public static NoticeDialog createDialog(
            List<DiscoveryCollection.Entry> entries, int requestedPage) {
        return DiscoveryDialogs.createDialog(entries, requestedPage);
    }

    public static NoticeDialog createDialog(
            List<DiscoveryCollection.Entry> entries, int requestedPage, boolean personal) {
        return DiscoveryDialogs.createDialog(entries, requestedPage, personal);
    }

    public static NoticeDialog createDialog(List<DiscoveryCollection.Entry> entries,
            int requestedPage, boolean personal, int itemId, int returnPage) {
        return DiscoveryDialogs.createDialog(entries, requestedPage, personal, itemId, returnPage);
    }

    public static NoticeDialog createDialog(List<DiscoveryCollection.Entry> entries,
            int requestedPage, boolean personal, int itemId, int returnPage, String pageCommandOverride) {
        return DiscoveryDialogs.createDialog(entries, requestedPage, personal, itemId, returnPage, pageCommandOverride);
    }

    public static ConfirmationDialog createSearchDialog() {
        return DiscoveryDialogs.createSearchDialog();
    }

    public static List<DiscoveryCollection.Entry> search(List<DiscoveryCollection.Entry> entries, String query) {
        return DiscoveryDialogs.search(entries, query);
    }

    public static ItemStack displayStack(ItemStack stack) {
        return DiscoveryDialogs.displayStack(stack);
    }

    public static Component discovererLine(List<String> names) {
        return DiscoveryDialogs.discovererLine(names);
    }

    public static Component discovererLine(List<String> names, int visibleNames) {
        return DiscoveryDialogs.discovererLine(names, visibleNames);
    }

}
