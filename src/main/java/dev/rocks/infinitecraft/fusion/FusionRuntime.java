package dev.rocks.infinitecraft.fusion;

import com.mojang.serialization.JsonOps;
import dev.rocks.infinitecraft.InfiniteCraftMod;
import dev.rocks.infinitecraft.ModConfig;
import dev.rocks.infinitecraft.catalog.CatalogSearch;
import dev.rocks.infinitecraft.catalog.GameCatalog;
import dev.rocks.infinitecraft.catalog.RecipeOverrides;
import dev.rocks.infinitecraft.command.FusionCommands;
import dev.rocks.infinitecraft.core.CatalogEntry;
import dev.rocks.infinitecraft.core.GenerationRequest;
import dev.rocks.infinitecraft.core.PairKey;
import dev.rocks.infinitecraft.core.RecipeGenerator;
import dev.rocks.infinitecraft.core.RecipeQuality;
import dev.rocks.infinitecraft.core.RecipeResult;
import dev.rocks.infinitecraft.discovery.DiscoveryAnnouncements;
import dev.rocks.infinitecraft.discovery.DiscoveryBook;
import dev.rocks.infinitecraft.discovery.DiscoveryCollection;
import dev.rocks.infinitecraft.discovery.KnownRecipes;
import dev.rocks.infinitecraft.discovery.SpecialItemsTab;
import dev.rocks.infinitecraft.engine.BlockedRecipeException;
import dev.rocks.infinitecraft.engine.GenerationFailure;
import dev.rocks.infinitecraft.engine.RecipeEngine;
import dev.rocks.infinitecraft.engine.RecipeStore;
import dev.rocks.infinitecraft.item.ComponentPairKey;
import dev.rocks.infinitecraft.item.FusionOrigin;
import dev.rocks.infinitecraft.item.ItemDataFusion;
import dev.rocks.infinitecraft.item.ItemTraits;
import dev.rocks.infinitecraft.item.PotionFusion;
import dev.rocks.infinitecraft.item.VanillaTraits;
import dev.rocks.infinitecraft.provider.RecipeGenerators;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** All entity access occurs on the server thread; workers receive immutable IDs only. */
public final class FusionRuntime implements AutoCloseable {
    private final MinecraftServer server;
    private ModConfig config;
    private final FusionCrafter crafters;

    public ItemStack crafterPreview(CrafterBlockEntity block) {
        return crafters.preview(block);
    }

    public boolean crafterWorking(CrafterBlockEntity block) {
        return crafters.working(block);
    }

    public void crafterUser(CrafterBlockEntity block, ServerPlayer player) {
        crafters.user(block, player);
    }

    public void triggerCrafter(CrafterBlockEntity block) {
        crafters.trigger(block);
    }

    public ModConfig settings() {
        return config;
    }

    boolean hasCapacity() {
        return pending + crafters.pending() < config.maxPending;
    }

    String queueMessage(String key) {
        var queue = engine.queuePosition(key);
        return queue == null ? engine.generationStatus(key) : "Fusion queued" + ".".repeat(1 + (int) ((ticks / 10) % 3))
                + " (" + queue.position() + "/" + queue.total() + ")";
    }
    public void unloadCrafter(CrafterBlockEntity block) {
        crafters.unload(block);
    }

