# Chest Stealer Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Java/Fabric/Meteor `Chest Stealer` module that detects eligible container screens, selects and transfers items with LiquidBounce-aligned selection/move/full-inventory semantics, optionally reuses the Inventory Cleaner cleanup plan, and auto-closes safely after completion.

**Architecture:** `ChestStealer` is a thin Meteor/settings/runtime adapter around four focused components: `ContainerDetector`, `SelectionPlanner`, `ChestTransferPlanner`, and `ChestStealSession`. Every network-affecting slot operation goes through the shared inventory executor/mutex, and every completed logical transaction invalidates the current snapshot/plan. Inventory Cleaner integration is behavioral: when standalone Cleaner is active, Chest Stealer asks it for a combined container+player cleanup plan; when Cleaner is inactive, every transferable container stack is considered useful and no cleanup-driven hotbar quick-swap/discard policy is invented.

**Tech Stack:** Java 21, Minecraft 1.21.11, Yarn 1.21.11+build.3, Fabric API 0.141.4+1.21.11, Meteor Client 1.21.11-SNAPSHOT, JUnit Jupiter 5.11.4, Gradle 9.2.0.

## Global Constraints

- Requires completed Runtime Foundation and Inventory Cleaner plans.
- Chest Stealer is a separate Meteor module in the same JAR and `Armor Stand` category.
- Default settings: AutoClose=true, SelectionMode=Distance, MoveMode=QuickMove, QuickSwaps=true, OnFull=Throw, CheckScreenHandlerType=true, CheckScreenTitle=true, Aura=true, SilentScreen=false.
- Independent container constraints defaults: StartDelay=1..2 ticks, ClickDelay=2..4 ticks, CloseDelay=1..2 ticks, MissChance=0%, Requires NoMovement/NoRotation/NotUsingItem/NotBreaking/NotDuringCombat.
- Container constraints do not expose player-inventory-only `InventoryOpen`.
- Selection modes: Distance, Index, Random.
- Distance StartItem: Default, Random, MaxSlot, MinSlot; RandomFactor allowed 0.25..2.0 and defaults 1.0..1.0.
- Index order defaults Ascending and supports Descending.
- Move modes: QuickMove and DragAndDrop.
- OnFull: None and Throw only; do not expose incomplete PutBack behavior.
- Default accepted handler families are standard generic 9x3, generic 9x6, and shulker-box equivalents under 1.21.11 mappings.
- Default titles: Chest, Large Chest, Shulker Box, Barrel, Chest Minecart, Chest Boat.
- All server inventory state remains authoritative. Never fabricate local success.
- Every action/transaction is followed by fresh handler validation and replanning.
- No anti-cheat bypass logic and no line-by-line LiquidBounce source translation.

---

### Task 1: Add Chest Stealer settings enums and container eligibility model

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ChestSelectionMode.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/DistanceStartItem.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/IndexOrder.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ChestMoveMode.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/OnFullMode.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ContainerFilterConfig.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ContainerDescriptor.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ContainerDetector.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/ContainerDetectorTest.java`

**Interfaces:**
- Produces `ContainerDetector.detect(MinecraftClient mc, ContainerFilterConfig config)` returning `Optional<ContainerDescriptor>`.
- `ContainerDescriptor` exposes sync id, title string, container slot ids, and handler identity needed to reject stale sessions.
- SilentScreen and ChestAura plans later consume the same detector.

- [ ] **Step 1: Write failing pure title/filter tests**

```java
@Test
void defaultTitlesAcceptReferenceNames() {
    ContainerFilterConfig config = ContainerFilterConfig.defaults();
    assertTrue(ContainerDetector.titleAllowed("Chest", config));
    assertTrue(ContainerDetector.titleAllowed("Large Chest", config));
    assertTrue(ContainerDetector.titleAllowed("Shulker Box", config));
    assertTrue(ContainerDetector.titleAllowed("Barrel", config));
    assertTrue(ContainerDetector.titleAllowed("Chest Minecart", config));
    assertTrue(ContainerDetector.titleAllowed("Chest Boat", config));
}

