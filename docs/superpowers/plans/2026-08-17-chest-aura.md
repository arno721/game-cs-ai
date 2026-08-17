# ChestAura Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the approved ChestAura child feature to Chest Stealer: discover nearby storage blocks, select a reachable interaction target, rotate silently through the shared rotation coordinator, interact through Minecraft's normal block-use path, wait/retry for a compatible container screen, and track both automated and manual storage interactions.

**Architecture:** ChestAura is not a standalone Meteor module. `ChestAuraController` is a runtime participant owned by `RuntimeOwner.CHEST_AURA`, activated only while the parent Chest Stealer module and its Aura setting are active. Storage discovery, target geometry, smoothing, retry state, and interacted-block tracking are separate testable components. All server-facing look changes use the shared `RotationCoordinator`; all block use goes through normal client interaction APIs and is revalidated with a raycast immediately before interaction.

**Tech Stack:** Java 21, Minecraft 1.21.11, Yarn 1.21.11+build.3, Fabric Loader 0.18.2, Fabric API 0.141.4+1.21.11, Fabric Loom 1.14-SNAPSHOT, Meteor Client 1.21.11-SNAPSHOT, JUnit Jupiter 5.11.4, Gradle 9.2.0.

## Global Constraints

- Requires completed Runtime Foundation, Inventory Cleaner, and Chest Stealer Core plans.
- ChestAura remains a child of Chest Stealer; do not add a fourth Meteor module.
- Defaults: Range=3.0, WallRange=0.0, Delay=5 ticks, SwingMode=DoNotHide, NotDuringCombat=true, TrackManualInteractions=true, PauseOn empty, AwaitContainer=true, Timeout=10 ticks, MaxRetries=4.
- Range allowed 1.0..6.0; WallRange allowed 0.0..6.0 and effective value is clamped to Range; Delay allowed 1..80; Timeout allowed 1..80; MaxRetries allowed 1..10.
- Default storage registry selection: registry path ending in `chest`, `shulker_box`, `barrel`, or `furnace`, plus explicit Brewing Stand, Dispenser, and Hopper blocks. Matching is case-insensitive against the registry path; do not match translated display names.
- Reject blocked vanilla chests and already-interacted positions.
- A double chest interaction tracks both halves.
- Final interaction is allowed only when a fresh raycast under the current server-facing rotation resolves to the intended block.
- ChestAura does nothing while an eligible container is already open.
- Rotation ownership is below Armor Stand Grabber and above no other rotation client. It uses `RuntimeOwner.CHEST_AURA`.
- `MovementCorrection=Silent`: server-facing yaw/pitch changes must not visibly snap the local camera; movement input remains based on the visible local orientation.
- `ResetThreshold=2.0°`; `TicksUntilReset=5`.
- AngleSmooth choices are exactly Linear, Sigmoid, Acceleration.
- Linear defaults HorizontalTurnSpeed=180..180 and VerticalTurnSpeed=180..180, allowed 0..180.
- Sigmoid defaults HorizontalTurnSpeed=180..180, VerticalTurnSpeed=180..180, Steepness=10 (0..20), Midpoint=0.3 (0..1).
- Acceleration defaults YawAcceleration=20..25 and PitchAcceleration=20..25, allowed 1..180.
- DynamicAccel defaults disabled: CoefDistance=-1.393 (-2..2), YawCrosshairAccel=17..20, PitchCrosshairAccel=17..20, acceleration ranges 1..180.
- AccelerationError defaults enabled: YawAccelError=0.1 and PitchAccelError=0.1, allowed 0.01..1.
- ConstantError defaults enabled: YawConstantError=0.1 and PitchConstantError=0.1, allowed 0.01..1.
- SigmoidDeceleration defaults disabled: Steepness=10 (0..20), Midpoint=0.3 (0..1).
- Because ChestAura targets blocks rather than entities, DynamicAccel's entity-distance/crosshair branch has no target entity and therefore uses distance=0/crosshair=false; settings remain exposed for compatibility with the approved rotation model.
- No anti-cheat bypass behavior; server remains authoritative.
- Clean-room Java implementation only; do not translate LiquidBounce implementation line-by-line.

---

