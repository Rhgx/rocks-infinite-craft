package dev.rocks.infinitecraft;

import dev.rocks.infinitecraft.command.FusionCommands;
import dev.rocks.infinitecraft.discovery.DiscoveryBook;
import dev.rocks.infinitecraft.discovery.DiscoveryScreenPayload;
import dev.rocks.infinitecraft.discovery.SpecialItemsTab;
import dev.rocks.infinitecraft.fusion.CrafterStatePayload;
import dev.rocks.infinitecraft.fusion.FusionCrafterBlock;
import dev.rocks.infinitecraft.fusion.FusionRuntime;
import dev.rocks.infinitecraft.traits.CombatTraits;
import dev.rocks.infinitecraft.traits.LuckyBlockTrait;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class InfiniteCraftMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Rocks' Infinite Craft");
    private static FusionRuntime runtime;
    private static final Set<CrafterBlockEntity> loadedCrafters =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public static boolean fusionInputSlot(int slot) { return FusionCrafterBlock.inputSlot(slot); }

    public static ItemStack crafterPreview(CrafterBlockEntity block) {
        return runtime == null ? ItemStack.EMPTY : runtime.crafterPreview(block);
    }

    public static boolean crafterWorking(CrafterBlockEntity block) {
        return runtime != null && runtime.crafterWorking(block);
    }

    public static void crafterChanged(BlockEntity block) {
        if (runtime != null && block.getLevel() instanceof ServerLevel
                && block instanceof CrafterBlockEntity crafter && FusionCrafterBlock.marked(crafter))
            runtime.touchCrafter(crafter);
    }

    public static void crafterPlaced(CrafterBlockEntity block,
            LivingEntity placer) {
        if (placer instanceof ServerPlayer player && FusionCrafterBlock.marked(block))
            FusionCrafterBlock.setOwner(block, player.getUUID());
    }

    /** Redstone pulses act like a lever: start a stopped crafter, stop a running one. */
    public static void pulseCrafter(CrafterBlockEntity block) {
        if (!FusionCrafterBlock.marked(block)) return;
        if (FusionCrafterBlock.paused(block)) triggerCrafter(block);
        else FusionCrafterBlock.setMode(block, FusionCrafterBlock.repeats(block), true);
    }

    public static void triggerCrafter(CrafterBlockEntity block) {
        if (runtime != null && FusionCrafterBlock.marked(block)) runtime.triggerCrafter(block);
    }

    public static boolean isFusionCrafter(BlockEntity block) {
        return block instanceof CrafterBlockEntity crafter && FusionCrafterBlock.marked(crafter);
    }
    public static boolean groundFusionEnabled() {
        return runtime != null && runtime.enabled() && runtime.settings().groundFusion;
    }

    public static boolean fusionEnabled(ServerPlayer player) {
        return runtime != null && runtime.enabled();
    }
    public static boolean soulboundBook() { return runtime == null || runtime.soulboundBook(); }
    public static boolean personalBook() { return runtime != null && runtime.personalBook(); }

    public static FusionRuntime runtime() { return runtime; }

    public static void replaceRuntime(FusionRuntime value) { runtime = value; }

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

    @Override
    public void onInitialize() {
        CombatTraits.initialize();
        LuckyBlockTrait.initialize();
        SpecialItemsTab.initialize();
        DiscoveryScreenPayload.initialize();
        CrafterStatePayload.initialize();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((block, world) -> {
            if (block instanceof CrafterBlockEntity crafter) {
                // Components may not be loaded yet; the runtime checks the marker at the next server tick.
                loadedCrafters.add(crafter);
                if (runtime != null) runtime.touchCrafter(crafter);
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((block, world) -> {
            if (block instanceof CrafterBlockEntity crafter) {
                loadedCrafters.remove(crafter);
                if (runtime != null) runtime.unloadCrafter(crafter);
            }
        });
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (runtime != null && player instanceof ServerPlayer serverPlayer
                    && world.getBlockEntity(hit.getBlockPos()) instanceof CrafterBlockEntity block
                    && FusionCrafterBlock.marked(block)) runtime.crafterUser(block, serverPlayer);
            return InteractionResult.PASS;
        });
        net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            if (key.identifier().equals(Identifier.withDefaultNamespace("blocks/crafter")))
                builder.apply(CopyComponentsFunction.copyComponentsFromBlockEntity(
                        LootContextParams.BLOCK_ENTITY)
                        .include(DataComponents.CUSTOM_DATA)
                        .include(DataComponents.CUSTOM_MODEL_DATA)
                        .include(DataComponents.CUSTOM_NAME)
                        .include(DataComponents.LORE));
        });
        DiscoveryBook.initialize(player -> runtime == null ? List.of() : runtime.discoveries(player));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                ModConfig config = ModConfig.load(FabricLoader.getInstance().getConfigDir().resolve("infinitecraft.json"));
                runtime = new FusionRuntime(server, config);
                loadedCrafters.forEach(runtime::touchCrafter);
                LOGGER.info("Infinite Craft ready: {}", runtime.status());
            } catch (Exception error) {
                LOGGER.error("Infinite Craft disabled: initialization failed. Check config and world recipe data.", error);
            }
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
            if (success && runtime != null) {
                try {
                    runtime.reloadCatalog();
                } catch (Exception error) {
                    LOGGER.error("Catalog reload rejected; previous catalog retained", error);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (runtime != null) runtime.close();
            runtime = null;
            loadedCrafters.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (runtime == null) return;
            try {
                runtime.tick();
            } catch (RuntimeException error) {
                LOGGER.error("Infinite Craft skipped a scan after an unexpected failure", error);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            DiscoveryBook.sync(handler.player);
            if (runtime != null) {
                SpecialItemsTab.send(handler.player, runtime.discoveries());
                runtime.welcome(handler.player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            DiscoveryBook.forgetCreativeCursor(handler.player.getUUID());
            FusionCommands.disconnect(handler.player.getUUID());
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                FusionCommands.register(dispatcher));
    }
}
