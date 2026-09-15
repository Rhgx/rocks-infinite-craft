package dev.rocks.infinitecraft.catalog;

import com.google.gson.JsonParser;
import com.mojang.serialization.Lifecycle;
import dev.rocks.infinitecraft.core.CatalogEntry;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegistryCatalogTest {
    private static RegistryAccess registries;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Identifier moddedId = Identifier.parse("catalog_test:unlisted_material");
        Registry.register(BuiltInRegistries.ITEM, moddedId,
                new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, moddedId))));
        // Fabric defers vanilla registry freezing until game startup, which this fixture does not run.
        // Match BuiltInRegistries.freeze's tag-binding and freeze sequence before testing reloads.
        ((MappedRegistry<?>) BuiltInRegistries.ITEM).bindAllTagsToEmpty();
        ((MappedRegistry<?>) BuiltInRegistries.BLOCK).bindAllTagsToEmpty();
        BuiltInRegistries.ITEM.freeze();
        BuiltInRegistries.BLOCK.freeze();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        // No server resource reload runs in this fixture. Bind one representative data-pack tag.
        BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(BuiltInRegistries.ITEM.key(), Map.of(
                TagKey.create(Registries.ITEM, Identifier.parse("testpack:building_materials")),
                List.of(BuiltInRegistries.ITEM.get(Identifier.parse("minecraft:stone")).orElseThrow())))).apply();
        BuiltInRegistries.BLOCK.prepareTagReload(new TagLoader.LoadResult<>(BuiltInRegistries.BLOCK.key(), Map.of())).apply();
    }

    @Test void includesEveryRegisteredBlockAndItem() {
        var catalog = GameCatalog.snapshot(registries, FeatureFlags.VANILLA_SET, Set.of(), Set.of());
        assertEquals(BuiltInRegistries.ITEM.size() + BuiltInRegistries.BLOCK.size(), catalog.size());
        assertTrue(catalog.size() > 1_000);
        assertTrue(find(catalog, "item", "minecraft:stone").craftable());
        assertTrue(find(catalog, "item", "minecraft:stone").tags().contains("testpack:building_materials"));
        assertTrue(find(catalog, "block", "minecraft:stone").craftable());
        var water = find(catalog, "block", "minecraft:water");
        assertFalse(water.craftable());
        assertEquals("Block has no inventory item", water.exclusionReason());
        assertFalse(find(catalog, "item", "minecraft:air").craftable());
        assertFalse(find(catalog, "item", "minecraft:command_block").craftable());
        assertFalse(find(catalog, "item", "minecraft:barrier").craftable());
        var modded = find(catalog, "item", "catalog_test:unlisted_material");
        assertTrue(modded.craftable());
        assertEquals("catalog_test", modded.namespace());
        assertEquals("unlisted material", modded.name());
    }

    @Test void usesSuppliedRegistryAndReloadedTagsRatherThanGlobalHolders() {
        var items = new MappedRegistry<Item>(Registries.ITEM, Lifecycle.stable());
        var blocks = new MappedRegistry<Block>(Registries.BLOCK, Lifecycle.stable());
        Identifier id = Identifier.parse("testpack:stone_variant");
        Registry.register(items, id, Items.STONE);
        Registry.register(blocks, id, Blocks.STONE);
        items.freeze();
        blocks.freeze();
        var access = new RegistryAccess.ImmutableRegistryAccess(List.of(items, blocks));
        var tag = TagKey.create(Registries.ITEM, Identifier.parse("testpack:local_material"));
        items.prepareTagReload(new TagLoader.LoadResult<>(items.key(),
                Map.of(tag, List.of(items.get(id).orElseThrow())))).apply();
        var catalog = GameCatalog.snapshot(access, FeatureFlags.VANILLA_SET, Set.of(), Set.of());
        assertEquals(2, catalog.size());
        assertEquals(List.of("testpack:local_material"), find(catalog, "item", id.toString()).tags());
        items.prepareTagReload(new TagLoader.LoadResult<>(items.key(), Map.of())).apply();
        assertTrue(find(GameCatalog.snapshot(access, FeatureFlags.VANILLA_SET, Set.of(), Set.of()),
                "item", id.toString()).tags().isEmpty());
    }

    @Test void exportsCompleteReplacement(@TempDir Path directory) throws IOException {
        var catalog = GameCatalog.snapshot(registries, FeatureFlags.VANILLA_SET, Set.of(), Set.of());
        Path target = directory.resolve("catalog.json");
        Files.writeString(target, "old contents");
        GameCatalog.export(target, catalog);
        assertEquals(catalog.size(), JsonParser.parseString(Files.readString(target)).getAsJsonArray().size());
        try (var paths = Files.list(directory)) {
            assertEquals(List.of(target), paths.toList());
        }
    }

    private static CatalogEntry find(List<CatalogEntry> catalog, String kind, String id) {
        return catalog.stream().filter(entry -> entry.kind().equals(kind) && entry.id().equals(id))
                .findFirst().orElseThrow();
    }
}
