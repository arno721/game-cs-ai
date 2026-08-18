# Runtime Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce the shared client runtime, inventory-operation arbitration, activity constraints, and rotation arbitration required by Armor Stand Grabber, Chest Stealer, ChestAura, and Inventory Cleaner without regressing the existing Armor Stand Grabber behavior.

**Architecture:** Keep the current addon identity and Meteor module model, but route low-level tick ownership through a small shared runtime. Shared services are narrow: `InventoryMutex` owns transactional inventory access, `ActivityTracker` exposes deterministic movement/use/breaking/combat state, and `RotationCoordinator` owns the final server/client look application path. Existing Armor Stand Grabber keeps its current session logic and rotation algorithms; only the final application and runtime wiring are extracted.

**Tech Stack:** Java 21, Minecraft 1.21.11, Yarn 1.21.11+build.3, Fabric Loader 0.18.2, Fabric API 0.141.4+1.21.11, Fabric Loom 1.14-SNAPSHOT, Meteor Client 1.21.11-SNAPSHOT, JUnit Jupiter 5.11.4, Gradle 9.2.0.

## Global Constraints

- Keep mod id `armor-stand-grabber`.
- Keep entrypoint `com.arno721.armorstandgrabber.ArmorStandGrabberAddon`.
- Keep package root `com.arno721.armorstandgrabber`.
- Target Minecraft 1.21.11 and Java 21.
- Existing Armor Stand Grabber settings and user-visible behavior must remain unchanged.
- Inventory ownership priority is `Armor Stand Grabber > Chest Stealer > Inventory Cleaner`.
- Rotation ownership priority is `Armor Stand Grabber > ChestAura`.
- An owner may finish only the minimum safe atomic sequence required to leave the cursor/handler consistent before yielding.
- `combatActive` lasts 100 ticks after the local player attacks a living entity or receives damage attributed to a living entity; unrelated environmental damage does not activate it.
- Runtime state clears on disconnect, world/session replacement, death/respawn session replacement, and explicit module/runtime reset.
- Do not add anti-cheat bypass behavior or server-acceptance claims.
- Implementation must remain a clean-room Java implementation; do not copy LiquidBounce Kotlin source.

---

