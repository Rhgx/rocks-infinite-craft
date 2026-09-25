package dev.rocks.infinitecraft.engine;

import dev.rocks.infinitecraft.core.CatalogEntry;
import dev.rocks.infinitecraft.core.GenerationRequest;
import dev.rocks.infinitecraft.core.PairKey;
import dev.rocks.infinitecraft.core.RecipeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecipeEngineTest {
    @TempDir Path directory;
    private static final RecipeResult STONE = new RecipeResult("minecraft:stone", 1);
    private static final CatalogEntry ENTRY = new CatalogEntry("minecraft:stone", "item", "minecraft",
            "Stone", List.of(), true, "");

    private GenerationRequest request(String first, String second) {
        return new GenerationRequest(first, second, List.of(ENTRY));
    }

    @Test void administrativeEditsCancelOlderGenerationAndPersist() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Path file = directory.resolve("recipes.json");
        RecipeStore store = new RecipeStore(file);
        String first = "minecraft:a", second = "minecraft:b";
        RecipeResult correction = new RecipeResult("minecraft:stone", 3);
        try (RecipeEngine engine = new RecipeEngine(store, ignored -> {
            started.countDown(); release.await(); return STONE;
        }, 4)) {
            var generation = engine.resolve(request(first, second), Map.of());
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var edit = engine.setRecipe(second, first, correction, Map.of(), Set.of("minecraft:stone"));
            assertTrue(generation.isCompletedExceptionally());
            assertThrows(ExecutionException.class, () -> engine.resolve(request(first, second), Map.of()).get());
            release.countDown();
            edit.get(2, TimeUnit.SECONDS);
            assertEquals(correction, new RecipeStore(file).get(PairKey.of(first, second)).orElseThrow());
            assertTrue(engine.inspectRecipe(first, second, Map.of(), Set.of()).contains("unavailable"));
            engine.forgetRecipe(first, second, Map.of()).get(2, TimeUnit.SECONDS);
            assertTrue(new RecipeStore(file).get(PairKey.of(first, second)).isEmpty());
        } finally { release.countDown(); }
    }

    @Test void componentRecipesSharePendingWorkAndPersistSeparatelyFromBaseRecipes() throws Exception {
        Path file = directory.resolve("recipes.json");
        RecipeStore store = new RecipeStore(file);
        String first = "minecraft:a", second = "minecraft:b", key = PairKey.of(first, second) + "#components";
        store.put(PairKey.of(first, second), new RecipeResult("minecraft:stone", 3));
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        try (RecipeEngine engine = new RecipeEngine(store, ignored -> {
            calls.incrementAndGet(); started.countDown(); release.await(); return STONE;
        }, 2)) {
            var a = engine.resolveVariant(request(first, second), Set.of("minecraft:stone"), result -> true, key);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var b = engine.resolveVariant(request(second, first), Set.of("minecraft:stone"), result -> true, key);
            assertNull(engine.queuePosition(key));
            String queuedKey = PairKey.of(first, second) + "#other";
            var queued = engine.resolveVariant(request(first, second), Set.of("minecraft:stone"), result -> true, queuedKey);
            assertEquals(new RecipeEngine.QueuePosition(1, 1), engine.queuePosition(queuedKey));
            String lastKey = PairKey.of(first, second) + "#last";
            var last = engine.resolveVariant(request(first, second), Set.of("minecraft:stone"), result -> true, lastKey);
            assertEquals(new RecipeEngine.QueuePosition(1, 2), engine.queuePosition(queuedKey));
            assertEquals(new RecipeEngine.QueuePosition(2, 2), engine.queuePosition(lastKey));
            release.countDown();
            assertEquals(STONE, queued.get(2, TimeUnit.SECONDS));
            assertNull(engine.queuePosition(queuedKey));
            assertEquals(STONE, last.get(2, TimeUnit.SECONDS));
            assertNull(engine.queuePosition(lastKey));
            assertEquals(STONE, a.get(2, TimeUnit.SECONDS));
            assertEquals(STONE, b.get(2, TimeUnit.SECONDS));
            assertEquals(STONE, engine.resolveVariant(request(first, second), Set.of("minecraft:stone"), result -> true, key).get());
            assertEquals(3, calls.get());
            assertEquals(STONE, new RecipeStore(file).get(key).orElseThrow());
            assertEquals(3, store.get(PairKey.of(first, second)).orElseThrow().count());
            engine.forgetRecipe(first, second, Map.of()).get(2, TimeUnit.SECONDS);
            assertTrue(new RecipeStore(file).get(key).isEmpty());
        } finally { release.countDown(); }
    }

    @Test void correctionsCancelVariantRequestsAndKnownRecipesRespectEdits() throws Exception {
        RecipeStore store = new RecipeStore(directory.resolve("recipes.json"));
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        String first = "minecraft:a", second = "minecraft:b";
        try (RecipeEngine engine = new RecipeEngine(store, ignored -> {
            started.countDown(); release.await(); return STONE;
        }, 2)) {
            var componentRequest = engine.resolveVariant(request(first, second), Set.of("minecraft:stone"), result -> true, PairKey.of(first, second) + "#components");
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var correction = new RecipeResult("minecraft:stone", 2);
            var edit = engine.setRecipe(second, first, correction, Map.of(), Set.of("minecraft:stone"));
            assertTrue(componentRequest.isCompletedExceptionally());
            assertThrows(IllegalStateException.class, () -> engine.knownRecipe(first, second, Map.of()));
            release.countDown();
            edit.get(2, TimeUnit.SECONDS);
            assertEquals(correction, engine.knownRecipe(first, second, Map.of()).orElseThrow());
            assertEquals(STONE, engine.knownRecipe(first, second, Map.of(PairKey.of(first, second), STONE)).orElseThrow());
        } finally { release.countDown(); }
    }

    @Test void closedEngineCannotOverwriteReplacementCorrection() throws Exception {
        RecipeStore store = new RecipeStore(directory.resolve("recipes.json"));
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
        String first = "minecraft:a", second = "minecraft:b";
        RecipeEngine old = new RecipeEngine(store, ignored -> {
            started.countDown();
            boolean waiting = true;
            // Simulate a provider that returns a result even after cancellation interrupts it.
            while (waiting) try { release.await(); waiting = false; } catch (InterruptedException ignoredInterrupt) { waiting = true; }
            finished.countDown();
            return STONE;
        }, 2);
        try (RecipeEngine replacement = new RecipeEngine(store, ignored -> STONE, 2)) {
            var abandoned = old.resolve(request(first, second), Map.of());
            assertTrue(started.await(2, TimeUnit.SECONDS));
            old.close();
            RecipeResult correction = new RecipeResult("minecraft:stone", 4);
            replacement.setRecipe(first, second, correction, Map.of(), Set.of("minecraft:stone")).get(2, TimeUnit.SECONDS);
            release.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertTrue(abandoned.isCompletedExceptionally());
            assertEquals(correction, store.get(PairKey.of(first, second)).orElseThrow());
        } finally { release.countDown(); old.close(); }
    }

    @Test void discoveriesPersistAndMissingOutputsStayStored() throws Exception {
        Path file = directory.resolve("recipes.json");
        String key = PairKey.of("minecraft:dirt", "minecraft:water_bucket");
        RecipeStore store = new RecipeStore(file);
        store.put(key, STONE);
        RecipeStore reloaded = new RecipeStore(file);
        assertEquals(STONE, reloaded.get(key).orElseThrow());
        try (RecipeEngine engine = new RecipeEngine(reloaded, request -> { throw new AssertionError(); }, 2)) {
            assertThrows(Exception.class, () -> engine.resolve(request("minecraft:dirt", "minecraft:water_bucket"),
                    Map.of(), Set.of()).get(2, TimeUnit.SECONDS));
            var override = new RecipeResult("example:machine", 1);
            assertEquals(override, engine.resolve(request("minecraft:dirt", "minecraft:water_bucket"),
                    Map.of(key, override), Set.of(override.itemId())).get(2, TimeUnit.SECONDS));
        }
        assertEquals(STONE, new RecipeStore(file).get(key).orElseThrow());
    }

    @Test void malformedAndFutureFilesAreNeverOverwritten() throws Exception {
        for (String contents : List.of("broken", "{}", "{\"schemaVersion\":2,\"recipes\":{}}",
                "{\"schemaVersion\":1,\"recipes\":{\"x\":{\"itemId\":\"minecraft:stone\",\"count\":1.5}}}")) {
            Path file = directory.resolve("invalid.json");
            Files.writeString(file, contents);
            assertThrows(IOException.class, () -> new RecipeStore(file));
            assertEquals(contents, Files.readString(file));
        }
    }

    @Test void reversedPairsDeduplicateAndOneCancellationDoesNotCancelAnother() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (RecipeEngine engine = new RecipeEngine(new RecipeStore(directory.resolve("recipes.json")), request -> {
            calls.incrementAndGet();
            started.countDown();
            assertTrue(release.await(2, TimeUnit.SECONDS));
            return STONE;
        }, 2)) {
            var first = engine.resolve(request("minecraft:a", "minecraft:b"), Map.of());
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var second = engine.resolve(request("minecraft:b", "minecraft:a"), Map.of());
            first.cancel(false);
            release.countDown();
            assertEquals(STONE, second.get(2, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        }
    }

    @Test void configuredWorkersRunConcurrentlyAndCancelledRecipesReleaseTheirWorker() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2), release = new CountDownLatch(1), wasInterrupted = new CountDownLatch(1);
        AtomicInteger interrupted = new AtomicInteger();
        try (RecipeEngine engine = new RecipeEngine(new RecipeStore(directory.resolve("recipes.json")), request -> {
            bothStarted.countDown();
            try { release.await(); }
            catch (InterruptedException error) { interrupted.incrementAndGet(); wasInterrupted.countDown(); throw error; }
            return STONE;
        }, 4, 3, 2)) {
            String firstKey = PairKey.of("minecraft:a", "minecraft:b");
            var first = engine.resolve(request("minecraft:a", "minecraft:b"), Map.of());
            var second = engine.resolve(request("minecraft:c", "minecraft:d"), Map.of());
            assertTrue(bothStarted.await(2, TimeUnit.SECONDS));
            engine.cancelRecipe(firstKey);
            assertThrows(java.util.concurrent.CompletionException.class, first::join);
            assertTrue(wasInterrupted.await(2, TimeUnit.SECONDS));
            assertEquals(1, interrupted.get());
            release.countDown();
            assertEquals(STONE, second.get(2, TimeUnit.SECONDS));
        } finally { release.countDown(); }
    }

    @Test void invalidGenerationIsNotCachedAndMayBeRetried() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RecipeStore store = new RecipeStore(directory.resolve("recipes.json"));
        try (RecipeEngine engine = new RecipeEngine(store, new dev.rocks.infinitecraft.core.RecipeGenerator() {
            public RecipeResult generate(GenerationRequest request) { throw new AssertionError(); }
            public List<RecipeResult> generateCandidates(GenerationRequest request, String feedback) {
                int call = calls.incrementAndGet();
                assertEquals(call == 1, feedback.isEmpty());
                return List.of(call == 1 ? new RecipeResult("example:missing", 1) : STONE);
            }
        }, 2)) {
            GenerationRequest request = request("minecraft:a", "minecraft:b");
            assertEquals(STONE, engine.resolve(request, Map.of()).get(2, TimeUnit.SECONDS));
            assertEquals(2, calls.get());
        }
    }

    @Test void triesSeveralCandidatesBeforeAnotherRequestAndRetainsTraits() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var result = new RecipeResult("minecraft:stone", 1, "Named stone", List.of("bouncy"), Map.of("bouncy", .35));
        RecipeStore store = new RecipeStore(directory.resolve("recipes.json"));
        var generator = new dev.rocks.infinitecraft.core.RecipeGenerator() {
            public RecipeResult generate(GenerationRequest request) { return STONE; }
            public List<RecipeResult> generateCandidates(GenerationRequest request) {
                calls.incrementAndGet(); return List.of(STONE, result);
            }
        };
        try (RecipeEngine engine = new RecipeEngine(store, generator, 2)) {
            assertEquals(result, engine.resolve(request("minecraft:a", "minecraft:b"), Map.of(), Set.of("minecraft:stone"),
                    recipe -> !recipe.traits().isEmpty()).get(2, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
            assertEquals(result, new RecipeStore(directory.resolve("recipes.json")).get(PairKey.of("minecraft:a", "minecraft:b")).orElseThrow());
        }
    }

    @Test void invalidOutputExhaustionPersistsBlockUntilOperatorClearsIt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Path file = directory.resolve("recipes.json");
        RecipeStore store = new RecipeStore(file);
        String first = "minecraft:a", second = "minecraft:b", key = PairKey.of(first, second);
        try (RecipeEngine engine = new RecipeEngine(store, ignored -> {
            calls.incrementAndGet(); throw new dev.rocks.infinitecraft.core.InvalidRecipeResponseException();
        }, 2, 3)) {
            assertThrows(ExecutionException.class, () -> engine.resolve(request(first, second), Map.of()).get(2, TimeUnit.SECONDS));
            assertEquals(3, calls.get());
            assertTrue(new RecipeStore(file).isBlocked(key));
            assertThrows(ExecutionException.class, () -> engine.resolve(request(second, first), Map.of()).get(2, TimeUnit.SECONDS));
            assertEquals(3, calls.get());
            engine.forgetRecipe(first, second, Map.of()).get(2, TimeUnit.SECONDS);
            assertFalse(new RecipeStore(file).isBlocked(key));
        }
    }

    @Test void emptyCandidateListDoesNotCallProviderOrBlockKnownRecipes() throws Exception {
        RecipeStore store = new RecipeStore(directory.resolve("recipes.json"));
        String first = "minecraft:a", second = "minecraft:b", key = PairKey.of(first, second);
        GenerationRequest empty = new GenerationRequest(first, second, List.of());
        AtomicInteger calls = new AtomicInteger();
        try (RecipeEngine engine = new RecipeEngine(store, ignored -> { calls.incrementAndGet(); return STONE; }, 2)) {
            assertThrows(ExecutionException.class, () -> engine.resolve(empty, Map.of(), Set.of("minecraft:stone")).get(2, TimeUnit.SECONDS));
            assertEquals(0, calls.get()); assertFalse(store.isBlocked(key));
            assertEquals(STONE, engine.resolve(empty, Map.of(key, STONE), Set.of("minecraft:stone")).get(2, TimeUnit.SECONDS));
        }
    }

    @Test void catalogueCancellationStopsMalformedRetriesWithoutCancellingEdits() throws Exception {
        RecipeStore store = new RecipeStore(directory.resolve("recipes.json"));
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        String first = "minecraft:a", second = "minecraft:b", key = PairKey.of(first, second);
        AtomicInteger calls = new AtomicInteger();
        try (RecipeEngine engine = new RecipeEngine(store, ignored -> {
            calls.incrementAndGet(); started.countDown(); release.await();
            throw new dev.rocks.infinitecraft.core.InvalidRecipeResponseException();
        }, 3)) {
            var pending = engine.resolve(request(first, second), Map.of());
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var edit = engine.setRecipe("minecraft:c", "minecraft:d", STONE, Map.of(), Set.of("minecraft:stone"));
            engine.cancelPendingGeneration();
            release.countDown();
            edit.get(2, TimeUnit.SECONDS);
            assertTrue(pending.isCompletedExceptionally());
            assertEquals(1, calls.get()); assertFalse(store.isBlocked(key));
            assertEquals(STONE, store.get(PairKey.of("minecraft:c", "minecraft:d")).orElseThrow());
        } finally { release.countDown(); }
    }

    @Test void transportFailuresAndCancellationNeverBlockAndVariantsStaySeparate() throws Exception {
        RecipeStore store = new RecipeStore(directory.resolve("recipes.json"));
        String first = "minecraft:a", second = "minecraft:b", key = PairKey.of(first, second);
        AtomicInteger calls = new AtomicInteger();
        try (RecipeEngine engine = new RecipeEngine(store, ignored -> {
            calls.incrementAndGet(); throw new IOException("Provider unavailable");
        }, 2)) {
            assertThrows(ExecutionException.class, () -> engine.resolve(request(first, second), Map.of()).get(2, TimeUnit.SECONDS));
            assertEquals(1, calls.get()); assertFalse(store.isBlocked(key));
        }
        try (RecipeEngine engine = new RecipeEngine(store, ignored -> STONE, 2)) {
            assertThrows(ExecutionException.class, () -> engine.resolveVariant(request(first, second), Set.of("minecraft:stone"),
                    recipe -> { throw new java.util.concurrent.CancellationException(); }, key + "#cancelled").get(2, TimeUnit.SECONDS));
            assertFalse(store.isBlocked(key + "#cancelled"));
            assertThrows(ExecutionException.class, () -> engine.resolveVariant(request(first, second), Set.of("minecraft:stone"),
                    recipe -> false, key + "#variant").get(2, TimeUnit.SECONDS));
            assertTrue(store.isBlocked(key + "#variant")); assertFalse(store.isBlocked(key));
            assertEquals(STONE, engine.resolve(request(first, second), Map.of()).get(2, TimeUnit.SECONDS));
            assertTrue(store.isBlocked(key + "#variant"));
            engine.setRecipe(first, second, STONE, Map.of(), Set.of("minecraft:stone")).get(2, TimeUnit.SECONDS);
            assertFalse(store.isBlocked(key + "#variant"));
        }
    }

    @Test void queueIsBoundedAndClosingCompletesQueuedRequests() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RecipeEngine engine = new RecipeEngine(new RecipeStore(directory.resolve("recipes.json")), request -> {
            started.countDown();
            release.await();
            return STONE;
        }, 1);
        try {
            var running = engine.resolve(request("minecraft:a", "minecraft:b"), Map.of());
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var queued = engine.resolve(request("minecraft:c", "minecraft:d"), Map.of());
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> engine.resolve(request("minecraft:e", "minecraft:f"), Map.of()).get());
            assertInstanceOf(RejectedExecutionException.class, failure.getCause());
            engine.close();
            assertTrue(running.isDone());
            assertTrue(queued.isDone());
            assertThrows(Exception.class, () -> engine.resolve(request("minecraft:a", "minecraft:b"), Map.of()).get());
        } finally {
            release.countDown();
            engine.close();
        }
    }

    @Test void providerFailureAndInvalidCountsLeaveNoDiscovery() throws Exception {
        RecipeStore store = new RecipeStore(directory.resolve("recipes.json"));
        AtomicInteger calls = new AtomicInteger();
        GenerationRequest request = request("minecraft:a", "minecraft:b");
        try (RecipeEngine engine = new RecipeEngine(store, ignored -> {
            return switch (calls.incrementAndGet()) {
                case 1 -> throw new IOException("Provider offline");
                case 2 -> new RecipeResult("minecraft:stone", 0);
                case 3 -> new RecipeResult("minecraft:stone", 65);
                default -> null;
            };
        }, 1)) {
            for (int attempt = 0; attempt < 4; attempt++) {
                assertThrows(ExecutionException.class, () -> engine.resolve(request, Map.of()).get(2, TimeUnit.SECONDS));
                assertTrue(store.get(PairKey.of(request.first(), request.second())).isEmpty());
                if (attempt == 0) assertFalse(store.isBlocked(PairKey.of(request.first(), request.second())));
            }
        }
        assertEquals("Provider limit reached. Try again later.", GenerationFailure.message(new IOException("Provider returned HTTP 429")));
        assertEquals("No valid result. Combination blocked.", GenerationFailure.message(new BlockedRecipeException()));
        assertFalse(GenerationFailure.message(new IOException("secret-provider-body")).contains("secret"));
    }
}
