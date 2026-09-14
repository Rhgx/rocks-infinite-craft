package dev.rocks.infinitecraft;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Track loaded stations independently of vanilla container ticking and sleeping optimizations. */
final class FusionCrafter implements AutoCloseable {
    static final String MARKER = "rocks_fusion_crafter";
    static final String OWNER = "rocks_fusion_owner";
    private static final net.minecraft.nbt.CompoundTag MARKER_PATTERN = markerTag();
    private final FusionRuntime runtime;
    private final MinecraftServer server;
    private final Map<CrafterBlockEntity, Station> stations = new HashMap<>();

    private final Set<CrafterBlockEntity> active = new HashSet<>();
    private final Set<CrafterBlockEntity> dirty = new HashSet<>();
    private boolean wasEnabled;

    private static final class Station {
        int stable;
        List<ItemStack> items = List.of();
        UUID user;
        FusionRuntime.PreparedFusion job;
        boolean attempted;
        boolean triggered;
        ItemStack preview = ItemStack.EMPTY;
    }

    FusionCrafter(FusionRuntime runtime, MinecraftServer server) {
        this.runtime = runtime;
        this.server = server;
    }

    static boolean marked(CrafterBlockEntity block) {
        var data = block.components().get(DataComponents.CUSTOM_DATA);
        return data != null && data.matchedBy(MARKER_PATTERN);
    }

