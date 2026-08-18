# Chest Stealer + Inventory Cleaner Design

Date: 2026-08-17
Target release: v0.4.0
Target branch: `feature/chest-stealer-inventory-cleaner`
Repository: `arno721/game-cs-ai`

## 1. Goal

Add two new independent Meteor modules to the existing addon JAR while preserving the current Armor Stand Grabber:

- `Chest Stealer`
- `Inventory Cleaner`

The user-visible behavior and settings of Chest Stealer, ChestAura, SilentScreen, and Inventory Cleaner should align with LiquidBounce `nextgen` at pinned reference commit `141631789e5aeb7851562052dca567ab49716e99` within the explicit scope boundaries in this document.

The implementation is a clean-room Java rewrite using Fabric/Minecraft client APIs. The project does not perform a line-by-line Kotlin-to-Java translation and does not import LiquidBounce framework classes.

## 2. Scope Boundary

In scope:

- Chest Stealer container detection, item selection, stealing, quick swaps, full-inventory behavior, delays, and auto-close.
- ChestAura container discovery, rotation, interaction, retry/await logic, and interacted-block tracking.
- SilentScreen render suppression, slot-input suppression, cursor behavior, and world-anchored inventory tag.
- Inventory Cleaner item categorization, scoring, amount constraints, hotbar/offhand planning, stack merging, blacklist handling, and disposal.
- Shared runtime for inventory scheduling, slot mapping, rotation ownership, lifecycle reset, and module arbitration.
- Unit tests for pure planning/scheduling logic and build verification for Minecraft/Fabric integration.

Out of scope:

- Porting the complete LiquidBounce event/config framework.
- Porting unrelated LiquidBounce modules such as Offhand, AutoArmor, Scaffold, ViaFabricPlus support, or its combat framework.
- Implementing anti-cheat bypass logic or making claims about server acceptance.
- Adding AutoArmor behavior to Inventory Cleaner.
- Implementing LiquidBounce options that exist in the pinned revision but are not functionally implemented there. `Greedy` is retained for compatibility but remains behaviorally inert in this revision.

## 3. Compatibility Target

Keep the existing addon identity and package root:

- mod id: `armor-stand-grabber`
- entrypoint: `com.arno721.armorstandgrabber.ArmorStandGrabberAddon`
- package root: `com.arno721.armorstandgrabber`

Do not rename the mod or package during this feature to avoid unnecessary Meteor configuration and addon identity breakage.

Target environment remains the repository's current toolchain: Minecraft 1.21.11, Java 21, Fabric Loader/Fabric API, Meteor Client, and Yarn mappings.

## 4. Module Layout

The same JAR exposes three independent Meteor modules under the existing category:

```text
Armor Stand
├─ Armor Stand Grabber
├─ Chest Stealer
└─ Inventory Cleaner
```

Suggested code organization:

```text
com.arno721.armorstandgrabber
├─ modules
│  ├─ ArmorStandGrabber.java
│  ├─ ChestStealer.java
│  └─ InventoryCleaner.java
├─ chest
│  ├─ ChestStealSession.java
│  ├─ ContainerDetector.java
│  ├─ SelectionPlanner.java
│  ├─ ChestAuraController.java
│  └─ SilentScreenController.java
├─ inventory
│  ├─ InventoryActionScheduler.java
│  ├─ InventoryAction.java
│  ├─ InventorySnapshot.java
│  ├─ SlotRef.java
│  ├─ SlotResolver.java
│  ├─ CleanupPlan.java
│  ├─ CleanupPlanGenerator.java
│  ├─ ItemCategorizer.java
│  ├─ ItemFacet.java
│  ├─ ItemPacker.java
│  └─ ItemConstraints.java
├─ runtime
│  ├─ ClientRuntime.java
│  ├─ InventoryMutex.java
│  ├─ RotationCoordinator.java
│  └─ RuntimeReset.java
└─ mixins
   ├─ ScreenMixin.java
   └─ HandledScreenMixin.java
```

