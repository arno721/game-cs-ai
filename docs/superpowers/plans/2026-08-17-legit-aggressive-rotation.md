# Armor Stand Grabber v0.3.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Normal/Legit/Aggressive delay profiles, stateful high-variance delay generation, and Lock/Silent rotation tracking to the Minecraft 1.21.11 Armor Stand Grabber addon.

**Architecture:** Keep Meteor responsible for module settings and right-click interception, but move session progression to Fabric `ClientTickEvents.END_CLIENT_TICK`. Pure-Java timing and rotation math are isolated behind deterministic testable classes; Minecraft-facing rotation uses local yaw/pitch for Lock and `PlayerMoveC2SPacket.LookAndOnGround` for Silent. Existing inventory overflow behavior is preserved and invoked by a focused retrieval session state machine.

**Tech Stack:** Java 21, Minecraft 1.21.11, Yarn 1.21.11+build.3, Fabric Loader 0.18.2, Fabric API 0.141.4+1.21.11, Fabric Loom 1.14-SNAPSHOT, Meteor Client 1.21.11-SNAPSHOT, JUnit 5, Gradle 9.2.0.

## Global Constraints

- Keep the addon standalone; do not modify Meteor Client.
- Preserve through-wall armor-stand targeting and Off/Silent/AutoOpen overflow behavior.
- Delay Profile values are exactly `Normal`, `Legit`, `Aggressive`.
- Legit must visibly select the empty hotbar slot and wait a configurable switch delay before interacting.
- Aggressive is a separate profile, not a modifier layered on Legit.
- Rotation modes are exactly `Lock` and `Silent`.
- Lock changes the visible client camera; Silent leaves the visible camera untouched and only emits server-side look packets.
- Do not use Meteor's high-level `Rotations` helper.
- Use Fabric client tick events plus vanilla 1.21.11 packet and interaction APIs.
- No mixin unless a verified API gap forces a design revision.
- Do not claim anti-cheat bypass or guaranteed server acceptance.
- Bump to `0.3.0` only after the complete CI build is green.

---

### Task 1: Add Fabric API and delay-profile model

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Create: `src/main/java/com/arno721/armorstandgrabber/delay/DelayProfile.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/delay/AggressiveAlgorithm.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/delay/AggressiveDelayConfig.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/delay/AggressiveDelayEngine.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/delay/AggressiveDelayEngineTest.java`

**Interfaces:**
- `AggressiveDelayEngine#beginSession(int totalItems)` resets previous delay, drift phase, rhythm state, burst counter, and processed count.
- `AggressiveDelayEngine#nextDelayMs(AggressiveAlgorithm algorithm, AggressiveDelayConfig config): long` returns one non-negative stateful delay.
- `AggressiveDelayConfig` is an immutable record containing min/max, sigma, skew, bias, jitter, correlation, momentum, drift, burst, pause, outlier, warmup/cooldown, acceleration/deceleration, and clamp controls.

- [ ] **Step 1: Add failing delay tests.** Cover normalized min/max, non-negative output for all eight algorithms, clamp behavior, pause bounds, burst state, drift evolution, correlation, and session reset using seeded `Random` instances.
- [ ] **Step 2: Run `gradle test --tests '*AggressiveDelayEngineTest'` in CI and verify compilation/test failure before the production classes exist.**
- [ ] **Step 3: Implement the model.** `MixtureDistribution` mixes Gaussian/uniform/log-normal branches; `CorrelatedRandom` blends a fresh sample with the previous delay; `DriftedGaussian` adds sinusoidal state; `LogNormal` uses `exp(gaussian)` normalized to range; `Gamma` uses Marsaglia-Tsang sampling; `BurstPause` maintains a burst counter and optional pause branch; `MarkovRhythm` transitions among Fast/Normal/Slow states; `AdaptiveHumanized` combines drift, momentum, correlation, jitter, envelope, outliers, and pauses.
- [ ] **Step 4: Clamp every normal branch to the normalized base range when `clampEnabled` is true, while explicit pause/outlier branches may exceed it; never return a negative delay.**
- [ ] **Step 5: Run the full delay test suite and commit once green.**

### Task 2: Add pure rotation math and tests

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/rotation/RotationMode.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/rotation/RotationTarget.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/rotation/RotationAlgorithm.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/rotation/RotationState.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/rotation/RotationMath.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/rotation/RotationMathTest.java`

**Interfaces:**
- `RotationMath.wrapDegrees(double)` returns a shortest-path yaw delta in `[-180, 180)`.
- `RotationMath.targetAngles(...)` computes desired yaw/pitch from player eye coordinates to a target point.
- `RotationMath.advance(RotationState state, double targetYaw, double targetPitch, RotationAlgorithm algorithm, double dtSeconds, RotationConfig config): RotationState` applies speed limits, acceleration/deceleration, smoothing, yaw wrapping, and pitch clamp.

- [ ] **Step 1: Write failing tests for shortest yaw wrap, pitch clamp, max yaw/pitch speed, acceleration, accelerate/decelerate stopping, smoothing convergence, and Adaptive error reduction.**
- [ ] **Step 2: Run only `RotationMathTest` and verify failure before implementation.**
- [ ] **Step 3: Implement `Instant`, `Linear`, `Smoothstep`, `EaseInOut`, `Accelerate`, `AccelerateDecelerate`, and `Adaptive` without any Minecraft dependency.**
- [ ] **Step 4: Run rotation tests and commit once green.**

### Task 3: Implement Fabric-driven RotationController

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/rotation/RotationConfig.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/rotation/RotationController.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java`