@Test
void blacklistWinsOverWhitelistAndDefaultNames() {
    ContainerFilterConfig config = new ContainerFilterConfig(
        true, true,
        List.of("Chest", "Custom Loot"),
        List.of("Chest")
    );
    assertFalse(ContainerDetector.titleAllowed("Chest", config));
    assertTrue(ContainerDetector.titleAllowed("Custom Loot", config));
}
```

- [ ] **Step 2: Run and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.ContainerDetectorTest
```

- [ ] **Step 3: Implement enums and immutable filter config**

```java
public enum ChestSelectionMode { Distance, Index, Random }
public enum DistanceStartItem { Default, Random, MaxSlot, MinSlot }
public enum IndexOrder { Ascending, Descending }
public enum ChestMoveMode { QuickMove, DragAndDrop }
public enum OnFullMode { None, Throw }
```

```java
public record ContainerFilterConfig(
    boolean checkHandlerType,
    boolean checkTitle,
    List<String> titleWhitelist,
    List<String> titleBlacklist
) {
    private static final List<String> DEFAULT_TITLES = List.of(
        "Chest", "Large Chest", "Shulker Box", "Barrel", "Chest Minecart", "Chest Boat"
    );

    public ContainerFilterConfig {
        titleWhitelist = List.copyOf(titleWhitelist);
        titleBlacklist = List.copyOf(titleBlacklist);
    }

    public static ContainerFilterConfig defaults() {
        return new ContainerFilterConfig(true, true, DEFAULT_TITLES, List.of());
    }
}
```

- [ ] **Step 4: Implement Minecraft container detection**

Eligibility requires all of:

1. `mc.currentScreen instanceof HandledScreen<?>`
2. player's `currentScreenHandler` is the same handler object backing that screen
3. handler is not the player inventory handler
4. when handler checking enabled, handler is `GenericContainerScreenHandler` with 3 or 6 rows or `ShulkerBoxScreenHandler`
5. when title checking enabled, normalized exact title text passes blacklist then whitelist/default set
6. at least one slot is classified by `SlotResolver` as `CONTAINER`

`ContainerDescriptor`:

```java
public record ContainerDescriptor(
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

Do not use screen title alone to infer slot layout.

- [ ] **Step 5: Run test/compile and commit**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.ContainerDetectorTest
gradle compileJava
```

```bash
git add src/main/java/com/arno721/armorstandgrabber/chest \
        src/test/java/com/arno721/armorstandgrabber/chest/ContainerDetectorTest.java
git commit -m "feat: detect eligible chest screens"
```

---

### Task 2: Implement Distance, Index, and Random selection planning

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/SelectionConfig.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/SelectionCandidate.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/SelectionPlanner.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/SelectionPlannerTest.java`

**Interfaces:**
- Produces `SelectionPlanner.order(List<SelectionCandidate> candidates, SelectionConfig config)`.
- `SelectionCandidate` contains handler slot id and visual grid coordinates; selection planning never reads live Minecraft state.

- [ ] **Step 1: Write failing ordering tests**

```java
@Test
void indexAscendingAndDescendingAreExact() {
    List<SelectionCandidate> input = List.of(
        new SelectionCandidate(8, 2, 0),
        new SelectionCandidate(2, 0, 0),
        new SelectionCandidate(5, 1, 0)
    );
    assertEquals(List.of(2, 5, 8), slotIds(planner.order(input, SelectionConfig.index(IndexOrder.Ascending))));
    assertEquals(List.of(8, 5, 2), slotIds(planner.order(input, SelectionConfig.index(IndexOrder.Descending))));
}