Optional interaction/mouse mixins are only added if public Fabric/Minecraft hooks cannot satisfy the required semantics.

## 5. Shared Runtime and Ownership

A single `ClientRuntime` receives Fabric client lifecycle/tick callbacks and coordinates all three modules. Module code should not register competing low-level inventory handlers independently when those handlers can conflict.

### 5.1 Inventory ownership

`InventoryMutex` prevents incompatible slot transactions from overlapping. Priority is:

1. Armor Stand Grabber active retrieval
2. Chest Stealer active container session
3. Standalone Inventory Cleaner

The mutex protects transaction execution, not planning. Chest Stealer may call the same cleanup planner used by Inventory Cleaner while Chest Stealer owns the inventory transaction channel.

If a lower-priority module is already in the middle of an atomic multi-click transaction, it is allowed to finish only the minimum safe sequence required to leave the cursor stack and handler in a consistent state; it may not begin a new transaction before yielding ownership.

### 5.2 Rotation ownership

`RotationCoordinator` arbitrates server/client rotation requests:

1. Armor Stand Grabber rotation request
2. ChestAura rotation request

Inventory Cleaner does not acquire rotation ownership.

Existing Armor Stand rotation algorithms and settings remain unchanged. The refactor only extracts their final application path into a shared backend so two modules do not send contradictory look updates during the same tick.

## 6. Chest Stealer Settings and Behavior

### 6.1 Constraints

Chest Stealer owns an independent inventory-constraint group with pinned defaults:

- StartDelay: 1..2 ticks
- ClickDelay: 2..4 ticks
- CloseDelay: 1..2 ticks
- MissChance: 0..0 percent
- Requires: NoMovement, NoRotation, NotUsingItem, NotBreaking, NotDuringCombat

Container constraints do not expose the player-inventory-only `InventoryOpen` requirement.

Each range is sampled per relevant action/wait, not once for the entire session.

`MissChance` is represented only by a safe no-op delay cycle: when a miss is selected, no slot click is issued, the configured click delay is observed, then the state is revalidated and replanned. A miss must never intentionally mutate a slot or cursor stack.

### 6.2 Top-level settings

- AutoClose: true
- SelectionMode: Distance / Index / Random, default Distance
- MoveMode: QuickMove / DragAndDrop, default QuickMove
- QuickSwaps: true
- OnFull: None / Throw, default Throw
- CheckScreenHandlerType: enabled
- CheckScreenTitle: enabled
- Aura: enabled
- SilentScreen: disabled

The incomplete `PutBack` option from the reference revision is not exposed.

### 6.3 Selection modes

Distance:

- StartItem: Default / Random / MaxSlot / MinSlot
- RandomFactor: default 1.0..1.0, allowed 0.25..2.0
- Choose a start candidate, then repeatedly choose the next candidate by shortest slot-space distance multiplied by a sampled random factor.

Index:

- Order: Ascending / Descending, default Ascending
- Sort by container slot index.

Random:

- Shuffle eligible container candidates for the current plan.

### 6.4 Move modes

QuickMove uses Minecraft's normal quick-move/shift-click path.

DragAndDrop uses pickup-based movement and may place into one or more empty/mergeable player slots. It must minimize unnecessary clicks and return any remainder to the original container slot when the player inventory cannot accept the entire stack.

### 6.5 Quick swaps

Quick swaps are derived from the cleanup plan and only target eligible hotbar/offhand destinations.

- Empty target: swap container item directly into target.
- Useful occupied target: relocate the existing useful item to an empty inventory slot, then swap the container item into the target.
- Useless occupied normal hotbar target: dispose of the old item if policy allows, then swap.
- Offhand target: do not force a pre-throw step; use a direct swap path.

After any quick-swap transaction, discard the old plan and regenerate from a fresh inventory/container snapshot.

### 6.6 Full inventory behavior

OnFull=None: do not dispose of player inventory; leave non-transferable container items untouched.

OnFull=Throw: ask the cleanup plan for discardable player items. Dispose of one safe candidate, wait for state update, then regenerate the plan before taking another container item.

