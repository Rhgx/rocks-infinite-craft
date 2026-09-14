# Settings and gameplay

Open **Mods > Rocks' Infinite Craft > Configure** with Mod Menu and Cloth Config installed. Dedicated servers use `config/infinitecraft.json`; run `/fusion reload` after editing it.

## Fusion

The host controls fusion with `/fusion enable`, `/fusion disable`, or `/fusion toggle`. Dedicated servers require operator permissions. The choice is saved per world; new worlds start with fusion off.

Drop two items individually near each other. Two identical items work too. Dropping an entire stack does not start fusion. Failed combinations leave the items intact.

Alternatively, craft a **Fusion Crafter** from a Crafter, Amethyst Shard and Copper Ingot.
Put items in two separate slots; fusion starts automatically after one second. Ground fusion
and Fusion Crafter have independent Gameplay toggles, both on by default. See the
[workstation guide](fusion-crafter/README.md) for details.

Generation starts disabled. Saved and data-pack recipes work without a model request. See [provider setup](providers.md) to enable new recipes.

## Gameplay settings

- **Recipe style:** power and silliness each have five stops from 0 to 100. Both default to 50.
- **Special chance:** 5% by default. Enabled item triggers can also request a special result: rarity, enchantments, potions/stew, or custom item data. Matching several categories does not multiply the chance.
- **Special traits:** at most 3 per item by default, configurable from 0 to 8. Inherited traits count; normal armor stats do not.
- **Output quantity:** defaults to a maximum of 8, configurable from 1 to 64. Outputs are split into stacks as needed.
- **Combine special items:** off by default. This applies to two previously crafted special items, not ordinary enchanted items or potions.
- **Modded items / item data fusion:** independently control modded results and support for items with components.

Special items and their descendants can go through five more combinations. The tooltip tracks this as `Combinations: X/5`. At the limit, the item still works but cannot fuse again.

Supported data includes enchantments, potion and stew effects, names, dyes, durability, and generated traits. If no result can preserve both inputs' supported data, a repeatable coin toss chooses which input's data survives. Unsupported components can still prevent fusion.

Special results can have styled names, dyed equipment, a different vanilla item appearance, and traits activated by holding, wearing, or consuming the item. Enchantments follow vanilla activation rules.

## Discovery Book

| Setting | Options |
| --- | --- |
| Book ownership | **Soulbound** by default: automatically given while fusion is on, cannot be dropped or stored, replaced after death. **Craftable**: book + copper ingot anywhere in the grid; droppable and has no vanishing curse. The recipe unlocks when you obtain a normal book. |
| Book visibility | **Global** by default: everyone's discoveries, with discoverer names. **Personal**: only results you have made, without the name line. |

Books show 25 discoveries per page, newest first. Hover items for their tooltips. Personal books also have **Share**, which posts the recipe in chat and closes the book. Sharing has a three-second cooldown.

Personal collections use the world's shared recipe cache. Making a known recipe adds it to your collection without another model request.

## Effects and messages

Toggle success/failure sounds and particles, and the combining animation. First discoveries, special discoveries, join messages, milestones, and queue feedback have separate toggles.

Milestones follow 10, 25, 50, 100, 250, 500, and the same pattern at larger scales. Their formatting and XP reward grow with the collection, with XP capped at 500 per milestone. Queued requests show `Fusion queued... (X/Y)`.

## Advanced limits

| Config field | Default | Range |
| --- | ---: | ---: |
| `generationAttempts` | 3 | 1–4 |
| `generationThreads` | 1 | 1–8 |
| `provider.timeoutSeconds` | 60 | 1–300 |
| `scanIntervalTicks` | 10 | 1–200 |
| `maxNearbyItems` | 64 | 2–256 |
| `maxPending` | 8 | 1–64 |
| `candidateLimit` | 48 | 1–256 |
| `cooldownTicks` | 100 | 20–12000 |

`excludedIds` and `excludedNamespaces` in the config file exclude additional items from results. A tick is 1/20 second at normal server speed.

Each generation response can propose up to five results. Repeated invalid responses block the combination; connection failures do not. See [recipe storage and commands](engine.md) to inspect or reset a pair.
