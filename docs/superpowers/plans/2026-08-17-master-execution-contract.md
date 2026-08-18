# Chest Stealer v0.4.0 Master Execution Contract

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement the referenced plans task-by-task. This contract is authoritative when a referenced plan contains an older conflicting signature or semantic statement.

**Purpose:** Resolve cross-plan interface mismatches found during self-review and define one unambiguous execution order for the five implementation plans. This document does not add feature scope beyond the approved design; it only makes existing scope executable without guessing.

**Pinned behavioral reference:** LiquidBounce `141631789e5aeb7851562052dca567ab49716e99`, studied for behavior/settings only. Implementation remains a clean-room Java rewrite.

## 1. Global execution order

Execute in this order:

1. `2026-08-17-runtime-foundation.md`
2. `2026-08-17-inventory-cleaner.md`, with the corrected domain contracts in §3 below
3. `2026-08-17-chest-stealer-core.md`, with the corrected handler/runtime contracts in §4
4. `2026-08-17-chest-aura.md`, with the corrected rotation history contract in §5
5. `2026-08-17-silent-screen-release.md`, with the observer/runtime contract in §6

Do not start a later plan until the previous plan's `gradle test` and `gradle build` completion gate is green.

## 2. Authoritative `ClientRuntime` contract

The Runtime Foundation plan is extended by this exact interface before Inventory Cleaner work begins:

```java
public final class ClientRuntime {
    public void register(RuntimeTickParticipant participant);
    public void registerObserver(Runnable observer);

    public void tick(MinecraftClient mc);
    public void reset();

    public InventoryMutex inventoryMutex();
    public CombatTracker combatTracker();
    public ActivityTracker activityTracker();
    public RotationCoordinator rotationCoordinator();
    public long tickCount();
}
```

Behavior:

- `tickCount` starts at 0 after construction/reset/world-player session replacement.
- On each valid `END_CLIENT_TICK` with non-null world/player, increment `tickCount` exactly once before prioritized participants execute.
- Prioritized participants execute in the already-defined owner priority order.
- Observers execute after all prioritized participants in registration order.
- `register` and `registerObserver` reject duplicate object identity registrations.
- `reset()` clears volatile runtime state (`InventoryMutex`, combat state, rotation state, activity-transition state, tick count) but does **not** discard participant/observer registrations.
- World/player replacement calls the same volatile-state reset path, then stores the new world/player and continues with the new session.

Add/extend runtime tests before leaving Runtime Foundation:

```java
@Test
void tickCountResetsAcrossSessionReplacement() { /* 0 -> 1 -> replacement -> 1 */ }

@Test
void observersRunAfterPrioritizedParticipantsInRegistrationOrder() { /* record call sequence */ }
```

This contract resolves later uses of `runtime.tickCount()`, `runtime.activityTracker()`, `runtime.rotationCoordinator()`, and `runtime.registerObserver(...)`.

## 3. Authoritative Inventory Cleaner domain corrections

### 3.1 `ItemsBlacklist` semantics

The earlier statement that blacklisted items are protected/useful is superseded.

`ItemsBlacklist` means **forced disposal / never useful for Cleaner planning**:

- Standalone Inventory Cleaner adds blacklisted player-inventory items to disposal candidates regardless of whether ordinary cleanup ranking would otherwise keep them.
- Chest Stealer with active Cleaner excludes blacklisted container items from the useful-container set.
- Blacklisted items never satisfy target-category assignments or amount limits.
- Do not fabricate a protected/useful classification for a blacklisted item.
- `Ignore` remains a target-slot protection choice; the executor must not use a protected Ignore slot as a normal relocation/source target. If a blacklist/Ignore conflict occurs, slot protection wins execution safety: do not mutate that protected physical slot automatically, and report no false disposal success.

Correct upper bounds for settings are:

```text
MaximumBlocks       0..2500  default 512
MaximumArrows       0..2500  default 128
MaximumThrowables   0..600   default 64
MaximumFoodPoints   0..2000  default 200
MaximumWaterBuckets 0..16    default 2
MaximumLavaBuckets  0..16    default 2
MaximumMilkBuckets  0..16    default 2
```

### 3.2 Score/domain ordering

Before creating `ItemFacet`, create the score types originally scheduled later:

- `src/main/java/com/arno721/armorstandgrabber/inventory/ItemScore.java`
- `src/main/java/com/arno721/armorstandgrabber/inventory/ItemScoreComparator.java`

`ItemFacet` is authoritative as:

```java
public record ItemFacet(
    ItemType type,
    ItemScore score,
    int allocationPriority,
    int unitsPerItem
) {}
```

`unitsPerItem` semantics:

