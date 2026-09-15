package dev.rocks.infinitecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.rocks.infinitecraft.provider.ProviderConfig;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public ProviderConfig provider = ProviderConfig.defaults();
    public boolean enabled = true;
    public boolean groundFusion = true;
    public boolean crafterFusion = true;
    public boolean joinMessage = true;
    public boolean firstDiscoveryMessage = true;
    public boolean specialDiscoveryMessage = true;
    public boolean milestoneMessages = true;
    public boolean queueFeedback = true;
    public boolean generationEnabled = false;
    public boolean failureSound = true;
    public boolean failureParticles = true;
    public boolean successParticles = true;
    public boolean successSound = true;
    public boolean allowModdedItems = true;
    public boolean allowItemData = true;
    public boolean generatedTraits = true;
    public boolean combineSpecialItems = false;
    public boolean soulboundBook = true;
    public boolean personalBook = false;
    public int specialResultChance = 5;
    public boolean specialIngredientTriggers = true;
    public boolean specialRarity = true;
    public boolean specialEnchantments = true;
    public boolean specialPotions = true;
    public boolean specialCustomData = true;
    public boolean combiningParticles = true;
    public int generationAttempts = 3;
    public int generationThreads = 1;
    public int maxOutputCount = 8;
    public int maxTraits = 3;
    public int power = 50;
    public int silliness = 50;
    public int scanIntervalTicks = 10;
    public int maxNearbyItems = 64;
    public int maxPending = 8;
    public int candidateLimit = 48;
    public int cooldownTicks = 100;
    public Set<String> excludedIds = Set.of();
    public Set<String> excludedNamespaces = Set.of();

    public boolean sameCatalog(ModConfig other) {
        return allowModdedItems == other.allowModdedItems && excludedIds.equals(other.excludedIds)
                && excludedNamespaces.equals(other.excludedNamespaces);
    }

    public boolean sameGeneration(ModConfig other) {
        return provider.equals(other.provider) && enabled == other.enabled && generationEnabled == other.generationEnabled
                && allowItemData == other.allowItemData && generatedTraits == other.generatedTraits
                && combineSpecialItems == other.combineSpecialItems && specialResultChance == other.specialResultChance
                && specialIngredientTriggers == other.specialIngredientTriggers && specialRarity == other.specialRarity
                && specialEnchantments == other.specialEnchantments && specialPotions == other.specialPotions
                && specialCustomData == other.specialCustomData && generationAttempts == other.generationAttempts
                && generationThreads == other.generationThreads
                && maxOutputCount == other.maxOutputCount && maxTraits == other.maxTraits
                && power == other.power && silliness == other.silliness && maxPending == other.maxPending
                && candidateLimit == other.candidateLimit;
    }

    public static ModConfig load(Path file) throws IOException {
        if (!Files.exists(file)) {
            new ModConfig().save(file);
        }
        try {
            if (Files.size(file) > 1_048_576) throw new IllegalArgumentException("Config is too large");
            var json = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            ModConfig config = GSON.fromJson(json, ModConfig.class);
            if (config == null) throw new IllegalArgumentException("Missing config");
            if (!json.has("generationEnabled") && config.provider != null)
                config.generationEnabled = !config.provider.provider().equals("disabled");
            config.validate();
            return config;
        } catch (RuntimeException error) {
            // Gson can include record constructor arguments in errors, including an entered API key.
            throw new IOException("Invalid infinitecraft config; file left unchanged");
        }
    }

    public void validate() {
        if (provider == null || excludedIds == null || excludedNamespaces == null)
            throw new IllegalArgumentException("Missing config values");
        if (generationEnabled && provider.provider().equals("disabled"))
            throw new IllegalArgumentException("Choose a provider before enabling generation");
        if (scanIntervalTicks < 1 || scanIntervalTicks > 200 || maxNearbyItems < 2 || maxNearbyItems > 256
                || maxPending < 1 || maxPending > 64 || candidateLimit < 1 || candidateLimit > 256
                || cooldownTicks < 20 || cooldownTicks > 12000 || generationAttempts < 1 || generationAttempts > 4
                || generationThreads < 1 || generationThreads > 8
                || power < 0 || power > 100 || silliness < 0 || silliness > 100
                || maxTraits < 0 || maxTraits > 8
                || maxOutputCount < 1 || maxOutputCount > 64
                || specialResultChance < 0 || specialResultChance > 100)
            throw new IllegalArgumentException("Config limits are out of range");
        excludedIds = Set.copyOf(excludedIds);
        excludedNamespaces = Set.copyOf(excludedNamespaces);
    }

    public void save(Path file) throws IOException {
        validate();
        Path absolute = file.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), "infinitecraft-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(this));
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