    static void setOwner(CrafterBlockEntity block, UUID owner) {
        var tag = block.components().getOrDefault(DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        tag.putBoolean(MARKER, true);
        tag.putString(OWNER, owner.toString());
        block.setComponents(net.minecraft.core.component.DataComponentMap.builder().addAll(block.components())
                .set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag)).build());
        block.setChanged();
    }

    static UUID owner(CrafterBlockEntity block) {
        var data = block.components().get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String value = data.copyTag().getStringOr(OWNER, "");
        try { return value.isEmpty() ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static net.minecraft.nbt.CompoundTag markerTag() {
        var marker = new net.minecraft.nbt.CompoundTag();
        marker.putBoolean(MARKER, true);
        return marker;
    }

    static boolean inputSlot(int slot) { return slot == 3 || slot == 5; }

    static ItemStack item() {
        var stack = new ItemStack(net.minecraft.world.item.Items.CRAFTER);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("Fusion Crafter").withStyle(style -> style.withColor(0xFFAA00).withItalic(false)));
        var marker = new net.minecraft.nbt.CompoundTag();
        marker.putBoolean(MARKER, true);
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(marker));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(
                List.of(), List.of(), List.of("rocks_fusion_crafter"), List.of()));
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                Component.literal("Place items in two slots to fuse.")
                        .withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GRAY).withItalic(false)))));
        return stack;
    }

    private static final class Placeholder {
        static final ItemStack STACK = create();
        private static ItemStack create() {
            var stack = new ItemStack(net.minecraft.world.item.Items.PAPER);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("?").withStyle(style -> style.withItalic(false)));
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(
                    List.of(), List.of(), List.of("rocks_fusion_unknown"), List.of()));
            return stack;
        }
    }

    ItemStack preview(CrafterBlockEntity block) {
        var station = stations.get(block);
        var preview = (station == null || station.preview.isEmpty() ? Placeholder.STACK : station.preview).copy();
        if (station != null && station.job != null)
            preview.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return preview;
    }

    void touch(CrafterBlockEntity block) {
        dirty.add(block);
    }

    void trigger(CrafterBlockEntity block) {
        touch(block);
        stations.computeIfAbsent(block, ignored -> new Station()).triggered = true;
    }

    void unload(CrafterBlockEntity block) {
        dirty.remove(block);
        active.remove(block);
        var station = stations.remove(block);
        if (station == null) return;
        cancel(station);
    }

    void user(CrafterBlockEntity block, ServerPlayer player) {
        touch(block);
        var station = stations.computeIfAbsent(block, ignored -> new Station());
        if (station.job == null) station.user = player.getUUID();
    }

    int pending() { return (int) active.stream().map(stations::get).filter(s -> s.job != null).count(); }
    boolean uses(String key) { return active.stream().map(stations::get).anyMatch(s -> s.job != null && s.job.key().equals(key)); }

    static List<Integer> inputSlots(List<ItemStack> items) {
        var slots = new ArrayList<Integer>();
        for (int i = 0; i < items.size(); i++) if (!items.get(i).isEmpty()) slots.add(i);
        return slots.size() == 2 ? slots : List.of();
    }

    private void cancel(Station station) {
        var job = station.job;
        station.job = null;
        if (job != null) runtime.releaseRecipe(job.key());
    }

    void cancelAll() {
        dirty.addAll(stations.keySet());
        for (var station : stations.values()) {
            cancel(station);
            station.stable = 0;
            station.attempted = false;
            station.triggered = false;
        }
    }

    void tick() {
        boolean enabled = runtime.enabled() && runtime.settings().crafterFusion;
        if (enabled != wasEnabled) {
            cancelAll();
            wasEnabled = enabled;
        }
        // Wakeups are drained before iterating: inventory changes during an exchange queue the next tick.
        for (var block : dirty) {
            if (!block.isRemoved() && marked(block)) {
                stations.computeIfAbsent(block, ignored -> new Station());
                active.add(block);
            }
        }
        dirty.clear();
        var iterator = active.iterator();
        while (iterator.hasNext()) {
            var block = iterator.next();
            var station = stations.get(block);
            if (block.isRemoved() || !marked(block)) {
                cancel(station);
                stations.remove(block);
                iterator.remove();
                continue;
            }
            try { tick(block, station); }
            catch (RuntimeException error) {
                cancel(station);
                station.attempted = true;
                InfiniteCraftMod.LOGGER.error("Fusion Crafter at {} failed; items retained", block.getBlockPos(), error);
            }
            if (!enabled || inputSlots(station.items).isEmpty() || (station.attempted && station.job == null))
                iterator.remove();
        }
    }

    private void tick(CrafterBlockEntity block, Station station) {
        var world = (ServerLevel) block.getLevel();
        if (world == null) return;
        if (!runtime.enabled() || !runtime.settings().crafterFusion) {
            cancel(station);
            station.stable = 0;
            station.attempted = false;
            return;
        }
        var current = block.getItems();
        if (!sameItems(station.items, current)) {
            cancel(station);
            station.items = current.stream().map(ItemStack::copy).toList();
            station.preview = ItemStack.EMPTY;
            station.stable = 0;
            station.attempted = false;
        }
        var slots = inputSlots(station.items);
        if (slots.isEmpty()) {
            station.triggered = false;
            return;
        }
        var player = station.user == null ? null : server.getPlayerList().getPlayer(station.user);
        if (player == null && station.job != null) {
            cancel(station);
            station.attempted = false;
            station.stable = 0;
        }
        if (player == null) {
            UUID owner = owner(block);
            if (owner != null) {
                player = server.getPlayerList().getPlayer(owner);
                if (player == null) return;
                station.user = owner;
            }
        }
        if (player == null) {
            var center = Vec3.atCenterOf(block.getBlockPos());
            player = server.getPlayerList().getPlayers().stream()
                    .filter(p -> p.level() == world && !p.isSpectator() && p.position().distanceToSqr(center) <= 64)
                    .min(Comparator.comparingDouble(p -> p.position().distanceToSqr(center))).orElse(null);
            if (player == null) return;
            station.user = player.getUUID();
        }
        if (station.job != null) {
            if (runtime.settings().queueFeedback && server.getTickCount() % 10 == 0) {
                String message = runtime.queueMessage(station.job.key());
                if (message != null) player.sendSystemMessage(Component.literal(message).withStyle(net.minecraft.ChatFormatting.GRAY), true);
            }
            if (station.job.resolution().isDone()) {
                var job = station.job;
                station.job = null;
                try {
                    var result = job.resolution().join();
                    var first = station.items.get(slots.get(0));
                    var second = station.items.get(slots.get(1));
                    var output = runtime.outputFor(result, first, second, job.dataFirst(), job.dataSecond());
                    if (output.isEmpty()) throw new IllegalArgumentException("Fusion result unavailable.");
                    exchange(block, world, slots, output, result.count(), player);
                    station.preview = output.copy();
                    station.items = block.getItems().stream().map(ItemStack::copy).toList();
                    station.attempted = false;
                    station.stable = 0;
                    runtime.recordDiscovery(first, second, output, player);
                    feedback(world, block, runtime.isSpecial(output), true);
                    station.user = null;
                } catch (java.util.concurrent.CompletionException | IllegalArgumentException error) {
                    player.sendSystemMessage(Component.literal("Fusion failed. Remove an item to retry."), true);
                    feedback(world, block, false, false);
                    InfiniteCraftMod.LOGGER.warn("Fusion Crafter request failed: {}", error.getClass().getSimpleName());
                }
            } else if (server.getTickCount() % 4 == 0 && runtime.settings().combiningParticles) {
                var center = Vec3.atBottomCenterOf(block.getBlockPos()).add(0, 1.2, 0);
                double angle = server.getTickCount() * .16;
                for (int side = 0; side < 2; side++) {
                    double a = angle + side * Math.PI;
                    FusionRuntime.sendParticleOutsideBlocks(world, net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                            center.x + Math.cos(a) * .3, center.y, center.z + Math.sin(a) * .3);
                }
            }
            return;
        }
        if (station.triggered) station.stable = 20;
        else station.stable++;
        if (station.attempted || station.stable < 20 || !runtime.hasCapacity()) return;
        station.attempted = true;
        station.triggered = false;
        try {
            var first = station.items.get(slots.get(0));
            var second = station.items.get(slots.get(1));
            if (DiscoveryBook.isBook(first) || DiscoveryBook.isBook(second))
                throw new IllegalArgumentException("Discovery Books cannot fuse.");
            station.job = runtime.prepareFusion(first.copyWithCount(1), second.copyWithCount(1));
        } catch (IllegalArgumentException error) {
            player.sendSystemMessage(Component.literal(error.getMessage()), true);
            feedback(world, block, false, false);
        }
    }

    static boolean sameItems(List<ItemStack> before, List<ItemStack> after) {
        if (before.size() != after.size()) return false;
        for (int i = 0; i < before.size(); i++) if (!ItemStack.matches(before.get(i), after.get(i))) return false;
        return true;
    }

    private void exchange(CrafterBlockEntity block, ServerLevel world, List<Integer> slots,
                          ItemStack output, int quantity, ServerPlayer player) {
        var facing = block.getBlockState().getValue(BlockStateProperties.ORIENTATION).front();
        var center = Vec3.atCenterOf(block.getBlockPos());
        var point = center.add(facing.getStepX() * .85, facing.getStepY() * .85, facing.getStepZ() * .85);
        var spawned = new ArrayList<ItemEntity>();
        var destination = net.minecraft.world.level.block.entity.HopperBlockEntity.getContainerAt(
                world, block.getBlockPos().relative(facing));
        // Fill the facing inventory first and dispense only what does not fit.
        try {
            for (var stack : FusionRuntime.splitOutput(output, quantity)) {
                var remaining = destination == null ? stack : net.minecraft.world.level.block.entity.HopperBlockEntity
                        .addItem(block, destination, stack, facing.getOpposite());
                if (remaining.isEmpty()) continue;
                var entity = new ItemEntity(world, point.x, point.y, point.z, remaining);
                entity.setThrower(player);
                entity.setDefaultPickUpDelay();
                entity.setDeltaMovement(facing.getStepX() * .12, .12 + facing.getStepY() * .12, facing.getStepZ() * .12);
                spawned.add(entity);
                if (!world.addFreshEntity(entity)) throw new IllegalArgumentException("Output unavailable.");
            }
        } catch (RuntimeException error) {
            spawned.forEach(ItemEntity::discard);
            throw error;
        }
        for (int slot : slots) block.setItem(slot, block.getItem(slot).copyWithCount(block.getItem(slot).getCount() - 1));
        block.setChanged();
    }

    private void feedback(ServerLevel world, CrafterBlockEntity block, boolean special, boolean success) {
        var point = Vec3.atBottomCenterOf(block.getBlockPos()).add(0, 1, 0);
        var config = runtime.settings();
        if (success ? config.successSound : config.failureSound)
            world.playSound(null, point.x, point.y, point.z,
                    success ? (special ? SoundEvents.NOTE_BLOCK_BELL : SoundEvents.NOTE_BLOCK_CHIME) : SoundEvents.NOTE_BLOCK_DIDGERIDOO,
                    SoundSource.BLOCKS, .25F, success ? 1.3F : .5F);
        if (success ? config.successParticles : config.failureParticles) {
            if (success && special) FusionRuntime.showSpecialParticles(world, point);
            else FusionRuntime.showResultParticles(world, point, success);
        }
    }

    @Override public void close() {
        cancelAll();
        active.clear();
        dirty.clear();
        stations.clear();
    }
}
