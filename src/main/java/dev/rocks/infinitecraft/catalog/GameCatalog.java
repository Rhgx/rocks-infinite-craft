package dev.rocks.infinitecraft.catalog;

import com.google.gson.GsonBuilder;
import dev.rocks.infinitecraft.core.CatalogEntry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.GameMasterBlockItem;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class GameCatalog {
    private static final Set<String> RESTRICTED = Set.of("minecraft:barrier", "minecraft:light",
            "minecraft:structure_void", "minecraft:debug_stick", "minecraft:bedrock",
            "minecraft:end_portal_frame", "minecraft:spawner", "minecraft:command_block_minecart");

    private GameCatalog() {}

    public static List<CatalogEntry> snapshot(MinecraftServer server) {
        return snapshot(server, Set.of(), Set.of());
    }

    /** Call on the server thread after startup or a successful data-pack reload. */
    public static List<CatalogEntry> snapshot(MinecraftServer server, Set<String> excludedIds,
                                               Set<String> excludedNamespaces) {
        return snapshot(server.registryAccess(), server.getWorldData().enabledFeatures(), excludedIds, excludedNamespaces);
    }

    static List<CatalogEntry> snapshot(RegistryAccess registries, FeatureFlagSet enabledFeatures, Set<String> excludedIds,
                                        Set<String> excludedNamespaces) {
        Registry<Item> items = registries.lookupOrThrow(Registries.ITEM);
        Registry<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        List<CatalogEntry> result = new ArrayList<>();
        for (Item item : items) {
            Identifier id = items.getKey(item);
            String reason = exclusion(item, id, enabledFeatures, excludedIds, excludedNamespaces);
            result.add(entry(id, "item", items.get(id).orElseThrow().tags()
                    .map(tag -> tag.location().toString()).sorted().toList(), reason));
        }
        for (Block block : blocks) {
            Identifier id = blocks.getKey(block);
            Item item = block.asItem();
            String reason = item == Items.AIR ? "Block has no inventory item"
                    : exclusion(item, items.getKey(item), enabledFeatures, excludedIds, excludedNamespaces);
            if (excludedIds.contains(id.toString()) || excludedNamespaces.contains(id.getNamespace())) {
                reason = "Excluded by server configuration";
            }
            result.add(entry(id, "block", blocks.get(id).orElseThrow().tags()
                    .map(tag -> tag.location().toString()).sorted().toList(), reason));
        }
        result.sort(Comparator.comparing(CatalogEntry::kind).thenComparing(CatalogEntry::id));
        return List.copyOf(result);
    }

    private static String exclusion(Item item, Identifier id, FeatureFlagSet enabledFeatures,
                                    Set<String> excludedIds, Set<String> excludedNamespaces) {
        if (item == Items.AIR) return "Air cannot be dropped";
        if (excludedIds.contains(id.toString()) || excludedNamespaces.contains(id.getNamespace())) {
            return "Excluded by server configuration";
        }
        if (item instanceof GameMasterBlockItem || RESTRICTED.contains(id.toString())) {
            return "Restricted utility item";
        }
        if (!item.isEnabled(enabledFeatures)) return "Disabled feature";
        return "";
    }

    private static CatalogEntry entry(Identifier id, String kind, List<String> tags, String reason) {
        return new CatalogEntry(id.toString(), kind, id.getNamespace(),
                id.getPath().replace('_', ' ').replace('/', ' '), tags, reason.isEmpty(), reason);
    }

    public static List<CatalogEntry> candidates(List<CatalogEntry> catalog, String first,
                                                String second, int limit) {
        return CatalogSearch.candidates(catalog, first, second, limit);
    }

    /** Write a complete replacement, retaining the previous file if serialization fails. */
    public static void export(Path path, List<CatalogEntry> catalog) throws IOException {
        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "catalog-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(catalog, writer);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