### 6.7 Container filters

Handler-type checking is enabled by default and initially allows the reference revision's standard chest/shulker handler classes (generic 9x3, generic 9x6, and shulker-box equivalents under Yarn mappings).

Title checking is enabled by default and recognizes the reference defaults:

- Chest
- Large Chest
- Shulker Box
- Barrel
- Chest Minecart
- Chest Boat

Support custom title strings and whitelist/blacklist filtering.

## 7. ChestAura

ChestAura is a toggleable child feature of Chest Stealer and defaults to enabled.

Pinned settings:

- Range: 3.0, allowed 1.0..6.0
- WallRange: 0.0, allowed 0.0..6.0 and clamped to Range
- Delay: 5 ticks, allowed 1..80
- SwingMode: `DoNotHide` (pinned reference enum value `DO_NOT_HIDE`)
- NotDuringCombat: true
- TrackManualInteractions: true
- PauseOn: Combat / UsingItem, default empty
- AwaitContainer: enabled
  - Timeout: 10 ticks
  - MaxRetries: 4
- ValidStorageBlocks: configurable set matching the pinned default construction
- Rotations: dedicated ChestAura rotation settings

`SwingMode=DoNotHide` means a successful ChestAura block interaction performs the normal visible main-hand swing animation rather than suppressing the client swing.

### 7.1 Storage discovery

Search outward from player eye position, sort candidates by distance, and reject:

- already interacted block positions
- blocks not in the configured storage set
- blocked vanilla chests
- targets for which no valid interaction/raycast point exists under Range/WallRange

The default storage set is constructed from registry entries whose identifier/path naming ends with the reference suffix families `CHEST`, `SHULKER_BOX`, `BARREL`, or `FURNACE`, plus explicit Brewing Stand, Dispenser, and Hopper entries. This intentionally captures vanilla variants such as trapped chests, colored shulker boxes, blast furnaces, and smokers when their registry naming matches those families. The final opened GUI still has to pass Chest Stealer handler/title filters before stealing begins.

### 7.2 Rotation and interaction

For a candidate storage block:

1. find a valid raycast-capable rotation
2. request it from `RotationCoordinator`
3. re-raycast using the current server-oriented rotation before interaction
4. interact through Minecraft's normal block-interaction path only if the raycast resolves to the intended block

Do not interact first and repair rotation afterward.

### 7.3 Await/retry

When AwaitContainer is enabled, a successful block interaction starts a wait window. If a compatible container screen appears within Timeout, mark the target successful. Otherwise retry up to MaxRetries. After success or retry exhaustion, mark the block as interacted and move on.

When AwaitContainer is disabled, track the block immediately after successful interaction and wait the configured Delay before seeking the next target.

### 7.4 Manual tracking and double chests

Manual block interactions are observed so the Aura does not immediately reopen a storage block the player just interacted with.

When a double chest half is tracked, also track the paired half. Clear target, retries, and interacted-block state when Aura/Chest Stealer is disabled or the world/session resets.

### 7.5 Rotation settings

The pinned ChestAura rotation group is non-combat-specific and exposes:

- AngleSmooth: Linear / Sigmoid / Acceleration
- MovementCorrection: Silent
- ResetThreshold: 2 degrees
- TicksUntilReset: 5

For this addon, `MovementCorrection=Silent` means the rotation backend may send the server-facing yaw/pitch required for ChestAura while leaving the visible client camera orientation unchanged; player movement input remains based on the visible local orientation and is not visibly snapped toward the chest target.

Linear defaults to 180..180 horizontal and vertical turn speed.

Sigmoid exposes horizontal/vertical turn speed plus Steepness=10 and Midpoint=0.3.

Acceleration exposes the reference-aligned yaw/pitch acceleration ranges and its DynamicAccel, AccelerationError, ConstantError, and optional SigmoidDeceleration subgroups. The Java implementation reproduces the behavior independently rather than translating source code.

## 8. SilentScreen

SilentScreen is a Chest Stealer child feature and defaults to disabled.

Settings:

- UnlockCursor: false
- DrawInventoryTag: enabled
  - Background: Rect / Texture, default Rect
  - Rect settings: fill color, outline color, Margin=2.0
  - Scale: 1.5, allowed 0.25..4.0
  - RenderOffset: vec3, default zero
  - ShowTitle: false

### 8.1 Screen preservation

SilentScreen must keep the real container screen and screen handler alive. It must not implement hiding by simply calling `setScreen(null)`.

When a current screen is eligible for Chest Stealer and SilentScreen is enabled:

- suppress normal container GUI rendering
- suppress manual GUI slot-click input
- keep the underlying screen handler, sync id, cursor stack, and slot mapping intact
- allow the internal inventory scheduler to continue using the handler

### 8.2 Cursor behavior

`UnlockCursor=false` uses game-style mouse capture while the container is visually hidden so the player can continue normal camera control. `UnlockCursor=true` keeps/releases a normal GUI cursor while the container remains hidden. Leaving SilentScreen, closing the container, disabling the module, disconnecting, or changing worlds must restore the cursor mode appropriate to the newly active vanilla screen/game state.

### 8.3 Mixins

Two mixins are part of the baseline design:

- `ScreenMixin`: cancel rendering/extraction for hidden eligible container screens
- `HandledScreenMixin`: cancel manual GUI slot-click paths while hidden

A mouse/cursor mixin is conditional. Prefer public Minecraft/Fabric mouse APIs first; add the mixin only if they cannot reproduce UnlockCursor and reliable restoration semantics on 1.21.11.

### 8.4 Inventory tag

Track the last interacted storage `BlockPos`, project its center into screen space, and draw live container stacks from the active handler. For double chests, center the tag between the two halves. `RenderOffset` applies before world-to-screen projection.

The tag must update from current handler state while stealing; it is not a static snapshot.

## 9. Inventory Cleaner

Inventory Cleaner is an independent Meteor module. It may also act as a planning provider to Chest Stealer.

Pinned settings:

- MaximumBlocks: 512
- MaximumArrows: 128
- MaximumThrowables: 64
- MaximumFoodPoints: 200
- MaximumWaterBuckets: 2
- MaximumLavaBuckets: 2
- MaximumMilkBuckets: 2
- ItemsBlacklist: empty
- Greedy: true, compatibility no-op for this pinned revision
- OffHandItem: Shield
- SlotItem-1: Weapon
- SlotItem-2: Bow
- SlotItem-3: Pickaxe
- SlotItem-4: Axe
- SlotItem-5: None
- SlotItem-6: Potion
- SlotItem-7: Food
- SlotItem-8: Block
- SlotItem-9: Block

Player inventory constraints also expose `InventoryOpen` in addition to the generic requirement set.

### 9.1 Sort choices

Support the pinned reference categories:

- Sword
- Weapon
- Spear
- Mace
- Bow
- Crossbow
- Axe
- Pickaxe
- Shovel
- Hoe
- Rod
- Shield
- Water
- Lava
- Milk
- Pearl
- Gapple
- Food
- Potion
- Block
- Throwables
- Ignore
- None

`Ignore` marks the target slot untouchable by Cleaner. `None` means no specific target category and does not create the same protection.

### 9.2 Item facets

A single ItemStack may expose multiple facets. For example an axe may be both a weapon candidate and an axe/tool candidate. Planning operates on facets rather than a single one-category-per-item classification.

Primary types include:

- Armor
- Sword
- Weapon
- Spear
- Mace
- Bow
- Crossbow
- Arrow
- Tool
- Rod
- Throwable
- Shield
- Food
- Bucket
- Pearl
- Gapple
- Potion
- Block
- None

Each type has an allocation priority so specialized target slots can be filled before more generic roles when necessary.

### 9.3 Comparison rules

Weapon scoring considers attack damage, attack speed, the pinned reference's relevant enchantment weights, durability, enchantability, and hotbar preference. It uses Minecraft item/component data rather than display-name heuristics.