@Test
void distanceWithFixedFactorUsesNearestNeighborFromMinSlot() {
    List<SelectionCandidate> input = List.of(
        new SelectionCandidate(0, 0, 0),
        new SelectionCandidate(1, 1, 0),
        new SelectionCandidate(9, 0, 1),
        new SelectionCandidate(17, 8, 1)
    );
    SelectionConfig config = SelectionConfig.distance(DistanceStartItem.MinSlot, 1.0, 1.0, new Random(1));
    assertEquals(List.of(0, 1, 9, 17), slotIds(planner.order(input, config)));
}

@Test
void randomModeIsDeterministicWithSeededRandom() {
    SelectionConfig config = SelectionConfig.random(new Random(123));
    assertEquals(
        slotIds(planner.order(candidates, config)),
        slotIds(new SelectionPlanner().order(candidates, SelectionConfig.random(new Random(123))))
    );
}
```

- [ ] **Step 2: Run and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.SelectionPlannerTest
```

- [ ] **Step 3: Implement exact selection semantics**

`SelectionCandidate`:

```java
public record SelectionCandidate(int slotId, int column, int row) {
    public double distanceTo(SelectionCandidate other) {
        return Math.hypot(column - other.column, row - other.row);
    }
}
```

`SelectionConfig` stores mode, start-item, index order, random-factor min/max and injected `Random`.

Distance mode:

- Default starts from the first candidate in the current candidate list.
- Random samples one start candidate.
- MinSlot starts with smallest `slotId`.
- MaxSlot starts with largest `slotId`.
- Repeatedly choose among remaining candidates by minimum `current.distanceTo(candidate) * sampledFactor`.
- Sample factor independently for every candidate comparison from inclusive real range `[min,max]` after normalizing reversed bounds.
- Ties resolve by smaller slot id.

Index mode sorts by `slotId`. Random mode copies and `Collections.shuffle(copy, random)`.

- [ ] **Step 4: Run tests and commit**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.SelectionPlannerTest
```

```bash
git add src/main/java/com/arno721/armorstandgrabber/chest/SelectionConfig.java \
        src/main/java/com/arno721/armorstandgrabber/chest/SelectionCandidate.java \
        src/main/java/com/arno721/armorstandgrabber/chest/SelectionPlanner.java \
        src/test/java/com/arno721/armorstandgrabber/chest/SelectionPlannerTest.java
git commit -m "feat: plan chest item selection order"
```

---

### Task 3: Add chest session identity and stale-handler protection

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ChestStealPhase.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ChestStealSession.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/ChestStealSessionTest.java`

**Interfaces:**
- Produces a state machine with phases `Idle`, `StartDelay`, `Planning`, `ClickDelay`, `CloseDelay`, `Closing`.
- Session identity is `(syncId, handlerClassName, title)` and must match every tick before an action executes.

- [ ] **Step 1: Write failing state-reset test**

```java
@Test
void beginAndResetDoNotLeakPreviousHandlerState() {
    ChestStealSession session = new ChestStealSession();
    session.begin(new ContainerDescriptor(3, "Chest", "GenericContainerScreenHandler", List.of(0, 1)), 10);
    assertEquals(ChestStealPhase.StartDelay, session.phase());
    assertEquals(3, session.syncId());
    session.reset();
    assertEquals(ChestStealPhase.Idle, session.phase());
    assertEquals(-1, session.syncId());
    assertEquals(0, session.deadlineTick());
}
```

- [ ] **Step 2: Implement state container**

Required fields:

```java
private ChestStealPhase phase = ChestStealPhase.Idle;
private int syncId = -1;
private String handlerClassName;
private String title;
private long deadlineTick;
private long lastMutationTick = -1;
```

`begin(descriptor, deadlineTick)` copies identity and enters `StartDelay`. `matches(descriptor)` compares all identity fields. `markMutation(tick)` sets `lastMutationTick`. `reset()` clears all state.

- [ ] **Step 3: Run test and commit**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.ChestStealSessionTest
```

```bash
git add src/main/java/com/arno721/armorstandgrabber/chest/ChestStealPhase.java \
        src/main/java/com/arno721/armorstandgrabber/chest/ChestStealSession.java \
        src/test/java/com/arno721/armorstandgrabber/chest/ChestStealSessionTest.java
