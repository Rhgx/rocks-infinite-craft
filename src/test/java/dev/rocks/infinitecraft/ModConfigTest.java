package dev.rocks.infinitecraft;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ModConfigTest {
    @TempDir Path directory;

    @Test void cosmeticSettingsDoNotInvalidateGenerationOrCatalog() {
        var original = new ModConfig();
        var changed = new ModConfig();
        changed.successSound = false;
        changed.combiningParticles = false;
        changed.personalBook = true;
        changed.soulboundBook = false;
        changed.queueFeedback = false;
        assertTrue(original.sameGeneration(changed));
        assertTrue(original.sameCatalog(changed));
        changed.maxTraits++;
        assertFalse(original.sameGeneration(changed));
        assertTrue(original.sameCatalog(changed));
        changed.excludedIds = java.util.Set.of("minecraft:stone");
        assertFalse(original.sameCatalog(changed));
    }

    @Test void savesFeatureTogglesAndEnteredKeyWithoutTemporaryFiles() throws Exception {
        Path file = directory.resolve("infinitecraft.json");
        ModConfig config = new ModConfig();
        assertFalse(config.generationEnabled);
        assertFalse(config.combineSpecialItems);
        assertTrue(config.soulboundBook);
        assertFalse(config.personalBook);
        config.soulboundBook = false;
        config.personalBook = true;
        config.combineSpecialItems = true;
        assertEquals("disabled", config.provider.provider());
        config.provider = new dev.rocks.infinitecraft.provider.ProviderConfig("openai", "", "test-model", "", 60, "test-secret");
        config.generationEnabled = true;
        config.firstDiscoveryMessage = false;
        config.specialDiscoveryMessage = true;
        config.milestoneMessages = false;
        config.queueFeedback = false;
        config.specialRarity = false;
        config.specialEnchantments = false;
        config.specialPotions = false;
        config.specialCustomData = false;
        config.failureSound = false;
        config.failureParticles = false;
        config.successParticles = false;
        config.successSound = false;
        config.allowItemData = false;
        config.generatedTraits = false;
        config.specialResultChance = 12;
        config.specialIngredientTriggers = false;
        config.combiningParticles = false;
        config.generationAttempts = 4;
        config.generationThreads = 3;
        config.maxOutputCount = 32;
        config.power = 90;
        config.silliness = 10;
        config.allowModdedItems = false;
        config.disabledTraits = java.util.Set.of("explosive");
        config.traitChances = java.util.Map.of("hardened", 40);
        config.save(file);
        ModConfig loaded = ModConfig.load(file);
        assertEquals(config.disabledTraits, loaded.disabledTraits);
        assertEquals(config.traitChances, loaded.traitChances);
        assertEquals("test-secret", loaded.provider.apiKey());
        assertTrue(loaded.generationEnabled);
        assertTrue(loaded.combineSpecialItems);
        assertFalse(loaded.soulboundBook);
        assertTrue(loaded.personalBook);
        assertFalse(loaded.firstDiscoveryMessage);
        assertTrue(loaded.specialDiscoveryMessage);
        assertFalse(loaded.milestoneMessages);
        assertFalse(loaded.queueFeedback);
        assertFalse(loaded.specialRarity);
        assertFalse(loaded.specialEnchantments);
        assertFalse(loaded.specialPotions);
        assertFalse(loaded.specialCustomData);
        loaded.firstDiscoveryMessage = true;
        loaded.specialDiscoveryMessage = false;
        loaded.save(file);
        var reversed = ModConfig.load(file);
        assertTrue(reversed.firstDiscoveryMessage);
        assertFalse(reversed.specialDiscoveryMessage);
        assertFalse(loaded.failureSound);
        assertFalse(loaded.failureParticles);
        assertFalse(loaded.successParticles);
        assertFalse(loaded.successSound);
        assertFalse(loaded.allowItemData);
        assertFalse(loaded.generatedTraits);
        assertEquals(12, loaded.specialResultChance);
        assertFalse(loaded.specialIngredientTriggers);
        assertFalse(loaded.combiningParticles);
        assertEquals(4, loaded.generationAttempts);
        assertEquals(3, loaded.generationThreads);
        assertEquals(32, loaded.maxOutputCount);
        assertEquals(90, loaded.power);
        assertEquals(10, loaded.silliness);
        assertFalse(loaded.allowModdedItems);
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
        String original = Files.readString(file);
        loaded.maxPending = 0;
        assertThrows(IllegalArgumentException.class, () -> loaded.save(file));
        assertEquals(original, Files.readString(file));
    }

    @Test void invalidProviderDoesNotLeakKeyThroughGsonCause() throws Exception {
        Path file = directory.resolve("infinitecraft.json");
        Files.writeString(file, "{\"provider\":{\"provider\":\"invalid\",\"timeoutSeconds\":60,\"apiKey\":\"test-secret\"}}");
        IOException error = assertThrows(IOException.class, () -> ModConfig.load(file));
        assertFalse(error.toString().contains("test-secret"));
        assertNull(error.getCause());
    }

    @Test void rejectsInvalidConfigWithoutOverwriting() throws Exception {
        Path file = directory.resolve("infinitecraft.json");
        for (String invalid : new String[]{"null", "{\"maxPending\":0}", "{\"provider\":null}", "{\"specialResultChance\":-1}", "{\"specialResultChance\":101}",
                "{\"traitChances\":{\"hardened\":101}}", "{\"disabledTraits\":null}",
                "{\"provider\":{\"provider\":\"openai\",\"model\":\"\",\"timeoutSeconds\":60}}"}) {
            Files.writeString(file, invalid);
            assertThrows(IOException.class, () -> ModConfig.load(file));
            assertEquals(invalid, Files.readString(file));
        }
    }

}
