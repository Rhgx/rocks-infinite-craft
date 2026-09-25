package dev.rocks.infinitecraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.rocks.infinitecraft.InfiniteCraftMod;
import dev.rocks.infinitecraft.ModConfig;
import dev.rocks.infinitecraft.discovery.DiscoveryBook;
import dev.rocks.infinitecraft.discovery.DiscoveryDialogs;
import dev.rocks.infinitecraft.fusion.FusionRuntime;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.IntStream;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class FusionCommands {
    private static final Map<UUID, String> discoverySearches = new HashMap<>();

    private FusionCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("fusion")
                .executes(context -> status(context.getSource()))
                .then(recipeCommands())
                .then(TraitTestCommand.create())
                .then(CrafterCommand.create())
                .then(favoriteCommand())
                .then(shareCommand())
                .then(bookCommand())
                .then(collectionCommand())
                .then(literal("enable").requires(FusionCommands::canControlFusion).executes(context -> setFusion(context, true)))
                .then(literal("disable").requires(FusionCommands::canControlFusion).executes(context -> setFusion(context, false)))
                .then(literal("toggle").requires(FusionCommands::canControlFusion).executes(context ->
                        setFusion(context, runtime() != null && !runtime().enabled())))
                .then(catalogCommand())
                .then(reloadCommand()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shareCommand() {
        return literal("share").then(argument("discovery", IntegerArgumentType.integer(1)).executes(context ->
                runtime() == null ? 0 : runtime().shareRecipe(context.getSource().getPlayerOrException(),
                        IntegerArgumentType.getInteger(context, "discovery"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> bookCommand() {
        return literal("book").executes(context -> giveBook(context.getSource()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> collectionCommand() {
        return literal("collection")
                .then(literal("favorites").executes(context -> favoritePage(context, 1))
                        .then(pageArgument("page", FusionCommands::favoritePage)))
                .executes(context -> collection(context, 1))
                .then(literal("search").executes(FusionCommands::openSearch))
                .then(literal("query")
                        .executes(context -> search(context, ""))
                        .then(argument("query", StringArgumentType.greedyString()).executes(context ->
                                search(context, StringArgumentType.getString(context, "query")))))
                .then(literal("results").then(pageArgument("page", FusionCommands::searchResults)))
                .then(literal("back").then(pageArgument("page", (context, page) -> collection(context, page))))
                .then(literal("item").then(argument("item", IntegerArgumentType.integer(1))
                        .then(argument("returnPage", IntegerArgumentType.integer(1))
                                .then(argument("recipePage", IntegerArgumentType.integer(1)).executes(context ->
                                        collection(context, page(context, "recipePage"), page(context, "item"),
                                                page(context, "returnPage")))))))
                .then(literal("close").executes(FusionCommands::closeCollection))
                .then(pageArgument("page", (context, page) -> collection(context, page)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> favoriteCommand() {
        var id = argument("discovery", IntegerArgumentType.integer(1))
                .executes(context -> toggleFavorite(context, false, 0));
        id.then(literal("page").then(pageArgument("page", (context, page) -> toggleFavorite(context, false, page))));
        id.then(literal("favorites").then(pageArgument("page", (context, page) -> toggleFavorite(context, true, page))));
        return literal("favorite").then(id);
    }

    private static int toggleFavorite(CommandContext<CommandSourceStack> context, boolean favoritesOnly, int page) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        if (runtime() == null || !canOpenCollection(player)
                || !runtime().toggleFavorite(player, IntegerArgumentType.getInteger(context, "discovery"))) return 0;
        if (page > 0) return favoritesOnly ? favoritePage(context, page) : collection(context, page);
        return 1;
    }

    private static int favoritePage(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        if (runtime() == null || !canOpenCollection(player)) return 0;
        player.openDialog(Holder.direct(DiscoveryDialogs.createDialog(
                runtime().discoveries(player), page, runtime().personalBook(), 0, 1,
                "/fusion collection favorites ", runtime().favoriteIds(player), true)));
        playBookClick(player);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> catalogCommand() {
        return literal("catalog")
                .executes(context -> showCatalog(context.getSource(), ""))
                .then(argument("query", StringArgumentType.greedyString()).executes(context ->
                        showCatalog(context.getSource(), StringArgumentType.getString(context, "query"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reloadCommand() {
        return literal("reload")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> reload(context.getSource()));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> pageArgument(String name, PageCommand command) {
        return argument(name, IntegerArgumentType.integer(1)).executes(context -> command.run(context, page(context, name)));
    }

    private static int page(CommandContext<CommandSourceStack> context, String name) {
        return IntegerArgumentType.getInteger(context, name);
    }

    private static int giveBook(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!InfiniteCraftMod.soulboundBook() && !canControlFusion(source)) {
            source.sendFailure(Component.literal("Craft a Discovery Book."));
            return 0;
        }
        if (!InfiniteCraftMod.fusionEnabled(player)) {
            source.sendFailure(Component.literal("Enable fusion first."));
            return 0;
        }
        if (!DiscoveryBook.give(player)) {
            source.sendFailure(Component.literal("Inventory full."));
            return 0;
        }
        return 1;
    }

    private static int closeCollection(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        context.getSource().getPlayerOrException().connection.send(ClientboundClearDialogPacket.INSTANCE);
        return 1;
    }

    private static int showCatalog(CommandSourceStack source, String query) {
        if (runtime() == null) return status(source);
        source.sendSuccess(() -> Component.literal(runtime().catalogSearch(query)), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        try {
            ModConfig config = ModConfig.load(FabricLoader.getInstance().getConfigDir().resolve("infinitecraft.json"));
            if (runtime() == null) InfiniteCraftMod.replaceRuntime(new FusionRuntime(source.getServer(), config));
            else runtime().reconfigure(config);
            source.sendSuccess(() -> Component.literal("Configuration, catalog and recipes reloaded."), false);
            return 1;
        } catch (Exception error) {
            InfiniteCraftMod.LOGGER.error("Manual catalog reload rejected", error);
            source.sendFailure(Component.literal("Reload rejected; old catalog retained. See server log."));
            return 0;
        }
    }

    @FunctionalInterface
    private interface PageCommand {
        int run(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException;
    }

    public static void disconnect(UUID player) {
        discoverySearches.remove(player);
    }

    private static FusionRuntime runtime() {
        return InfiniteCraftMod.runtime();
    }

    public static boolean canControlFusion(CommandSourceStack source) {
        // Command-tree serialization probes requirements without a live server or player.
        if (source.getServer() == null) return false;
        if (source.getServer().isSingleplayer()) {
            var player = source.getPlayer();
            return player != null && source.getServer().isSingleplayerOwner(
                    new NameAndId(player.getUUID(), player.getName().getString()));
        }
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
    }

    private static int setFusion(CommandContext<CommandSourceStack> context, boolean enabled) {
        if (runtime() == null) return status(context.getSource());
        try {
            runtime().setEnabled(enabled);
        } catch (IOException error) {
            InfiniteCraftMod.LOGGER.error("Could not save the world's fusion toggle", error);
            context.getSource().sendFailure(Component.literal("Could not save fusion setting. See server log."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Item fusion " + (runtime().enabled() ? "enabled" : "disabled")), false);
        return 1;
    }

    private static int collection(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException {
        return collection(context, page, 0, 1);
    }

    private static int collection(CommandContext<CommandSourceStack> context, int page, int itemId, int returnPage) throws CommandSyntaxException {
        if (runtime() == null) return status(context.getSource());
        var player = context.getSource().getPlayerOrException();
        if (!canOpenCollection(player)) {
            context.getSource().sendFailure(Component.literal("Craft a Discovery Book."));
            return 0;
        }
        var entries = runtime().discoveries(player);
        if (itemId > 0) {
            var selected = entries.stream().filter(entry -> entry.id() == itemId).findFirst();
            if (selected.isEmpty() || entries.stream().filter(entry -> ItemStack.isSameItemSameComponents(
                    entry.result(), selected.orElseThrow().result())).count() < 2) return 0;
        }
        player.openDialog(Holder.direct(DiscoveryDialogs.createDialog(
                entries, page, InfiniteCraftMod.personalBook(), itemId, returnPage, null, runtime().favoriteIds(player), false)));
        // Send only to the reader so browsing never makes sounds for nearby players.
        if (context.getNodes().stream().noneMatch(node -> node.getNode().getName().equals("back"))) playBookClick(player);
        return 1;
    }

    private static boolean canOpenCollection(ServerPlayer player) {
        return InfiniteCraftMod.soulboundBook() || DiscoveryBook.isBook(player.containerMenu.getCarried())
                || IntStream.concat(Inventory.EQUIPMENT_SLOT_MAPPING.keySet().intStream(),
                        IntStream.range(0, player.getInventory().getContainerSize()))
                        .anyMatch(slot -> DiscoveryBook.isBook(player.getInventory().getItem(slot)));
    }

    private static int openSearch(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        if (!canOpenCollection(player)) return 0;
        player.openDialog(Holder.direct(DiscoveryBook.createSearchDialog()));
        return 1;
    }

    private static int search(CommandContext<CommandSourceStack> context, String query) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        if (!canOpenCollection(player)) return 0;
        discoverySearches.put(player.getUUID(), query.strip());
        return searchResults(context, 1, false);
    }

    private static int searchResults(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException {
        return searchResults(context, page, true);
    }

    private static int searchResults(CommandContext<CommandSourceStack> context, int page, boolean playSound) throws CommandSyntaxException {
        if (runtime() == null) return status(context.getSource());
        var player = context.getSource().getPlayerOrException();
        if (!canOpenCollection(player)) return 0;
        String query = discoverySearches.getOrDefault(player.getUUID(), "");
        var entries = DiscoveryBook.search(runtime().discoveries(player), query);
        player.openDialog(Holder.direct(DiscoveryDialogs.createDialog(entries, page,
                InfiniteCraftMod.personalBook(), 0, 1, "/fusion collection results ", runtime().favoriteIds(player), false)));
        if (playSound) playBookClick(player);
        return 1;
    }

    private static void playBookClick(ServerPlayer player) {
        player.connection.send(new ClientboundSoundPacket(
                SoundEvents.UI_BUTTON_CLICK, SoundSource.MASTER,
                player.getX(), player.getEyeY(), player.getZ(), 0.3F, 1.0F, player.getRandom().nextLong()));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(runtime() == null
                ? "Infinite Craft is disabled. See the server log for initialization errors."
                : runtime().status()), false);
        return runtime() == null ? 0 : 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> recipeCommands() {
        var recipes = literal("recipe").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        for (String action : new String[] {"inspect", "forget", "set"}) {
            var second = argument("second", IdentifierArgument.id());
            if (action.equals("set")) second.then(argument("output", IdentifierArgument.id())
                    .executes(context -> recipeCommand(context, action, 1))
                    .then(argument("count", IntegerArgumentType.integer(1, 64))
                            .executes(context -> recipeCommand(context, action, IntegerArgumentType.getInteger(context, "count")))));
            else second.executes(context -> recipeCommand(context, action, 1));
            recipes.then(literal(action).then(argument("first", IdentifierArgument.id()).then(second)));
        }
        return recipes;
    }

    private static int recipeCommand(CommandContext<CommandSourceStack> context, String action, int count) {
        CommandSourceStack source = context.getSource();
        if (runtime() == null) return status(source);
        String first = IdentifierArgument.getId(context, "first").toString();
        String second = IdentifierArgument.getId(context, "second").toString();
        try {
            if (action.equals("inspect")) {
                String message = runtime().inspectRecipe(first, second);
                source.sendSuccess(() -> Component.literal(message), false);
                return 1;
            }
            CompletableFuture<Void> update = action.equals("set")
                    ? runtime().setRecipe(first, second, IdentifierArgument.getId(context, "output").toString(), count)
                    : runtime().forgetRecipe(first, second);
            update.whenComplete((ignored, failure) -> source.getServer().execute(() -> {
                if (failure == null) source.sendSuccess(() -> Component.literal(action.equals("set")
                        ? "Recipe saved for " + first + " + " + second + "."
                        : "Saved recipe forgotten for " + first + " + " + second + ". It can be discovered again."), false);
                else {
                    Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
                    String message = cause instanceof IllegalArgumentException || cause instanceof IllegalStateException
                            ? cause.getMessage() : "Recipe update failed or was cancelled. Inspect the recipe before retrying; the queue may be full.";
                    source.sendFailure(Component.literal(message));
                }
            }));
            return 1;
        } catch (IllegalArgumentException error) {
            source.sendFailure(Component.literal(error.getMessage()));
            return 0;
        }
    }
}
