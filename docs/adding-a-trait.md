# Adding a trait

Traits live in [`src/main/java/dev/rocks/infinitecraft/traits/`](../src/main/java/dev/rocks/infinitecraft/traits/). Add a definition and register it in `TraitRegistry.DEFINITIONS`. The registry supplies model choices, strength validation, hints, and allowed components, so ordinary traits need no provider or engine changes.

## Vanilla attributes

The existing armor trait is a minimal example:

```java
new AttributeTrait("armored", new StrengthRange(0, 8, 4),
        () -> Attributes.ARMOR, ADD),
```

| Argument | Meaning |
| --- | --- |
| `"armored"` | Unique ID used by model responses and saved recipes. |
| `StrengthRange(0, 8, 4)` | Minimum, maximum, and default native amount. Model strength 0–1 maps to 0–8. |
| `() -> Attributes.ARMOR` | Attribute supplier. Keep it lazy so opening settings does not initialize registries. |
| `ADD` | Flat addition. Use `INCREASE_FRACTION` or `DECREASE_FRACTION` for proportional changes. |

Attribute traits support main hand, offhand, and armor slots. `auto` uses the item's equipment slot or main hand. Multiple requested armor slots resolve to one. Reapplying a trait replaces its modifier rather than stacking copies.

An optional fifth argument, such as `() -> MobEffects.SPEED`, offers a `consumed` activation using that potion effect. Strength controls its level.

The model selects traits and normalized strengths:

```json
{"traits":["armored"],"strengths":{"armored":0.5},"activations":{"armored":"chest"}}
```

## Component abilities

Implement [`TraitDefinition`](../src/main/java/dev/rocks/infinitecraft/traits/TraitDefinition.java) and register the instance. [`DeathProtectionTrait`](../src/main/java/dev/rocks/infinitecraft/traits/DeathProtectionTrait.java) is a small complete example.

| Method | Purpose |
| --- | --- |
| `id()` | Unique trait ID. |
| `components()` | Every component written, including helper writes. |
| `apply(output, value)` | Apply the mapped native value to the output copy. |
| `range()` | Optional strength range; omit for on/off abilities. |
| `hint()` | Optional short hint, such as Edible or Wearable. |
| `supports(base)` | Reject incompatible item types. |
| `conflicts()` | Trait IDs that cannot coexist. |
| `validResult(result)` | Validate component combinations after application and inheritance. |
| `activationModes()` | Supported activation strings; defaults to `auto`. |
| `prepareActivation(output, mode)` | Prepare an equipment slot or reject the activation. |
| `apply(output, value, mode)` | Apply behavior for the chosen activation. |
| `phase()` | Apply in `EQUIPMENT`, `EFFECT`, or `FOOD` order. |

Only implement optional methods you need. For food abilities, extend `FoodTrait` and implement `effect(value)`. Override `replaces(effect)` if retuning should replace an existing effect.

## Inheritance and compatibility

`VanillaTraits` copies the item, validates choices, applies traits, and adds hints. Traits modify that copy; they do not consume inputs, call a model, or save recipes.

`ItemDataFusion` derives its component allowlist from trait declarations. Ordinary components use equality merging. Components needing special merge behavior require a rule there. `TraitComponents` contains shared operations for equipment, attributes, and durability.

Keep existing trait and modifier IDs stable because saved items and recipes use them. Read current ranges directly in [`TraitRegistry`](../src/main/java/dev/rocks/infinitecraft/traits/TraitRegistry.java).

## Check the change

Run the [tests](testing.md). Cover the actual effect, strength bounds, repeated application, incompatible targets, native serialization, and inheritance. Registry-driven tests use a stick fixture; supply another item if the trait rejects it.

Finally, try holding, wearing, or consuming the result in-game, including with a vanilla guest.
