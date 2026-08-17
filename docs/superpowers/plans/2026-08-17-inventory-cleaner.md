# Inventory Cleaner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an independent Meteor `Inventory Cleaner` module and a reusable cleanup-planning engine that Chest Stealer can call to decide which items are useful, where hotbar/offhand items should go, which stacks should be merged, and which items are safe to discard.

**Architecture:** Separate observation, planning, and execution. Minecraft-specific profiling converts `ItemStack` data into immutable `ItemFacet`/score records; the pure `CleanupPlanGenerator` consumes those records plus configured target slots/amount limits and returns deterministic moves, merges, and discard candidates. The standalone module uses the shared `InventoryMutex` and `InventoryActionScheduler`; Chest Stealer later reuses the planner without activating the standalone module.

**Tech Stack:** Java 21, Minecraft 1.21.11, Yarn 1.21.11+build.3, Fabric API 0.141.4+1.21.11, Meteor Client 1.21.11-SNAPSHOT, JUnit Jupiter 5.11.4, Gradle 9.2.0.

## Global Constraints

- Requires the completed Runtime Foundation plan.
- Keep mod id `armor-stand-grabber` and package root `com.arno721.armorstandgrabber`.
- Inventory Cleaner is a separate Meteor module under category `Armor Stand`.
- Standalone Inventory Cleaner has lower inventory priority than Chest Stealer and Armor Stand Grabber.
- Default limits: MaximumBlocks=512, MaximumArrows=128, MaximumThrowables=64, MaximumFoodPoints=200, MaximumWaterBuckets=2, MaximumLavaBuckets=2, MaximumMilkBuckets=2.
- ItemsBlacklist defaults empty.
- Greedy defaults true but is a compatibility no-op for the pinned reference revision.
- OffHandItem defaults Shield.
- Hotbar defaults: Weapon, Bow, Pickaxe, Axe, None, Potion, Food, Block, Block.
- Supported target choices: Sword, Weapon, Spear, Mace, Bow, Crossbow, Axe, Pickaxe, Shovel, Hoe, Rod, Shield, Water, Lava, Milk, Pearl, Gapple, Food, Potion, Block, Throwables, Ignore, None.
- `Ignore` protects the configured target slot from Cleaner; `None` creates no category requirement and no protection.
- An ItemStack may expose multiple facets.
- Inventory Cleaner does not equip armor.
- Player-inventory constraints include `InventoryOpen` in addition to the generic runtime requirements.
- Every executed slot action is followed by fresh state observation before a new cleanup plan is trusted.
- No line-by-line LiquidBounce translation; implement behavior independently in Java.

---

### Task 1: Define slot, facet, target, and cleanup-plan domain types

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/InventoryRegion.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/SlotRef.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/SortChoice.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/ItemType.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/ItemFacet.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/ItemProfile.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/PlannedMove.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/CleanupPlan.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/inventory/InventoryDomainTest.java`

**Interfaces:**
- Produces stable types consumed by all later Inventory Cleaner and Chest Stealer tasks.
- `SlotRef` contains screen-handler slot id plus semantic region/index; never treat player inventory index as handler slot id.
- `ItemProfile` contains one physical stack and a list of facets; allocation code may select a facet without duplicating the physical stack.

- [ ] **Step 1: Write failing domain tests**

```java
@Test
void ignoreAndNoneHaveDifferentProtectionSemantics() {
    assertTrue(SortChoice.Ignore.protectsSlot());
    assertFalse(SortChoice.None.protectsSlot());
}

@Test
void oneProfileCanExposeMultipleFacets() {
    SlotRef slot = new SlotRef(12, InventoryRegion.PLAYER_MAIN, 5);
    ItemProfile profile = new ItemProfile(
        slot,
        "minecraft:diamond_axe",
        1,
        1,
        false,
        List.of(
            new ItemFacet(ItemType.WEAPON, 90, 0),
            new ItemFacet(ItemType.AXE, 100, 0)
        )
    );
    assertEquals(2, profile.facets().size());
    assertTrue(profile.supports(SortChoice.Weapon));
    assertTrue(profile.supports(SortChoice.Axe));
}