### Task 1: Define storage policy and interacted-block tracking

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/StorageBlockPolicy.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/InteractedStorageTracker.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/StorageBlockPolicyTest.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/InteractedStorageTrackerTest.java`

**Interfaces:**
- Produces `StorageBlockPolicy.defaultRegistryPathMatch(String path)` as a pure helper.
- Produces `StorageBlockPolicy.isConfigured(Block block, Set<Identifier> configuredIds)`.
- Produces `StorageBlockPolicy.isBlockedChest(ClientWorld world, BlockPos pos)`.
- Produces `StorageBlockPolicy.doubleChestPartner(ClientWorld world, BlockPos pos)` returning `Optional<BlockPos>`.
- Produces `InteractedStorageTracker.mark(BlockPos)`, `markWithPartner(BlockPos, Optional<BlockPos>)`, `contains(BlockPos)`, `clear()`.

- [ ] **Step 1: Write failing pure default-policy tests**

```java
@Test
void defaultRegistrySuffixFamiliesMatchExpectedVariants() {
    assertTrue(StorageBlockPolicy.defaultRegistryPathMatch("chest"));
    assertTrue(StorageBlockPolicy.defaultRegistryPathMatch("trapped_chest"));
    assertTrue(StorageBlockPolicy.defaultRegistryPathMatch("blue_shulker_box"));
    assertTrue(StorageBlockPolicy.defaultRegistryPathMatch("barrel"));
    assertTrue(StorageBlockPolicy.defaultRegistryPathMatch("blast_furnace"));
    assertTrue(StorageBlockPolicy.defaultRegistryPathMatch("furnace"));
    assertFalse(StorageBlockPolicy.defaultRegistryPathMatch("ender_chest_like_decoration"));
}

@Test
void explicitDefaultIdsCoverNonSuffixStorageBlocks() {
    Set<String> explicit = StorageBlockPolicy.explicitDefaultPaths();
    assertTrue(explicit.contains("brewing_stand"));
    assertTrue(explicit.contains("dispenser"));
    assertTrue(explicit.contains("hopper"));
}
```

`InteractedStorageTrackerTest`:

```java
@Test
void doubleChestMarkStoresBothPositionsAndClearResetsState() {
    InteractedStorageTracker tracker = new InteractedStorageTracker();
    BlockPos a = new BlockPos(1, 2, 3);
    BlockPos b = new BlockPos(2, 2, 3);
    tracker.markWithPartner(a, Optional.of(b));
    assertTrue(tracker.contains(a));
    assertTrue(tracker.contains(b));
    tracker.clear();
    assertFalse(tracker.contains(a));
    assertFalse(tracker.contains(b));
}
```

- [ ] **Step 2: Run focused tests and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.StorageBlockPolicyTest \
            --tests com.arno721.armorstandgrabber.chest.InteractedStorageTrackerTest
```

Expected: the new classes do not exist.

- [ ] **Step 3: Implement registry defaults without display-name heuristics**

The pure path matcher is:

```java
public static boolean defaultRegistryPathMatch(String path) {
    String normalized = path.toLowerCase(Locale.ROOT);
    return normalized.equals("chest")
        || normalized.endsWith("_chest")
        || normalized.equals("shulker_box")
        || normalized.endsWith("_shulker_box")
        || normalized.equals("barrel")
        || normalized.endsWith("_barrel")
        || normalized.equals("furnace")
        || normalized.endsWith("_furnace");
}
```

The explicit paths are exactly `brewing_stand`, `dispenser`, and `hopper`.

Add `defaultBlockIds()` that iterates `Registries.BLOCK`, includes IDs whose path passes the matcher, and adds `Blocks.BREWING_STAND`, `Blocks.DISPENSER`, and `Blocks.HOPPER` by registry ID.

- [ ] **Step 4: Implement vanilla blocked/double-chest helpers**

For chest blocks, inspect `ChestBlock` state and vanilla chest geometry. `isBlockedChest` must reject a chest when vanilla would not permit opening because a solid block is immediately above or a sitting cat occupies the blocking volume. Use Minecraft state/entity queries; do not hard-code translated names.

For a normal/trapped double chest, derive the partner from chest type/facing properties and return the other half only when the neighboring block is the same chest block and has the complementary chest type. Non-double storage returns empty.

- [ ] **Step 5: Run tests, compile integration, and commit**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.StorageBlockPolicyTest \
            --tests com.arno721.armorstandgrabber.chest.InteractedStorageTrackerTest
