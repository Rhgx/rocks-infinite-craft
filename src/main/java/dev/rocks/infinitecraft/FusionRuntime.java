package dev.rocks.infinitecraft;

import dev.rocks.infinitecraft.catalog.GameCatalog;
import dev.rocks.infinitecraft.catalog.CatalogSearch;
import dev.rocks.infinitecraft.catalog.RecipeOverrides;
import dev.rocks.infinitecraft.core.CatalogEntry;
import dev.rocks.infinitecraft.core.GenerationRequest;
import dev.rocks.infinitecraft.core.RecipeResult;
import dev.rocks.infinitecraft.engine.RecipeEngine;
import dev.rocks.infinitecraft.engine.RecipeStore;
import dev.rocks.infinitecraft.provider.RecipeGenerators;
import java.io.IOException;
import java.nio.file.Path;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.entity.EntityTypeTest;

/** All entity access occurs on the server thread; workers receive immutable IDs only. */
public final class FusionRuntime implements AutoCloseable {
    private final MinecraftServer server;
    private ModConfig config;
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
    private final Map<PhysicalPair, FailedPair> failedPairs = new HashMap<>();
    private record PhysicalPair(UUID first, UUID second) {
        static PhysicalPair of(ItemEntity a, ItemEntity b) {
            return a.getUUID().compareTo(b.getUUID()) <= 0
                    ? new PhysicalPair(a.getUUID(), b.getUUID()) : new PhysicalPair(b.getUUID(), a.getUUID());
        }
    }
    private record FailedPair(WeakReference<ItemEntity> first, WeakReference<ItemEntity> second) {
        FailedPair(ItemEntity first, ItemEntity second) {
            this(new WeakReference<>(first), new WeakReference<>(second));
        }
    }
    private long ticks;
    private long epoch;
    private long requestId;
    private int pending;
    private volatile boolean closed;

    public FusionRuntime(MinecraftServer server, ModConfig config) throws IOException {
        this.server = server;
        this.config = config;
        dataDirectory = server.getWorldPath(LevelResource.ROOT).resolve("infinitecraft");
        fusionEnabled = FusionWorldState.load(dataDirectory.resolve("fusion-enabled.json"));
        store = new RecipeStore(dataDirectory.resolve("recipes.json"));
        discoveries = new DiscoveryCollection(dataDirectory.resolve("discoveries.json"), server.registryAccess());
        engine = createEngine(config);
        try { reloadCatalog(); }
        catch (IOException | RuntimeException error) { close(); throw error; }
    }

    private RecipeEngine createEngine(ModConfig settings) {
        var provider = RecipeGenerators.create(settings.provider);
        return new RecipeEngine(store, new dev.rocks.infinitecraft.core.RecipeGenerator() {
            public RecipeResult generate(GenerationRequest request) throws Exception {
                if (!settings.generationEnabled) throw new IllegalStateException("Recipe generation is disabled");
                return provider.generate(request);
            }
            public List<RecipeResult> generateCandidates(GenerationRequest request) throws Exception {
                if (!settings.generationEnabled) throw new IllegalStateException("Recipe generation is disabled");
                return provider.generateCandidates(request);
            }
        }, settings.maxPending, settings.generationAttempts);
    }

    /** Preserve one synchronized store and export worker across settings changes. */
    public void reconfigure(ModConfig settings) throws IOException {
        ModConfig previous = config;
        RecipeEngine replacement = createEngine(settings);
        config = settings;
        try { reloadCatalog(); }
        catch (IOException | RuntimeException error) {
            config = previous;
            replacement.close();
            throw error;
        }
        RecipeEngine old = engine;
        engine = replacement;
        old.close();
        for (var player : server.getPlayerList().getPlayers()) DiscoveryBook.sync(player);
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
        epoch++;
        engine.cancelPendingGeneration();
        reserved.clear();
        combining.clear();
        pending = 0;
        // Cancel physical exchanges from older snapshots without cancelling shared recipe discoveries.
        List<CatalogEntry> export = next;
        exportWorker.execute(() -> {
            try { GameCatalog.export(dataDirectory.resolve("catalog.json"), export); }
            catch (IOException error) { InfiniteCraftMod.LOGGER.error("Could not export live catalog", error); }
        });
    }

