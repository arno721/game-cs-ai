# Armor Stand Grabber

Meteor Client addon for **Minecraft 1.21.11**.

Runtime requirements: Meteor Client for 1.21.11, Fabric Loader, and Fabric API compatible with Minecraft 1.21.11.

## Core behavior

- Adds `Armor Stand Grabber` under the `Armor Stand` category.
- `Range` controls the maximum through-wall armor-stand targeting distance.
- A valid right-click is intercepted and the target armor stand is kept as the active retrieval target until the session completes or aborts.
- Equipment is retrieved one stack at a time into empty hotbar slots, with optional inventory overflow handling.
- When the module is disabled, normal Minecraft/Meteor interaction behavior is untouched.
- Inventory and interaction results remain server-authoritative; a server may still reject an action because of reach, protection, or server rules.

## Delay profiles

### Normal

Preserves the v0.2 behavior. The addon selects an empty hotbar slot and interacts immediately, then applies the configured item-to-item delay.

Normal includes the existing delay algorithms: Fixed Midpoint, Uniform Random, Gaussian, Triangular, Fast Biased, Slow Biased, Midpoint Jitter, Alternating, and Humanized Drift.

### Legit

Uses an explicit visible hotbar-switch phase for every item:

`select empty hotbar slot -> switch delay -> interact -> item delay -> next item`

Switch delay and item delay each have independent Min/Max millisecond settings and independent delay algorithms.

### Aggressive

Uses the same visible switch/interact phases as Legit, but both switch and item delays come from one stateful timing engine. Available algorithm families are:

- Mixture Distribution
- Correlated Random
- Drifted Gaussian
- Log Normal
- Gamma
- Burst / Pause
- Markov Rhythm
- Adaptive Humanized

The engine exposes base Min/Max plus Sigma, Skew, Bias, Jitter, Correlation, Momentum, Drift Strength/Speed, Burst Chance/Size/Multiplier, Pause Chance/Min/Max, Outlier Chance/Scale, Warm-up, Cool-down, Acceleration, Deceleration, and Clamp controls. Samples can depend on prior samples and the current session state rather than being independent random values.

## Rotation

Rotation can remain active for the entire unfinished retrieval session, including switch delays, item delays, overflow operations, and interaction waits.

- `Lock`: continuously updates the actual local yaw/pitch, so the visible client camera remains aimed at the armor stand.
- `Silent`: leaves the visible client camera under player control and sends separate server-side look updates with Minecraft's native `PlayerMoveC2SPacket.LookAndOnGround` path.

Target modes:

- Center
- Upper Body
- Interaction Point (tracks the equipment slot currently being retrieved and falls back to upper body between items)

Rotation algorithms:

- Instant
- Linear
- Smoothstep
- Ease In-Out
- Accelerate
- Accelerate-Decelerate
- Adaptive

Rotation controls include maximum yaw/pitch speed, angular acceleration, angular deceleration, and smoothing. Yaw uses shortest-path wrapping and pitch is clamped to Minecraft's valid look range.

The client tick integration uses Fabric API `ClientTickEvents.END_CLIENT_TICK`. Silent mode does not use Meteor's high-level rotation helper.

## Inventory overflow

`Overflow Mode` controls what happens when the hotbar has no empty slot:

- `Off`: stop.
- `Silent`: use an empty main-inventory slot as overflow without opening the inventory GUI.
- `Auto Open`: automatically open the inventory and perform the same transfer automatically.

With `Auto Open`, `Auto Close` decides whether an inventory screen opened by the addon is closed when the session ends.

## Build

```bash
gradle test
gradle build
```

The remapped addon jar is produced under `build/libs/`.