gradle compileJava
```

```bash
git add src/main/java/com/arno721/armorstandgrabber/chest/StorageBlockPolicy.java \
        src/main/java/com/arno721/armorstandgrabber/chest/InteractedStorageTracker.java \
        src/test/java/com/arno721/armorstandgrabber/chest/StorageBlockPolicyTest.java \
        src/test/java/com/arno721/armorstandgrabber/chest/InteractedStorageTrackerTest.java
git commit -m "feat: define chest aura storage policy"
```

---

### Task 2: Implement range-aware target discovery and interaction geometry

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraTarget.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/InteractionPointSampler.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraTargetFinder.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/InteractionPointSamplerTest.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/ChestAuraTargetFinderTest.java`

**Interfaces:**
- Produces `ChestAuraTarget(BlockPos blockPos, Vec3d hitPos, Direction side, double distance, float targetYaw, float targetPitch, boolean visible)`.
- Produces pure `InteractionPointSampler.sample(Box box)` for center + six face-center points.
- Produces `ChestAuraTargetFinder.find(MinecraftClient mc, double range, double wallRange, Set<Identifier> storageIds, InteractedStorageTracker tracker)`.

- [ ] **Step 1: Write failing geometry tests**

```java
@Test
void samplerReturnsCenterAndSixFaceCenters() {
    Box box = new Box(0, 0, 0, 1, 1, 1);
    List<Vec3d> points = InteractionPointSampler.sample(box);
    assertEquals(7, points.size());
    assertTrue(points.contains(new Vec3d(0.5, 0.5, 0.5)));
    assertTrue(points.contains(new Vec3d(1.0, 0.5, 0.5)));
    assertTrue(points.contains(new Vec3d(0.0, 0.5, 0.5)));
}

@Test
void candidatesSortByDistanceThenBlockCoordinates() {
    List<BlockCandidate> sorted = ChestAuraTargetFinder.sortCandidates(List.of(
        new BlockCandidate(new BlockPos(3, 0, 0), 3.0),
        new BlockCandidate(new BlockPos(1, 0, 0), 1.0),
        new BlockCandidate(new BlockPos(0, 0, 1), 1.0)
    ));
    assertEquals(List.of(new BlockPos(0, 0, 1), new BlockPos(1, 0, 0), new BlockPos(3, 0, 0)),
        sorted.stream().map(BlockCandidate::pos).toList());
}
```

- [ ] **Step 2: Run and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.InteractionPointSamplerTest \
            --tests com.arno721.armorstandgrabber.chest.ChestAuraTargetFinderTest
```

- [ ] **Step 3: Implement world scan bounds and filtering**

Use integer scan bounds around the player's eye position:

```java
int radius = (int) Math.ceil(range);
BlockPos center = BlockPos.ofFloored(mc.player.getEyePos());
```

Enumerate `[-radius,+radius]` on each axis, skip unloaded positions, and reject before geometry work when:

- block registry ID is not configured
- tracker contains the position
- `StorageBlockPolicy.isBlockedChest` is true
- squared eye-to-block-center distance exceeds `range * range`

Sort remaining positions by exact eye-to-center distance ascending, then x, y, z for deterministic ties.

- [ ] **Step 4: Find a raycast-capable interaction point**

For each candidate:

1. obtain the block state's outline/collision shape bounding box in world coordinates; fall back to full block box only when the state shape is empty but the block is otherwise configured storage
2. sample center + six face centers
3. derive yaw/pitch from eye position to each point
4. raycast from eye to the point using `RaycastContext.ShapeType.OUTLINE` and `FluidHandling.NONE`
5. mark the point visible when the returned `BlockHitResult` resolves to the intended block
6. visible point is always preferred
7. when no visible point exists and eye-to-point distance is `<= effectiveWallRange`, allow the nearest sampled point as a rotation candidate with `visible=false`; this does **not** authorize interaction through the obstruction
8. choose lowest distance point; ties prefer visible then deterministic coordinate order

`effectiveWallRange = Math.min(range, wallRange)`.

- [ ] **Step 5: Compile and commit**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.InteractionPointSamplerTest \
            --tests com.arno721.armorstandgrabber.chest.ChestAuraTargetFinderTest
gradle compileJava
```