@Test
void cleanupPlanIsImmutableFromCallerCollections() {
    List<PlannedMove> moves = new ArrayList<>();
    CleanupPlan plan = new CleanupPlan(moves, List.of(), List.of(), Set.of());
    moves.add(new PlannedMove(1, 2, PlannedMove.Kind.SWAP));
    assertTrue(plan.moves().isEmpty());
}
```

- [ ] **Step 2: Run focused tests and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.inventory.InventoryDomainTest
```

Expected: new types do not exist.

- [ ] **Step 3: Implement exact enums and records**

```java
public enum InventoryRegion {
    CONTAINER,
    PLAYER_MAIN,
    PLAYER_HOTBAR,
    PLAYER_OFFHAND,
    PLAYER_ARMOR,
    OTHER
}
```

```java
public record SlotRef(int slotId, InventoryRegion region, int logicalIndex) {
    public SlotRef {
        if (slotId < 0) throw new IllegalArgumentException("slotId must be >= 0");
    }
}
```

```java
public enum SortChoice {
    Sword, Weapon, Spear, Mace, Bow, Crossbow, Axe, Pickaxe, Shovel, Hoe,
    Rod, Shield, Water, Lava, Milk, Pearl, Gapple, Food, Potion, Block,
    Throwables, Ignore, None;

    public boolean protectsSlot() { return this == Ignore; }
}
```

```java
public enum ItemType {
    ARMOR, SWORD, WEAPON, SPEAR, MACE, BOW, CROSSBOW, ARROW,
    AXE, PICKAXE, SHOVEL, HOE, TOOL, ROD, THROWABLE, SHIELD,
    WATER, LAVA, MILK, BUCKET, PEARL, GAPPLE, FOOD, POTION, BLOCK, NONE
}
```

`ItemFacet`:

```java
public record ItemFacet(ItemType type, long score, int allocationPriority) {}
```

`ItemProfile` fields/signature:

```java
public record ItemProfile(
    SlotRef slot,
    String itemId,
    int count,
    int maxCount,
    boolean blacklisted,
    List<ItemFacet> facets
) {
    public ItemProfile {
        facets = List.copyOf(facets);
    }

    public boolean supports(SortChoice choice) {
        return facets.stream().anyMatch(f -> ItemTypeMapping.matches(choice, f.type()));
    }
}
```

Create `ItemTypeMapping` as a package-private class in `ItemProfile.java` or its own focused file if implementation exceeds 80 lines. Exact mapping:

- Sword -> SWORD
- Weapon -> WEAPON, SWORD, SPEAR, MACE, AXE
- Spear -> SPEAR
- Mace -> MACE
- Bow -> BOW
- Crossbow -> CROSSBOW
- Axe/Pickaxe/Shovel/Hoe -> matching specialized facet
- Rod -> ROD
- Shield -> SHIELD
- Water/Lava/Milk -> matching bucket facet
- Pearl -> PEARL
- Gapple -> GAPPLE
- Food -> FOOD or GAPPLE
- Potion -> POTION
- Block -> BLOCK
- Throwables -> THROWABLE or PEARL
- Ignore/None -> no item-type match

`PlannedMove`:

```java
public record PlannedMove(int sourceSlotId, int targetSlotId, Kind kind) {
    public enum Kind { SWAP, QUICK_MOVE, PICKUP_MOVE, MERGE }
}
```

`CleanupPlan`:

```java
public record CleanupPlan(
    List<PlannedMove> moves,
    List<Integer> mergeSourceSlotIds,
    List<Integer> discardSlotIds,
    Set<Integer> usefulSlotIds
) {
    public CleanupPlan {
        moves = List.copyOf(moves);
        mergeSourceSlotIds = List.copyOf(mergeSourceSlotIds);
        discardSlotIds = List.copyOf(discardSlotIds);
        usefulSlotIds = Set.copyOf(usefulSlotIds);
    }

    public boolean isUseful(int slotId) { return usefulSlotIds.contains(slotId); }
}
```

- [ ] **Step 4: Run focused test and verify GREEN**

```bash
gradle test --tests com.arno721.armorstandgrabber.inventory.InventoryDomainTest
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/arno721/armorstandgrabber/inventory \
        src/test/java/com/arno721/armorstandgrabber/inventory/InventoryDomainTest.java
git commit -m "feat: add inventory cleanup domain model"
```

---

