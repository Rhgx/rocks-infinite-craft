package dev.rocks.infinitecraft;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class InfiniteCraftMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Rocks' Infinite Craft");
    private static FusionRuntime runtime;

    static boolean fusionEnabled(net.minecraft.server.level.ServerPlayer player) {
        return runtime != null && runtime.enabled();
    }
    public static boolean soulboundBook() { return runtime == null || runtime.soulboundBook(); }
    public static boolean personalBook() { return runtime != null && runtime.personalBook(); }

    public static void applyConfigToIntegratedServer(MinecraftServer server, ModConfig config) {
        server.execute(() -> {
            try {
                if (runtime == null) runtime = new FusionRuntime(server, config);
                else runtime.reconfigure(config);
                LOGGER.info("Infinite Craft configuration applied");
            } catch (Exception error) {
                LOGGER.error("Configuration could not be applied; previous runtime retained", error);
                for (var player : server.getPlayerList().getPlayers()) player.sendSystemMessage(
                        Component.literal("Infinite Craft configuration rejected. Previous settings retained; see server log."), false);
            }
        });
    }

    @Override public void onInitialize() {
        DiscoveryBook.initialize(player -> runtime == null ? java.util.List.of() : runtime.discoveries(player));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                ModConfig config = ModConfig.load(FabricLoader.getInstance().getConfigDir().resolve("infinitecraft.json"));
                runtime = new FusionRuntime(server, config);
                LOGGER.info("Infinite Craft ready: {}", runtime.status());
            } catch (Exception error) {
                LOGGER.error("Infinite Craft disabled: initialization failed. Check config and world recipe data.", error);
            }
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
            if (success && runtime != null) {
                try { runtime.reloadCatalog(); }
                catch (Exception error) { LOGGER.error("Catalog reload rejected; previous catalog retained", error); }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (runtime != null) runtime.close();
            runtime = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (runtime == null) return;
            try { runtime.tick(); }
            catch (RuntimeException error) {
                LOGGER.error("Infinite Craft skipped a scan after an unexpected failure", error);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            DiscoveryBook.sync(handler.player);
            if (runtime != null) runtime.welcome(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            DiscoveryBook.forgetCreativeCursor(handler.player.getUUID());
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                literal("fusion").executes(context -> status(context.getSource()))
                .then(recipeCommands())
                .then(literal("share").then(argument("discovery", IntegerArgumentType.integer(1)).executes(context ->
                        runtime == null ? 0 : runtime.shareRecipe(context.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(context, "discovery")))))
                .then(literal("book").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    if (!soulboundBook() && !canControlFusion(context.getSource())) {
                        context.getSource().sendFailure(Component.literal("Craft a Discovery Book."));
                        return 0;
                    }
                    if (!fusionEnabled(player)) {
                        context.getSource().sendFailure(Component.literal("Enable fusion first."));
                        return 0;
                    }
                    if (!DiscoveryBook.give(player)) {
                        context.getSource().sendFailure(Component.literal("Inventory full."));
                        return 0;
                    }
                    return 1;
                }))
                .then(literal("collection").executes(context -> collection(context, 1))
                        .then(literal("back").then(argument("page", IntegerArgumentType.integer(1)).executes(context ->
                                collection(context, IntegerArgumentType.getInteger(context, "page")))))
                        .then(literal("item").then(argument("item", IntegerArgumentType.integer(1))
                                .then(argument("returnPage", IntegerArgumentType.integer(1))
                                .then(argument("recipePage", IntegerArgumentType.integer(1)).executes(context ->
                                        collection(context, IntegerArgumentType.getInteger(context, "recipePage"),
                                                IntegerArgumentType.getInteger(context, "item"),
                                                IntegerArgumentType.getInteger(context, "returnPage")))))))
                        .then(literal("close").executes(context -> {
                            context.getSource().getPlayerOrException().connection.send(
                                    net.minecraft.network.protocol.common.ClientboundClearDialogPacket.INSTANCE);
                            return 1;
                        }))
                        .then(argument("page", IntegerArgumentType.integer(1)).executes(context ->
                                collection(context, IntegerArgumentType.getInteger(context, "page")))))
                .then(literal("enable").requires(InfiniteCraftMod::canControlFusion).executes(context -> setFusion(context, true)))
                .then(literal("disable").requires(InfiniteCraftMod::canControlFusion).executes(context -> setFusion(context, false)))
                .then(literal("toggle").requires(InfiniteCraftMod::canControlFusion).executes(context ->
                        setFusion(context, runtime != null && !runtime.enabled())))
                .then(literal("catalog").executes(context -> {
                    if (runtime == null) return status(context.getSource());
                    context.getSource().sendSuccess(() -> Component.literal(runtime.catalogSearch("")), false);
                    return 1;
                }).then(argument("query", StringArgumentType.greedyString()).executes(context -> {
                    if (runtime == null) return status(context.getSource());
                    String query = StringArgumentType.getString(context, "query");
                    context.getSource().sendSuccess(() -> Component.literal(runtime.catalogSearch(query)), false);
                    return 1;
                })))
                .then(literal("reload").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS)).executes(context -> {
                    try {
                        ModConfig config = ModConfig.load(FabricLoader.getInstance().getConfigDir().resolve("infinitecraft.json"));
                        if (runtime == null) runtime = new FusionRuntime(context.getSource().getServer(), config);
                        else runtime.reconfigure(config);
                        context.getSource().sendSuccess(() -> Component.literal("Configuration, catalog and recipes reloaded."), false);
                        return 1;
                    } catch (Exception error) {
                        LOGGER.error("Manual catalog reload rejected", error);
                        context.getSource().sendFailure(Component.literal("Reload rejected; old catalog retained. See server log."));
                        return 0;
                    }
                }))));
    }

    static boolean canControlFusion(CommandSourceStack source) {
        // Command-tree serialization probes requirements without a live server or player.
        if (source.getServer() == null) return false;
        if (source.getServer().isSingleplayer()) {
            var player = source.getPlayer();
            return player != null && source.getServer().isSingleplayerOwner(
                    new net.minecraft.server.players.NameAndId(player.getUUID(), player.getName().getString()));
        }
        return net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS).test(source);
    }

    private int setFusion(CommandContext<CommandSourceStack> context, boolean enabled) {
        if (runtime == null) return status(context.getSource());
        try {
            runtime.setEnabled(enabled);
        } catch (java.io.IOException error) {
            LOGGER.error("Could not save the world's fusion toggle", error);
            context.getSource().sendFailure(Component.literal("Could not save fusion setting. See server log."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Item fusion " + (runtime.enabled() ? "enabled" : "disabled")), false);
        return 1;
    }

    private int collection(CommandContext<CommandSourceStack> context, int page) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return collection(context, page, 0, 1);
    }

    private int collection(CommandContext<CommandSourceStack> context, int page, int itemId, int returnPage) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (runtime == null) return status(context.getSource());
        var player = context.getSource().getPlayerOrException();
        if (!soulboundBook() && !DiscoveryBook.isBook(player.containerMenu.getCarried())
                && !java.util.stream.IntStream.concat(net.minecraft.world.entity.player.Inventory.EQUIPMENT_SLOT_MAPPING.keySet().intStream(),
                        java.util.stream.IntStream.range(0, player.getInventory().getContainerSize()))
                        .anyMatch(slot -> DiscoveryBook.isBook(player.getInventory().getItem(slot)))) {
            context.getSource().sendFailure(Component.literal("Craft a Discovery Book."));
            return 0;
        }
        var entries = runtime.discoveries(player);
        if (itemId > 0) {
            var selected = entries.stream().filter(entry -> entry.id() == itemId).findFirst();
            if (selected.isEmpty() || entries.stream().filter(entry -> net.minecraft.world.item.ItemStack.isSameItemSameComponents(
                    entry.result(), selected.orElseThrow().result())).count() < 2) return 0;
        }
        player.openDialog(net.minecraft.core.Holder.direct(DiscoveryBook.createDialog(entries, page, personalBook(), itemId, returnPage)));
        // Send only to the reader so browsing never makes sounds for nearby players.
        if (context.getNodes().stream().noneMatch(node -> node.getNode().getName().equals("back")))
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, net.minecraft.sounds.SoundSource.MASTER,
                player.getX(), player.getEyeY(), player.getZ(), 0.3F, 1.0F, player.getRandom().nextLong()));
        return 1;
    }

    private int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(runtime == null
                ? "Infinite Craft is disabled. See the server log for initialization errors."
                : runtime.status()), false);
        return runtime == null ? 0 : 1;
    }

    private LiteralArgumentBuilder<CommandSourceStack> recipeCommands() {
        var recipes = literal("recipe").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS));
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

    private int recipeCommand(CommandContext<CommandSourceStack> context, String action, int count) {
        CommandSourceStack source = context.getSource();
        if (runtime == null) return status(source);
        String first = IdentifierArgument.getId(context, "first").toString();
        String second = IdentifierArgument.getId(context, "second").toString();
        try {
            if (action.equals("inspect")) {
                String message = runtime.inspectRecipe(first, second);
                source.sendSuccess(() -> Component.literal(message), false);
                return 1;
            }
            CompletableFuture<Void> update = action.equals("set")
                    ? runtime.setRecipe(first, second, IdentifierArgument.getId(context, "output").toString(), count)
                    : runtime.forgetRecipe(first, second);
            update.whenComplete((ignored, failure) -> source.getServer().execute(() -> {
                if (failure == null) source.sendSuccess(() -> Component.literal(action.equals("set")
                        ? "Recipe saved for " + first + " + " + second + "."
                        : "Saved recipe forgotten for " + first + " + " + second + ". It can be discovered again."), false);
                else {
                    Throwable cause = failure instanceof java.util.concurrent.CompletionException ? failure.getCause() : failure;
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