- FOOD: vanilla nutrition points contributed by one item
- BLOCK / ARROW / THROWABLE / WATER / LAVA / MILK: 1
- all non-amount-limited facets: 0

The synthetic Task-1 examples that used integer scores become, for example:

```java
new ItemFacet(ItemType.WEAPON, ItemScore.generic(90, false, 12), 0, 0)
```

Task 3 of the Inventory Cleaner plan then tests/extends these already-created score types and implements `ItemProfiler`; it must not create duplicate score classes.

### 3.3 `ItemProfile` data needed by later planners

The authoritative profile is:

```java
public record ItemProfile(
    SlotRef slot,
    String itemId,
    String stackCompatibilityKey,
    int count,
    int maxCount,
    boolean blacklisted,
    List<ItemFacet> facets
) {
    public ItemProfile {
        facets = List.copyOf(facets);
    }
}
```

`stackCompatibilityKey` is produced from item identity plus complete stack component state. It is used only as a planning key; before an actual merge, production code revalidates compatibility against the live `ItemStack` using Minecraft's item/component equality API.

Amount constraints are calculated from `facet.unitsPerItem() * profile.count()` for function-value limits such as food points, and from stack count for category count limits. A physical stack remains indivisible for retention decisions as already specified.

### 3.4 Target assignments must survive into `CleanupPlan`

Add:

```java
public record TargetSlot(TargetSlot.Kind kind, int index) {
    public enum Kind { HOTBAR, OFFHAND }

    public static TargetSlot hotbar(int index) {
        if (index < 0 || index > 8) throw new IllegalArgumentException("hotbar index must be 0..8");
        return new TargetSlot(Kind.HOTBAR, index);
    }

    public static TargetSlot offhand() {
        return new TargetSlot(Kind.OFFHAND, 40);
    }
}
```

The authoritative `CleanupPlan` contains:

```java
public record CleanupPlan(
    List<PlannedMove> moves,
    List<Integer> mergeSourceSlotIds,
    List<Integer> discardSlotIds,
    Set<Integer> usefulSlotIds,
    Map<TargetSlot, Integer> targetAssignments
) { /* defensive copies */ }
```

`targetAssignments` maps each requested hotbar/offhand target to the chosen physical source handler slot id. Chest Stealer QuickSwap planning uses this map and does not reverse-engineer assignment intent from arbitrary moves.

### 3.5 Cleanup generation phase correction

The authoritative generation order is:

1. mark protected `Ignore` physical target slots
2. remove blacklisted profiles from all useful/category candidate pools
3. allocate offhand/hotbar targets from non-blacklisted profiles
4. retain armor profiles required by the approved armor-retention policy without equipping them
5. apply category/function amount constraints
6. retain recognized uncapped utilities
7. emit target assignments/moves
8. compute safe stack merge candidates
9. classify remaining non-protected, non-blacklisted, non-useful player items as ordinary disposal candidates
10. add blacklisted player-inventory items to forced disposal candidates unless the physical slot is protected by the `Ignore` safety rule

Container profiles are never emitted as disposal actions. Blacklisted container profiles simply remain non-useful and are not stolen when Cleaner integration is active.

Update the Inventory Cleaner planner test that previously expected blacklist protection to instead assert:

```java
@Test
void blacklistIsForcedDisposalAndNeverUseful() {
    CleanupPlan plan = generator.generate(...);
    assertTrue(plan.discardSlotIds().contains(blacklistedPlayerSlot));
    assertFalse(plan.isUseful(blacklistedPlayerSlot));
    assertFalse(plan.isUseful(blacklistedContainerSlot));
}
```

## 4. Authoritative Chest Stealer session identity/runtime corrections

### 4.1 Strong handler identity

`syncId + class + title` is not sufficient by itself to prove the same live container object. `ContainerDescriptor` additionally carries the handler reference:

```java
public record ContainerDescriptor(
    ScreenHandler handler,
    int syncId,
    String title,
    String handlerClassName,
    List<Integer> containerSlotIds
) {
    public ContainerDescriptor {
        containerSlotIds = List.copyOf(containerSlotIds);
    }
}
```

`ChestStealSession` stores the exact handler reference captured at begin. `matches` requires:

```text
current descriptor handler == stored handler reference
AND syncId equal
AND handler class name equal
AND title equal
```

Before every mutation, also require `mc.player.currentScreenHandler == stored handler`.

Pure session tests may use a small fake identity object through an extracted `HandlerIdentity` record if constructing a real `ScreenHandler` would require Minecraft bootstrap; production still performs direct object-identity comparison.

### 4.2 Runtime services

All Chest Stealer uses of tick/activity services refer to the authoritative §2 `ClientRuntime` accessors. Do not create module-local duplicate tick counters or combat trackers.

