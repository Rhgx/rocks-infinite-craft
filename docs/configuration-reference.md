# Settings and gameplay

Open **Mods > Rocks' Infinite Craft > Configure** with Mod Menu and Cloth Config installed. Settings are grouped into **Gameplay**, **AI Generation**, **Special Items**, **Traits**, **Feedback** and **Advanced** tabs. Every option has a tooltip, and options that depend on a switch that is off are greyed out. Dedicated servers use `config/infinitecraft.json`; run `/fusion reload` after editing it.

## Fusion

The host controls fusion with `/fusion enable`, `/fusion disable`, or `/fusion toggle`. Dedicated servers require operator permissions. The choice is saved per world; new worlds start with fusion off.

Drop two items individually near each other. Two identical items work too. Dropping an entire stack does not start fusion. Failed combinations leave the items intact.

Alternatively, craft a **Fusion Crafter** from a Crafter, Amethyst Shard and Copper Ingot.
Put items in two separate slots; fusion starts automatically after one second. Ground fusion
and Fusion Crafter have independent Gameplay toggles, both on by default. See the
[workstation guide](fusion-crafter/README.md) for details.

Generation starts disabled. Saved and data-pack recipes work without a model request. See [provider setup](providers.md) to enable new recipes.

## Gameplay and special item settings

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

## Feedback

Toggle success/failure sounds and particles, and the combining animation. First discoveries, special discoveries, join messages, milestones, and queue feedback have separate toggles.

Milestones follow 10, 25, 50, 100, 250, 500, and the same pattern at larger scales. Their formatting and XP reward grow with the collection, with XP capped at 500 per milestone. Queued requests show `Fusion queued... (X/Y)`; active requests show `Generating...` or `Retrying fusion...`.

## Advanced limits

| Config field | Default | Range |
| --- | ---: | ---: |
| `generationAttempts` | 3 | 1–4 |
| `generationThreads` | 1 | 1–8 simultaneous generation requests; for Ollama, match `OLLAMA_NUM_PARALLEL` when memory allows |
| `provider.timeoutSeconds` | 60 | 1–300 |
| `scanIntervalTicks` | 10 | 1–200 |
| `maxNearbyItems` | 64 | 2–256 |
| `maxPending` | 8 | 1–64 |
| `candidateLimit` | 48 | 1–256 |
| `cooldownTicks` | 100 | 20–12000 |

`excludedIds` and `excludedNamespaces` in the config file exclude additional items from results. A tick is 1/20 second at normal server speed.

Initial requests ask for one ordinary result or two special results. Retries request up to five alternatives with validation guidance. Repeated invalid responses block the combination; connection failures do not. See [recipe storage and commands](engine.md) to inspect or reset a pair.

Hit traits include `launching` (one second of levitation), `frostbite` (three seconds of Slowness I), and `revealing` (five seconds of glowing). Their item hints are Uplifting, Chilly, and Exposing.

Connection tests show elapsed seconds and can be cancelled. Debug logs include Ollama prompt/output timings and token counts, plus accepted recipe attempt counts and total generation time. These measurements exclude request bodies and API keys.

Combat traits work on melee hits, bow and crossbow arrows, thrown tridents, snowballs, eggs and ender pearls. Arrows inherit both ammunition and firing-weapon traits, without applying duplicate traits twice. Thrown items use their own traits. Switching held items after firing does not change the shot. Zero-damage hits can trigger effects, but cannot grant lifesteal or increase the explosion chance.

### Reactive traits

| Trait | Hint | Trigger |
| --- | --- | --- |
| `startled` | ⚡ Jumpy | Taking an attack while worn grants Speed I for 3 seconds. |
| `hardened` | ⚓ Stubborn | Taking an attack while worn has a 25% chance to grant Resistance I for 4 seconds. |
| `cold_shoulder` | ❄ Prickly | Taking an attack while worn has a 30% chance to give the attacker Slowness I for 3 seconds. |
| `airy_food` | ☁ Airy | Eating grants Slow Falling for 15 seconds. |
| `hearty_food` | ♥ Hearty | Eating grants Absorption I for 30 seconds. |
| `vanishing_food` | ☯ Elusive | Eating grants Invisibility for 15 seconds. |
| `nibbleable` | 🍖 Nibbleable | Provides 3–8 meals, selected through trait strength; default 5. |