```bash
git add src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraTarget.java \
        src/main/java/com/arno721/armorstandgrabber/chest/InteractionPointSampler.java \
        src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraTargetFinder.java \
        src/test/java/com/arno721/armorstandgrabber/chest/InteractionPointSamplerTest.java \
        src/test/java/com/arno721/armorstandgrabber/chest/ChestAuraTargetFinderTest.java
git commit -m "feat: find chest aura interaction targets"
```

---

### Task 3: Implement clean-room ChestAura rotation smoothing

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraAngleSmooth.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraRotationConfig.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/RotationPair.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraRotator.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/ChestAuraRotatorTest.java`

**Interfaces:**
- Produces `ChestAuraAngleSmooth { Linear, Sigmoid, Acceleration }`.
- Produces immutable `ChestAuraRotationConfig` containing every approved setting/range-backed value.
- Produces `ChestAuraRotator.step(currentServer, previousServer, target, config, Random)` and `resetStep(...)` as pure math.
- Produces `ChestAuraRotator.applyTarget(..., RotationCoordinator)` using `RuntimeOwner.CHEST_AURA` and `visible=false`.

- [ ] **Step 1: Write failing deterministic rotation tests**

```java
@Test
void linearAtOneHundredEightyCanReachNinetyDegreeTargetInOneTick() {
    ChestAuraRotationConfig config = ChestAuraRotationConfig.defaults()
        .withMode(ChestAuraAngleSmooth.Linear)
        .withLinearSpeeds(180, 180, 180, 180);
    RotationPair result = rotator.step(
        new RotationPair(0, 0), new RotationPair(0, 0), new RotationPair(90, 30), config, new Random(1));
    assertEquals(90.0, result.yaw(), 1e-6);
    assertEquals(30.0, result.pitch(), 1e-6);
}

@Test
void sigmoidSpeedShrinksWhenDifferenceIsSmall() {
    ChestAuraRotationConfig config = ChestAuraRotationConfig.defaults().withMode(ChestAuraAngleSmooth.Sigmoid);
    double small = rotator.maxStepForDifference(5, 180, 10, 0.3);
    double large = rotator.maxStepForDifference(120, 180, 10, 0.3);
    assertTrue(small < large);
}

@Test
void accelerationHonorsConfiguredAccelerationBoundWhenErrorsDisabled() {
    ChestAuraRotationConfig config = ChestAuraRotationConfig.defaults()
        .withMode(ChestAuraAngleSmooth.Acceleration)
        .withYawAcceleration(20, 20)
        .withPitchAcceleration(20, 20)
        .withAccelerationError(false, 0.1, 0.1)
        .withConstantError(false, 0.1, 0.1);
    RotationPair result = rotator.step(
        new RotationPair(0, 0), new RotationPair(0, 0), new RotationPair(90, 40), config, new Random(1));
    assertTrue(Math.abs(result.yaw()) <= 20.0001);
    assertTrue(Math.abs(result.pitch()) <= 20.0001);
}
```

- [ ] **Step 2: Run and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.ChestAuraRotatorTest
```

- [ ] **Step 3: Implement immutable defaults and validation**

`ChestAuraRotationConfig.defaults()` returns exactly:

```text
mode = Linear
movementCorrection = Silent
resetThreshold = 2.0
ticksUntilReset = 5
linearHorizontal = 180..180
linearVertical = 180..180
sigmoidHorizontal = 180..180
sigmoidVertical = 180..180
sigmoidSteepness = 10
sigmoidMidpoint = 0.3
yawAcceleration = 20..25
pitchAcceleration = 20..25
dynamicAccelEnabled = false
coefDistance = -1.393
yawCrosshairAccel = 17..20
pitchCrosshairAccel = 17..20
accelerationErrorEnabled = true
yawAccelError = 0.1
pitchAccelError = 0.1
constantErrorEnabled = true
yawConstantError = 0.1
pitchConstantError = 0.1
sigmoidDecelerationEnabled = false
sigmoidDecelSteepness = 10
sigmoidDecelMidpoint = 0.3
```

Normalize every min/max range before sampling and clamp values to the approved setting limits.

- [ ] **Step 4: Implement independent smoothing math**

Common helpers:

- yaw delta is wrapped to `[-180, 180)`
- pitch target/result clamps to `[-90, 90]`
- sampled range uses `min + random.nextDouble() * (max - min)`; equal bounds return the bound exactly

Linear:

```text
yawStep = clampAbs(yawDelta, sampledHorizontalTurnSpeed)
pitchStep = clampAbs(pitchDelta, sampledVerticalTurnSpeed)
```

Sigmoid:

```text
rotationDifference = min(180, hypot(yawDelta, pitchDelta))
scaled = rotationDifference / 120
factor = 1 / (1 + exp(-steepness * (scaled - midpoint)))
yawLimit = clamp(factor * sampledHorizontalTurnSpeed, 0, 180)
pitchLimit = clamp(factor * sampledVerticalTurnSpeed, 0, 180)
```

Acceleration clean-room state model:

```text
previousYawVelocity = wrapped(previousServerYaw - beforePreviousServerYaw)
previousPitchVelocity = previousServerPitch - beforePreviousServerPitch
requiredYawVelocity = yawDelta
requiredPitchVelocity = pitchDelta
baseYawAccel = sample(yawAcceleration)
basePitchAccel = sample(pitchAcceleration)
```

For block targets use `distance=0` and `crosshair=false`, so the base acceleration ranges are used. When DynamicAccel is enabled for a future entity-bearing target, use crosshair acceleration ranges when crosshair=true and add `coefDistance * distance` to the signed acceleration center.

For each axis:

```text
neededAcceleration = requiredVelocity - previousVelocity
boundedAcceleration = clamp(neededAcceleration, -sampledAccel + distanceFactor, +sampledAccel + distanceFactor)
```

If SigmoidDeceleration is enabled, multiply bounded acceleration by the same logistic factor based on total rotation difference.

If AccelerationError is enabled, add `boundedAcceleration * uniform(-axisError,+axisError)`.
If ConstantError is enabled, add `uniform(-axisConstantError,+axisConstantError)`.

New velocity is previous velocity + final acceleration; clamp it so it cannot overshoot the required axis delta in the same tick. New rotation is current + new velocity.

This defines the addon behavior independently while preserving the approved setting surface and qualitative behavior.

- [ ] **Step 5: Implement silent coordinator application and reset**

```java
public boolean applyTarget(RotationPair result, RotationCoordinator coordinator) {
    return coordinator.tryApply(RuntimeOwner.CHEST_AURA, result.yaw(), result.pitch(), false);
}
```

When target ownership disappears, increment a no-target counter. Before `ticksUntilReset`, emit no reset request. Starting on that tick, compare last server-facing rotation to visible `mc.player.getYaw()/getPitch()`; if angular difference exceeds ResetThreshold, use the currently selected smoothing mode to step silently back toward the visible rotation. Stop requesting rotation once both axes are within threshold, then clear rotator history.

- [ ] **Step 6: Run tests and commit**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.ChestAuraRotatorTest
gradle compileJava
```

```bash
git add src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraAngleSmooth.java \
        src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraRotationConfig.java \
        src/main/java/com/arno721/armorstandgrabber/chest/RotationPair.java \
        src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraRotator.java \
        src/test/java/com/arno721/armorstandgrabber/chest/ChestAuraRotatorTest.java
git commit -m "feat: add chest aura rotation smoothing"
```

---

### Task 4: Implement await-container/retry state machine

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraPhase.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraSession.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/ChestAuraSessionTest.java`

**Interfaces:**
- Phases: `Idle`, `Aiming`, `AwaitingContainer`, `Delay`.
- Produces `beginTarget(BlockPos)`, `markInteraction(long tick, boolean await, int timeoutTicks)`, `markContainerOpened()`, `markTimeoutOrRetry(long tick, int maxRetries)`, `enterDelay(long tick, int delayTicks)`, `reset()`.

- [ ] **Step 1: Write failing retry-boundary tests**

```java
@Test
void awaitSuccessCompletesWithoutConsumingExtraRetry() {
    ChestAuraSession session = new ChestAuraSession();
    BlockPos pos = new BlockPos(1, 2, 3);
    session.beginTarget(pos);
    session.markInteraction(10, true, 10);
    assertEquals(ChestAuraPhase.AwaitingContainer, session.phase());
    session.markContainerOpened();
    assertTrue(session.successful());
    assertEquals(0, session.retries());
}

@Test
void timeoutRetriesUntilMaxThenExhausts() {
    ChestAuraSession session = new ChestAuraSession();
    session.beginTarget(BlockPos.ORIGIN);
    for (int i = 0; i < 4; i++) {
        session.markInteraction(i * 11L, true, 10);
        assertEquals(i == 3, session.markTimeoutOrRetry(i * 11L + 10, 4));
    }
    assertTrue(session.exhausted());
}
```