### Task 1: Add deterministic inventory ownership arbitration

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/runtime/RuntimeOwner.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/runtime/InventoryMutex.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/runtime/InventoryMutexTest.java`

**Interfaces:**
- Produces: `RuntimeOwner.inventoryPriority()` and `RuntimeOwner.rotationPriority()`.
- Produces: `InventoryMutex.tryAcquire(RuntimeOwner owner)`, `InventoryMutex.beginAtomic(RuntimeOwner owner)`, `InventoryMutex.endAtomic(RuntimeOwner owner)`, `InventoryMutex.release(RuntimeOwner owner)`, `InventoryMutex.owner()`, `InventoryMutex.isOwnedBy(RuntimeOwner owner)`, `InventoryMutex.reset()`.
- Later tasks must use these methods rather than maintaining separate module-local inventory locks.

- [ ] **Step 1: Write the failing priority/preemption tests**

```java
package com.arno721.armorstandgrabber.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryMutexTest {
    @Test
    void higherPriorityOwnerCanPreemptOnlyOutsideAtomicSequence() {
        InventoryMutex mutex = new InventoryMutex();

        assertTrue(mutex.tryAcquire(RuntimeOwner.INVENTORY_CLEANER));
        assertTrue(mutex.tryAcquire(RuntimeOwner.CHEST_STEALER));
        assertEquals(RuntimeOwner.CHEST_STEALER, mutex.owner());

        mutex.beginAtomic(RuntimeOwner.CHEST_STEALER);
        assertFalse(mutex.tryAcquire(RuntimeOwner.ARMOR_STAND_GRABBER));
        assertTrue(mutex.yieldRequested());
        assertEquals(RuntimeOwner.CHEST_STEALER, mutex.owner());

        mutex.endAtomic(RuntimeOwner.CHEST_STEALER);
        assertNull(mutex.owner());
        assertTrue(mutex.tryAcquire(RuntimeOwner.ARMOR_STAND_GRABBER));
    }

    @Test
    void lowerPriorityOwnerCannotStealOwnership() {
        InventoryMutex mutex = new InventoryMutex();
        assertTrue(mutex.tryAcquire(RuntimeOwner.ARMOR_STAND_GRABBER));
        assertFalse(mutex.tryAcquire(RuntimeOwner.CHEST_STEALER));
        assertEquals(RuntimeOwner.ARMOR_STAND_GRABBER, mutex.owner());
    }

    @Test
    void resetClearsOwnerAtomicStateAndYieldRequest() {
        InventoryMutex mutex = new InventoryMutex();
        mutex.tryAcquire(RuntimeOwner.CHEST_STEALER);
        mutex.beginAtomic(RuntimeOwner.CHEST_STEALER);
        mutex.tryAcquire(RuntimeOwner.ARMOR_STAND_GRABBER);
        mutex.reset();

        assertNull(mutex.owner());
        assertFalse(mutex.atomic());
        assertFalse(mutex.yieldRequested());
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
gradle test --tests com.arno721.armorstandgrabber.runtime.InventoryMutexTest
```

Expected: compilation failure because `RuntimeOwner` and `InventoryMutex` do not exist.

- [ ] **Step 3: Implement the minimal ownership model**

`RuntimeOwner.java`:

```java
package com.arno721.armorstandgrabber.runtime;

public enum RuntimeOwner {
    ARMOR_STAND_GRABBER(300, 300),
    CHEST_STEALER(200, 0),
    CHEST_AURA(0, 200),
    INVENTORY_CLEANER(100, 0);

    private final int inventoryPriority;
    private final int rotationPriority;

    RuntimeOwner(int inventoryPriority, int rotationPriority) {
        this.inventoryPriority = inventoryPriority;
        this.rotationPriority = rotationPriority;
    }

    public int inventoryPriority() { return inventoryPriority; }
    public int rotationPriority() { return rotationPriority; }
}
```

`InventoryMutex.java` must implement this exact state machine:

```java
package com.arno721.armorstandgrabber.runtime;

public final class InventoryMutex {
    private RuntimeOwner owner;
    private boolean atomic;
    private boolean yieldRequested;

    public boolean tryAcquire(RuntimeOwner candidate) {
        if (candidate.inventoryPriority() <= 0) return false;
        if (owner == null || owner == candidate) {
            owner = candidate;
            return true;
        }
        if (candidate.inventoryPriority() <= owner.inventoryPriority()) return false;
        if (atomic) {
            yieldRequested = true;
            return false;
        }
        owner = candidate;
        yieldRequested = false;
        return true;
    }

    public void beginAtomic(RuntimeOwner candidate) {
        if (owner != candidate) throw new IllegalStateException("inventory mutex is not owned by " + candidate);
        atomic = true;
    }

    public void endAtomic(RuntimeOwner candidate) {
        if (owner != candidate) return;
        atomic = false;
        if (yieldRequested) {
            owner = null;
            yieldRequested = false;
        }
    }

    public void release(RuntimeOwner candidate) {
        if (owner != candidate) return;
        if (atomic) throw new IllegalStateException("cannot release inventory mutex during atomic sequence");
        owner = null;
        yieldRequested = false;
    }

    public RuntimeOwner owner() { return owner; }
    public boolean isOwnedBy(RuntimeOwner candidate) { return owner == candidate; }
    public boolean atomic() { return atomic; }
    public boolean yieldRequested() { return yieldRequested; }

    public void reset() {
        owner = null;
        atomic = false;
        yieldRequested = false;
    }
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

```bash
gradle test --tests com.arno721.armorstandgrabber.runtime.InventoryMutexTest
```

Expected: all three tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/arno721/armorstandgrabber/runtime/RuntimeOwner.java \
        src/main/java/com/arno721/armorstandgrabber/runtime/InventoryMutex.java \
        src/test/java/com/arno721/armorstandgrabber/runtime/InventoryMutexTest.java
git commit -m "feat: add inventory runtime arbitration"
```

---

### Task 2: Add generic inventory timing constraints and deterministic activity state

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/runtime/InventoryConstraintConfig.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/runtime/ActivitySnapshot.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/runtime/ConstraintGate.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/runtime/CombatTracker.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/runtime/ConstraintGateTest.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/runtime/CombatTrackerTest.java`

**Interfaces:**
- Produces immutable `InventoryConstraintConfig` with `startDelayMinTicks`, `startDelayMaxTicks`, `clickDelayMinTicks`, `clickDelayMaxTicks`, `closeDelayMinTicks`, `closeDelayMaxTicks`, `missChancePercent`, and requirement booleans.
- Produces `ActivitySnapshot(boolean moving, boolean rotating, boolean usingItem, boolean breakingBlock, boolean combatActive, boolean inventoryOpen)`.
- Produces `ConstraintGate.allowed(InventoryConstraintConfig config, ActivitySnapshot snapshot, boolean requireInventoryOpen)`.
- Produces `CombatTracker.recordLocalAttack()`, `CombatTracker.recordIncomingLivingAttack()`, `CombatTracker.tick()`, `CombatTracker.active()`, `CombatTracker.reset()`.

- [ ] **Step 1: Write failing tests for exact requirement semantics**

```java
@Test
void genericRequirementsRejectOnlyEnabledConditions() {
    InventoryConstraintConfig config = new InventoryConstraintConfig(
        1, 2, 2, 4, 1, 2, 0,
        true, true, true, true, true
    );

    assertTrue(ConstraintGate.allowed(config,
        new ActivitySnapshot(false, false, false, false, false, false), false));
    assertFalse(ConstraintGate.allowed(config,
        new ActivitySnapshot(true, false, false, false, false, false), false));
    assertFalse(ConstraintGate.allowed(config,
        new ActivitySnapshot(false, true, false, false, false, false), false));
    assertFalse(ConstraintGate.allowed(config,
        new ActivitySnapshot(false, false, true, false, false, false), false));
    assertFalse(ConstraintGate.allowed(config,
        new ActivitySnapshot(false, false, false, true, false, false), false));
    assertFalse(ConstraintGate.allowed(config,
        new ActivitySnapshot(false, false, false, false, true, false), false));
}

@Test
void standaloneCleanerCanRequireInventoryOpen() {
    InventoryConstraintConfig config = InventoryConstraintConfig.defaults();
    ActivitySnapshot closed = new ActivitySnapshot(false, false, false, false, false, false);
    ActivitySnapshot open = new ActivitySnapshot(false, false, false, false, false, true);
    assertFalse(ConstraintGate.allowed(config, closed, true));
    assertTrue(ConstraintGate.allowed(config, open, true));
}
```

`CombatTrackerTest`:

```java
@Test
void combatWindowLastsExactlyOneHundredTicksAfterRefresh() {
    CombatTracker tracker = new CombatTracker(100);
    tracker.recordLocalAttack();
    for (int i = 0; i < 99; i++) {
        assertTrue(tracker.active());
        tracker.tick();
    }
    assertTrue(tracker.active());
    tracker.tick();
    assertFalse(tracker.active());
}

@Test
void incomingLivingAttackRefreshesWindowAndResetClearsIt() {
    CombatTracker tracker = new CombatTracker(100);
    tracker.recordIncomingLivingAttack();
    tracker.tick();
    tracker.recordIncomingLivingAttack();
    assertTrue(tracker.active());
    tracker.reset();
    assertFalse(tracker.active());
}
```

- [ ] **Step 2: Run focused tests and verify RED**

```bash
gradle test --tests 'com.arno721.armorstandgrabber.runtime.*Test'
```

Expected: compilation failure because the new runtime types do not exist.

- [ ] **Step 3: Implement the immutable records and pure gates**

Use these exact public signatures:

```java
public record InventoryConstraintConfig(
    int startDelayMinTicks,
    int startDelayMaxTicks,
    int clickDelayMinTicks,
    int clickDelayMaxTicks,
    int closeDelayMinTicks,
    int closeDelayMaxTicks,
    int missChancePercent,
    boolean noMovement,
    boolean noRotation,
    boolean notUsingItem,
    boolean notBreaking,
    boolean notDuringCombat
) {
    public static InventoryConstraintConfig defaults() {
        return new InventoryConstraintConfig(1, 2, 2, 4, 1, 2, 0, true, true, true, true, true);
    }
}

public record ActivitySnapshot(
    boolean moving,
    boolean rotating,
    boolean usingItem,
    boolean breakingBlock,
    boolean combatActive,
    boolean inventoryOpen
) {}
```

`ConstraintGate.allowed` returns false iff an enabled requirement is currently violated, and additionally requires `inventoryOpen` when `requireInventoryOpen` is true.

`CombatTracker` stores `remainingTicks`; both record methods set it to the configured duration, `tick()` decrements when positive, and `reset()` zeroes it.

- [ ] **Step 4: Run focused tests and verify GREEN**

```bash
gradle test --tests 'com.arno721.armorstandgrabber.runtime.*Test'
```

Expected: all runtime tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/arno721/armorstandgrabber/runtime \
        src/test/java/com/arno721/armorstandgrabber/runtime
git commit -m "feat: add inventory activity constraints"
```

---

### Task 3: Add shared tick runtime and lifecycle reset

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/runtime/RuntimeTickParticipant.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/runtime/ClientRuntime.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/modules/ArmorStandGrabber.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/runtime/ClientRuntimeOrderTest.java`

**Interfaces:**
- Produces `RuntimeTickParticipant.runtimeOwner()` and `RuntimeTickParticipant.onRuntimeTick()`.
- Produces `ClientRuntime.register(RuntimeTickParticipant)`, `ClientRuntime.tick(MinecraftClient)`, `ClientRuntime.inventoryMutex()`, `ClientRuntime.combatTracker()`, `ClientRuntime.reset()`.
- Armor Stand Grabber implements `RuntimeTickParticipant` and its existing `onFabricTick()` body moves to `onRuntimeTick()` with no behavior changes.

- [ ] **Step 1: Write a failing pure ordering test**

Keep ordering in a package-private helper so it can be tested without constructing a Minecraft client:

```java
@Test
void participantsRunInDescendingInventoryThenRotationPriority() {
    assertEquals(
        List.of(
            RuntimeOwner.ARMOR_STAND_GRABBER,
            RuntimeOwner.CHEST_STEALER,
            RuntimeOwner.CHEST_AURA,
            RuntimeOwner.INVENTORY_CLEANER
        ),
        ClientRuntime.sortOwnersForTick(List.of(
            RuntimeOwner.INVENTORY_CLEANER,
            RuntimeOwner.CHEST_AURA,
            RuntimeOwner.CHEST_STEALER,
            RuntimeOwner.ARMOR_STAND_GRABBER
        ))
    );
}
```

The sort key is `max(inventoryPriority, rotationPriority)` descending, with enum ordinal only as a stable final tie-breaker.

- [ ] **Step 2: Run the test and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.runtime.ClientRuntimeOrderTest
```

Expected: `ClientRuntime` does not exist.

- [ ] **Step 3: Implement runtime registration and reset detection**

`RuntimeTickParticipant`:

```java
public interface RuntimeTickParticipant {
    RuntimeOwner runtimeOwner();
    void onRuntimeTick();
}
```

`ClientRuntime` must:

```java
public final class ClientRuntime {
    private final InventoryMutex inventoryMutex = new InventoryMutex();
    private final CombatTracker combatTracker = new CombatTracker(100);
    private final List<RuntimeTickParticipant> participants = new ArrayList<>();
    private ClientWorld lastWorld;
    private ClientPlayerEntity lastPlayer;

    public void register(RuntimeTickParticipant participant) { /* reject duplicate instance; sort */ }
    public void tick(MinecraftClient mc) { /* detect world/player replacement; tick combat; invoke participants */ }
    public InventoryMutex inventoryMutex() { return inventoryMutex; }
    public CombatTracker combatTracker() { return combatTracker; }
    public void reset() { inventoryMutex.reset(); combatTracker.reset(); lastWorld = null; lastPlayer = null; }
}
```

On `tick`, when `mc.world != lastWorld` or `mc.player != lastPlayer`, clear shared state before updating the stored references. When `mc.player == null || mc.world == null`, reset and return. Do not reset every tick while null; idempotence is required.

- [ ] **Step 4: Route the existing Armor Stand tick through `ClientRuntime`**

Change the module declaration to:

```java
public class ArmorStandGrabber extends Module implements RuntimeTickParticipant
```

Add:

```java
@Override
public RuntimeOwner runtimeOwner() {
    return RuntimeOwner.ARMOR_STAND_GRABBER;
}

@Override
public void onRuntimeTick() {
    if (!isActive() || session.phase() == RetrievalPhase.Idle) return;
    if (!validateSession()) return;
    if (rotationEnabled.get() && rotationController.isActive()) {
        rotationController.tick(session.currentSlot(), rotationMode.get(), rotationTarget.get(), rotationAlgorithm.get(), rotationConfig());
    }
    processSession(nowMs());
}
```

Delete/rename the old public `onFabricTick()` so there is exactly one tick entry path.

Update addon initialization from direct module callback registration to:

```java
ArmorStandGrabber armorStandGrabber = new ArmorStandGrabber();
ClientRuntime runtime = new ClientRuntime();
runtime.register(armorStandGrabber);
Modules.get().add(armorStandGrabber);
ClientTickEvents.END_CLIENT_TICK.register(runtime::tick);
```

Store `ClientRuntime` in a static accessor on the addon only if later modules need it:

```java
private static ClientRuntime runtime;
public static ClientRuntime runtime() { return runtime; }
```

Initialize it before module construction when constructors start consuming shared services in Task 4.

- [ ] **Step 5: Run regression tests and build**

```bash
gradle test
gradle build
```

Expected: existing delay/rotation tests plus new runtime tests pass; addon compiles against 1.21.11.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java \
        src/main/java/com/arno721/armorstandgrabber/modules/ArmorStandGrabber.java \
        src/main/java/com/arno721/armorstandgrabber/runtime \
        src/test/java/com/arno721/armorstandgrabber/runtime
git commit -m "refactor: route modules through shared client runtime"
```

---

### Task 4: Extract final rotation application into `RotationCoordinator`

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/runtime/RotationCoordinator.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/rotation/RotationController.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/modules/ArmorStandGrabber.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/runtime/RotationCoordinatorTest.java`

**Interfaces:**
- Produces `RotationCoordinator.tryApply(RuntimeOwner owner, double yaw, double pitch, boolean visible)`.
- Produces `RotationCoordinator.serverYaw()`, `serverPitch()`, `changedThisTick()`, `beginTick()`, `reset()`.
- `RotationController` consumes `RotationCoordinator` and `RuntimeOwner.ARMOR_STAND_GRABBER` instead of directly calling `player.setYaw/setPitch` or sending `LookAndOnGround` packets.
- ChestAura plan later uses the same coordinator with `RuntimeOwner.CHEST_AURA`.

- [ ] **Step 1: Write failing pure winner/state tests**

Implement the coordinator with an injectable sink so priority can be tested without Minecraft:

```java
@Test
void higherPriorityRotationWinsWithinTick() {
    List<String> applied = new ArrayList<>();
    RotationCoordinator coordinator = new RotationCoordinator(
        (yaw, pitch, visible) -> applied.add(yaw + ":" + pitch + ":" + visible)
    );

    coordinator.beginTick();
    assertTrue(coordinator.tryApply(RuntimeOwner.CHEST_AURA, 20, 5, false));
    assertTrue(coordinator.tryApply(RuntimeOwner.ARMOR_STAND_GRABBER, 50, 10, true));
    assertEquals(50.0, coordinator.serverYaw());
    assertEquals(10.0, coordinator.serverPitch());
    assertTrue(coordinator.changedThisTick());
}

@Test
void lowerPriorityRequestIsRejectedAfterHigherPriorityClaim() {
    RotationCoordinator coordinator = new RotationCoordinator((yaw, pitch, visible) -> {});
    coordinator.beginTick();
    assertTrue(coordinator.tryApply(RuntimeOwner.ARMOR_STAND_GRABBER, 1, 2, false));
    assertFalse(coordinator.tryApply(RuntimeOwner.CHEST_AURA, 3, 4, false));
}
```

- [ ] **Step 2: Run test and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.runtime.RotationCoordinatorTest
```

Expected: `RotationCoordinator` does not exist.

- [ ] **Step 3: Implement coordinator with Minecraft sink**

Public constructor used by production:

```java
public RotationCoordinator(MinecraftClient mc) {
    this((yaw, pitch, visible) -> {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (visible) {
            mc.player.setYaw((float) yaw);
            mc.player.setPitch((float) pitch);
        } else {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                (float) yaw,
                (float) pitch,
                mc.player.isOnGround(),
                mc.player.horizontalCollision
            ));
        }
    });
}
```

Package-private test constructor:

```java
RotationCoordinator(RotationSink sink)
```

`tryApply` rejects owners with `rotationPriority <= 0`; within one tick it accepts a request when there is no winner or when candidate priority is greater than or equal to current winner priority. It updates server yaw/pitch and invokes the sink only on accepted requests. `beginTick()` clears the winner and `changedThisTick` but preserves last server yaw/pitch. `reset()` clears both winner and last server rotation to `NaN`.

- [ ] **Step 4: Integrate coordinator into runtime and existing Armor Stand rotation**

Add one `RotationCoordinator` to `ClientRuntime`, call `rotationCoordinator.beginTick()` once before participant ticks, expose `rotationCoordinator()`, and call `rotationCoordinator.reset()` from runtime reset.

Change `RotationController` constructor to:

```java
public RotationController(MinecraftClient mc, RotationCoordinator coordinator)
```

Replace the final Lock/Silent branch with:

```java
coordinator.tryApply(
    RuntimeOwner.ARMOR_STAND_GRABBER,
    state.yaw(),
    state.pitch(),
    mode == RotationMode.Lock
);
```

Construct Armor Stand Grabber with the shared coordinator:

```java
public ArmorStandGrabber(RotationCoordinator rotationCoordinator) {
    super(...);
    this.rotationController = new RotationController(mc, rotationCoordinator);
}
```

Update addon construction order:

```java
ClientRuntime runtime = new ClientRuntime(MinecraftClient.getInstance());
ArmorStandGrabber armorStandGrabber = new ArmorStandGrabber(runtime.rotationCoordinator());
runtime.register(armorStandGrabber);
```

- [ ] **Step 5: Verify Armor Stand rotation math and build remain green**

```bash
gradle test --tests 'com.arno721.armorstandgrabber.rotation.*Test'
gradle test --tests 'com.arno721.armorstandgrabber.runtime.*Test'
gradle build
```

Expected: all tests pass and no direct `PlayerMoveC2SPacket.LookAndOnGround` construction remains in `RotationController.java`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/arno721/armorstandgrabber/runtime/RotationCoordinator.java \
        src/main/java/com/arno721/armorstandgrabber/runtime/ClientRuntime.java \
        src/main/java/com/arno721/armorstandgrabber/rotation/RotationController.java \
        src/main/java/com/arno721/armorstandgrabber/modules/ArmorStandGrabber.java \
        src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java \
        src/test/java/com/arno721/armorstandgrabber/runtime/RotationCoordinatorTest.java
git commit -m "refactor: centralize rotation arbitration"
```

---

### Task 5: Wire activity observation and protect Armor Stand inventory transactions

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/runtime/ActivityTracker.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/runtime/ClientRuntime.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/modules/ArmorStandGrabber.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/inventory/InventoryOverflowController.java`

**Interfaces:**
- Produces `ActivityTracker.snapshot(MinecraftClient mc, RotationCoordinator rotationCoordinator, CombatTracker combatTracker)`.
- `ArmorStandGrabber` consumes `InventoryMutex` and owns it for the duration of an active retrieval session.
- `InventoryOverflowController` does not acquire the mutex itself; caller ownership is mandatory so nested helpers cannot deadlock.

- [ ] **Step 1: Add shared services to Armor Stand Grabber constructor**

Use this constructor shape:

```java
public ArmorStandGrabber(InventoryMutex inventoryMutex, RotationCoordinator rotationCoordinator) {
    super(ArmorStandGrabberAddon.CATEGORY, "armor-stand-grabber", "Retrieve armor-stand equipment through blocks with configurable timing, overflow, and rotation tracking.");
    this.inventoryMutex = inventoryMutex;
    this.rotationController = new RotationController(mc, rotationCoordinator);
}
```

Delete field initialization that constructs `RotationController` before the constructor. Keep `InventoryOverflowController` module-local.

- [ ] **Step 2: Acquire/release ownership at session boundaries**

At the beginning of `beginSession`, before `session.begin(...)`:

```java
if (!inventoryMutex.tryAcquire(RuntimeOwner.ARMOR_STAND_GRABBER)) {
    warning("Another inventory automation is finishing an atomic transaction.");
    return;
}
```

At every path through `finishSession`, after safe overflow cleanup and before returning:

```java
if (inventoryMutex.isOwnedBy(RuntimeOwner.ARMOR_STAND_GRABBER) && !inventoryMutex.atomic()) {
    inventoryMutex.release(RuntimeOwner.ARMOR_STAND_GRABBER);
}
```

Wrap each existing SWAP-based overflow click pair as one atomic sequence. In `InventoryOverflowController`, expose callbacks rather than making it aware of owners:

```java
public int prepareOverflow(int originalSelectedSlot, OverflowMode mode, Runnable beginAtomic, Runnable endAtomic)
public boolean finishTransfer(Runnable beginAtomic, Runnable endAtomic)
public void abortTransfer(Runnable beginAtomic, Runnable endAtomic)
```

Each helper invokes `beginAtomic.run()` immediately before the first `clickSlot` and `endAtomic.run()` in a `finally` block after the matching safe sequence. The Armor Stand caller supplies:

```java
() -> inventoryMutex.beginAtomic(RuntimeOwner.ARMOR_STAND_GRABBER)
() -> inventoryMutex.endAtomic(RuntimeOwner.ARMOR_STAND_GRABBER)
```

- [ ] **Step 3: Implement `ActivityTracker`**

Movement is true when player input has non-zero forward/sideways movement or jump is pressed. Using-item uses `player.isUsingItem()`. Breaking uses the interaction manager's active block-breaking state available in Yarn 1.21.11. Rotation is `rotationCoordinator.changedThisTick()`. Combat uses `CombatTracker.active()`. Inventory-open is true only for `InventoryScreen`.

Expose:

```java
public final class ActivityTracker {
    public ActivitySnapshot snapshot(MinecraftClient mc, RotationCoordinator rotation, CombatTracker combat) { ... }
}
```

Do not infer combat from generic health loss here; attack/damage hooks refresh `CombatTracker` in the later feature plans. Until those hooks are installed, the value remains false.

- [ ] **Step 4: Build and run all tests**

```bash
gradle test
gradle build
```

Expected: no regressions and the shared runtime foundation compiles before any new Chest/Inventory Cleaner module exists.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/arno721/armorstandgrabber/runtime \
        src/main/java/com/arno721/armorstandgrabber/modules/ArmorStandGrabber.java \
        src/main/java/com/arno721/armorstandgrabber/inventory/InventoryOverflowController.java \
        src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java
git commit -m "refactor: protect shared inventory runtime"
```

---

## Self-Review Checklist

- Spec coverage: inventory priority, rotation priority, deterministic combat window type, generic inventory constraints, lifecycle reset, and Armor Stand compatibility are all assigned to tasks.
- Placeholder scan: no deferred `TBD`, `TODO`, or unspecified helper remains.
- Type consistency: later plans may rely on `RuntimeOwner`, `InventoryMutex`, `InventoryConstraintConfig`, `ActivitySnapshot`, `ConstraintGate`, `CombatTracker`, `ActivityTracker`, `ClientRuntime`, and `RotationCoordinator` exactly as defined here.
- Regression gate: this plan is complete only when `gradle test` and `gradle build` are green before starting Inventory Cleaner.