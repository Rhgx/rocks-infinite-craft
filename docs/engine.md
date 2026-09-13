# Recipe storage and commands

World data lives in `<world>/infinitecraft/`:

| File | Contents |
| --- | --- |
| `recipes.json` | Saved results and blocked combinations |
| `discoveries.json` | Discovery Book entries and player ownership |
| `fusion-enabled.json` | The host's fusion toggle |
| `catalog.json` | Export of the current item and block catalog |

Back up this directory with the world. Invalid recipe files are left untouched rather than silently replaced.

## Recipe commands

The host or server operator can inspect, replace, or forget a pair:

```text
/fusion recipe inspect minecraft:stone minecraft:dirt
/fusion recipe set minecraft:stone minecraft:dirt minecraft:clay 1
/fusion recipe forget minecraft:stone minecraft:dirt
```

The count defaults to 1. Manual recipes must respect the output's stack limit. Forgetting a pair clears its saved variants and blocks so it can be generated again. Data-pack overrides take priority and must be changed in the pack itself.

## How results are reused

Input order does not matter. Items with different component data can have different recipes; identical pairs share a result and any pending generation work.

The engine checks explicit recipes and saved results before requesting generation. One worker handles generation with a bounded queue. Picking up an input cancels that physical exchange without cancelling other players' requests for the same recipe.

Each model response can contain five proposals. Invalid proposals are skipped. If all configured attempts return invalid results, the pair is saved as blocked. Timeouts, authentication failures, and cancelled requests do not permanently block it.

Results are validated before saving and checked again before consuming items. Missing modded outputs remain stored, so restoring the mod can make those recipes available again.