- [ ] **Step 2: Run and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.ChestAuraSessionTest
```

- [ ] **Step 3: Implement exact retry semantics**

`retries` counts total interaction attempts for the current target. On the first successful interaction call, increment to 1 and set await deadline. If no eligible container appears by deadline and retries `< maxRetries`, return to `Aiming` for the same target. If retries `>= maxRetries`, mark exhausted. A compatible container appearing while awaiting marks success immediately.

When AwaitContainer=false, a successful normal block interaction marks success immediately and enters Delay.

Changing target resets retries, success/exhausted, and await deadline.

- [ ] **Step 4: Run tests and commit**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.ChestAuraSessionTest
```

```bash
git add src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraPhase.java \
        src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraSession.java \
        src/test/java/com/arno721/armorstandgrabber/chest/ChestAuraSessionTest.java
git commit -m "feat: track chest aura await retries"
```

---

### Task 5: Implement normal block interaction, visible swing, and manual tracking

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraInteractor.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ManualStorageInteractionTracker.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/ManualStorageInteractionTrackerTest.java`

**Interfaces:**
- `ChestAuraInteractor.interact(MinecraftClient mc, ChestAuraTarget target, double range)` returns `boolean` only after a fresh raycast resolves to the exact target block and the normal interaction call returns accepted/success semantics.
- `ManualStorageInteractionTracker.observeUse(BlockHitResult hit, ClientWorld world, StorageBlockPolicy policy, Set<Identifier> configured, InteractedStorageTracker tracker)`.

- [ ] **Step 1: Write failing pure manual-tracking policy test**

Use an injected partner resolver so the unit test remains pure:

```java
@Test
void validManualStorageUseMarksOriginalAndPartner() {
    InteractedStorageTracker tracker = new InteractedStorageTracker();
    BlockPos a = new BlockPos(1, 2, 3);
    BlockPos b = new BlockPos(2, 2, 3);
    ManualStorageInteractionTracker.markResolved(a, Optional.of(b), tracker);
    assertTrue(tracker.contains(a));
    assertTrue(tracker.contains(b));
}
```

- [ ] **Step 2: Implement final raycast gate and normal interaction**

Before every use:

1. read current server-facing yaw/pitch from `RotationCoordinator`
2. construct look vector from those angles
3. raycast from eye to `range`
4. require `BlockHitResult` and exact `target.blockPos()` equality
5. call normal client interaction:

```java
ActionResult result = mc.interactionManager.interactBlock(
    mc.player,
    Hand.MAIN_HAND,
    hitResult
);
```

6. accept only when result indicates accepted/success semantics
7. for SwingMode `DoNotHide`, call `mc.player.swingHand(Hand.MAIN_HAND)` after accepted interaction

Do not send raw block-interaction packets directly.

- [ ] **Step 3: Track manual uses through Fabric interaction callback**

Register `UseBlockCallback.EVENT` once in addon initialization. The callback must never consume/change vanilla use behavior; it observes and returns `ActionResult.PASS`.

When parent Chest Stealer is active, Aura is enabled, and `TrackManualInteractions=true`, pass the callback `BlockHitResult` to `ManualStorageInteractionTracker`. It marks only configured storage blocks and their double-chest partner.

Store the controller reference in addon/runtime wiring so the callback delegates to `chestAuraController.onManualUse(...)` rather than duplicating tracker state.

- [ ] **Step 4: Compile and commit**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.ManualStorageInteractionTrackerTest
gradle compileJava
```

```bash
git add src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraInteractor.java \
        src/main/java/com/arno721/armorstandgrabber/chest/ManualStorageInteractionTracker.java \
        src/test/java/com/arno721/armorstandgrabber/chest/ManualStorageInteractionTrackerTest.java
git commit -m "feat: interact with chest aura targets"
```

---

### Task 6: Complete deterministic combat observation used by constraints/Aura