Armor traits activate after an unblocked damaging attack, not environmental damage. Multiple pieces with the same trait do not multiply the rolls. New wearable items use the chest slot; existing armor retains its slot.

Nibbleable food uses vanilla food, consumption and durability components. The server replaces item consumption with one durability point per meal, preserving nutrition and other eating effects. The last bite consumes the item; creative mode does not spend bites. Unbreaking does not increase the meal count. Vanilla guests can use these items with the mod installed on the server.

### Trait controls

The **Traits** settings tab lists every registered trait. Each toggle controls whether a trait can appear in new recipes; turning a combat trait off also stops its server-side triggers on existing items. Existing native attributes and food effects stay on items already created.

**Chance** changes combat triggers immediately and eating effects on newly created outputs. Explosive uses a **Base chance**, rising with recent damage up to five times that value, capped at 100%. Its default remains 10–50%. Dedicated-server hosts can edit `disabledTraits` and `traitChances` in `config/infinitecraft.json`, then run `/fusion reload`.

### Favorites and crafter modes

Use ☆ / ★ in the Discovery Book to favorite an output or show only favorites. Favorites are personal, saved per world, and include every discovered recipe for the selected output. Both the modded screen and vanilla dialogs support them.

With the mod installed on the client, the button beside the search field sorts the list by newest, oldest or name. When a selected output is also an ingredient, **Made from** and **Used in** switch the right page between the recipes that make it and the recipes that use it. Click any discovered item in a recipe to open it.

With [JEI](https://modrinth.com/mod/jei) installed on the client, discovered fusions also appear in JEI under **Item Fusion**, following the same **Book visibility** setting. New discoveries show up as they are made.

Fusion Crafters default to **Repeat**. With the mod installed on the client, the crafter screen shows the current status (Ready, Fusing, Stopped or waiting for items) and has two buttons: the mode button switches between **Once** and **Repeat** without starting anything, and **Start**/**Stop** runs or stops the crafter. Stopping lets a fusion in progress finish. Without the client mod, `/fusion crafter` while looking at a crafter opens a dialog with the same controls; `/fusion crafter once|repeat|start|stop` and `/fusion crafter mode once|repeat` also work. Only its owner or the host can change its mode. Once mode pauses after one successful fusion, including after a world reload; **Start** or a redstone pulse runs another fusion. Redstone works like a lever: each pulse starts a stopped crafter or stops a running one, and a fusion in progress still finishes. Removing and replacing inputs does not bypass the pause.

Provider outages, timeouts, access failures and rate limits display distinct messages and do not permanently block combinations. Only exhausted invalid generation blocks a combination.

### Lucky Block

Lucky Block can appear on any fusion output, with a 5% roll per input item pair and world seed when there is room under the trait limit. Input order does not affect it. It is added by the server rather than selected by the model. Disable it under Traits → Utility to stop adding it to recipes. Existing Lucky Block items remain usable.

Right-click consumes one item, including in creative mode. One roll grants an outcome: 30 seconds of invulnerability (4%), instant death (4%), full health and food, Strength III for 30 seconds, Speed III for 30 seconds, invisibility for 30 seconds, three diamonds, 100 XP, Levitation II for eight seconds, Poison II for 15 seconds, blindness for 15 seconds, Slowness IV for 20 seconds, or Rocket: Levitation XX for three seconds with firework sparks at your feet. The remaining outcomes share the other 92% approximately equally. Invulnerability cycles the glowing outline through six colors and plays the Super Star theme on note block sounds, audible within 32 blocks under the Jukebox/Note Blocks volume. Its action-bar message is shown in rainbow colors; instant death is shown in dark red. Vanilla clients also display that temporary team color on the name tag; real server teams are unchanged. Invulnerability ends on death or leaving the server and does not change creative mode or player abilities.

Edible effect chances default to 100%. Explicit host overrides still apply; already-created food retains the probability stored in its components.