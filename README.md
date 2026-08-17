# Armor Stand Grabber

Meteor Client addon for **Minecraft 1.21.11**.

## Behavior

- Adds an `Armor Stand Grabber` module under the `Armor Stand` category.
- `Range` controls the maximum through-wall targeting distance.
- When enabled, right-clicking while aiming at an armor stand searches through intervening blocks.
- If a target is found, vanilla right-click for that action is cancelled and the addon retrieves the armor stand's equipped stacks one at a time.
- When disabled, normal Minecraft/Meteor interaction behavior is untouched.
- Inventory contents remain server-authoritative; the addon only uses normal entity interaction and inventory actions.

## Delay

All delays use milliseconds and are re-sampled between successfully retrieved items.

- `Min Delay`: minimum delay in ms.
- `Max Delay`: maximum delay in ms.
- `Delay Algorithm`:
  - Fixed Midpoint
  - Uniform Random
  - Gaussian
  - Triangular
  - Fast Biased
  - Slow Biased
  - Midpoint Jitter
  - Alternating
  - Humanized Drift

Every algorithm is clamped to the configured Min/Max interval. If Min is greater than Max, the addon normalizes the two values at runtime.

## Inventory overflow

`Overflow Mode` controls what happens after all empty hotbar slots have been used.

- `Off`: stop when the hotbar has no empty slot.
- `Silent`: automatically place additional retrieved equipment into empty main-inventory slots without opening the inventory GUI.
- `Auto Open`: automatically open the player inventory when overflow is needed and automatically move additional equipment into empty main-inventory slots.

When `Auto Open` is selected, `Auto Close` controls whether the inventory screen is closed again after the retrieval session finishes or aborts. Turning Auto Close off leaves the inventory screen open.

## Build

```bash
gradle test
gradle build
```

The addon jar is produced under `build/libs/`.