### Task 2: Build screen-handler snapshots and safe slot resolution

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/InventorySnapshot.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/SlotResolver.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/InventorySnapshotFactory.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/inventory/SlotResolverTest.java`

**Interfaces:**
- Produces `InventorySnapshot(int syncId, List<ItemProfile> profiles, Map<Integer, SlotRef> bySlotId, int cursorCount)`.
- Produces `SlotResolver.classify(ScreenHandler handler, int slotId, PlayerInventory playerInventory)` and `SlotResolver.playerHotbarSlotId(...)`.
- Produces `InventorySnapshotFactory.capture(MinecraftClient mc, ItemProfiler profiler, Set<String> blacklist)`.

- [ ] **Step 1: Write the pure logical-index tests**

The pure helper must map player inventory indices 0..8 to HOTBAR, 9..35 to MAIN, 40 to OFFHAND, 36..39 to ARMOR; all other player indices are OTHER. Test it directly:

```java
@Test
void playerInventoryIndicesClassifyWithoutAssumingHandlerSlotId() {
    assertEquals(InventoryRegion.PLAYER_HOTBAR, SlotResolver.regionForPlayerIndex(0));
    assertEquals(InventoryRegion.PLAYER_HOTBAR, SlotResolver.regionForPlayerIndex(8));
    assertEquals(InventoryRegion.PLAYER_MAIN, SlotResolver.regionForPlayerIndex(9));
    assertEquals(InventoryRegion.PLAYER_MAIN, SlotResolver.regionForPlayerIndex(35));
    assertEquals(InventoryRegion.PLAYER_ARMOR, SlotResolver.regionForPlayerIndex(36));
    assertEquals(InventoryRegion.PLAYER_OFFHAND, SlotResolver.regionForPlayerIndex(40));
}
```

- [ ] **Step 2: Run and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.inventory.SlotResolverTest
```

- [ ] **Step 3: Implement handler-aware resolution**

`SlotResolver.classify` must inspect the actual `Slot` object from `handler.slots.get(slotId)`. When `slot.inventory == playerInventory`, derive region from `slot.getIndex()` using `regionForPlayerIndex`; otherwise use `CONTAINER` for slots before the first player-owned slot and `OTHER` for foreign auxiliary slots that are not normal container storage.

Never use hard-coded chest offsets such as `slotId - 27`.

`InventorySnapshotFactory.capture` must return `null` when player/interaction manager is absent. It reads `mc.player.currentScreenHandler`, captures `syncId`, cursor stack count, and profiles every non-empty slot through `ItemProfiler.profile(ItemStack, SlotRef, blacklist)`. Empty slots still appear in `bySlotId` so move planners can target them.

- [ ] **Step 4: Compile integration layer**

```bash
gradle test --tests com.arno721.armorstandgrabber.inventory.SlotResolverTest
gradle compileJava
```

Expected: Yarn 1.21.11 slot APIs compile.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/arno721/armorstandgrabber/inventory/InventorySnapshot.java \
        src/main/java/com/arno721/armorstandgrabber/inventory/SlotResolver.java \
        src/main/java/com/arno721/armorstandgrabber/inventory/InventorySnapshotFactory.java \
        src/test/java/com/arno721/armorstandgrabber/inventory/SlotResolverTest.java
git commit -m "feat: capture handler-aware inventory snapshots"
```

---

### Task 3: Implement Minecraft item profiling and clean-room score construction

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/ItemProfiler.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/ItemScore.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/ItemScoreComparator.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/PotionTier.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/inventory/ItemScoreComparatorTest.java`

**Interfaces:**
- Produces `ItemProfiler.profile(ItemStack stack, SlotRef slot, Set<String> blacklist)`.
- Produces deterministic facet scores without display-name heuristics.
- `ItemScoreComparator` compares immutable score tuples and is testable without Minecraft bootstrap.

- [ ] **Step 1: Write failing score-order tests**

```java
@Test
void weaponDamageOutranksTieBreakers() {
    ItemScore weaker = ItemScore.weapon(8.0, 1.6, 0, 100, false, 1);
    ItemScore stronger = ItemScore.weapon(9.0, 1.0, 0, 20, false, 99);
    assertTrue(ItemScoreComparator.INSTANCE.compare(stronger, weaker) > 0);
}

@Test
void miningSpeedThenFortuneThenDurabilityAreStable() {
    ItemScore base = ItemScore.tool(8.0, false, 0, 100, false, 10);
    ItemScore faster = ItemScore.tool(9.0, false, 0, 20, false, 20);
    assertTrue(ItemScoreComparator.INSTANCE.compare(faster, base) > 0);
}

@Test
void deterministicTieBreakerMakesComparisonTotal() {
    ItemScore a = ItemScore.generic(0, false, 3);
    ItemScore b = ItemScore.generic(0, false, 4);
    assertNotEquals(0, ItemScoreComparator.INSTANCE.compare(a, b));
}
```

