# Fusion Crafter

Craft a Crafter, an Amethyst Shard and a Copper Ingot together in any arrangement.
Place items in the two open slots in the middle row. The other seven slots are locked. Fusion begins after one second without changes,
consumes one item from each slot, and ejects the result from the front. Two identical items
work when placed in separate slots. Stacks repeat until an input runs out.

The preview shows a question mark until a fusion finishes, then shows its last result.
The question mark glints while generation is underway.
It is a preview only; the output still ejects from the front.

Changing either input cancels pending generation. After a failure, remove and reinsert an item
to retry. The player who last interacted with the station receives discoveries; a nearby player
is used after loading. A player must be online for the station to start.

The Gameplay settings include separate Ground fusion and Fusion Crafter toggles.
Marked stations never perform ordinary Crafter recipes. Redstone is unnecessary, but each pulse toggles the crafter between running and stopped.

With the mod installed, placed stations use the normal chunk renderer, with block lighting
and hidden-face culling. Empty stations sleep until their inventory changes.
Vanilla guests can use them too, but they see ordinary Crafter visuals. Modded clients use
the Fusion Crafter textures bundled inside the mod.

The textures reuse Minecraft assets belonging to Mojang.