    public String status() {
        return "Infinite Craft: " + catalog.size() + " catalog entries, " + allowed.size()
                + " eligible items, " + overrides.size() + " explicit recipes, " + pending
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

    public boolean enabled() { return !closed && config.enabled && fusionEnabled; }

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

    public void welcome(net.minecraft.server.level.ServerPlayer player) {
        if (!config.enabled || !config.joinMessage) return;
        if (enabled() || !InfiniteCraftMod.canControlFusion(player.createCommandSourceStack())) {
            player.sendSystemMessage(Component.literal("Infinite Craft: fusion is " + (enabled() ? "on." : "off."))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            return;
        }
        player.sendSystemMessage(Component.literal("Infinite Craft: fusion is off. ").withStyle(net.minecraft.ChatFormatting.GRAY)
                .append(Component.literal("[Enable fusion]").withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                                Component.literal("/fusion enable").withStyle(net.minecraft.ChatFormatting.GRAY)))
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/fusion enable")))));
    }

    public List<DiscoveryCollection.Entry> discoveries() { return discoveries.entries(); }
    public List<DiscoveryCollection.Entry> discoveries(ServerPlayer player) {
        return config.personalBook ? discoveries.entries(player.getUUID(), player.getName().getString()) : discoveries.entries();
    }
    public boolean soulboundBook() { return config.soulboundBook; }
    public boolean personalBook() { return config.personalBook; }

    private boolean isSpecial(ItemStack stack) {
        return ItemDataFusion.specialIngredient(stack, config.specialRarity, config.specialEnchantments,
                config.specialPotions, config.specialCustomData);
    }

    private void recordDiscovery(ItemStack first, ItemStack second, ItemStack output, ServerPlayer player) {
        try {
            int revision = discoveries.revision();
            boolean firstDiscovery = discoveries.record(first, second, output, player.getName().getString(), player.getUUID());
            if (revision == discoveries.revision()) return;
            var snapshot = discoveries.snapshot();
            int discoveryCount = firstDiscovery ? discoveries.outputCount() : 0;
            if (firstDiscovery && config.milestoneMessages && isMilestone(discoveryCount)) {
                var milestone = Component.literal(discoveryCount + " discoveries!")
                        .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD);
                for (var viewer : server.getPlayerList().getPlayers()) viewer.sendSystemMessage(milestone);
            }
            exportWorker.execute(() -> {
                try { discoveries.save(snapshot); }
                catch (IOException error) { InfiniteCraftMod.LOGGER.error("Could not save discovery collection", error); }
            });
            if (firstDiscovery && (isSpecial(output) ? config.specialDiscoveryMessage : config.firstDiscoveryMessage)) {
                var message = discoveryMessage(output, player.getName().getString(), isSpecial(output));
                for (var viewer : server.getPlayerList().getPlayers()) viewer.sendSystemMessage(message);
            }
        } catch (RuntimeException error) { InfiniteCraftMod.LOGGER.error("Fusion completed but discovery could not be recorded", error); }
    }

    static Component discoveryMessage(ItemStack output, String discoverer) {
        return discoveryMessage(output, discoverer, ItemDataFusion.specialIngredient(output));
    }

    private static Component discoveryMessage(ItemStack output, String discoverer, boolean special) {
        return Component.empty()
                .append(Component.literal(special ? "[SPECIAL] " : "[FIRST] ")
                        .withStyle(style -> style.withColor(special ? net.minecraft.ChatFormatting.LIGHT_PURPLE : net.minecraft.ChatFormatting.GOLD).withBold(true)))
                .append(Component.literal(discoverer).withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(Component.literal(" found ").withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(output.getDisplayName());
    }

    static boolean isMilestone(int count) {
        for (long scale = 10; scale <= count; scale *= 10) {
            if (count == scale || count == scale * 5 / 2 || count == scale * 5) return true;
        }
        return false;
    }

    public int shareRecipe(ServerPlayer player, int id) {
        var entries = discoveries.entries();
        if (id < 1 || id > entries.size()) return 0;
        if (config.personalBook && !discoveries.owns(id, player.getUUID(), player.getName().getString())) return 0;
        player.connection.send(net.minecraft.network.protocol.common.ClientboundClearDialogPacket.INSTANCE);
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, net.minecraft.sounds.SoundSource.MASTER,
                player.getX(), player.getEyeY(), player.getZ(), 0.3F, 1.0F, player.getRandom().nextLong()));
        if (ticks - lastRecipeShare.getOrDefault(player.getUUID(), -100L) < 60) return 0;
        lastRecipeShare.put(player.getUUID(), ticks);
        var entry = entries.get(id - 1);
        var message = Component.literal(player.getName().getString() + ": ").withStyle(net.minecraft.ChatFormatting.GRAY)
                .append(entry.first().getDisplayName()).append(" + ")
                .append(entry.second().getDisplayName()).append(" = ").append(entry.result().getDisplayName());
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
        if (!enabled()) return;
        ticks++;
        if (config.combiningParticles && ticks % 2 == 0) showCombiningParticles();
        if (config.queueFeedback && ticks % 10 == 0) {
            var notified = new HashSet<UUID>();
            for (var visual : combining.values()) {
                var queue = engine.queuePosition(visual.recipeKey());
                if (queue == null || !notified.add(visual.player())) continue;
                var viewer = server.getPlayerList().getPlayer(visual.player());
                if (viewer != null) viewer.sendSystemMessage(Component.literal("Fusion queued"
                        + ".".repeat(1 + (int) ((ticks / 10) % 3)) + " (" + queue.position() + "/" + queue.total() + ")")
                        .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            }
        }
        if (ticks % config.scanIntervalTicks != 0) return;
        cooldowns.values().removeIf(until -> until <= ticks);
        failedPairs.entrySet().removeIf(entry -> {
            ItemEntity first = entry.getValue().first.get();
            ItemEntity second = entry.getValue().second.get();
            // Keep only UUID suppression when chunks unload; never retain the chunk's entities.
            // A reloaded physical pair stays suppressed until it is observed separated.
            for (ServerLevel world : server.getAllLevels()) {
                if (world.getEntity(entry.getKey().first) instanceof ItemEntity loaded) first = loaded;
                if (world.getEntity(entry.getKey().second) instanceof ItemEntity loaded) second = loaded;
            }
            if (first == null || second == null) return false;
            if (first != entry.getValue().first.get() || second != entry.getValue().second.get()) {
                entry.setValue(new FailedPair(first, second));
            }
            if (first.getRemovalReason() != null && first.getRemovalReason().shouldDestroy()) return true;
            if (second.getRemovalReason() != null && second.getRemovalReason().shouldDestroy()) return true;
            return !first.isRemoved() && !second.isRemoved()
                    && (first.level() != second.level() || first.distanceToSqr(second) > 0.64);
        });
        // Retain failed identities instead of evicting them into automatic paid retries.
        if (failedPairs.size() >= 4096) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!enabled() || pending >= config.maxPending) continue;
            ServerLevel world = player.level();
            List<ItemEntity> nearby = new ArrayList<>();
            world.getEntities(EntityTypeTest.forClass(ItemEntity.class), player.getBoundingBox().inflate(8),
                    item -> eligible(item, player), nearby, config.maxNearbyItems + 1);
            // Skip overloaded areas rather than doing an unbounded pairwise scan.
            if (nearby.size() > config.maxNearbyItems) continue;
            for (int first = 0; first < nearby.size() && pending < config.maxPending; first++) {
                ItemEntity a = nearby.get(first);
                if (!eligible(a, player)) continue;
                for (int second = first + 1; second < nearby.size(); second++) {
                    ItemEntity b = nearby.get(second);
                    if (eligible(b, player) && a.distanceToSqr(b) <= 0.64
                            && !failedPairs.containsKey(PhysicalPair.of(a, b))) {
                        request(world, player, a, b);
                        break;
                    }
                }
            }
        }
    }

