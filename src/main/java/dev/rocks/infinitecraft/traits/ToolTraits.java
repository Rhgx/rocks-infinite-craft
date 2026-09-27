package dev.rocks.infinitecraft.traits;

import dev.rocks.infinitecraft.item.ItemTraits;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/** Server-side effects for traits on tools: self repair, smelted drops and vein mining. */
public final class ToolTraits {
    private static final int REPAIR_INTERVAL_TICKS = 60;
    private static final int VEIN_LIMIT = 24;
    private static final int VEIN_WAVE_TICKS = 3;
    private static final List<Vein> VEINS = new ArrayList<>();
    private static boolean breakingVein;

    /** A vein spreads outward one ring per wave, so the break visibly travels through it. */
    private record Vein(ServerPlayer player, ServerLevel level, Predicate<BlockState> matches, Iterator<List<BlockPos>> waves) {
    }

    private ToolTraits() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % VEIN_WAVE_TICKS == 0) VEINS.removeIf(vein -> !breakWave(vein));
            if (server.getTickCount() % REPAIR_INTERVAL_TICKS != 0 || TraitSettings.chance("self_repairing") <= 0) return;
            for (var player : server.getPlayerList().getPlayers()) {
                var inventory = player.getInventory();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    var stack = inventory.getItem(slot);
                    if (!stack.isDamaged() || !ItemTraits.inherited(stack).contains("self_repairing")) continue;
                    stack.setDamageValue(stack.getDamageValue() - 1);
                    if (!stack.isDamaged()) repaired(player);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> VEINS.clear());

        // Every loot table with a tool: block drops, fishing and shearing.
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            if (!(context.getOptionalParameter(LootContextParams.TOOL) instanceof ItemStack tool)
                    || !ItemTraits.inherited(tool).contains("auto_smelt") || TraitSettings.chance("auto_smelt") <= 0) return;
            // Sneaking keeps drops raw. Fishing reports the hook, so check its owner.
            var entity = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
            if (entity instanceof Projectile projectile) entity = projectile.getOwner();
            if (entity != null && entity.isShiftKeyDown()) return;
            boolean changed = false;
            for (int i = 0; i < drops.size(); i++) {
                var result = smelted(context.getLevel(), drops.get(i));
                changed |= result != drops.get(i);
                drops.set(i, result);
            }
            Vec3 origin = context.getOptionalParameter(LootContextParams.ORIGIN);
            if (changed && origin != null) sizzle(context.getLevel(), origin);
        });

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (breakingVein || !(player instanceof ServerPlayer server) || player.isShiftKeyDown()
                    || !(state.is(ConventionalBlockTags.ORES) || state.is(BlockTags.LOGS))
                    || !ItemTraits.inherited(player.getMainHandItem()).contains("vein_miner")
                    || TraitSettings.chance("vein_miner") <= 0) return;
            var matches = sameKind(state);
            var waves = vein(pos, candidate -> matches.test(level.getBlockState(candidate)), VEIN_LIMIT);
            if (!waves.isEmpty()) VEINS.add(new Vein(server, server.level(), matches, waves.iterator()));
        });
    }

    /** Returns false once the vein is finished or the player can no longer continue it. */
    private static boolean breakWave(Vein vein) {
        var player = vein.player();
        // A player who changed dimension must not break the same positions in the new one.
        if (!vein.waves().hasNext() || player.hasDisconnected() || !player.isAlive() || player.level() != vein.level()) return false;
        breakingVein = true;
        try {
            for (var pos : vein.waves().next()) {
                var tool = player.getMainHandItem();
                // Stops when the tool is switched, or one use before it would break.
                if (!ItemTraits.inherited(tool).contains("vein_miner")
                        || tool.isDamageableItem() && tool.getDamageValue() >= tool.getMaxDamage() - 1) return false;
                var state = vein.level().getBlockState(pos);
                if (!vein.matches().test(state)) continue;
                if (!player.gameMode.destroyBlock(pos)) return false;
                // Vanilla hides the break effect from the breaking player, who predicted it; these blocks were not predicted.
                player.connection.send(new ClientboundLevelEventPacket(
                        LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state), false));
            }
        } finally {
            breakingVein = false;
        }
        return vein.waves().hasNext();
    }

    /** Ores match by their c:ores/<type> tag, so stone and deepslate variants join one vein; logs match exactly. */
    private static Predicate<BlockState> sameKind(BlockState state) {
        return state.typeHolder().tags()
                .filter(tag -> tag.location().getNamespace().equals("c") && tag.location().getPath().startsWith("ores/"))
                .findFirst()
                .<Predicate<BlockState>>map(tag -> other -> other.is(tag))
                .orElse(other -> other.is(state.getBlock()));
    }

    private static ItemStack smelted(ServerLevel level, ItemStack drop) {
        var input = new SingleRecipeInput(drop);
        return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level)
                .map(recipe -> recipe.value().assemble(input))
                .filter(result -> !result.isEmpty())
                .map(result -> result.copyWithCount(Math.min(result.getMaxStackSize(), result.getCount() * drop.getCount())))
                .orElse(drop);
    }

    private static void sizzle(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 4, .2, .2, .2, .01);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + .2, at.z, 3, .15, .1, .15, .01);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                .35F, .9F + level.getRandom().nextFloat() * .3F);
    }

    private static void repaired(ServerPlayer player) {
        var level = player.level();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1, player.getZ(),
                5, .35, .4, .35, 0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, .6F, 1.4F);
    }

    /** Rings of matching blocks around the start (edges and corners count), nearest first, excluding the start. */
    static List<List<BlockPos>> vein(BlockPos start, Predicate<BlockPos> matches, int limit) {
        var waves = new ArrayList<List<BlockPos>>();
        var seen = new HashSet<BlockPos>(List.of(start));
        List<BlockPos> wave = List.of(start);
        int found = 0;
        while (found < limit) {
            var next = new ArrayList<BlockPos>();
            for (var pos : wave) {
                for (var candidate : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
                    var block = candidate.immutable();
                    if (found < limit && seen.add(block) && matches.test(block)) {
                        next.add(block);
                        found++;
                    }
                }
            }
            if (next.isEmpty()) break;
            waves.add(next);
            wave = next;
        }
        return waves;
    }
}