- [ ] **Step 2: Run and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.inventory.ItemScoreComparatorTest
```

- [ ] **Step 3: Implement score tuples and comparator chain**

`ItemScore` is a record of ordered long/double fields rather than one opaque magic number:

```java
public record ItemScore(
    double primary,
    double secondary,
    double tertiary,
    double quaternary,
    boolean hotbarPreferred,
    int stableSlotTieBreaker
) {
    public static ItemScore weapon(double damage, double attackSpeed, int enchantWeight, int durability, boolean hotbar, int slot) {
        return new ItemScore(damage, attackSpeed, enchantWeight, durability, hotbar, slot);
    }

    public static ItemScore tool(double miningSpeed, boolean silkTouch, int fortune, int durability, boolean hotbar, int slot) {
        return new ItemScore(miningSpeed, silkTouch ? 1 : 0, fortune, durability, hotbar, slot);
    }

    public static ItemScore generic(double primary, boolean hotbar, int slot) {
        return new ItemScore(primary, 0, 0, 0, hotbar, slot);
    }
}
```

`ItemScoreComparator` compares primary, secondary, tertiary, quaternary, hotbar preference, then the negative slot index so lower physical slot index wins identical ties.

- [ ] **Step 4: Implement `ItemProfiler` with component/type checks**

Build facets from Minecraft classes/components, never from translated display names:

- swords: SWORD + WEAPON facets
- axes: AXE + TOOL + WEAPON facets
- pickaxe/shovel/hoe: specialized + TOOL facets
- trident/spear-family item available in 1.21.11 mappings: SPEAR + WEAPON
- mace: MACE + WEAPON
- bow/crossbow/fishing rod/shield: matching facet
- arrows: ARROW
- throwable snowball/egg/wind-charge-style projectile items: THROWABLE
- ender pearl: PEARL + THROWABLE
- golden apple / enchanted golden apple: GAPPLE + FOOD
- other edible item with nutrition component: FOOD
- potion items: POTION only when every effect is non-harmful under the local good/bad policy; bad-only potions are not useful Potion candidates
- block items: BLOCK only when the block is a full, stable, player-supporting placement candidate; reject falling blocks, block-entity blocks, slippery/slow/jump-reducing blocks, and non-full collision shapes from the Block facet
- water/lava/milk buckets: matching facet + BUCKET
- armor items: ARMOR facet for retention comparison only; no target-slot equip plan

Use registry IDs from `Registries.ITEM.getId(stack.getItem()).toString()` for blacklist matching.

Potion quality tiers used by the clean-room comparator are fixed as:

- S: instant health
- A: regeneration, resistance, fire resistance, health boost, absorption
- B: speed, strength, slow falling, invisibility
- C: saturation, water breathing, jump boost, haste, night vision
- D: luck
- F: every other beneficial/unrecognized effect

Bad effects are slowness, mining fatigue, instant damage, nausea, blindness, hunger, weakness, poison, wither, glowing, levitation, unluck, bad omen, darkness. Potion ordering compares descending tier multiset, then amplifier multiset, then type preference `splash > drinkable > lingering`, then duration multiset, hotbar preference, stable slot.

Food ordering: enchanted golden apple > golden apple > saturation/nutrition ratio > nutrition > saturation > preferred stack count > hotbar > stable slot.

Armor ordering: create an armor quality tuple from defense, toughness, relevant protection enchantments, durability, and stable slot; the planner retains the best current set and future-use diamond upgrades but never equips them.

- [ ] **Step 5: Run score tests and compile Minecraft profiling**

```bash
gradle test --tests com.arno721.armorstandgrabber.inventory.ItemScoreComparatorTest
gradle compileJava
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/arno721/armorstandgrabber/inventory/ItemProfiler.java \
        src/main/java/com/arno721/armorstandgrabber/inventory/ItemScore.java \
        src/main/java/com/arno721/armorstandgrabber/inventory/ItemScoreComparator.java \
        src/main/java/com/arno721/armorstandgrabber/inventory/PotionTier.java \
        src/test/java/com/arno721/armorstandgrabber/inventory/ItemScoreComparatorTest.java