git commit -m "feat: track chest stealing sessions"
```

---

### Task 4: Implement QuickMove and DragAndDrop transfer planning

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/InventoryTransaction.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ChestTransferPlanner.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/InventoryTransactionExecutor.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/ChestTransferPlannerTest.java`

**Interfaces:**
- Produces `ChestTransferPlanner.quickMove(int sourceSlotId)`.
- Produces `ChestTransferPlanner.dragAndDrop(SourceStack source, List<DestinationStack> destinations)`.
- Produces `InventoryTransaction(List<InventoryAction> actions)` with the first/last action marking atomic boundaries.
- `InventoryTransactionExecutor.execute(...)` sends the whole safe transaction while mutex ownership remains with Chest Stealer.

- [ ] **Step 1: Write failing pure transfer tests**

```java
@Test
void quickMoveIsOneShiftClick() {
    InventoryTransaction tx = planner.quickMove(4);
    assertEquals(1, tx.actions().size());
    InventoryAction action = tx.actions().getFirst();
    assertEquals(4, action.slotId());
    assertEquals(SlotActionType.QUICK_MOVE, action.actionType());
}

@Test
void dragAndDropFillsMergeBeforeEmptyAndReturnsRemainder() {
    SourceStack source = new SourceStack(2, 50, 64, "stone");
    List<DestinationStack> destinations = List.of(
        new DestinationStack(30, 40, 64, "stone"),
        new DestinationStack(31, 0, 64, null)
    );
    InventoryTransaction tx = planner.dragAndDrop(source, destinations);
    assertEquals(List.of(2, 30, 31), tx.visitedSlotIds());
    assertFalse(tx.returnsToSource());
}

@Test
void dragAndDropReturnsCursorRemainderWhenCapacityIsInsufficient() {
    SourceStack source = new SourceStack(2, 64, 64, "stone");
    List<DestinationStack> destinations = List.of(new DestinationStack(30, 60, 64, "stone"));
    InventoryTransaction tx = planner.dragAndDrop(source, destinations);
    assertTrue(tx.returnsToSource());
    assertEquals(2, tx.visitedSlotIds().getLast());
}
```

- [ ] **Step 2: Run and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.ChestTransferPlannerTest
```

- [ ] **Step 3: Implement transfer planner**

QuickMove transaction is:

```java
new InventoryAction(sourceSlotId, 0, SlotActionType.QUICK_MOVE, true, true)
```

DragAndDrop transaction:

1. PICKUP source with button 0
2. visit compatible merge destinations first, then empty destinations
3. use PICKUP button 0 to place as much as Minecraft permits into each destination
4. if modeled capacity is less than source count, append PICKUP source slot to return remainder
5. first action `atomicStart=true`; final action `atomicEnd=true`; intermediate actions false/false

Destination compatibility must be derived from actual `ItemStack` component equality in production; pure tests use compatibility keys.

- [ ] **Step 4: Implement executor with cursor recovery**

Before each action verify sync id and handler identity still match. If identity changes before the first click, execute nothing and abort. If identity changes after cursor pickup, make only the safest available attempt to place the cursor stack back into the original source slot when that same handler still exists; otherwise stop and let vanilla/server synchronization resolve it, release only after the atomic state is explicitly ended/reset by session teardown.

After a successful transaction, never execute a second planned transaction from the same snapshot.

- [ ] **Step 5: Test/build and commit**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.ChestTransferPlannerTest
gradle build
```

```bash
git add src/main/java/com/arno721/armorstandgrabber/chest/InventoryTransaction.java \
        src/main/java/com/arno721/armorstandgrabber/chest/ChestTransferPlanner.java \
        src/main/java/com/arno721/armorstandgrabber/chest/InventoryTransactionExecutor.java \
        src/test/java/com/arno721/armorstandgrabber/chest/ChestTransferPlannerTest.java
git commit -m "feat: execute chest item transfers"
```

