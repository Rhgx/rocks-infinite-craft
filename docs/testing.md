# Testing

With Java 25 installed, run `./gradlew build`, or `./gradlew.bat build` on Windows. For one test class:

```sh
./gradlew test --tests dev.rocks.infinitecraft.DiscoveryBookTest
```

Tests cover provider protocols using local fake servers, validation, recipe persistence, cancellation, item components, and discovery dialogs. They do not call paid providers or verify graphics and multiplayer interaction.

Before a release, check in-game:

1. Switch between Ollama, Codex, and hosted APIs. Check model IDs, custom fields, connection tests, and saved settings.
2. Drop items individually, including identical items. Try bulk drops, a failed pair, and picking up an input during generation.
3. Repeat a known pair and reopen the world. Confirm the result and fusion toggle persist.
4. Try global/personal and soulbound/craftable books. Check navigation, tooltips, Share, offhand movement, dropping, and respawning.
5. Try generated food, worn items, enchantments, potions, and the five-combination limit.
6. Invite a vanilla client and check fusion, books, effects, and host-only controls.

Close Minecraft before replacing its loaded mod JAR.
