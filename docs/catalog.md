# Catalog and data-pack recipes

The catalog comes from the server's live block and item registries, including mods and tags. It refreshes at startup and on a successful data-pack reload. Installing mods requires a restart.

Only eligible items can become outputs. Air, unavailable features, operator-only blocks, and selected utility items are excluded. Use `excludedIds` and `excludedNamespaces` in the config for additional restrictions. Individual stacks must also pass component compatibility checks.

The model receives a shortlist ranked by names, IDs, and tags. No vector database is needed. The full catalog is exported to `<world>/infinitecraft/catalog.json`.

## Add a fixed recipe

Place a JSON file at `data/<namespace>/infinitecraft/recipes/<name>.json` in a data pack:

```json
{
  "first": "minecraft:cobblestone",
  "second": "minecraft:coal",
  "result": "minecraft:stone",
  "count": 1
}
```

- Use full item IDs. Input order does not matter.
- `count` defaults to 1 and accepts 1–64, subject to the output's stack limit.
- Inputs must exist and the output must be eligible.
- Tags and component selectors are not supported in this format.

Fixed recipes work without generation enabled and take priority over saved results. To replace another pack's recipe, use its resource path in a higher-priority pack. Two differently named files for the same pair are an error.

Invalid recipe sets leave the previous active set in place and log the offending file. You can also manage saved recipes with [commands](engine.md).
