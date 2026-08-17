# Armor Stand Grabber

Meteor Client addon for **Minecraft 1.21.11**.

## Behavior

- Adds an `Armor Stand Grabber` module under the `Armor Stand` category.
- `Range` controls the maximum targeting distance.
- When enabled, right-clicking while aiming at an armor stand searches through intervening blocks.
- If a target is found, vanilla right-click for that action is cancelled and the addon attempts to remove every equipped stack into distinct empty hotbar slots.
- When disabled, normal Minecraft/Meteor interaction behavior is untouched.
- Inventory contents remain server-authoritative; the addon only sends normal interaction actions.

## Build

```bash
gradle build
```

The addon jar is produced under `build/libs/`.