---

### Task 5: Implement cleanup-aware useful-item selection and QuickSwaps

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ChestLootPlanner.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/QuickSwapPlanner.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/ChestLootPlannerTest.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/QuickSwapPlannerTest.java`

**Interfaces:**
- `ChestLootPlanner.plan(InventorySnapshot snapshot, InventoryCleaner cleaner)` returns `ChestLootPlan` containing useful container slot ids, cleanup plan when applicable, and quick swaps.
- Cleaner inactive: all non-empty container slots are useful, `cleanupPlan` is empty, quick-swap list is empty.
- Cleaner active: call its reusable cleanup planner over the combined snapshot.

- [ ] **Step 1: Write failing inactive/active behavior tests**

```java
@Test
void inactiveCleanerTreatsEveryContainerStackAsUseful() {
    ChestLootPlan plan = planner.plan(snapshotWithContainerSlots(1, 2, 3), CleanerStub.inactive());
    assertEquals(Set.of(1, 2, 3), plan.usefulContainerSlotIds());
    assertTrue(plan.quickSwaps().isEmpty());
}

@Test
void activeCleanerUsesCleanupUsefulSet() {
    ChestLootPlan plan = planner.plan(snapshotWithContainerSlots(1, 2, 3), CleanerStub.activeUseful(Set.of(2)));
    assertEquals(Set.of(2), plan.usefulContainerSlotIds());
}
```

- [ ] **Step 2: Implement QuickSwap decision table**

For each cleanup target assignment whose chosen physical source is a container slot:

- empty normal hotbar target -> one SWAP transaction, button target hotbar index
- useful occupied normal hotbar target -> first QUICK_MOVE or PICKUP_MOVE old useful target to an empty main-inventory slot, then SWAP container source to target
- discardable occupied normal hotbar target -> THROW full old stack (`SlotActionType.THROW`, button 1), then SWAP container source
- offhand target -> direct SWAP with button 40; do not pre-throw

If no empty relocation slot exists for a useful hotbar occupant, skip that quick swap; normal stealing may still occur later.

Each quick swap is a separate atomic transaction. Execute at most one and replan.

- [ ] **Step 3: Write/verify QuickSwap tests**

```java
@Test
void occupiedUsefulHotbarRelocatesBeforeSwap() {
    QuickSwapPlan plan = quickSwapPlanner.plan(source, targetHotbar, occupiedUseful, emptyMainSlot, false);
    assertEquals(List.of(
        QuickSwapStep.RELOCATE_USEFUL,
        QuickSwapStep.SWAP_CONTAINER_IN
    ), plan.steps());
}

@Test
void offhandNeverSchedulesPreThrow() {
    QuickSwapPlan plan = quickSwapPlanner.plan(source, offhand, occupiedJunk, null, true);
    assertEquals(List.of(QuickSwapStep.SWAP_CONTAINER_IN), plan.steps());
}
```

Run:

```bash
gradle test --tests 'com.arno721.armorstandgrabber.chest.*LootPlannerTest' \
            --tests 'com.arno721.armorstandgrabber.chest.QuickSwapPlannerTest'
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/arno721/armorstandgrabber/chest/ChestLootPlanner.java \
        src/main/java/com/arno721/armorstandgrabber/chest/QuickSwapPlanner.java \
        src/test/java/com/arno721/armorstandgrabber/chest/ChestLootPlannerTest.java \
        src/test/java/com/arno721/armorstandgrabber/chest/QuickSwapPlannerTest.java
git commit -m "feat: plan chest loot and quick swaps"
```

---

### Task 6: Implement OnFull disposal and stable auto-close logic

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/ChestCompletionPlanner.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/ChestCompletionPlannerTest.java`

