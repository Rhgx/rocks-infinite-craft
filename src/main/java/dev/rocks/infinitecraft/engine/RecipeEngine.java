package dev.rocks.infinitecraft.engine;

import dev.rocks.infinitecraft.core.CatalogEntry;
import dev.rocks.infinitecraft.core.GenerationRequest;
import dev.rocks.infinitecraft.core.PairKey;
import dev.rocks.infinitecraft.core.RecipeGenerator;
import dev.rocks.infinitecraft.core.RecipeResult;
import dev.rocks.infinitecraft.core.InvalidRecipeResponseException;
import java.util.function.Predicate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Resolves recipes off the game thread. The caller still validates live game state. */
public final class RecipeEngine implements AutoCloseable {
    private final RecipeStore store;
    private final RecipeGenerator generator;
    private final int generationAttempts;
    private final ThreadPoolExecutor worker;
    private final Map<String, CompletableFuture<RecipeResult>> pending = new java.util.LinkedHashMap<>();
    private final Map<String, CompletableFuture<Void>> edits = new HashMap<>();
    private volatile boolean closed;
    private volatile String activeKey;

    public record QueuePosition(int position, int total) {}

    /** Positions count waiting recipes only, excluding the active request and shared callers. */
    public synchronized QueuePosition queuePosition(String key) {
        int position = 0, total = 0;
        for (var entry : pending.entrySet()) {
            if (entry.getValue().isDone() || entry.getKey().equals(activeKey)) continue;
            total++;
            if (entry.getKey().equals(key)) position = total;
        }
        return position == 0 ? null : new QueuePosition(position, total);
    }

    public RecipeEngine(RecipeStore store, RecipeGenerator generator, int maxQueued) {
        this(store, generator, maxQueued, 3);
    }

    public RecipeEngine(RecipeStore store, RecipeGenerator generator, int maxQueued, int generationAttempts) {
        if (maxQueued < 1) throw new IllegalArgumentException("maxQueued must be positive");
        if (generationAttempts < 1 || generationAttempts > 4) throw new IllegalArgumentException("generationAttempts must be between 1 and 4");
        this.generationAttempts = generationAttempts;
        this.store = store;
        this.generator = generator;
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxQueued), runnable -> {
                    Thread thread = new Thread(runnable, "infinite-craft-recipes");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public CompletableFuture<RecipeResult> resolve(GenerationRequest request, Map<String, RecipeResult> overrides) {
        return resolve(request, overrides, eligible(request));
    }

    public synchronized java.util.Optional<RecipeResult> knownVariant(String key) { return store.get(key); }

    public synchronized CompletableFuture<RecipeResult> resolveVariant(GenerationRequest request,
            Set<String> allowedOutputs, Predicate<RecipeResult> validator, String key) {
        String pair = PairKey.of(request.first(), request.second());
        if (!key.startsWith(pair + "#")) throw new IllegalArgumentException("Invalid component recipe key");
        if (closed || edits.containsKey(pair)) return CompletableFuture.failedFuture(new IllegalStateException("Recipe unavailable"));
        var allowed = Set.copyOf(allowedOutputs);
        var cached = store.get(key);
        if (cached.isPresent()) return checked(cached.orElseThrow(), allowed);
        if (store.isBlocked(key)) return blockedFailure();
        var existing = pending.get(key);
        if (existing != null) return existing.thenApply(result -> validate(result, allowed));
        var future = new CompletableFuture<RecipeResult>();
        pending.put(key, future);
        try { worker.execute(() -> generate(key, request, allowed, validator, future)); }
        catch (RejectedExecutionException error) { pending.remove(key); future.completeExceptionally(error); }
        return future.copy();
    }

    public synchronized CompletableFuture<RecipeResult> resolve(GenerationRequest request,
            Map<String, RecipeResult> overrides, Set<String> allowedOutputs) {
        return resolve(request, overrides, allowedOutputs, result -> true);
    }

    public synchronized CompletableFuture<RecipeResult> resolve(GenerationRequest request,
            Map<String, RecipeResult> overrides, Set<String> allowedOutputs, Predicate<RecipeResult> validator) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Recipe engine is closed"));
        Set<String> allowed = Set.copyOf(allowedOutputs);
        String key = PairKey.of(request.first(), request.second());
        if (edits.containsKey(key)) return CompletableFuture.failedFuture(new IllegalStateException("Recipe is being updated"));
        RecipeResult override = overrides.get(key);
        if (override != null) return checked(override, allowed);
        RecipeResult cached = store.get(key).orElse(null);
        if (cached != null) {
            // Keep unavailable discoveries on disk so reinstalling a mod restores them.
            return checked(cached, allowed);
        }
        if (store.isBlocked(key)) return blockedFailure();
        CompletableFuture<RecipeResult> existing = pending.get(key);
        if (existing != null) return existing.thenApply(result -> validate(result, allowed));
        CompletableFuture<RecipeResult> future = new CompletableFuture<>();
        pending.put(key, future);
        try {
            worker.execute(() -> generate(key, request, allowed, validator, future));
        } catch (RejectedExecutionException exception) {
            pending.remove(key);
            future.completeExceptionally(exception);
        }
        // Each caller gets a dependent future, so cancelling one exchange cannot cancel others.
        return future.thenApply(result -> result);
    }