### 4.3 Cleaner integration

When Cleaner is inactive:

- every non-empty transferable container item is useful
- there are no cleanup-derived quick swaps
- `OnFull=Throw` has no Cleaner disposal plan and therefore must **not** invent arbitrary junk; it behaves as no legal discard candidate and leaves remaining loot

When Cleaner is active:

- use `CleanupPlan.targetAssignments()` for QuickSwap intent
- use `CleanupPlan.discardSlotIds()` for legal OnFull Throw candidates
- blacklisted container items are non-useful by §3.1

## 5. Authoritative ChestAura rotation-history correction

The Acceleration clean-room algorithm uses two server-rotation samples, not an undefined third sample.

For:

```java
step(currentServer, previousServer, target, config, random)
```

use:

```text
previousYawVelocity   = wrapYaw(currentServer.yaw - previousServer.yaw)
previousPitchVelocity = currentServer.pitch - previousServer.pitch
requiredYawDelta      = wrapYaw(target.yaw - currentServer.yaw)
requiredPitchDelta    = target.pitch - currentServer.pitch
neededYawAcceleration   = requiredYawDelta - previousYawVelocity
neededPitchAcceleration = requiredPitchDelta - previousPitchVelocity
```

Then apply the already-defined acceleration bounds, optional sigmoid deceleration, acceleration-proportional error, constant error, and no-overshoot clamp.

Delete any wording/reference to `beforePreviousServerYaw` or `beforePreviousServerPitch`; those values are not part of the public state contract.

`ChestAuraRotator` retains its last accepted server rotation so `previousServer` can be supplied deterministically on the next tick. If the shared coordinator rejected the ChestAura request because Armor Stand Grabber won rotation ownership, do not advance ChestAura's accepted-rotation history for that rejected request.

## 6. Authoritative SilentScreen observer/runtime correction

The SilentScreen release plan Task 5 also modifies:

- `src/main/java/com/arno721/armorstandgrabber/runtime/ClientRuntime.java`
- `src/test/java/com/arno721/armorstandgrabber/runtime/ClientRuntimeOrderTest.java`

`registerObserver(Runnable)` is not deferred; it is part of the §2 Runtime Foundation contract and therefore should already exist before SilentScreen. SilentScreen Task 5 only registers the controller observer and adds/extends ordering tests if necessary.

Observer execution must never participate in inventory/rotation ownership priority. SilentScreen observes final per-tick state after modules have acted.

## 7. Source-derived defaults locked by self-review

The following are now frozen for implementation and must not be guessed later:

```text
ChestAura rotation group:
  AngleSmooth = Linear / Sigmoid / Acceleration
  MovementCorrection = Silent
  ResetThreshold = 2
  TicksUntilReset = 5

Linear:
  HorizontalTurnSpeed = 180..180, allowed 0..180
  VerticalTurnSpeed   = 180..180, allowed 0..180

Sigmoid:
  HorizontalTurnSpeed = 180..180, allowed 0..180
  VerticalTurnSpeed   = 180..180, allowed 0..180
  Steepness = 10, allowed 0..20
  Midpoint = 0.3, allowed 0..1

Acceleration:
  YawAcceleration   = 20..25, allowed 1..180
  PitchAcceleration = 20..25, allowed 1..180
  DynamicAccel = false
    CoefDistance = -1.393, allowed -2..2
    YawCrosshairAccel = 17..20, allowed 1..180
    PitchCrosshairAccel = 17..20, allowed 1..180
  AccelerationError = true
    YawAccelError = 0.1, allowed 0.01..1
    PitchAccelError = 0.1, allowed 0.01..1
  ConstantError = true
    YawConstantError = 0.1, allowed 0.01..1
    PitchConstantError = 0.1, allowed 0.01..1
  SigmoidDeceleration = false
    Steepness = 10, allowed 0..20
    Midpoint = 0.3, allowed 0..1

SilentScreen inventory tag:
  Background = Rect / Texture, default Rect
  Rect fill = 0x80000000
  Rect outline = 0x00000000
  Rect margin = 2.0, allowed 0..100
  Scale = 1.5, allowed 0.25..4
  RenderOffset = 0,0,0
  ShowTitle = false
```

## 8. Final no-guess gate

Before implementation begins, an implementing agent must read this contract plus the plan it is executing. If any older code snippet conflicts with this contract, this contract wins.

There are no deferred `TBD`, `TODO`, or "decide during implementation" feature semantics in the approved plan set. Compile-time mapping/API spelling may be adapted to the pinned Yarn 1.21.11 names only when the semantic API described by the plan is unchanged.

The final release may claim automated test/build success only after `gradle clean test build` succeeds. It may claim gameplay/server validation only for checklist cases actually executed in Minecraft.