git commit -m "feat: profile inventory item utility"
```

---

### Task 4: Implement amount constraints and allocation to offhand/hotbar targets

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/ItemConstraints.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/TargetLayout.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/AllocationPlanner.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/inventory/AllocationPlannerTest.java`

**Interfaces:**
- Produces `ItemConstraints` with exact seven maximum values.
- Produces `TargetLayout(SortChoice offhand, List<SortChoice> hotbar)`; hotbar list length must be exactly 9.
- Produces `AllocationPlanner.allocate(List<ItemProfile> profiles, TargetLayout layout)` returning `AllocationResult(Set<Integer> usefulSlotIds, List<PlannedMove> moves, Set<Integer> protectedSlotIds)`.

- [ ] **Step 1: Write failing allocation tests**

Use synthetic profiles/facets only:

```java
@Test
void specializedTargetWinsBeforeGenericWeaponTarget() {
    ItemProfile axe = TestProfiles.profile(10, ItemType.AXE, 100, ItemType.WEAPON, 80);
    ItemProfile sword = TestProfiles.profile(11, ItemType.SWORD, 95, ItemType.WEAPON, 95);
    TargetLayout layout = new TargetLayout(SortChoice.None, List.of(
        SortChoice.Weapon, SortChoice.Axe, SortChoice.None, SortChoice.None, SortChoice.None,
        SortChoice.None, SortChoice.None, SortChoice.None, SortChoice.None
    ));

    AllocationResult result = new AllocationPlanner().allocate(List.of(axe, sword), layout);
    assertEquals(2, result.usefulSlotIds().size());
    assertTrue(result.assignments().containsValue(10));
    assertTrue(result.assignments().containsValue(11));
}

@Test
void ignoreProtectsPhysicalTargetSlotEvenWhenItsItemIsOtherwiseJunk() {
    TargetLayout layout = new TargetLayout(SortChoice.None, List.of(
        SortChoice.Ignore, SortChoice.None, SortChoice.None, SortChoice.None, SortChoice.None,
        SortChoice.None, SortChoice.None, SortChoice.None, SortChoice.None
    ));
    AllocationResult result = new AllocationPlanner().allocate(List.of(), layout);
    assertTrue(result.protectedHotbarIndices().contains(0));
}
```

- [ ] **Step 2: Run and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.inventory.AllocationPlannerTest
```

- [ ] **Step 3: Implement specialized-first allocation**

Allocation order is deterministic:

1. Ignore slots become protected and are removed from target consideration.
2. Offhand target is allocated.
3. Specialized hotbar categories (Spear/Mace/Sword/Bow/Crossbow/Axe/Pickaxe/Shovel/Hoe/Rod/Shield/Water/Lava/Milk/Pearl/Gapple/Potion) are allocated.
4. Generic Weapon/Food/Block/Throwables targets are allocated.
5. `None` targets are skipped.

For each target, select the highest-scoring unused physical profile exposing a matching facet. A physical stack may satisfy only one target assignment even if it has multiple facets. Existing correct placement wins an otherwise equal comparison to reduce actions.

`AllocationResult` contains mappings by logical hotbar index/offhand plus the set of useful physical slot ids.

- [ ] **Step 4: Implement amount counters**

`ItemConstraints`:

```java
public record ItemConstraints(
    int maximumBlocks,
    int maximumArrows,
    int maximumThrowables,
    int maximumFoodPoints,
    int maximumWaterBuckets,
    int maximumLavaBuckets,
    int maximumMilkBuckets
) {
    public static ItemConstraints defaults() {
        return new ItemConstraints(512, 128, 64, 200, 2, 2, 2);
    }
}
```

The future cleanup planner retains stacks in descending quality/order until each configured total is reached. Food uses `nutrition * stackCount` toward MaximumFoodPoints. Partial stacks are indivisible for retention: if retaining the next stack crosses the maximum, retain it only when the accumulated amount was previously below the maximum; discard further stacks afterward.

- [ ] **Step 5: Run tests and commit**

```bash
gradle test --tests com.arno721.armorstandgrabber.inventory.AllocationPlannerTest
```

```bash
git add src/main/java/com/arno721/armorstandgrabber/inventory/ItemConstraints.java \
        src/main/java/com/arno721/armorstandgrabber/inventory/TargetLayout.java \
        src/main/java/com/arno721/armorstandgrabber/inventory/AllocationPlanner.java \
        src/test/java/com/arno721/armorstandgrabber/inventory/AllocationPlannerTest.java