    public void touchCrafter(CrafterBlockEntity block) {
        crafters.touch(block);
    }
    void releaseRecipe(String key) {
        if (!crafters.uses(key) && combining.values().stream().noneMatch(job -> job.recipeKey().equals(key)))
            engine.cancelRecipe(key);
    }
    private RecipeEngine engine;
    private final RecipeStore store;
    private final DiscoveryCollection discoveries;
    private final Path dataDirectory;
    private final ExecutorService exportWorker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "infinite-craft-catalog");
        thread.setDaemon(true);
        return thread;
    });
    private List<CatalogEntry> catalog = List.of();
    private CatalogSearch.Index candidateIndex = new CatalogSearch.Index(List.of());
    private Map<String, RecipeResult> overrides = Map.of();
    private Map<String, List<String>> potionOptions = Map.of();
    private Set<String> allowed = Set.of();
    private boolean fusionEnabled;
    private final Map<UUID, Long> reserved = new HashMap<>();
    private final Map<Long, CombiningVisual> combining = new LinkedHashMap<>();
    private record CombiningVisual(ServerLevel world, UUID player, UUID first, UUID second, String recipeKey) {}
    private final Map<UUID, Long> lastRecipeShare = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    // Both sides are weak: unloaded drops lose fusion intent, so UUID tombstones are unnecessary.
    private final Map<ItemEntity, Set<ItemEntity>> failedPairs = new WeakHashMap<>();
    private final FusionCompatibility compatibility = new FusionCompatibility();

    private void rememberFailure(ItemEntity a, ItemEntity b) {
        failedPairs.computeIfAbsent(a, ignored -> Collections.newSetFromMap(new WeakHashMap<>())).add(b);
    }

    private boolean failed(ItemEntity a, ItemEntity b) {
        return failedPairs.getOrDefault(a, Set.of()).contains(b) || failedPairs.getOrDefault(b, Set.of()).contains(a);
    }
    private long ticks;
    private long epoch;
    private long requestId;
    private int pending;
    private volatile boolean closed;

    public FusionRuntime(MinecraftServer server, ModConfig config) throws IOException {
        this.server = server;
        this.config = config;
        crafters = new FusionCrafter(this, server);
        dataDirectory = server.getWorldPath(LevelResource.ROOT).resolve("infinitecraft");
        fusionEnabled = FusionWorldState.load(dataDirectory.resolve("fusion-enabled.json"));
        store = new RecipeStore(dataDirectory.resolve("recipes.json"));
        discoveries = new DiscoveryCollection(dataDirectory.resolve("discoveries.json"), server.registryAccess());
        engine = createEngine(config);
        try {
            reloadCatalog();
        } catch (IOException | RuntimeException error) {
            close(); throw error;
        }
    }

    private RecipeEngine createEngine(ModConfig settings) {
        var provider = RecipeGenerators.create(settings.provider);
        return new RecipeEngine(store, new RecipeGenerator() {
            public RecipeResult generate(GenerationRequest request) throws Exception {
                if (!settings.generationEnabled) throw new IllegalStateException("Recipe generation is disabled");
                return provider.generate(request);
            }
            public List<RecipeResult> generateCandidates(GenerationRequest request) throws Exception {
                if (!settings.generationEnabled) throw new IllegalStateException("Recipe generation is disabled");
                return provider.generateCandidates(request);
            }
            public List<RecipeResult> generateCandidates(GenerationRequest request, String feedback) throws Exception {
                if (!settings.generationEnabled) throw new IllegalStateException("Recipe generation is disabled");
                return provider.generateCandidates(request, feedback);
            }
        }, settings.maxPending, settings.generationAttempts, settings.generationThreads);
    }

    /** Preserve one synchronized store and export worker across settings changes. */
    public void reconfigure(ModConfig settings) throws IOException {
        ModConfig previous = config;
        boolean catalogChanged = !previous.sameCatalog(settings);
        RecipeEngine replacement = previous.sameGeneration(settings) ? null : createEngine(settings);
        config = settings;
        try {
            if (catalogChanged) reloadCatalog();
            else if (replacement != null) cancelExchanges();
        } catch (IOException | RuntimeException error) {
            config = previous;
            if (replacement != null) replacement.close();
            throw error;
        }
        if (replacement != null) {
            RecipeEngine old = engine;
            engine = replacement;
            old.close();
        }
        if (previous.soulboundBook != settings.soulboundBook || previous.enabled != settings.enabled)
            for (var player : server.getPlayerList().getPlayers()) DiscoveryBook.sync(player);
        if (previous.personalBook != settings.personalBook)
            for (var player : server.getPlayerList().getPlayers()) KnownRecipes.send(player, discoveries(player), true);
    }

    private void cancelExchanges() {
        epoch++;
        crafters.cancelAll();
        engine.cancelPendingGeneration();
        reserved.clear();
        combining.clear();
        pending = 0;
        compatibility.clear();
    }

    public void reloadCatalog() throws IOException {
        List<CatalogEntry> next = GameCatalog.snapshot(server, config.excludedIds, config.excludedNamespaces);
        if (!config.allowModdedItems) next = next.stream().map(entry -> entry.namespace().equals("minecraft") ? entry
                : new CatalogEntry(entry.id(), entry.kind(), entry.namespace(), entry.name(), entry.tags(), false,
                        "Modded items disabled by server configuration")).toList();
        Map<String, RecipeResult> recipes = RecipeOverrides.load(server, next);
        Set<String> eligible = next.stream().filter(entry -> entry.kind().equals("item") && entry.craftable())
                .map(CatalogEntry::id).collect(Collectors.toUnmodifiableSet());
        var nextIndex = new CatalogSearch.Index(next);
        var nextPotions = PotionFusion.options();
        catalog = next;
        candidateIndex = nextIndex;
        potionOptions = nextPotions;
        overrides = Map.copyOf(recipes);
        allowed = eligible;
        cancelExchanges();
        // Cancel physical exchanges from older snapshots without cancelling shared recipe discoveries.
        List<CatalogEntry> export = next;
        exportWorker.execute(() -> {
            try {
                GameCatalog.export(dataDirectory.resolve("catalog.json"), export);
            } catch (IOException error) {
                InfiniteCraftMod.LOGGER.error("Could not export live catalog", error);
            }
        });
    }

    public String status() {
        return "Infinite Craft: " + catalog.size() + " catalog entries, " + allowed.size()
                + " eligible items, " + overrides.size() + " explicit recipes, " + (pending + crafters.pending())
                + " pending exchanges. Provider: " + config.provider.provider() + ". Fusion is " + (enabled() ? "on." : "off.");
    }

    public String catalogSearch(String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        List<CatalogEntry> matches = catalog.stream().filter(entry -> entry.id().contains(normalized)
                || entry.name().toLowerCase(Locale.ROOT).contains(normalized)).toList();
        return matches.size() + " matches. First 15:\n" + matches.stream().limit(15)
                .map(entry -> entry.kind() + " " + entry.id() + (entry.craftable() ? "" : " [" + entry.exclusionReason() + "]"))
                .collect(Collectors.joining("\n")) + "\nFull catalog: world/infinitecraft/catalog.json";
    }

    public boolean enabled() {
        return !closed && config.enabled && fusionEnabled;
    }

    public void setEnabled(boolean value) throws IOException {
        FusionWorldState.save(dataDirectory.resolve("fusion-enabled.json"), value);
        fusionEnabled = value;
        if (!value) {
            FusionDrops.clear();
            epoch++;
            engine.cancelPendingGeneration();
            reserved.clear();
            combining.clear();
            pending = 0;
        }
        for (var player : server.getPlayerList().getPlayers()) DiscoveryBook.sync(player);
    }

    public void welcome(ServerPlayer player) {
        if (!config.enabled || !config.joinMessage) return;
        if (enabled() || !FusionCommands.canControlFusion(player.createCommandSourceStack())) {
            player.sendSystemMessage(fusionStatus());
            return;
        }
        player.sendSystemMessage(fusionStatus().append(" ")
                .append(Component.literal("[Enable fusion]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.literal("/fusion enable").withStyle(ChatFormatting.GRAY)))
                        .withClickEvent(new ClickEvent.RunCommand("/fusion enable")))));
    }

    private MutableComponent fusionStatus() {
        return Component.literal("Infinite Craft").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" · fusion is ").withStyle(ChatFormatting.GRAY))
                .append(enabled() ? Component.literal("on").withStyle(ChatFormatting.GREEN)
                        : Component.literal("off").withStyle(ChatFormatting.RED));
    }

    public List<DiscoveryCollection.Entry> discoveries() {
        return discoveries.entries();
    }
    public List<DiscoveryCollection.Entry> discoveries(ServerPlayer player) {
        return config.personalBook ? discoveries.entries(player.getUUID(), player.getName().getString()) : discoveries.entries();
    }
    public Set<Integer> favoriteIds(ServerPlayer player) {
        return discoveries.favoriteIds(player.getUUID()).stream().filter(id -> !config.personalBook
                || discoveries.owns(id, player.getUUID(), player.getName().getString())).collect(Collectors.toSet());
    }

    public boolean toggleFavorite(ServerPlayer player, int id) {
        if (discoveries(player).stream().noneMatch(entry -> entry.id() == id)) return false;
        discoveries.toggleFavorite(id, player.getUUID());
        discoveries.saveAsync(exportWorker, error -> {
            InfiniteCraftMod.LOGGER.error("Could not save discovery favorites", error);
            server.execute(() -> player.sendSystemMessage(Component.literal("Favorites could not be saved.").withStyle(ChatFormatting.RED)));
        });
        return true;
    }
    public boolean soulboundBook() {
        return config.soulboundBook;
    }
    public boolean personalBook() {
        return config.personalBook;
    }

    boolean isSpecial(ItemStack stack) {
        return ItemDataFusion.specialIngredient(stack, config.specialRarity, config.specialEnchantments,
                config.specialPotions, config.specialCustomData);
    }

    void recordDiscovery(ItemStack first, ItemStack second, ItemStack output, ServerPlayer player) {
        try {
            int revision = discoveries.revision();
            boolean firstDiscovery = discoveries.record(first, second, output, player.getName().getString(), player.getUUID());
            if (revision == discoveries.revision()) return;
            int discoveryCount = firstDiscovery ? discoveries.outputCount() : 0;
            int milestoneTier = DiscoveryAnnouncements.milestoneTier(discoveryCount);
            if (firstDiscovery && config.milestoneMessages && milestoneTier >= 0) {
                int experience = DiscoveryAnnouncements.milestoneExperience(milestoneTier);
                player.giveExperiencePoints(experience);
                var milestone = DiscoveryAnnouncements.milestoneMessage(discoveryCount, milestoneTier, player.getName().getString());
                for (var viewer : server.getPlayerList().getPlayers()) viewer.sendSystemMessage(viewer == player
                        ? milestone.copy().append(Component.literal("  +" + experience + " XP")
                                .withStyle(style -> style.withColor(0x80FF20).withBold(false)))
                        : milestone);
            }
            discoveries.saveAsync(exportWorker,
                    error -> InfiniteCraftMod.LOGGER.error("Could not save discovery collection", error));
            if (firstDiscovery && isSpecial(output)) SpecialItemsTab.sendAll(server, discoveries.entries());
            var recipe = discoveries.find(first, second, output);
            if (recipe != null) for (var viewer : config.personalBook ? List.of(player) : server.getPlayerList().getPlayers())
                KnownRecipes.send(viewer, List.of(recipe), false);
            if (firstDiscovery && (isSpecial(output) ? config.specialDiscoveryMessage : config.firstDiscoveryMessage)) {
                var message = DiscoveryAnnouncements.discoveryMessage(output, player.getName().getString(), isSpecial(output));
                for (var viewer : server.getPlayerList().getPlayers()) viewer.sendSystemMessage(message);
            }
        } catch (RuntimeException error) {
            InfiniteCraftMod.LOGGER.error("Fusion completed but discovery could not be recorded", error);
        }
    }

    public int shareRecipe(ServerPlayer player, int id) {
        var entries = discoveries.entries();
        if (id < 1 || id > entries.size()) return 0;
        if (config.personalBook && !discoveries.owns(id, player.getUUID(), player.getName().getString())) return 0;
        player.connection.send(ClientboundClearDialogPacket.INSTANCE);
        player.connection.send(new ClientboundSoundPacket(
                SoundEvents.UI_BUTTON_CLICK, SoundSource.MASTER,
                player.getX(), player.getEyeY(), player.getZ(), 0.3F, 1.0F, player.getRandom().nextLong()));
        if (ticks - lastRecipeShare.getOrDefault(player.getUUID(), -100L) < 60) return 0;
        lastRecipeShare.put(player.getUUID(), ticks);
        var entry = entries.get(id - 1);
        var message = Component.literal("✎ ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(player.getName().getString()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" shared a recipe: ").withStyle(ChatFormatting.GRAY))
                .append(entry.first().getDisplayName()).append(Component.literal(" + ").withStyle(ChatFormatting.GRAY))
                .append(entry.second().getDisplayName()).append(Component.literal(" = ").withStyle(ChatFormatting.GRAY))
                .append(entry.result().getDisplayName());
        for (var viewer : server.getPlayerList().getPlayers()) viewer.sendSystemMessage(message);
        return 1;
    }


    public String inspectRecipe(String first, String second) {
        return engine.inspectRecipe(first, second, overrides, allowed);
    }

    public CompletableFuture<Void> setRecipe(String first, String second, String output, int count) {
        if (!allowed.contains(first) || !allowed.contains(second) || !allowed.contains(output))
            throw new IllegalArgumentException("Choose registered, eligible items.");
        ItemStack stack = BuiltInRegistries.ITEM.getValue(Identifier.parse(output)).getDefaultInstance();
        if (count < 1 || count > stack.getMaxStackSize()) throw new IllegalArgumentException("Output count exceeds its stack limit.");
        return engine.setRecipe(first, second, new RecipeResult(output, count), overrides, allowed);
    }

    public CompletableFuture<Void> forgetRecipe(String first, String second) {
        return engine.forgetRecipe(first, second, overrides);
    }

    public void tick() {
        crafters.tick();
        if (!enabled()) return;
        ticks++;
        cancelAbandonedExchanges();
        if (config.combiningParticles) showCombiningParticles();
        if (config.queueFeedback && ticks % 10 == 0) {
            var notified = new HashSet<UUID>();
            for (var visual : combining.values()) {
                String message = queueMessage(visual.recipeKey());
                if (message == null || !notified.add(visual.player())) continue;
                var viewer = server.getPlayerList().getPlayer(visual.player());
                if (viewer != null) viewer.sendSystemMessage(Component.literal(message)
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }
        if (ticks % config.scanIntervalTicks != 0) return;
        cooldowns.values().removeIf(until -> until <= ticks);
        failedPairs.entrySet().removeIf(entry -> {
            var first = entry.getKey();
            if (first == null || first.isRemoved()) return true;
            entry.getValue().removeIf(second -> second.isRemoved() || first.level() != second.level()
                    || first.distanceToSqr(second) > 0.64);
            return entry.getValue().isEmpty();
        });
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!enabled() || !config.groundFusion || !hasCapacity()) continue;
            ServerLevel world = player.level();
            List<ItemEntity> nearby = new ArrayList<>();
            world.getEntities(EntityTypeTest.forClass(ItemEntity.class), player.getBoundingBox().inflate(8),
                    item -> eligible(item, player), nearby, config.maxNearbyItems + 1);
            // Skip overloaded areas rather than doing an unbounded pairwise scan.
            if (nearby.size() > config.maxNearbyItems) continue;
            for (int first = 0; first < nearby.size() && hasCapacity(); first++) {
                ItemEntity a = nearby.get(first);
                if (!eligible(a, player)) continue;
                for (int second = first + 1; second < nearby.size(); second++) {
                    ItemEntity b = nearby.get(second);
                    if (eligible(b, player) && a.distanceToSqr(b) <= 0.64
                            && !failed(a, b)) {
                        request(world, player, a, b);
                        break;
                    }
                }
            }
        }
    }

    private void cancelAbandonedExchanges() {
        var abandoned = combining.entrySet().stream().filter(entry -> !stillPresent(entry.getValue()))
                .map(Map.Entry::getKey).toList();
        for (long token : abandoned) {
            CombiningVisual visual = combining.remove(token);
            if (visual == null) continue;
            pending--;
            reserved.remove(visual.first(), token);
            reserved.remove(visual.second(), token);
            releaseRecipe(visual.recipeKey());
        }
    }

    private boolean stillPresent(CombiningVisual visual) {
        ServerPlayer player = server.getPlayerList().getPlayer(visual.player());
        var first = visual.world().getEntity(visual.first());
        var second = visual.world().getEntity(visual.second());
        return config.groundFusion && player != null && player.level() == visual.world()
                && first instanceof ItemEntity a && second instanceof ItemEntity b
                && !a.isRemoved() && !b.isRemoved() && a.getOwner() == player && b.getOwner() == player
                && a.distanceToSqr(b) <= 0.64 && a.distanceToSqr(player) <= 100;
    }

    private boolean eligible(ItemEntity entity, ServerPlayer player) {
        ItemStack stack = entity.getItem();
        return !entity.isRemoved() && entity.getOwner() == player && !stack.isEmpty() && !DiscoveryBook.isBook(stack) && FusionDrops.intentional(entity)
                && !reserved.containsKey(entity.getUUID()) && !cooldowns.containsKey(entity.getUUID());
    }

    private void request(ServerLevel world, ServerPlayer player, ItemEntity a, ItemEntity b) {
        if (a == b) return;
        ItemStack liveFirst = a.getItem().copy();
        ItemStack liveSecond = b.getItem().copy();
        PreparedFusion prepared;
        try {
            prepared = prepareFusion(liveFirst, liveSecond);
        } catch (IllegalArgumentException error) {
            deny(world, player, a, b, error.getMessage()); return;
        }
        long requestEpoch = epoch;
        long token = ++requestId;
        reserved.put(a.getUUID(), token);
        reserved.put(b.getUUID(), token);
        combining.put(token, new CombiningVisual(world, player.getUUID(), a.getUUID(), b.getUUID(), prepared.key()));
        pending++;
        ItemStack inputFirst = prepared.inputFirst(), inputSecond = prepared.inputSecond();
        ItemStack preservedFirst = prepared.dataFirst(), preservedSecond = prepared.dataSecond();
        var resolution = prepared.resolution();
        resolution.whenComplete((result, error) -> {
            if (closed) return;
            server.execute(() -> {
                try {
                    if (closed) return;
                    if (requestEpoch != epoch) return;
                    if (combining.remove(token) == null) return;
                    pending--;
                    boolean owns = Objects.equals(reserved.get(a.getUUID()), token)
                            && Objects.equals(reserved.get(b.getUUID()), token);
                    reserved.remove(a.getUUID(), token);
                    reserved.remove(b.getUUID(), token);
                    if (requestEpoch != epoch || !owns) return;
                    cooldowns.put(a.getUUID(), ticks + config.cooldownTicks);
                    cooldowns.put(b.getUUID(), ticks + config.cooldownTicks);
                    if (error != null) {
                        // Provider bodies and credentials are never echoed into chat or logs.
                        Throwable cause = error;
                        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                        deny(world, player, a, b, GenerationFailure.message(cause));
                        InfiniteCraftMod.LOGGER.warn("Fusion request {} failed: {}", token, cause.getMessage());
                        return;
                    }
                    if (!enabled() || player.level() != world
                            || server.getPlayerList().getPlayer(player.getUUID()) != player
                            || a.getOwner() != player || b.getOwner() != player
                            || world.getEntity(a.getUUID()) != a || world.getEntity(b.getUUID()) != b
                            || a.isRemoved() || b.isRemoved() || a.distanceToSqr(b) > 0.64
                            || a.distanceToSqr(player) > 100 || !ItemStack.matches(liveFirst, a.getItem())
                            || !ItemStack.matches(liveSecond, b.getItem()) || !allowed.contains(result.itemId())) return;
                    try {
                        if (!exchange(world, a, b, result, player, inputFirst, inputSecond, preservedFirst, preservedSecond)) {
                            deny(world, player, a, b, "Fusion result unavailable.");
                        }
                    } catch (RuntimeException failure) {
                        InfiniteCraftMod.LOGGER.error("Fusion exchange {} failed", token, failure);
                        deny(world, player, a, b, "Fusion failed.");
                    }
                    } catch (RuntimeException callbackFailure) {
                    rememberFailure(a, b);
                    InfiniteCraftMod.LOGGER.error("Fusion completion could not be processed; pair suppressed", callbackFailure);
                }
            });
        });
    }

    record PreparedFusion(CompletableFuture<RecipeResult> resolution, String key, ItemStack inputFirst, ItemStack inputSecond,
            ItemStack dataFirst, ItemStack dataSecond) {}

    PreparedFusion prepareFusion(ItemStack liveFirst, ItemStack liveSecond) {
        var normalized = discoveries.normalize(FusionOrigin.strip(liveFirst), FusionOrigin.strip(liveSecond));
        ItemStack first = normalized.first();
        ItemStack second = normalized.second();
        ItemStack identityFirst = new DiscoveryCollection.ResultKey(first).stack();
        ItemStack identitySecond = new DiscoveryCollection.ResultKey(second).stack();
        // Only crafted lineage markers count here, not ingredients that merely trigger special generation.
        if (!config.combineSpecialItems && FusionCount.get(first) >= 0 && FusionCount.get(second) >= 0) {
            throw new IllegalArgumentException("Combining special items is disabled.");
        }
        if (FusionCount.exhausted(first, config.specialCombinationLimit)
                || FusionCount.exhausted(second, config.specialCombinationLimit)) {
            throw new IllegalArgumentException(
                    "Combination limit reached (" + config.specialCombinationLimit + "/"
                            + config.specialCombinationLimit + ").");
        }
        if (ItemTraits.inherited(first, second).size() > config.maxTraits) {
            throw new IllegalArgumentException("Trait limit reached.");
        }
        String firstId = BuiltInRegistries.ITEM.getKey(first.getItem()).toString();
        String secondId = BuiltInRegistries.ITEM.getKey(second.getItem()).toString();
        if ((!config.allowItemData && (!first.getComponentsPatch().isEmpty() || !second.getComponentsPatch().isEmpty()))
                || !ItemDataFusion.supported(first) || !ItemDataFusion.supported(second)
                || !allowed.contains(firstId) || !allowed.contains(secondId)) {
            throw new IllegalArgumentException("Unsupported ingredients.");
        }
        boolean hasData = !identityFirst.getComponentsPatch().isEmpty() || !identitySecond.getComponentsPatch().isEmpty();
        RecipeResult known;
        try {
            known = engine.knownRecipe(firstId, secondId, overrides).orElse(null);
        } catch (IllegalStateException error) {
            throw new IllegalArgumentException("Recipe update pending.");
        }
        var effects = PotionFusion.describe(first, second);
        boolean ingredientTriggered = config.generatedTraits && config.specialIngredientTriggers
                && (isSpecial(first) || isSpecial(second));
        boolean generateVariant = hasData && !overrides.containsKey(PairKey.of(firstId, secondId));
        String failureKey;
        try {
            failureKey = hasData ? componentFailureKey(identityFirst, identitySecond) : "";
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Unsupported item data.");
        }
        var variant = generateVariant ? engine.knownVariant(failureKey).orElse(null) : null;
        var saved = generateVariant ? cachedVariantOrBase(variant, known,
                recipe -> compatibility.accepts(recipe.itemId(), first, second)) : known;
        if (generateVariant && saved == known && known != null) generateVariant = false;
        boolean needsGeneration = saved == null;
        Set<String> compatibleIds = hasData ? compatibility.outputs(identityFirst, identitySecond, saved, allowed) : allowed;
        ItemStack dataFirst = first, dataSecond = second;
        int dataPriority = 0;
        if (compatibleIds.isEmpty()) {
            var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            boolean firstWins = ComponentPairKey.firstWins(
                    ItemStack.CODEC.encodeStart(ops, identityFirst).getOrThrow(),
                    ItemStack.CODEC.encodeStart(ops, identitySecond).getOrThrow(), server.overworld().getSeed());
            dataPriority = firstWins ? 1 : 2;
            dataFirst = firstWins ? first : ItemStack.EMPTY;
            dataSecond = firstWins ? ItemStack.EMPTY : second;
            compatibleIds = compatibility.outputs(dataFirst, dataSecond, saved, allowed);
        }
        final ItemStack preservedFirst = dataFirst, preservedSecond = dataSecond;
        if (compatibleIds.isEmpty()) {
            throw new IllegalArgumentException("Item data is incompatible.");
        }
        long requestEpoch = epoch;
        GenerationRequest request = new GenerationRequest(firstId, secondId,
                needsGeneration ? candidateIndex.candidates(firstId, secondId, config.candidateLimit, compatibleIds, config.silliness) : List.of(),
                config.generatedTraits && (ingredientTriggered || server.overworld().getRandom().nextInt(100) < config.specialResultChance)
                        ? VanillaTraits.ids().stream().filter(id -> !id.equals("lucky_block") && !config.disabledTraits.contains(id)).toList() : List.of(), effects, config.maxOutputCount, config.power, config.silliness,
                config.generatedTraits && !PotionFusion.describe(preservedFirst, preservedSecond).isEmpty() ? potionOptions : Map.of(),
                config.maxTraits, ItemTraits.inherited(preservedFirst, preservedSecond),
                !config.specialRarity ? 0 : RecipeQuality.fromRarities(
                        first.getOrDefault(DataComponents.RARITY, Rarity.COMMON).ordinal(),
                        second.getOrDefault(DataComponents.RARITY, Rarity.COMMON).ordinal()), dataPriority);
        Predicate<RecipeResult> validator = result -> validateOnServer(result, first, second, preservedFirst, preservedSecond, requestEpoch);
        // Effect-bearing variants go through generation, not a local brewing shortcut or ID-only discovery.
        var resolution = generateVariant
                ? engine.resolveVariant(request, allowed, validator, failureKey)
                : engine.resolve(request, overrides, allowed, validator);
        return new PreparedFusion(resolution,
                generateVariant ? failureKey : PairKey.of(firstId, secondId),
                first, second, preservedFirst, preservedSecond);
    }

    public static RecipeResult cachedVariantOrBase(RecipeResult variant, RecipeResult base,
            Predicate<RecipeResult> compatible) {
        return variant != null ? variant : base != null && compatible.test(base) ? base : null;
    }

    ItemStack outputFor(RecipeResult result, ItemStack first, ItemStack second,
            ItemStack dataFirst, ItemStack dataSecond) {
        return FusionOutput.create(result, first, second, dataFirst, dataSecond,
                server, config, allowed, this::isSpecial);
    }

    private boolean validateOnServer(RecipeResult result, ItemStack first, ItemStack second, ItemStack dataFirst, ItemStack dataSecond, long requestEpoch) {
        Supplier<Boolean> check = () -> {
            if (closed || requestEpoch != epoch) throw new CancellationException();
            try {
                return !outputFor(result, first, second, dataFirst, dataSecond).isEmpty();
            } catch (IllegalArgumentException invalid) {
                return false;
            }
        };
        if (server.isSameThread()) return check.get();
        var future = server.submit(check);
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            future.cancel(false);
            Thread.currentThread().interrupt();
            throw new CancellationException();
        } catch (ExecutionException | TimeoutException error) {
            future.cancel(false);
            throw new CancellationException();
        }
    }

    private String componentFailureKey(ItemStack first, ItemStack second) {
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var a = ItemStack.CODEC.encodeStart(ops, first.copyWithCount(1)).getOrThrow();
        var b = ItemStack.CODEC.encodeStart(ops, second.copyWithCount(1)).getOrThrow();
        return ComponentPairKey.of(BuiltInRegistries.ITEM.getKey(first.getItem()).toString(),
                BuiltInRegistries.ITEM.getKey(second.getItem()).toString(), a, b);
    }

    private void showCombiningParticles() {
        for (var visual : combining.values()) {
            var player = server.getPlayerList().getPlayer(visual.player());
            if (player == null || player.level() != visual.world() || !enabled()) continue;
            if (!(visual.world().getEntity(visual.first()) instanceof ItemEntity a)
                    || !(visual.world().getEntity(visual.second()) instanceof ItemEntity b)
                    || a.isRemoved() || b.isRemoved() || a.distanceToSqr(b) > .64 || a.distanceToSqr(player) > 100) continue;
            FusionEffects.showWorking(visual.world(), a.position().add(b.position()).scale(.5), ticks);
        }
    }

    public static List<ItemStack> splitOutput(ItemStack template, int quantity) {
        if (template.isEmpty() || quantity < 1 || quantity > 64) throw new IllegalArgumentException("Invalid output quantity");
        List<ItemStack> stacks = new ArrayList<>();
        for (int remaining = quantity; remaining > 0;) {
            int count = Math.min(remaining, template.getMaxStackSize());
            stacks.add(template.copyWithCount(count));
            remaining -= count;
        }
        return stacks;
    }

    private boolean exchange(ServerLevel world, ItemEntity a, ItemEntity b, RecipeResult result, ServerPlayer player,
            ItemStack inputFirst, ItemStack inputSecond, ItemStack dataFirst, ItemStack dataSecond) {
        if (a == b || a.getItem().isEmpty() || b.getItem().isEmpty()) return false;
        ItemStack output = outputFor(result, inputFirst, inputSecond, dataFirst, dataSecond);
        if (output.isEmpty()) return false;
        var midpoint = a.position().add(b.position()).scale(0.5);
        List<ItemEntity> spawned = new ArrayList<>();
        for (ItemStack stack : splitOutput(output, result.count())) {
            ItemEntity entity = new ItemEntity(world, midpoint.x, midpoint.y, midpoint.z, stack);
            entity.setThrower(player);
            entity.setPickUpDelay(20);
            spawned.add(entity);
        }
        ItemStack beforeA = a.getItem().copy();
        ItemStack beforeB = b.getItem().copy();
        // Server-thread exchange: retain entities until the spawn succeeds so rollback is possible.
        try {
            a.setItem(beforeA.copyWithCount(beforeA.getCount() - 1));
            b.setItem(beforeB.copyWithCount(beforeB.getCount() - 1));
            for (ItemEntity entity : spawned) {
                if (!world.addFreshEntity(entity)) {
                    spawned.forEach(ItemEntity::discard);
                    a.setItem(beforeA);
                    b.setItem(beforeB);
                    return false;
                }
            }
        } catch (RuntimeException error) {
            spawned.forEach(ItemEntity::discard);
            a.setItem(beforeA);
            b.setItem(beforeB);
            throw error;
        }
        if (a.getItem().isEmpty()) a.discard();
        if (b.getItem().isEmpty()) b.discard();
        for (ItemEntity entity : spawned) cooldowns.put(entity.getUUID(), ticks + config.cooldownTicks);
        recordDiscovery(inputFirst, inputSecond, output, player);
        try {
            boolean special = isSpecial(output);
            if (config.successSound) world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                    special ? SoundEvents.NOTE_BLOCK_BELL : SoundEvents.NOTE_BLOCK_CHIME,
                    SoundSource.PLAYERS, .25F, special ? 1.1F : 1.5F);
            if (config.successParticles) FusionEffects.showResult(world, midpoint, true, special);
        } catch (RuntimeException cosmeticFailure) {
            InfiniteCraftMod.LOGGER.warn("Fusion completed but success effects could not be sent", cosmeticFailure);
        }
        return true;
    }

    private void deny(ServerLevel world, ServerPlayer player, ItemEntity a, ItemEntity b, String message) {
        rememberFailure(a, b);
        if (player.level() != world || server.getPlayerList().getPlayer(player.getUUID()) != player
                || a.isRemoved() || b.isRemoved() || world.getEntity(a.getUUID()) != a
                || world.getEntity(b.getUUID()) != b || a.distanceToSqr(b) > .64) return;
        var point = a.position().add(b.position()).scale(.5);
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);
        try {
            if (config.failureParticles) FusionEffects.showResult(world, point, false, false);
            if (config.failureSound) world.playSound(null, point.x, point.y, point.z,
                    SoundEvents.NOTE_BLOCK_DIDGERIDOO, SoundSource.PLAYERS, .25F, .5F);
        } catch (RuntimeException cosmeticFailure) {
            InfiniteCraftMod.LOGGER.warn("Could not send fusion failure effects", cosmeticFailure);
        }
    }

    @Override
    public void close() {
        closed = true;
        crafters.close();
        engine.close();
        exportWorker.shutdown();
        try {
            if (!exportWorker.awaitTermination(5, TimeUnit.SECONDS)) {
                InfiniteCraftMod.LOGGER.warn("Discovery exports are still finishing during shutdown");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        reserved.clear();
        combining.clear();
        fusionEnabled = false;
        FusionDrops.clear();
        failedPairs.clear();
    }
}