    private boolean eligible(ItemEntity entity, ServerPlayer player) {
        ItemStack stack = entity.getItem();
        return !entity.isRemoved() && entity.getOwner() == player && !stack.isEmpty() && !DiscoveryBook.isBook(stack) && FusionDrops.intentional(entity)
                && !reserved.containsKey(entity.getUUID()) && !cooldowns.containsKey(entity.getUUID());
    }

    private void request(ServerLevel world, ServerPlayer player, ItemEntity a, ItemEntity b) {
        if (failedPairs.size() >= 4096) return;
        if (a == b && a.getItem().getCount() < 2) return;
        ItemStack first = a.getItem().copy();
        ItemStack second = b.getItem().copy();
        // Only crafted lineage markers count here, not ingredients that merely trigger special generation.
        if (!config.combineSpecialItems && FusionCount.get(first) >= 0 && FusionCount.get(second) >= 0) {
            deny(world, player, a, b, "Combining special items is disabled.");
            return;
        }
        if (FusionCount.exhausted(first) || FusionCount.exhausted(second)) {
            deny(world, player, a, b, "Combination limit reached (5/5).");
            return;
        }
        if (ItemTraits.inherited(first, second).size() > config.maxTraits) {
            deny(world, player, a, b, "Trait limit reached.");
            return;
        }
        String firstId = BuiltInRegistries.ITEM.getKey(first.getItem()).toString();
        String secondId = BuiltInRegistries.ITEM.getKey(second.getItem()).toString();
        if ((!config.allowItemData && (!first.getComponentsPatch().isEmpty() || !second.getComponentsPatch().isEmpty()))
                || !ItemDataFusion.supported(first) || !ItemDataFusion.supported(second)
                || !allowed.contains(firstId) || !allowed.contains(secondId)) {
            deny(world, player, a, b, "Unsupported ingredients.");
            return;
        }
        boolean hasData = !first.getComponentsPatch().isEmpty() || !second.getComponentsPatch().isEmpty();
        RecipeResult known;
        try { known = engine.knownRecipe(firstId, secondId, overrides).orElse(null); }
        catch (IllegalStateException error) {
            deny(world, player, a, b, "Recipe update pending.");
            return;
        }
        var effects = PotionFusion.describe(first, second);
        boolean ingredientTriggered = config.generatedTraits && config.specialIngredientTriggers
                && (isSpecial(first) || isSpecial(second));
        boolean generateVariant = hasData && !overrides.containsKey(dev.rocks.infinitecraft.core.PairKey.of(firstId, secondId));
        String failureKey;
        try { failureKey = hasData ? componentFailureKey(first, second) : ""; }
        catch (RuntimeException error) { deny(world, player, a, b, "Unsupported item data."); return; }
        boolean needsGeneration = generateVariant ? engine.knownVariant(failureKey).isEmpty() : known == null;
        var saved = generateVariant ? engine.knownVariant(failureKey).orElse(null) : known;
        Set<String> compatibleIds = hasData ? (needsGeneration ? compatibleOutputs(first, second)
                : saved != null && allowed.contains(saved.itemId()) && !ItemDataFusion.prepare(BuiltInRegistries.ITEM.getValue(Identifier.parse(saved.itemId()))
                        .getDefaultInstance(), first, second, false).isEmpty() ? allowed : Set.of()) : allowed;
        ItemStack dataFirst = first, dataSecond = second;
        int dataPriority = 0;
        if (compatibleIds.isEmpty()) {
            var ops = server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
            boolean firstWins = ComponentPairKey.firstWins(
                    ItemStack.CODEC.encodeStart(ops, first.copyWithCount(1)).getOrThrow(),
                    ItemStack.CODEC.encodeStart(ops, second.copyWithCount(1)).getOrThrow(), server.overworld().getSeed());
            dataPriority = firstWins ? 1 : 2;
            dataFirst = firstWins ? first : ItemStack.EMPTY;
            dataSecond = firstWins ? ItemStack.EMPTY : second;
            compatibleIds = compatibleOutputs(dataFirst, dataSecond);
        }
        final ItemStack preservedFirst = dataFirst, preservedSecond = dataSecond;
        if (compatibleIds.isEmpty()) {
            deny(world, player, a, b, "Item data is incompatible.");
            return;
        }
        long requestEpoch = epoch;
        long token = ++requestId;
        reserved.put(a.getUUID(), token);
        reserved.put(b.getUUID(), token);
        combining.put(token, new CombiningVisual(world, player.getUUID(), a.getUUID(), b.getUUID(),
                generateVariant ? failureKey : dev.rocks.infinitecraft.core.PairKey.of(firstId, secondId)));
        pending++;
        GenerationRequest request = new GenerationRequest(firstId, secondId,
                needsGeneration ? candidateIndex.candidates(firstId, secondId, config.candidateLimit, compatibleIds) : List.of(),
                config.generatedTraits && (ingredientTriggered || world.getRandom().nextInt(100) < config.specialResultChance)
                        ? VanillaTraits.ids() : List.of(), effects, config.maxOutputCount, config.power, config.silliness,
                config.generatedTraits && !PotionFusion.describe(preservedFirst, preservedSecond).isEmpty() ? potionOptions : Map.of(),
                config.maxTraits, ItemTraits.inherited(preservedFirst, preservedSecond),
                !config.specialRarity ? 0 : dev.rocks.infinitecraft.core.RecipeQuality.fromRarities(
                        first.getOrDefault(net.minecraft.core.component.DataComponents.RARITY, net.minecraft.world.item.Rarity.COMMON).ordinal(),
                        second.getOrDefault(net.minecraft.core.component.DataComponents.RARITY, net.minecraft.world.item.Rarity.COMMON).ordinal()), dataPriority);
        java.util.function.Predicate<RecipeResult> validator = result -> validateOnServer(result, first, second, preservedFirst, preservedSecond, requestEpoch);
        // Effect-bearing variants go through generation, not a local brewing shortcut or ID-only discovery.
        var resolution = generateVariant
                ? engine.resolveVariant(request, allowed, validator, failureKey)
                : engine.resolve(request, overrides, allowed, validator);
        resolution.whenComplete((result, error) -> {
            if (closed) return;
            server.execute(() -> {
                try {
                    if (closed) return;
                    if (requestEpoch != epoch) return;
                    pending--;
                    combining.remove(token);
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
                        deny(world, player, a, b, cause instanceof dev.rocks.infinitecraft.engine.BlockedRecipeException
                                ? "Combination blocked." : "Fusion failed. Pick up and drop to retry.");
                        InfiniteCraftMod.LOGGER.warn("Fusion request {} failed: {}", token, cause.getMessage());
                        return;
                    }
                    if (!enabled() || player.level() != world
                            || server.getPlayerList().getPlayer(player.getUUID()) != player
                            || a.getOwner() != player || b.getOwner() != player
                            || world.getEntity(a.getUUID()) != a || world.getEntity(b.getUUID()) != b
                            || a.isRemoved() || b.isRemoved() || a.distanceToSqr(b) > 0.64
                            || a == b && a.getItem().getCount() < 2
                            || a.distanceToSqr(player) > 100 || !ItemStack.matches(first, a.getItem())
                            || !ItemStack.matches(second, b.getItem()) || !allowed.contains(result.itemId())) return;
                    try {
                        if (!exchange(world, a, b, result, player, preservedFirst, preservedSecond)) {
                            deny(world, player, a, b, "Fusion result unavailable.");
                        }
                    }
                    catch (RuntimeException failure) {
                        InfiniteCraftMod.LOGGER.error("Fusion exchange {} failed", token, failure);
                        deny(world, player, a, b, "Fusion failed.");
                    }
                    } catch (RuntimeException callbackFailure) {
                    failedPairs.put(PhysicalPair.of(a, b), new FailedPair(a, b));
                    InfiniteCraftMod.LOGGER.error("Fusion completion could not be processed; pair suppressed", callbackFailure);
                }
            });
        });
    }

    private Set<String> compatibleOutputs(ItemStack first, ItemStack second) {
        return catalog.stream().filter(entry -> entry.kind().equals("item") && entry.craftable())
                .filter(entry -> !ItemDataFusion.prepare(BuiltInRegistries.ITEM.getValue(Identifier.parse(entry.id()))
                        .getDefaultInstance(), first, second, false).isEmpty())
                .map(CatalogEntry::id).collect(Collectors.toUnmodifiableSet());
    }

    private ItemStack outputFor(RecipeResult result, ItemStack first, ItemStack second, ItemStack dataFirst, ItemStack dataSecond) {
        if (result.traits().size() > config.maxTraits) return ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(result.itemId());
        if (id == null || !allowed.contains(result.itemId()) || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        ItemStack output = BuiltInRegistries.ITEM.getValue(id).getDefaultInstance();
        if (output.isEmpty() || result.count() < 1 || result.count() > 64) return ItemStack.EMPTY;
        output.setCount(1);
        var brewed = config.allowItemData ? ItemDataFusion.brew(server.potionBrewing(), dataFirst, dataSecond) : ItemStack.EMPTY;
        boolean applyBrewing = !brewed.isEmpty() && brewed.is(output.getItem());
        output = ItemDataFusion.prepare(applyBrewing ? brewed : output, dataFirst, dataSecond, applyBrewing);
        if (!output.isEmpty() && !PotionFusion.applyChoice(output, result.potion())) return ItemStack.EMPTY;
        if (!output.isEmpty()) output = VanillaTraits.apply(output, result.traits(), result.name(), result.strengths(), result.activations(), result.nameStyle(), result.nameParts());
        if (!output.isEmpty() && !result.dyeColor().isEmpty()) {
            if (!output.is(net.minecraft.tags.ItemTags.CAULDRON_CAN_REMOVE_DYE)) return ItemStack.EMPTY;
            output.set(net.minecraft.core.component.DataComponents.DYED_COLOR,
                    new net.minecraft.world.item.component.DyedItemColor(Integer.parseInt(result.dyeColor().substring(1), 16)));
        }
        if (!output.isEmpty() && !result.itemModel().isEmpty()) {
            var modelItem = Identifier.parse(result.itemModel());
            if (!modelItem.getNamespace().equals("minecraft") || !BuiltInRegistries.ITEM.containsKey(modelItem)) return ItemStack.EMPTY;
            var model = BuiltInRegistries.ITEM.getValue(modelItem).getDefaultInstance()
                    .get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
            if (model == null || !model.getNamespace().equals("minecraft")) return ItemStack.EMPTY;
            output.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, model);
        }
        if (!output.isEmpty() && !FusionCount.apply(output, first, second, this::isSpecial)) return ItemStack.EMPTY;
        if (!output.isEmpty() && !ItemTraits.apply(output, dataFirst, dataSecond, result.traits(), config.maxTraits)) return ItemStack.EMPTY;
        if (output.isEmpty() || output.getCount() > output.getMaxStackSize()) return ItemStack.EMPTY;
        return ItemStack.validateStrict(output).result().orElse(ItemStack.EMPTY);
    }

    private boolean validateOnServer(RecipeResult result, ItemStack first, ItemStack second, ItemStack dataFirst, ItemStack dataSecond, long requestEpoch) {
        java.util.function.Supplier<Boolean> check = () -> {
            if (closed || requestEpoch != epoch) throw new java.util.concurrent.CancellationException();
            try { return !outputFor(result, first, second, dataFirst, dataSecond).isEmpty(); }
            catch (IllegalArgumentException invalid) { return false; }
        };
        if (server.isSameThread()) return check.get();
        var future = server.submit(check);
        try { return future.get(15, java.util.concurrent.TimeUnit.SECONDS); }
        catch (InterruptedException error) {
            future.cancel(false);
            Thread.currentThread().interrupt();
            throw new java.util.concurrent.CancellationException();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException error) {
            future.cancel(false);
            throw new java.util.concurrent.CancellationException();
        }
    }

    private String componentFailureKey(ItemStack first, ItemStack second) {
        var ops = server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
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
            var center = a.position().add(b.position()).scale(.5);
            // Both trails orbit the same midpoint, always opposite one another.
            double radius = .24 + .04 * Math.sin(ticks * .08);
            for (int side = 0; side < 2; side++) {
                for (int trail = 0; trail < 2; trail++) {
                    double angle = (ticks - trail * 2) * .16 + side * Math.PI;
                    sendParticleOutsideBlocks(visual.world(), ParticleTypes.ELECTRIC_SPARK,
                            center.x + Math.cos(angle) * radius, center.y + .5 + Math.sin(angle * 2) * .08,
                            center.z + Math.sin(angle) * radius);
                }
            }
        }
    }

    static List<ItemStack> splitOutput(ItemStack template, int quantity) {
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
            ItemStack dataFirst, ItemStack dataSecond) {
        boolean sameEntity = a == b;
        if (a.getItem().isEmpty() || b.getItem().isEmpty() || sameEntity && a.getItem().getCount() < 2) return false;
        ItemStack output = outputFor(result, a.getItem(), b.getItem(), dataFirst, dataSecond);
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
            a.setItem(beforeA.copyWithCount(beforeA.getCount() - (sameEntity ? 2 : 1)));
            if (!sameEntity) b.setItem(beforeB.copyWithCount(beforeB.getCount() - 1));
            for (ItemEntity entity : spawned) {
                if (!world.addFreshEntity(entity)) {
                    spawned.forEach(ItemEntity::discard);
                    a.setItem(beforeA);
                    if (!sameEntity) b.setItem(beforeB);
                    return false;
                }
            }
        } catch (RuntimeException error) {
            spawned.forEach(ItemEntity::discard);
            a.setItem(beforeA);
            if (!sameEntity) b.setItem(beforeB);
            throw error;
        }
        if (a.getItem().isEmpty()) a.discard();
        if (!sameEntity && b.getItem().isEmpty()) b.discard();
        for (ItemEntity entity : spawned) cooldowns.put(entity.getUUID(), ticks + config.cooldownTicks);
        recordDiscovery(beforeA, beforeB, output, player);
        try {
            boolean special = isSpecial(output);
            if (config.successSound) world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                    special ? SoundEvents.NOTE_BLOCK_BELL : SoundEvents.NOTE_BLOCK_CHIME,
                    SoundSource.PLAYERS, .25F, special ? 1.1F : 1.5F);
            if (config.successParticles) {
                if (special) showSpecialParticles(world, midpoint);
                else showResultParticles(world, midpoint, true);
            }
        } catch (RuntimeException cosmeticFailure) {
            InfiniteCraftMod.LOGGER.warn("Fusion completed but success effects could not be sent", cosmeticFailure);
        }
        return true;
    }

    private void deny(ServerLevel world, ServerPlayer player, ItemEntity a, ItemEntity b, String message) {
        failedPairs.put(PhysicalPair.of(a, b), new FailedPair(a, b));
        if (player.level() != world || server.getPlayerList().getPlayer(player.getUUID()) != player
                || a.isRemoved() || b.isRemoved() || world.getEntity(a.getUUID()) != a
                || world.getEntity(b.getUUID()) != b || a.distanceToSqr(b) > .64) return;
        var point = a.position().add(b.position()).scale(.5);
        player.sendSystemMessage(Component.literal(message), true);
        try {
            if (config.failureParticles) showResultParticles(world, point, false);
            if (config.failureSound) world.playSound(null, point.x, point.y, point.z,
                    SoundEvents.NOTE_BLOCK_DIDGERIDOO, SoundSource.PLAYERS, .25F, .5F);
        } catch (RuntimeException cosmeticFailure) {
            InfiniteCraftMod.LOGGER.warn("Could not send fusion failure effects", cosmeticFailure);
        }
    }

    private static void showSpecialParticles(ServerLevel world, net.minecraft.world.phys.Vec3 center) {
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 2;
            sendParticleOutsideBlocks(world, i % 2 == 0 ? ParticleTypes.WITCH : ParticleTypes.END_ROD,
                    center.x + Math.cos(angle) * .3, center.y + .4 + i * .08,
                    center.z + Math.sin(angle) * .3);
        }
    }

    private static void showResultParticles(ServerLevel world, net.minecraft.world.phys.Vec3 center,
            boolean success) {
        // Different native textures and motion distinguish outcomes without colored dust.
        var particle = success ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SMOKE;
        int count = success ? 4 : 3;
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            sendParticleOutsideBlocks(world, particle, center.x + Math.cos(angle) * .2,
                    center.y + .5 + (success ? (i % 2) * .15 : 0), center.z + Math.sin(angle) * .2);
        }
    }

    private static void sendParticleOutsideBlocks(ServerLevel world, ParticleOptions particle,
            double x, double y, double z) {
        // Keep spawn positions outside blocks, with room for the visible particle. No downward launch velocity.
        if (world.noBlockCollision(null, new net.minecraft.world.phys.AABB(x - .15, y - .15, z - .15,
                x + .15, y + .15, z + .15)))
            world.sendParticles(particle, x, y, z, 0, 0, 0, 0, 0);
    }

    @Override public void close() {
        closed = true;
        engine.close();
        exportWorker.shutdown();
        try {
            if (!exportWorker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                InfiniteCraftMod.LOGGER.warn("Discovery exports are still finishing during shutdown");
            }
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        reserved.clear();
        combining.clear();
        fusionEnabled = false;
        FusionDrops.clear();
        failedPairs.clear();
    }
}