git commit -m "feat: plan cleaner target allocation"
```

---

### Task 5: Generate complete cleanup plans, including useful retention and discard candidates

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/CleanupPlanGenerator.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/StackMergePlanner.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/inventory/CleanupPlanGeneratorTest.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/inventory/StackMergePlannerTest.java`

**Interfaces:**
- Produces `CleanupPlanGenerator.generate(List<ItemProfile> profiles, TargetLayout layout, ItemConstraints constraints, Set<Integer> protectedPhysicalSlots)`.
- Produces `StackMergePlanner.mergeSources(List<StackState> stacks)` where `StackState` is a pure record of slot id, compatibility key, count, max count, useful/protected status.
- Chest Stealer later calls the same `generate` method over combined container + player profiles.

- [ ] **Step 1: Write failing planner tests**

```java
@Test
void blacklistAndProtectedSlotsAreNeverDiscarded() {
    ItemProfile blacklisted = TestProfiles.blacklisted(20, ItemType.NONE);
    ItemProfile junk = TestProfiles.profile(21, ItemType.NONE, 0);
    CleanupPlan plan = generator.generate(
        List.of(blacklisted, junk),
        TargetLayout.defaults(),
        ItemConstraints.defaults(),
        Set.of(20)
    );
    assertFalse(plan.discardSlotIds().contains(20));
    assertTrue(plan.discardSlotIds().contains(21));
}

@Test
void allocatedAndAmountLimitedItemsAreUseful() {
    // synthetic weapon plus block stacks totaling <= 512
    CleanupPlan plan = generator.generate(profiles, layout, ItemConstraints.defaults(), Set.of());
    assertTrue(plan.isUseful(bestWeaponSlot));
    assertTrue(plan.isUseful(firstBlockStack));
}
```

`StackMergePlannerTest`:

```java
@Test
void mergeOnlyCompatibleNonProtectedStacksWithCapacity() {
    List<Integer> sources = new StackMergePlanner().mergeSources(List.of(
        new StackState(1, "stone", 32, 64, true, false),
        new StackState(2, "stone", 16, 64, true, false),
        new StackState(3, "dirt", 16, 64, true, false),
        new StackState(4, "stone", 8, 64, true, true)
    ));
    assertEquals(List.of(2), sources);
}
```

- [ ] **Step 2: Run and verify RED**

```bash
gradle test --tests 'com.arno721.armorstandgrabber.inventory.*PlannerTest'
```

- [ ] **Step 3: Implement cleanup generation in fixed phases**

`generate` performs these phases in order:

1. mark blacklisted items useful/protected from disposal
2. apply `AllocationPlanner`
3. retain best armor set/future-use armor profiles
4. apply amount limits for blocks, arrows, throwables, food points, and bucket types
5. retain remaining recognized useful utilities not capped by an amount setting
6. keep all protected physical slots
7. emit target moves for offhand/hotbar assignment
8. calculate merge candidates among retained stackable items
9. classify every remaining unprotected, unallocated, non-blacklisted player-inventory item as discardable

Container items may appear in `profiles` when Chest Stealer calls this later. Container items not chosen as useful are not placed in `discardSlotIds`; only player-owned regions are discardable.

- [ ] **Step 4: Implement merge compatibility key**

Production snapshot code must derive a stack compatibility key using item identity plus data components/NBT-equivalent component state so only `ItemStack.areItemsAndComponentsEqual`-equivalent stacks are merged. Pure tests use string keys.

`StackMergePlanner` chooses a destination with available capacity, then source stacks in ascending count order so partial stacks consolidate predictably. Protected target stacks may receive compatible items but may never be chosen as a source to empty.

- [ ] **Step 5: Run tests and commit**

```bash
gradle test --tests 'com.arno721.armorstandgrabber.inventory.*PlannerTest'
```

```bash
git add src/main/java/com/arno721/armorstandgrabber/inventory/CleanupPlanGenerator.java \
        src/main/java/com/arno721/armorstandgrabber/inventory/StackMergePlanner.java \
        src/test/java/com/arno721/armorstandgrabber/inventory/CleanupPlanGeneratorTest.java \
        src/test/java/com/arno721/armorstandgrabber/inventory/StackMergePlannerTest.java
git commit -m "feat: generate inventory cleanup plans"
```