    private void generate(String key, GenerationRequest request, Set<String> allowed, Predicate<RecipeResult> validator,
            CompletableFuture<RecipeResult> future) {
        activeKey = key;
        try {
            if (future.isDone() || closed) return;
            RecipeResult result = generateCandidate(request, allowed, validator, key, future);
            if (closed || future.isDone()) return;
            if (!store.updateIf(key, result, () -> !closed && !future.isDone())) return;
            synchronized (this) {
                if (closed) return;
                if (future.isDone()) return;
                pending.remove(key, future);
                future.complete(result);
            }
        } catch (Exception exception) {
            synchronized (this) {
                pending.remove(key, future);
                future.completeExceptionally(exception);
            }
        } finally {
            activeKey = null;
        }
    }

    private RecipeResult generateCandidate(GenerationRequest request, Set<String> allowed, Predicate<RecipeResult> validator,
            String failureKey, CompletableFuture<RecipeResult> future) throws Exception {
        Set<String> shortlist = eligible(request);
        if (shortlist.isEmpty()) throw new java.io.IOException("No eligible crafting candidates available");
        for (int attempt = 0; attempt < generationAttempts; attempt++) {
            if (closed || future.isDone() || Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            java.util.List<RecipeResult> candidates;
            try { candidates = generator.generateCandidates(request); }
            catch (InvalidRecipeResponseException invalid) { continue; }
            if (candidates == null) continue;
            for (RecipeResult candidate : candidates.stream().limit(5).toList()) {
                try { validate(candidate, shortlist); validate(candidate, allowed); }
                catch (IllegalArgumentException invalid) { continue; }
                candidate = dev.rocks.infinitecraft.core.RecipeQuality.apply(candidate, request);
                if (candidate.count() <= request.maxOutputCount() && validator.test(candidate)) return candidate;
            }
        }
        if (closed || future.isDone() || Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        if (failureKey != null) store.blockIf(failureKey, () -> !closed && !future.isDone());
        throw new BlockedRecipeException();
    }

    private static CompletableFuture<RecipeResult> blockedFailure() {
        return CompletableFuture.failedFuture(new BlockedRecipeException());
    }

    public synchronized String inspectRecipe(String first, String second, Map<String, RecipeResult> overrides, Set<String> allowed) {
        String key = PairKey.of(first, second);
        if (edits.containsKey(key)) return key + ": update pending";
        RecipeResult result = overrides.get(key);
        String source = "data-pack override";
        if (result == null) { result = store.get(key).orElse(null); source = "saved discovery"; }
        if (result == null) return key + ": " + (store.isBlocked(key) ? "blocked after invalid generation" : pending.containsKey(key) ? "generation pending" : "no recipe saved");
        return key + " -> " + result.count() + " " + result.itemId() + " (" + source + ")"
                + (allowed.contains(result.itemId()) ? "" : " [output unavailable or excluded]");
    }

    public synchronized java.util.Optional<RecipeResult> knownRecipe(String first, String second, Map<String, RecipeResult> overrides) {
        if (closed) throw new IllegalStateException("Recipe engine is closed");
        String key = PairKey.of(first, second);
        if (edits.containsKey(key)) throw new IllegalStateException("Recipe is being updated");
        RecipeResult override = overrides.get(key);
        return override == null ? store.get(key) : java.util.Optional.of(override);
    }

    public CompletableFuture<Void> setRecipe(String first, String second, RecipeResult result,
            Map<String, RecipeResult> overrides, Set<String> allowed) {
        validate(result, allowed);
        return edit(PairKey.of(first, second), result, overrides);
    }

    public CompletableFuture<Void> forgetRecipe(String first, String second, Map<String, RecipeResult> overrides) {
        return edit(PairKey.of(first, second), null, overrides);
    }

    private synchronized CompletableFuture<Void> edit(String key, RecipeResult result, Map<String, RecipeResult> overrides) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Recipe engine is closed"));
        if (overrides.containsKey(key)) return CompletableFuture.failedFuture(new IllegalArgumentException("A data-pack override controls this pair; edit or remove that override first"));
        if (edits.containsKey(key)) return CompletableFuture.failedFuture(new IllegalStateException("Recipe update already pending"));
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            worker.execute(() -> {
                try {
                    if (closed) return;
                    if (!store.updateIf(key, result, () -> !closed && !future.isDone(), true)) return;
                    synchronized (this) {
                        edits.remove(key);
                        future.complete(null);
                    }
                } catch (Exception error) {
                    synchronized (this) { edits.remove(key); future.completeExceptionally(error); }
                }
            });
            edits.put(key, future);
            pending.entrySet().removeIf(entry -> {
                if (!entry.getKey().equals(key) && !entry.getKey().startsWith(key + "#")) return false;
                entry.getValue().cancel(false);
                return true;
            });
        } catch (RejectedExecutionException error) { future.completeExceptionally(error); }
        return future.copy();
    }

    private static Set<String> eligible(GenerationRequest request) {
        return request.candidates().stream().filter(CatalogEntry::craftable)
                .map(CatalogEntry::id).collect(Collectors.toUnmodifiableSet());
    }

    private static CompletableFuture<RecipeResult> checked(RecipeResult result, Set<String> allowed) {
        try {
            return CompletableFuture.completedFuture(validate(result, allowed));
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static RecipeResult validate(RecipeResult result, Set<String> allowed) {
        if (result == null || result.count() < 1 || result.count() > 64 || !allowed.contains(result.itemId())) {
            throw new IllegalArgumentException("Recipe output is unavailable, excluded, or invalid: " + result);
        }
        return result;
    }

    @Override
    public synchronized void close() {
        closed = true;
        worker.shutdownNow();
        cancelPendingGeneration();
        edits.values().forEach(future -> future.cancel(false));
        edits.clear();
    }

    public synchronized void cancelPendingGeneration() {
        pending.values().forEach(future -> future.cancel(false));
        pending.clear();
    }
}