**Interfaces:**
- `RotationController#begin(ArmorStandEntity target)` initializes Lock/Silent state from the current client/server direction.
- `RotationController#tick(EquipmentSlot currentSlot, RotationTarget targetMode, RotationAlgorithm algorithm, RotationConfig config)` updates one Fabric client tick.
- `RotationController#prepareInteraction(...)` forces the latest rotation update immediately before an armor-stand interaction.
- `RotationController#stop()` clears active state without snapping the visible camera.

- [ ] **Step 1: Register `ClientTickEvents.END_CLIENT_TICK` once from the addon entrypoint and route it to the module.**
- [ ] **Step 2: For Lock, apply computed yaw/pitch to the local player every active tick and let normal movement packets carry it.**
- [ ] **Step 3: For Silent, never change local player yaw/pitch; send `PlayerMoveC2SPacket.LookAndOnGround` through the current network handler using the computed server rotation.**
- [ ] **Step 4: Recalculate Center/UpperBody/InteractionPoint from the live armor-stand coordinates each tick; InteractionPoint falls back to UpperBody when no slot is active.**
- [ ] **Step 5: Stop packet emission immediately when the retrieval session ends or aborts.**

### Task 4: Refactor retrieval into Normal/Legit/Aggressive state machine

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/session/RetrievalPhase.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/session/RetrievalSession.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/InventoryOverflowController.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/modules/ArmorStandGrabber.java`

**Interfaces:**
- Required phases: `Idle`, `PrepareSlot`, `WaitSwitchDelay`, `RotateForInteraction`, `Interact`, `WaitItem`, `FinishOverflow`, `WaitItemDelay`, `Complete`, `Abort`.
- `RetrievalSession#begin(ArmorStandEntity)` snapshots the equipment queue and original selected slot.
- `RetrievalSession#tick(long nowMs)` advances exactly according to the current phase.
- `InventoryOverflowController` owns Off/Silent/AutoOpen buffer preparation, transfer completion, Auto Close, and restoration.

- [ ] **Step 1: Move existing overflow state and swap operations into `InventoryOverflowController` without changing behavior.**
- [ ] **Step 2: Normal skips `WaitSwitchDelay`, interacts immediately after selecting a destination slot, and then uses the existing item-delay sampler.**
- [ ] **Step 3: Legit visibly selects/synchronizes the empty hotbar slot, samples `switch-min/max-delay-ms`, waits, rotates, interacts, waits for the item, then samples the independent Legit item delay.**
- [ ] **Step 4: Aggressive uses the same visible switch/interact phases as Legit but obtains both switch and item waits from the stateful `AggressiveDelayEngine`; call `beginSession(totalItems)` once per retrieval.**
- [ ] **Step 5: Rotation remains active during switch waits, interaction waits, overflow operations, and item delays; call `prepareInteraction` immediately before each entity interaction.**
- [ ] **Step 6: On completion/abort/deactivation/world loss, restore the original selected slot, unwind any active overflow buffer, apply Auto Close only to addon-opened inventory screens, stop rotation, and reset aggressive state.**

### Task 5: Meteor settings, documentation, version, and CI verification

**Files:**
- Modify: `src/main/java/com/arno721/armorstandgrabber/modules/ArmorStandGrabber.java`
- Modify: `README.md`
- Modify: `gradle/libs.versions.toml`
- Modify: `src/main/resources/fabric.mod.json`

**Interfaces:**
- Expose profile-specific Meteor settings using `.visible(...)` predicates.
- Expose the Rotation group and all requested algorithm/speed/acceleration/smoothing controls.

- [ ] **Step 1: Add Delay Profile settings and hide irrelevant Normal/Legit/Aggressive controls.**
- [ ] **Step 2: Add Rotation Enabled, Mode, Target Point, Algorithm, max yaw/pitch speed, acceleration, deceleration, and smoothing settings.**
- [ ] **Step 3: Add explicit Fabric API dependency metadata and document the required runtime dependency.**
- [ ] **Step 4: Update README with Normal/Legit/Aggressive data flow and Lock/Silent semantics.**
- [ ] **Step 5: Run GitHub Actions `gradle build --stacktrace`; fix only evidence-backed compile/test failures until `compileJava`, all JUnit tests, `remapJar`, and `build` succeed.**
- [ ] **Step 6: After green CI, change mod version from `0.2.0` to `0.3.0`, rerun the complete build, download the artifact, inspect JAR contents and `fabric.mod.json`, and deliver the remapped jar.**