---

### Task 6: Add one-action-at-a-time inventory scheduler and executor

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/InventoryAction.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/InventoryActionScheduler.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/InventoryActionExecutor.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/inventory/InventoryActionSchedulerTest.java`

**Interfaces:**
- Produces `InventoryAction(slotId, button, SlotActionType actionType, boolean atomicStart, boolean atomicEnd)`.
- Produces scheduler methods `armStartDelay`, `armClickDelay`, `armCloseDelay`, `ready`, `recordAction`, `recordSafeMiss`, `reset`.
- `InventoryActionExecutor.execute(MinecraftClient mc, InventoryAction action, RuntimeOwner owner, InventoryMutex mutex)` is the only new Cleaner/Chest code path that sends `clickSlot`.

- [ ] **Step 1: Write failing deterministic scheduler test using injected random**

```java
@Test
void equalDelayBoundsAreExactAndMissConsumesDelayWithoutAction() {
    InventoryActionScheduler scheduler = new InventoryActionScheduler(new Random(1));
    InventoryConstraintConfig config = new InventoryConstraintConfig(1, 1, 3, 3, 2, 2, 100, false, false, false, false, false);
    scheduler.armStartDelay(10, config);
    assertFalse(scheduler.ready(10));
    assertTrue(scheduler.ready(11));
    assertTrue(scheduler.shouldMiss(config));
    scheduler.recordSafeMiss(11, config);
    assertFalse(scheduler.ready(13));
    assertTrue(scheduler.ready(14));
}
```

- [ ] **Step 2: Run and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.inventory.InventoryActionSchedulerTest
```

- [ ] **Step 3: Implement tick-based delay sampling and safe miss**

Use inclusive integer range sampling with normalized bounds. `shouldMiss` returns true only when `missChancePercent > 0` and a 0..99 draw is below the percent. `recordSafeMiss` only advances the click deadline; it never constructs an `InventoryAction`.

- [ ] **Step 4: Implement action executor**

```java
public boolean execute(MinecraftClient mc, InventoryAction action, RuntimeOwner owner, InventoryMutex mutex) {
    if (mc.player == null || mc.interactionManager == null) return false;
    if (!mutex.isOwnedBy(owner)) return false;
    ScreenHandler handler = mc.player.currentScreenHandler;
    if (action.slotId() < 0 || action.slotId() >= handler.slots.size()) return false;

    if (action.atomicStart()) mutex.beginAtomic(owner);
    try {
        mc.interactionManager.clickSlot(
            handler.syncId,
            action.slotId(),
            action.button(),
            action.actionType(),
            mc.player
        );
        return true;
    } finally {
        if (action.atomicEnd()) mutex.endAtomic(owner);
    }
}
```

Multi-click PICKUP/merge operations use `atomicStart=true` on the first click and `atomicEnd=true` on the final click. If an intermediate action becomes invalid, execute only the minimum safe cursor-return click before calling `endAtomic`.

- [ ] **Step 5: Run test/build and commit**

```bash
gradle test --tests com.arno721.armorstandgrabber.inventory.InventoryActionSchedulerTest
gradle build
```

```bash
git add src/main/java/com/arno721/armorstandgrabber/inventory/InventoryAction.java \
        src/main/java/com/arno721/armorstandgrabber/inventory/InventoryActionScheduler.java \
        src/main/java/com/arno721/armorstandgrabber/inventory/InventoryActionExecutor.java \
        src/test/java/com/arno721/armorstandgrabber/inventory/InventoryActionSchedulerTest.java
git commit -m "feat: schedule safe inventory actions"
```

---