**Interfaces:**
- Produces `ChestCompletionDecision` with `TRANSFER`, `DISCARD_PLAYER_SLOT`, `WAIT`, `BEGIN_CLOSE_DELAY`, or `CLOSE`.
- Only cleanup-plan discard candidates may be returned for OnFull=Throw.

- [ ] **Step 1: Write failing completion tests**

```java
@Test
void onFullNoneNeverDiscardsPlayerItems() {
    ChestCompletionDecision decision = planner.decide(true, false, List.of(25), OnFullMode.None, false);
    assertNotEquals(ChestCompletionDecision.Kind.DISCARD_PLAYER_SLOT, decision.kind());
}

@Test
void onFullThrowChoosesOneCleanupDiscardCandidate() {
    ChestCompletionDecision decision = planner.decide(true, false, List.of(25, 26), OnFullMode.Throw, false);
    assertEquals(ChestCompletionDecision.Kind.DISCARD_PLAYER_SLOT, decision.kind());
    assertEquals(25, decision.slotId());
}

@Test
void noUsefulItemsBeginsCloseDelayOnlyAfterStableReplan() {
    assertEquals(
        ChestCompletionDecision.Kind.WAIT,
        planner.decide(false, true, List.of(), OnFullMode.Throw, true).kind()
    );
}
```

- [ ] **Step 2: Implement rules**

- If useful transferable item exists: `TRANSFER`.
- If useful item exists but no transfer capacity and OnFull=Throw and cleanup plan has discardable player slot: dispose exactly one full stack and replan.
- If useful item exists but no transfer capacity and no legal discard: `WAIT`; do not close because loot remains.
- If no useful item remains and current tick follows a mutation/replan stabilization tick: `WAIT` once.
- If no useful item remains and stable: `BEGIN_CLOSE_DELAY`.
- After close delay deadline and handler identity still matches: `CLOSE`.

AutoClose=false causes session to release mutex/reset without closing once no useful items remain.

- [ ] **Step 3: Run tests and commit**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.ChestCompletionPlannerTest
```

```bash
git add src/main/java/com/arno721/armorstandgrabber/chest/ChestCompletionPlanner.java \
        src/test/java/com/arno721/armorstandgrabber/chest/ChestCompletionPlannerTest.java
git commit -m "feat: handle full chest sessions safely"
```

---

### Task 7: Add the Meteor `Chest Stealer` module and runtime loop

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/modules/ChestStealer.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java`

**Interfaces:**
- Implements `RuntimeTickParticipant` with `runtimeOwner() == RuntimeOwner.CHEST_STEALER`.
- Constructor receives `ClientRuntime` and the shared `InventoryCleaner` module instance.
- Exposes `ContainerDetector containerDetector()`, `boolean silentScreenEnabled()`, `boolean auraEnabled()` for later ChestAura/SilentScreen integration without duplicating settings.

- [ ] **Step 1: Add settings groups and exact defaults**

Groups: `General`, `Constraints`, `Selection`, `Container`, `Aura`, `Silent Screen`.

General:

```text
auto-close = true
move-mode = QuickMove
quick-swaps = true
on-full = Throw
```

Selection:

```text
selection-mode = Distance
distance-start-item = Default
random-factor-min = 1.0
random-factor-max = 1.0
index-order = Ascending
```

Constraints:

```text
start-delay-min-ticks = 1
start-delay-max-ticks = 2
click-delay-min-ticks = 2
click-delay-max-ticks = 4
close-delay-min-ticks = 1
close-delay-max-ticks = 2
miss-chance-percent = 0
no-movement = true
no-rotation = true
not-using-item = true
not-breaking = true
not-during-combat = true
```

Container:

```text
check-screen-handler-type = true
check-screen-title = true
title-whitelist = [Chest, Large Chest, Shulker Box, Barrel, Chest Minecart, Chest Boat]
title-blacklist = []
```

Aura and Silent Screen booleans are created now with defaults true/false; their detailed child settings are added in later plans.

- [ ] **Step 2: Implement deterministic tick state machine**

Core flow:

```java
@Override
public void onRuntimeTick() {
    if (!isActive()) { resetSession(); return; }

    Optional<ContainerDescriptor> detected = containerDetector.detect(mc, filterConfig());
    if (detected.isEmpty()) { resetSession(); return; }
    ContainerDescriptor descriptor = detected.get();

    if (session.phase() == ChestStealPhase.Idle || !session.matches(descriptor)) {
        resetSession();
        if (!runtime.inventoryMutex().tryAcquire(RuntimeOwner.CHEST_STEALER)) return;
        session.begin(descriptor, runtime.tickCount() + scheduler.sampleStartDelay(constraints()));
        return;
    }

    ActivitySnapshot activity = runtime.activityTracker().snapshot(mc, runtime.rotationCoordinator(), runtime.combatTracker());
    if (!ConstraintGate.allowed(constraints(), activity, false)) return;
    if (!runtime.inventoryMutex().isOwnedBy(RuntimeOwner.CHEST_STEALER)
        && !runtime.inventoryMutex().tryAcquire(RuntimeOwner.CHEST_STEALER)) return;

    switch (session.phase()) {
        case StartDelay -> waitOrEnterPlanning();
        case Planning -> planAndExecuteOneLogicalOperation(descriptor);
        case ClickDelay -> waitOrEnterPlanning();
        case CloseDelay -> waitOrClose(descriptor);
        case Closing, Idle -> { }
    }
}
```

`planAndExecuteOneLogicalOperation` order:

1. capture fresh snapshot; abort current tick if cursor non-empty outside an owned transaction
2. create ChestLootPlan
3. if QuickSwaps enabled, execute at most first available quick swap
4. otherwise order useful container candidates via SelectionPlanner
5. attempt first transferable candidate in selected MoveMode
6. if none fit, apply OnFull decision; throw at most one legal player discard stack
7. if no useful items remain, enter stabilization/close logic
8. on any mutation, `session.markMutation(tick)` and arm click delay

A MissChance hit performs no click, marks no mutation, arms click delay, then returns.

- [ ] **Step 3: Close through vanilla handled-screen path**

When `CLOSE` is reached and descriptor identity still matches:

```java
mc.player.closeHandledScreen();
```

Do not call `mc.setScreen(null)` as the close mechanism. After close request, reset session and release Chest Stealer mutex only when not atomic.

- [ ] **Step 4: Register module and runtime participant**

Addon construction after Cleaner:

```java
InventoryCleaner cleaner = new InventoryCleaner(runtime);
ChestStealer chestStealer = new ChestStealer(runtime, cleaner);
Modules.get().add(armor);
Modules.get().add(chestStealer);
Modules.get().add(cleaner);
runtime.register(armor);
runtime.register(chestStealer);
runtime.register(cleaner);
```

Keep category registration unchanged.

- [ ] **Step 5: Full regression gate**

```bash
gradle test
gradle build
```

Expected: one JAR compiles with Armor Stand Grabber, Chest Stealer, Inventory Cleaner; ChestAura and SilentScreen booleans exist but do not yet add their detailed behavior.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/arno721/armorstandgrabber/modules/ChestStealer.java \
        src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java
git commit -m "feat: add chest stealer core module"
```

---

## Self-Review Checklist

- Spec coverage: handler/title filters, independent constraints, all selection modes, both move modes, QuickSwaps, OnFull None/Throw, safe miss, and AutoClose are assigned to tasks.
- Placeholder scan: no deferred implementation placeholders remain; Aura/SilentScreen are explicitly separate approved subproject plans, not unfinished steps in this core plan.
- Type consistency: ChestAura/SilentScreen may use `ContainerDetector`, `ContainerDescriptor`, and `ChestStealer` accessors defined here.
- State invariant: sync id/handler/title are revalidated before all mutations; every mutation invalidates prior snapshot and cleanup plan.
- Completion gate: `gradle test` and `gradle build` green before ChestAura work.