Mining-tool scoring considers effective mining speed, Silk Touch, Unbreaking, Fortune, durability, and hotbar preference.

Armor scoring evaluates the best available current armor combination and also retains armor pieces that are useful in the reference algorithm's future full-diamond-armor comparison. Inventory Cleaner does not equip armor.

Food scoring prioritizes enchanted golden apples, then golden apples, then compares saturation-to-nutrition ratio, nutrition, saturation, preferred stack-size behavior, hotbar preference, and a deterministic tie breaker. `MaximumFoodPoints` is a nutrition-value constraint (`stack count × nutrition`), not an item-count limit.

Potion classification uses the same beneficial-effect membership set as the pinned `PotionItemFacet.GOOD_STATUS_EFFECTS`: an item receives the normal Potion facet only when every contained potion effect belongs to that verified pinned allow-set. The Java port will enumerate those effect registry keys explicitly from the pinned reference during implementation; no name, lore, or localization heuristic is permitted.

Block classification behaviorally matches the pinned `ScaffoldBlockItemSelection` validity/unfavourable decisions for vanilla block stacks, but is implemented independently inside `BlockUsefulnessEvaluator`; it does not import or translate Scaffold source. Nonqualifying block stacks fall back to a generic/non-useful facet rather than automatically receiving the Block role.

### 9.4 Amount constraints

The pinned reference semantics are target/minimum-style at stack granularity, not exact hard caps. A stack may push the retained total above the configured value when that stack is needed to satisfy the desired amount. Do not split stacks merely to hit the numeric setting exactly.

Constraints include block count, arrow count, throwable count, food value, bucket subtype counts, and a single weapon-like functional requirement where applicable.

### 9.5 Blacklist

Blacklist filtering happens before normal candidate allocation. Blacklisted items are never selected merely because they satisfy a normal category. In standalone Cleaner they are explicit disposal candidates.

### 9.6 Greedy

The setting remains visible and defaults to true because it exists in the pinned reference revision. The pinned revision does not implement its effective greedy-check path, so the clean-room compatibility implementation intentionally treats it as a no-op. The setting tooltip should state that it is retained for pinned-revision compatibility and currently has no behavioral effect.

### 9.7 Cleanup plan

`CleanupPlan` contains:

- useful item slots
- planned hotbar/offhand swaps
- groups of mergeable partial stacks

The generator:

1. filters blacklist candidates
2. categorizes all available items into facets
3. groups facets by category
4. processes categories in descending allocation priority
5. selects useful items under amount constraints
6. allocates requested hotbar/offhand targets
7. groups compatible partial stacks for possible merging
8. marks protected/forbidden slots as useful

A physical slot may not be allocated to more than one incompatible target role.

### 9.8 Standalone execution order

Inventory Cleaner processes one logical operation at a time in this order:

1. hotbar/offhand swap
2. stack merge
3. disposal

After each state-changing operation, refresh the snapshot and regenerate the plan.

Stack identity for merging must include item identity plus relevant data components so incompatible stacks are never merged.

## 10. Chest Stealer + Cleaner Integration

If Inventory Cleaner is disabled:

- all eligible container items are treated as useful
- Chest Stealer behaves as a take-all module

If Inventory Cleaner is enabled:

- planning input is player inventory plus current container slots
- the same CleanupPlanGenerator determines useful container items, hotbar swaps, merge opportunities, and discardable player items
- Chest Stealer executes container-oriented actions from that plan

There is one shared planner implementation, not separate duplicated classifiers.

## 11. Inventory Platform Layer

### 11.1 Vanilla interaction path

Prefer `ClientPlayerInteractionManager`/equivalent Yarn-mapped vanilla interaction methods for slot actions. The scheduler chooses the action; Minecraft produces the appropriate network packet and revision/state payload.

Avoid hand-constructing click-slot packets unless a required operation has no safe public path.

If direct packet construction becomes unavoidable, isolate it behind `MinecraftInventoryBridge`; module/planner classes must not reference packet classes directly.

### 11.2 Slot abstraction

Never treat player inventory indices as server handler slot IDs.