### Task 7: Add the standalone Meteor `Inventory Cleaner` module

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/modules/InventoryCleaner.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java`

**Interfaces:**
- Produces module name `inventory-cleaner`.
- Implements `RuntimeTickParticipant` with `runtimeOwner() == RuntimeOwner.INVENTORY_CLEANER`.
- Exposes `CleanupPlan createPlan(InventorySnapshot snapshot)` publicly so Chest Stealer can reuse active Cleaner settings when the module is active.
- Exposes `boolean plannerEnabledForChest()` returning `isActive()`.

- [ ] **Step 1: Add exact Meteor settings**

Create groups `General`, `Constraints`, `Hotbar`.

General integer settings with defaults and non-negative ranges:

```text
maximum-blocks = 512
maximum-arrows = 128
maximum-throwables = 64
maximum-food-points = 200
maximum-water-buckets = 2
maximum-lava-buckets = 2
maximum-milk-buckets = 2
greedy = true
```

Use a `StringListSetting` named `items-blacklist`, default empty, storing registry IDs such as `minecraft:ender_pearl`.

Target settings:

```text
offhand-item = Shield
slot-item-1 = Weapon
slot-item-2 = Bow
slot-item-3 = Pickaxe
slot-item-4 = Axe
slot-item-5 = None
slot-item-6 = Potion
slot-item-7 = Food
slot-item-8 = Block
slot-item-9 = Block
```

Constraint settings create `InventoryConstraintConfig`; include an additional `inventory-open` boolean default true for standalone execution.

- [ ] **Step 2: Implement one-action-per-replan tick loop**

Exact high-level state flow:

```java
@Override
public void onRuntimeTick() {
    if (!isActive() || mc.player == null || mc.interactionManager == null) {
        resetExecution();
        return;
    }

    ActivitySnapshot activity = activityTracker.snapshot(mc, rotationCoordinator, combatTracker);
    InventoryConstraintConfig constraints = constraintConfig();
    if (!ConstraintGate.allowed(constraints, activity, inventoryOpen.get())) return;
    if (!inventoryMutex.tryAcquire(RuntimeOwner.INVENTORY_CLEANER)) return;

    InventorySnapshot snapshot = snapshotFactory.capture(mc, itemProfiler, blacklistIds());
    if (snapshot == null || snapshot.cursorCount() != 0) {
        releaseIfSafe();
        return;
    }

    if (!scheduler.ready(runtimeTick)) return;
    if (scheduler.shouldMiss(constraints)) {
        scheduler.recordSafeMiss(runtimeTick, constraints);
        return;
    }

    CleanupPlan plan = createPlan(snapshot);
    Optional<InventoryAction> next = chooseNextAction(snapshot, plan);
    if (next.isEmpty()) {
        releaseIfSafe();
        return;
    }

    if (executor.execute(mc, next.get(), RuntimeOwner.INVENTORY_CLEANER, inventoryMutex)) {
        scheduler.recordAction(runtimeTick, constraints);
    }
}
```

Action priority: required hotbar/offhand moves -> stack merges -> discard. Execute only one logical action/atomic sequence, then discard the entire plan and recapture next tick.

- [ ] **Step 3: Register module in addon/runtime**

Construction order after Runtime Foundation:

```java
ClientRuntime runtime = new ClientRuntime(MinecraftClient.getInstance());
ArmorStandGrabber armor = new ArmorStandGrabber(runtime.inventoryMutex(), runtime.rotationCoordinator());
InventoryCleaner cleaner = new InventoryCleaner(runtime);
Modules.get().add(armor);
Modules.get().add(cleaner);
runtime.register(armor);
runtime.register(cleaner);
ClientTickEvents.END_CLIENT_TICK.register(runtime::tick);
```

Do not add Chest Stealer yet.

- [ ] **Step 4: Verify full test/build gate**

```bash
gradle test
gradle build
```

Expected: existing Armor Stand tests and all new inventory tests pass; one JAR contains both Meteor modules.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/arno721/armorstandgrabber/modules/InventoryCleaner.java \
        src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java
git commit -m "feat: add standalone inventory cleaner"
```

---

## Self-Review Checklist

- Spec coverage: all target categories, defaults, amount limits, blacklist, Greedy compatibility no-op, multi-facet items, armor retention without equipping, stack merging, disposal, InventoryOpen constraint, and reusable cleanup planning are covered.
- Placeholder scan: no `TBD`, `TODO`, or unspecified implementation phase remains.
- Type consistency: Chest Stealer may depend on `InventorySnapshot`, `ItemProfile`, `CleanupPlanGenerator`, `CleanupPlan`, `TargetLayout`, `ItemConstraints`, `InventoryActionScheduler`, and `InventoryActionExecutor` exactly as defined here.
- Safety invariant: no planner output is trusted after a slot action; the next action requires a new `InventorySnapshot` and a regenerated plan.
- Completion gate: `gradle test` and `gradle build` must be green before starting Chest Stealer core.