**Files:**
- Modify: `src/main/java/com/arno721/armorstandgrabber/runtime/ActivityTracker.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/runtime/ClientRuntime.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/runtime/CombatObservationTest.java`

**Interfaces:**
- Outgoing living-entity attack refreshes `CombatTracker` to 100 ticks.
- Incoming hurt transition refreshes only when the local player's current/recent attacker is a `LivingEntity`.
- Avoid a damage mixin unless the 1.21.11 public entity state cannot expose the attacker reliably; the baseline plan uses ordinary client state + Fabric attack callback.

- [ ] **Step 1: Add pure hurt-transition helper test**

```java
@Test
void incomingCombatTriggersOnlyOnNewHurtTransitionWithLivingAttacker() {
    CombatObservation observation = new CombatObservation();
    assertFalse(observation.incomingCombat(false, false));
    assertTrue(observation.incomingCombat(true, true));
    assertFalse(observation.incomingCombat(true, true));
    assertFalse(observation.incomingCombat(false, false));
    assertFalse(observation.incomingCombat(true, false));
}
```

Implement `CombatObservation` as a package-private helper inside `ActivityTracker.java` or a focused `runtime/CombatObservation.java` file if it exceeds 60 lines. It remembers whether the previous tick was in a hurt state.

- [ ] **Step 2: Register outgoing attack callback**

In addon initialization:

```java
AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
    if (player == MinecraftClient.getInstance().player && entity instanceof LivingEntity) {
        runtime.combatTracker().recordLocalAttack();
    }
    return ActionResult.PASS;
});
```

The callback observes only; it does not cancel or alter attack behavior.

- [ ] **Step 3: Observe incoming living-attacker hurt transition on runtime tick**

In `ClientRuntime.tick` before participant execution, after null/session replacement handling:

```text
hurtNow = player.hurtTime > 0
livingAttacker = player.getAttacker() instanceof LivingEntity
if CombatObservation.incomingCombat(hurtNow, livingAttacker):
    combatTracker.recordIncomingLivingAttack()
```

Use the exact Yarn 1.21.11 accessor exposed on `LivingEntity`; if the mapping names it `getAttacker()`/equivalent, bind to that compiled accessor without changing the behavioral rule. Do not infer an attacker from arbitrary health loss.

- [ ] **Step 4: Verify 100-tick behavior remains green**

```bash
gradle test --tests com.arno721.armorstandgrabber.runtime.CombatTrackerTest \
            --tests com.arno721.armorstandgrabber.runtime.CombatObservationTest
gradle compileJava
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/arno721/armorstandgrabber/runtime/ActivityTracker.java \
        src/main/java/com/arno721/armorstandgrabber/runtime/ClientRuntime.java \
        src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java \
        src/test/java/com/arno721/armorstandgrabber/runtime/CombatObservationTest.java
git commit -m "feat: observe local combat activity"
```

---

