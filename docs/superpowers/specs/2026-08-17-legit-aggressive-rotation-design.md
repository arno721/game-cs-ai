# Armor Stand Grabber v0.3.0 — Legit, Aggressive Delay, and Rotation Design

## Goal

Extend the Minecraft 1.21.11 Meteor addon with three distinct delay profiles and a rotation controller that can keep the active armor stand targeted for the entire retrieval session.

The addon remains standalone and must not modify the Meteor Client jar or Meteor source tree.

## Global Constraints

- Target Minecraft: `1.21.11`.
- Target Java: `21`.
- Target Meteor dependency: `1.21.11-SNAPSHOT`.
- Preserve the existing through-wall armor-stand targeting behavior.
- Preserve existing overflow modes: `Off`, `Silent`, and `Auto Open`.
- Preserve `Auto Close` behavior for `Auto Open`.
- Existing server authority remains unchanged: servers may reject interactions based on reach, protection, or their own rules.
- Do not claim anti-cheat bypass or guaranteed server acceptance.
- Rotation implementation must not depend on Meteor's high-level `Rotations` helper. Use Fabric client lifecycle/tick hooks plus Minecraft's native movement/look packet path for the server-side mode.

## Delay Profile

Add a top-level enum setting named `delay-profile` with exactly three values:

1. `Normal`
2. `Legit`
3. `Aggressive`

Only settings relevant to the selected profile should be visible.

### Normal

Normal preserves v0.2.0 semantics:

1. Find/select an empty hotbar slot.
2. Immediately interact with the armor stand for the current equipment slot.
3. Wait for the item to arrive.
4. Sample the existing item-to-item delay.
5. Continue with the next equipment slot.

Normal uses the existing item delay controls:

- `min-delay-ms`
- `max-delay-ms`
- `delay-algorithm`

The current delay algorithms remain available.

### Legit

Legit makes the selected hotbar change visible and inserts an explicit delay before every interaction.

Per item:

1. Resolve the hotbar destination/buffer slot.
2. Switch the client's selected hotbar slot to that slot and synchronize it to the server.
3. Sample and wait `switch-delay`.
4. Verify the session target is still valid.
5. Interact with the armor stand.
6. Wait for the retrieved item.
7. Complete overflow handling if needed.
8. Sample and wait `item-delay`.
9. Continue to the next equipment slot.

Legit settings:

- `switch-min-delay-ms`
- `switch-max-delay-ms`
- `switch-delay-algorithm`
- `item-min-delay-ms`
- `item-max-delay-ms`
- `item-delay-algorithm`

Both delay ranges are clamped to non-negative milliseconds. If a user enters min > max, the sampler normalizes the range internally rather than failing the session.

### Aggressive

Aggressive uses a stateful high-variance timing engine. It is a separate profile, not a modifier layered on top of Legit.

Aggressive still uses the same retrieval phases as Legit — visible hotbar switch, switch wait, interaction, item wait, item-to-item wait — but delay values come from one stateful timing model.

The engine must support these algorithm families:

- `MixtureDistribution`
- `CorrelatedRandom`
- `DriftedGaussian`
- `LogNormal`
- `Gamma`
- `BurstPause`
- `MarkovRhythm`
- `AdaptiveHumanized`

Aggressive settings:

### Base Range

- `aggressive-min-delay-ms`
- `aggressive-max-delay-ms`

### Distribution Shape

- `sigma`
- `skew`
- `bias`
- `jitter`
- `outlier-chance`
- `outlier-scale`

### Temporal State

- `correlation`
- `momentum`
- `drift-strength`
- `drift-speed`

### Burst / Pause Behavior

- `burst-chance`
- `burst-size-min`
- `burst-size-max`
- `burst-multiplier`
- `pause-chance`
- `pause-min-ms`
- `pause-max-ms`

### Session Envelope

- `warmup-items`
- `cooldown-items`
- `acceleration`
- `deceleration`
- `clamp-enabled`

The aggressive engine stores per-session state including at minimum:

- previous sampled delay,
- current drift phase/value,
- current rhythm state,
- current burst counter,
- processed item count,
- total planned item count.

Each sampled delay may therefore depend on both configured parameters and the immediately preceding session history.

The final returned delay must never be negative. When clamping is enabled, normal samples are constrained to the configured base range except explicit pause/outlier branches, which are allowed to exceed it according to their own configured scales.

## Rotation

Add a separate `Rotation` setting group. Rotation operates independently from Delay Profile.

### Settings

- `rotation-enabled`
- `rotation-mode`: `Lock`, `Silent`
- `rotation-target`: `Center`, `UpperBody`, `InteractionPoint`
- `rotation-algorithm`: `Instant`, `Linear`, `Smoothstep`, `EaseInOut`, `Accelerate`, `AccelerateDecelerate`, `Adaptive`
- `max-yaw-speed-deg-per-sec`
- `max-pitch-speed-deg-per-sec`
- `rotation-acceleration-deg-per-sec2`
- `rotation-deceleration-deg-per-sec2`
- `rotation-smoothing`

Rotation starts when a retrieval session starts and remains active through all switch delays, item delays, overflow operations, and interaction waits until the session finishes or aborts.