Use typed `SlotRef` variants (container, main inventory, hotbar, armor, offhand) and resolve them against the current `ScreenHandler` immediately before execution.

### 11.3 Handler session token

Every scheduled action is bound to a `HandlerSession` containing at minimum:

- screen identity
- handler identity
- sync id
- handler type
- slot count
- monotonically changing local generation token

If any required identity changes before execution, reject the queued action as stale.

## 12. Scheduler and Transaction Safety

The scheduler follows:

```text
IDLE
→ WAIT_START_DELAY
→ VALIDATE
→ EXECUTE
→ WAIT_CLICK_DELAY
→ AWAIT_STATE_CHANGE
→ REPLAN
```

Auto-close uses:

```text
PLAN_EMPTY
→ WAIT_CLOSE_DELAY
→ VALIDATE_EMPTY_AGAIN
→ CLOSE
```

A close is allowed only when the same handler/session is active, no useful container item remains, no transaction is pending, and the cursor stack is in a safe state.

### 12.1 Snapshot discipline

Use `snapshot → plan → one logical transaction → fresh snapshot → replan`.

A logical transaction may contain a small atomic sequence such as:

- relocate useful hotbar occupant then swap container item
- pickup source, merge into one or more targets, return remainder

Do not queue an entire chest's clicks from a stale snapshot.

### 12.2 Cursor safety

Multi-click transactions must track cursor-stack preconditions and expected safe completion. On screen/handler replacement, stop sending old-handler clicks. Do not invent an arbitrary emergency slot and risk overwriting items.

Module disable may finish only the minimal safe tail of an already-started atomic transaction, then releases ownership without starting further work.

## 13. Activity Requirements

Provide a lightweight `PlayerActivityTracker` for requirements used by this addon:

- movement
- rotation stability
- item use
- block breaking
- combat activity

This replaces dependencies on LiquidBounce's combat/activity managers.

For this addon, `combatActive` is deterministic and local: set/refresh a 100-tick (5-second at 20 TPS) countdown when the local player either (a) successfully attacks a living entity through the client interaction path, or (b) receives damage whose source has a living-entity attacker. `combatActive` remains true while that countdown is above zero. Clear it on disconnect, world change, death/respawn session replacement, or runtime reset. Environmental damage with no living-entity attacker does not activate combat. This is the explicit compatibility boundary used by `NotDuringCombat` and `PauseOn Combat`; it is not claimed to reproduce every internal LiquidBounce `CombatManager` signal.

`NoMovement` passes only when movement input has zero horizontal movement and jump is not pressed. `NotUsingItem` reflects the player's current item-use state. `NotBreaking` reflects the client interaction manager's active block-breaking state. `NoRotation` passes only when the rotation coordinator reports no change from the previously accepted server-facing yaw/pitch for the current tick window.

## 14. Lifecycle Reset

On disconnect, world change, null player/network handler, death/respawn player replacement, or explicit runtime reset:

- clear scheduler queues
- release inventory ownership
- release rotation ownership
- reset ChestStealSession
- clear ChestAura target/retry/interacted state
- disable SilentScreen hidden state and restore cursor mode
- discard Cleaner's current snapshot/plan
- clear PlayerActivityTracker timers

No handler ids, pending clicks, world block positions, combat timers, or rotation targets survive across worlds/servers.

## 15. Mixin Budget

Baseline mixins:

- `ScreenMixin`: suppress rendering for eligible hidden container screens
- `HandledScreenMixin`: suppress user-generated container slot clicks while hidden

Conditional mixins, only if public APIs cannot provide equivalent semantics:

- interaction-manager observer for final block-interaction tracking
- mouse/cursor hook for UnlockCursor and reliable restoration

Mixins remain bridge/hooks only. Chest Stealer and Inventory Cleaner logic stays in normal Java classes.

Add the mixin configuration to `fabric.mod.json` and update the description to reflect the multi-module addon while retaining the same mod id and entrypoint.

## 16. Error Handling

All actions validate their context immediately before execution. Abort/replan on:

- screen replacement
- handler/sync-id replacement
- player/world/network loss
- invalid resolved slot
- container no longer eligible
- target block unloading/disappearing
- cursor stack violating transaction preconditions
- rotation ownership loss

ChestAura does not mark a target permanently successful merely because an interaction method returned success; AwaitContainer controls confirmation when enabled.

No component claims bypass, stealth, or guaranteed multiplayer acceptance. The server remains authoritative.

## 17. Testing

Existing JUnit 5 support is reused.

Pure/planner tests:

- `SelectionPlannerTest`
  - Distance ordering
  - random-factor boundaries
  - start-item modes
  - Index ascending/descending
- `InventoryCapacityTest`
  - empty slots
  - compatible partial stacks
  - partial transfer capacity
- `ItemCategorizerTest`
  - weapons
  - tools and subtypes
  - armor
  - food value
  - buckets
  - throwable
  - potion filtering
  - blacklist
- `CleanupPlanGeneratorTest`
  - useful-item selection
  - amount constraints
  - target-slot allocation
  - Ignore vs None
  - no duplicate physical-slot allocation
- `StackMergePlannerTest`
- `QuickSwapPlannerTest`
  - empty target
  - useful occupied target
  - useless occupied target
  - offhand
- `InventoryActionSchedulerTest`
  - delay sampling
  - stale session rejection
  - mutex ownership
  - cancellation
  - fresh-plan requirement
  - MissChance no-op path
- `ChestAuraTrackerTest`
  - timeout/retry
  - double-chest tracking
  - manual interaction tracking
- `PlayerActivityTrackerTest`
  - attack starts/refreshes 100-tick combat window
  - entity-caused received damage starts/refreshes it
  - environmental damage does not
  - lifecycle reset clears it

Build gates:

```text
./gradlew test
./gradlew build
```

Manual/integration validation matrix includes single/double chest, barrel, shulker, blocked/out-of-range/wall interactions, both move modes, Cleaner on/off, full inventory with both OnFull modes, QuickSwap, stack merge, blacklist, SilentScreen/cursor/tag, Aura retry/manual interaction, disconnect/disable mid-session, and concurrency with Armor Stand Grabber.

Unit tests validate deterministic logic and safety invariants; they do not prove behavior on every multiplayer server.

## 18. Version and Git Flow

Target version: v0.4.0.

Development flow:

```text
main
→ feature/chest-stealer-inventory-cleaner
→ approved design spec
→ implementation plan
→ implementation commits
→ tests/build
→ pull request
→ merge
→ release artifact
```

No implementation work begins until this written spec is reviewed and approved, followed by a separate implementation plan.

## 19. Reference and License Note

Behavioral reference:

- CCBlueX/LiquidBounce
- branch family: `nextgen`
- pinned commit: `141631789e5aeb7851562052dca567ab49716e99`

The repository is GPL-3.0-only. The implementation strategy remains an independently structured Java rewrite informed by public behavior/settings, rather than reproduction of LiquidBounce source text or framework internals.

## 20. Acceptance Criteria

The feature is design-complete when the implementation satisfies all of the following:

- Existing Armor Stand Grabber behavior remains available in the same JAR.
- Chest Stealer and Inventory Cleaner are separate Meteor modules.
- Chest Stealer settings and decision behavior match the pinned reference within the scope boundary.
- ChestAura can discover, rotate toward, interact with, retry, and track configured storage blocks.
- SilentScreen hides eligible container GUI rendering without destroying the active handler and blocks manual hidden-slot clicks.
- Inventory Cleaner exposes the pinned hotbar/offhand and amount settings and produces deterministic cleanup plans.
- Chest Stealer uses Cleaner plans only when Cleaner is enabled; otherwise it takes all eligible items.
- Inventory transactions never intentionally operate on stale handler sessions.
- Competing addon modules do not issue conflicting inventory or rotation operations in the same ownership window.
- Disconnect/world-change/module-disable paths clear transient runtime state safely.
- Unit tests pass and the project builds successfully on the repository's target toolchain.
