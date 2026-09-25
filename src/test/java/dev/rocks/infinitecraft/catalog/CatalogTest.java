package dev.rocks.infinitecraft.catalog;

import com.google.gson.JsonParser;
import dev.rocks.infinitecraft.core.CatalogEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CatalogTest {
    private static final List<CatalogEntry> CATALOG = List.of(
            item("minecraft:stone", true), item("example:copper_gear", true),
            item("example:copper_plate", true), item("minecraft:barrier", false),
            new CatalogEntry("example:copper_gear", "block", "example", "copper gear", List.of(), true, ""));

    private static CatalogEntry item(String id, boolean allowed) {
        return new CatalogEntry(id, "item", id.split(":")[0], id.split(":")[1].replace('_', ' '),
                List.of(), allowed, allowed ? "" : "Restricted");
    }

    @Test void candidatesIncludeModdedItemsWithoutDuplicateBlocksOrExcludedItems() {
        var result = CatalogSearch.candidates(CATALOG, "example:copper_gear", "minecraft:stone", 20);
        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(entry -> entry.id().equals("example:copper_plate")));
        assertTrue(result.stream().allMatch(entry -> entry.kind().equals("item") && entry.craftable()));
    }

    @Test void reservesSpaceOutsideRepeatedMaterialVariants() {
        var catalog = new ArrayList<CatalogEntry>();
        catalog.add(item("minecraft:copper", true));
        catalog.add(item("minecraft:stone", true));
        for (String variant : List.of("door", "trapdoor", "stairs", "slab", "wall", "fence", "button", "plate")) {
            catalog.add(item("minecraft:copper_" + variant, true));
        }
        catalog.add(item("minecraft:compass", true));
        catalog.add(item("modded:engine", true));
        var result = CatalogSearch.candidates(catalog, "minecraft:copper", "minecraft:stone", 4);
        assertTrue(result.stream().anyMatch(entry -> entry.id().equals("minecraft:compass") || entry.id().equals("modded:engine")));
        var reversed = new ArrayList<>(catalog);
        Collections.reverse(reversed);
        assertEquals(result, CatalogSearch.candidates(reversed, "minecraft:stone", "minecraft:copper", 4));
    }

    @Test void compatibleOutputFilterDoesNotChangeThePreparedIndex() {
        var index = new CatalogSearch.Index(CATALOG);
        assertEquals(List.of(item("example:copper_plate", true)), index.candidates(
                "example:copper_gear", "minecraft:stone", 20, Set.of("example:copper_plate")));
        assertTrue(index.candidates("example:copper_gear", "minecraft:stone", 20, Set.of()).isEmpty());
        assertEquals(3, index.candidates("example:copper_gear", "minecraft:stone", 20).size());
    }

    @Test void validatesRecipeOverrides() {
        var valid = RecipeOverrides.parse(JsonParser.parseString("""
                {"first":"minecraft:stone","second":"example:copper_gear","result":"example:copper_plate","count":2}
                """), CATALOG);
        assertEquals(2, valid.getValue().count());
        for (String count : List.of("0", "65", "1.5", "\"2\"", "null", "999999999999999999")) {
            assertThrows(IllegalArgumentException.class, () -> RecipeOverrides.parse(JsonParser.parseString(
                    "{\"first\":\"minecraft:stone\",\"second\":\"example:copper_gear\",\"result\":\"example:copper_plate\",\"count\":" + count + "}"), CATALOG));
        }
        for (String output : List.of("minecraft:barrier", "missing:thing", "stone")) {
            assertThrows(IllegalArgumentException.class, () -> RecipeOverrides.parse(JsonParser.parseString(
                    "{\"first\":\"minecraft:stone\",\"second\":\"example:copper_gear\",\"result\":\"" + output + "\"}"), CATALOG));
        }
    }
}