### Target Point

- `Center`: center of the armor stand bounding box.
- `UpperBody`: upper torso area.
- `InteractionPoint`: the current equipment slot's interaction coordinate. While no concrete slot is being processed, fall back to upper body.

The target point must be recalculated from the armor stand's current coordinates while the session is active so a moving armor stand remains tracked.

### Lock Mode

Lock mode changes the actual client view.

On each Fabric client tick while the session is active:

1. Calculate desired yaw/pitch to the current target point.
2. Advance current rotation according to the selected rotation algorithm, speed limits, acceleration/deceleration, and smoothing.
3. Apply the resulting yaw and pitch to the local player/camera.
4. Allow Minecraft's normal movement packet flow to send the resulting look direction.

The user should visibly see the camera tracking the armor stand.

### Silent Mode

Silent mode must leave the visible client camera under user control.

On each Fabric client tick while the session is active:

1. Calculate desired server yaw/pitch to the current target point.
2. Advance a separate server-rotation state using the selected algorithm and limits.
3. Send the resulting server-side look using Minecraft's native `PlayerMoveC2SPacket.LookAndOnGround` packet path through the current network handler.
4. Do not write the computed silent yaw/pitch into the local player's visible yaw/pitch.

Immediately before each armor-stand interaction, ensure the latest silent yaw/pitch update has been sent for that target point.

When the session finishes, stop emitting silent look packets. The player's visible view must never be snapped to the silent rotation.

## Rotation Algorithms

### Instant

Immediately set current rotation equal to desired rotation.

### Linear

Move toward the target at constant yaw/pitch speed up to configured maxima.

### Smoothstep

Use a smoothstep interpolation factor over the normalized angular distance.

### EaseInOut

Use a symmetric ease-in/ease-out response so large corrections start and end more slowly.

### Accelerate

Maintain angular velocity state and increase velocity toward configured maxima using configured acceleration.

### AccelerateDecelerate

Accelerate while far from target and decelerate when stopping distance approaches remaining angular distance.

### Adaptive

Choose an interpolation strength based on angular distance: large errors rotate faster, small errors reduce velocity and smoothing overshoot.

All algorithms must use wrapped yaw differences so crossing `-180/180` follows the shortest angular path. Pitch must remain within Minecraft's valid look range.

## Retrieval State Machine

Refactor the current single module state machine into focused units while preserving behavior.

Recommended responsibilities:

- `ArmorStandGrabber`: Meteor settings and event entry point only.
- `RetrievalSession`: per-session progression and equipment queue.
- `DelayEngine`: profile selection and delay generation.
- `AggressiveDelayEngine`: stateful aggressive model.
- `RotationController`: Lock/Silent rotation state and Fabric tick integration.
- `InventoryOverflowController`: Off/Silent/AutoOpen transfer logic.

Required retrieval phases:

- `Idle`
- `PrepareSlot`
- `WaitSwitchDelay`
- `RotateForInteraction`
- `Interact`
- `WaitItem`
- `FinishOverflow`
- `WaitItemDelay`
- `Complete`
- `Abort`

Normal may skip `WaitSwitchDelay`. Legit and Aggressive use it.

## Fabric Integration

Add Fabric API as an explicit dependency compatible with Minecraft 1.21.11.

Use Fabric client lifecycle/tick events for session and rotation updates. Use Minecraft's native interaction manager and native movement/look packet classes for the actual interaction and Silent server rotation.

Do not use a mixin unless a verified 1.21.11 API gap makes it necessary. If a mixin becomes necessary during implementation, stop and revise this design before adding it.

## Error Handling

Abort the active session if any of these occur:

- player/world/interaction manager becomes unavailable,
- armor stand dies or unloads,
- a non-inventory GUI interrupts the session,
- server interaction does not produce the expected item before timeout,
- no allowed inventory destination remains,
- required inventory transfer fails.

On abort:

- restore the original selected hotbar slot when possible,
- close only an inventory screen that the addon itself opened and only when `Auto Close` is enabled,
- stop rotation immediately,
- clear aggressive delay state,
- preserve the user's visible camera direction in Silent mode.

## Testing

### Delay Unit Tests

Add deterministic tests using seeded random sources for:

- every Aggressive algorithm returns non-negative delay,
- bounded branches respect configured min/max when clamp is enabled,
- min/max normalization,
- correlation uses previous delay,
- drift state evolves across samples,
- burst counters enter and exit correctly,
- pause branch uses pause bounds,
- warmup and cooldown modify timing envelope,
- session reset clears state.

### Rotation Unit Tests

Test pure rotation math independently from Minecraft runtime:

- shortest wrapped yaw path,
- pitch clamping,
- speed limiting,
- acceleration,
- accelerate/decelerate stopping behavior,
- smoothing convergence,
- Adaptive reduces angular error without overshoot beyond configured tolerance.

### Build Verification

GitHub Actions must run the complete Gradle build and unit test suite on Java 21. The deliverable is accepted only after `gradle build` succeeds and the remapped addon jar is uploaded as an artifact.

## Versioning

Bump addon version from `0.2.0` to `0.3.0` only after the new functionality is implemented and the full CI build is green.