### Task 7: Wire ChestAura settings and controller into Chest Stealer/runtime

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraController.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/PauseReason.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/modules/ChestStealer.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/ChestAuraControllerGateTest.java`

**Interfaces:**
- Controller implements `RuntimeTickParticipant` and returns `RuntimeOwner.CHEST_AURA`.
- Parent module owns settings; controller receives typed getters/config snapshots rather than duplicate Meteor settings.
- Controller exposes `onManualUse(BlockHitResult)` and `lastInteractedBlock()` for SilentScreen integration.

- [ ] **Step 1: Write failing pure gating tests**

Extract pure gate:

```java
@Test
void auraPausesForParentOffContainerOpenAndConfiguredPauseReasons() {
    assertFalse(ChestAuraController.shouldRun(false, true, false, false, false, true));
    assertFalse(ChestAuraController.shouldRun(true, true, true, false, false, true));
    assertFalse(ChestAuraController.shouldRun(true, true, false, true, false, true));
    assertFalse(ChestAuraController.shouldRun(true, true, false, false, true, true));
    assertTrue(ChestAuraController.shouldRun(true, true, false, false, false, true));
}
```

Arguments are parentActive, auraEnabled, eligibleContainerOpen, combatPauseActive, usingItemPauseActive, notDuringCombatAllowed.

- [ ] **Step 2: Add exact Aura settings to parent Chest Stealer**

Add group `Aura` with:

```text
aura = true
range = 3.0 [1.0..6.0]
wall-range = 0.0 [0.0..6.0]
delay-ticks = 5 [1..80]
swing-mode = DoNotHide
not-during-combat = true
track-manual-interactions = true
pause-on-combat = false
pause-on-using-item = false
await-container = true
timeout-ticks = 10 [1..80]
max-retries = 4 [1..10]
valid-storage-blocks = dynamic default set
```

Add a nested/logically grouped `Aura Rotations` settings surface containing every value from Task 3. Use Meteor settings matching their native types; range pairs are represented by min/max settings when Meteor does not provide a range-setting type already used by this addon.

- [ ] **Step 3: Implement controller tick flow**

Exact order:

```text
1. if parent inactive/Aura disabled -> full reset and return
2. if eligible Chest Stealer container is open -> clear current target/rotation target, preserve interacted set, return
3. derive combat/use-item pause state and NotDuringCombat gate; if paused -> do not interact, preserve target and retry deadline, request no new block use
4. if in AwaitingContainer and a compatible container appears -> mark target successful, mark target + partner interacted, enter Delay
5. if AwaitingContainer timed out -> retry same target or mark exhausted + interacted according to MaxRetries
6. if Delay deadline not reached -> return
7. if no current target -> scan via ChestAuraTargetFinder; if none, execute rotator reset behavior and return
8. smooth toward target and submit silent rotation through RotationCoordinator
9. re-raycast using accepted/current server-facing rotation
10. if exact block hit -> normal interaction
11. accepted interaction -> AwaitingContainer or immediate success depending on setting
12. on success/exhaustion -> mark target + partner interacted and clear current target after Delay scheduling
```

Target change always resets retry count.

A higher-priority rejected rotation request means no interaction that tick.

- [ ] **Step 4: Register runtime participant**

Construction order:

```java
ChestStealer chestStealer = new ChestStealer(runtime, cleaner);
ChestAuraController chestAura = new ChestAuraController(runtime, chestStealer);
Modules.get().add(armor);
Modules.get().add(chestStealer);
Modules.get().add(cleaner);
runtime.register(armor);
runtime.register(chestStealer);
runtime.register(chestAura);
runtime.register(cleaner);
```

Register `UseBlockCallback` and `AttackEntityCallback` exactly once during addon initialization and delegate to the created runtime/controller instances.

- [ ] **Step 5: Reset semantics**

On Chest Stealer disable, Aura disable, disconnect, world/player replacement, or runtime reset:

- clear current target
- clear retry/await/delay state
- clear interacted storage set
- clear rotator history/reset request
- clear `lastInteractedBlock`

On an ordinary compatible container opening during a live Aura session, preserve the interacted set and last block long enough for SilentScreen tag anchoring; final parent-disable/session-reset clears both.

- [ ] **Step 6: Full test/build gate**

```bash
gradle test
gradle build
```

Expected: all Armor Stand, runtime, Inventory Cleaner, Chest Stealer, and ChestAura tests pass; ChestAura compiles against 1.21.11 normal interaction/raycast APIs.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraController.java \
        src/main/java/com/arno721/armorstandgrabber/chest/PauseReason.java \
        src/main/java/com/arno721/armorstandgrabber/modules/ChestStealer.java \
        src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java \
        src/test/java/com/arno721/armorstandgrabber/chest/ChestAuraControllerGateTest.java
git commit -m "feat: add chest aura controller"
```

---

## Self-Review Checklist

- Spec coverage: storage defaults, blocked chest rejection, range/wall range, delay, visible swing, combat/use-item pauses, manual tracking, AwaitContainer timeout/retries, double-chest tracking, all three rotation modes, silent movement correction, reset threshold, and reset ticks are assigned to explicit tasks.
- Rotation settings are pinned numerically; no `TBD`, `TODO`, or deferred parameter lookup remains.
- WallRange never grants permission to interact through an obstruction; final exact-block raycast is mandatory.
- `RuntimeOwner.CHEST_AURA` is the only rotation owner used by the controller.
- Combat semantics remain the approved local 100-tick model and do not expand to unrelated environmental damage.
- Every target/world/session reset has an explicit state-clearing path.
- Completion gate: `gradle test` and `gradle build` must be green before SilentScreen work.