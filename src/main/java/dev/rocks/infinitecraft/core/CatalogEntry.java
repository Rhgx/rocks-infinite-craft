package dev.rocks.infinitecraft.core;

import java.util.List;

public record CatalogEntry(String id, String kind, String namespace, String name,
                           List<String> tags, boolean craftable, String exclusionReason) {
    public CatalogEntry { tags = List.copyOf(tags); }
}
