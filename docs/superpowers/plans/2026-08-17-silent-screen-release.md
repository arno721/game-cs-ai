# SilentScreen + v0.4.0 Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the approved SilentScreen child feature, including hidden-container rendering/input behavior, cursor handling, a live world-anchored inventory tag, and final v0.4.0 release verification/documentation for the combined Armor Stand Grabber + Chest Stealer + Inventory Cleaner addon.

**Architecture:** Keep the actual `HandledScreen` and `ScreenHandler` alive while hiding only their visual/manual-interaction surface. `SilentScreenController` owns lifecycle/cursor/pass-through state; tiny client mixins delegate decisions to a static `SilentScreenHooks` bridge and never own module state themselves. The inventory tag renders from the live handler through Meteor's 2D render event after projecting the last interacted storage position into screen space. Release work happens only after all feature tests/builds are green.

**Tech Stack:** Java 21, Minecraft 1.21.11, Yarn 1.21.11+build.3, Fabric Loader 0.18.2, Fabric API 0.141.4+1.21.11, Fabric Loom 1.14-SNAPSHOT, Meteor Client 1.21.11-SNAPSHOT, Mixin, JUnit Jupiter 5.11.4, Gradle 9.2.0.

## Global Constraints

- Requires completed Runtime Foundation, Inventory Cleaner, Chest Stealer Core, and ChestAura plans.
- SilentScreen is a child of Chest Stealer and defaults disabled.
- `UnlockCursor=false` by default.
- `DrawInventoryTag=true` by default.
- Background choices are exactly Rect and Texture, default Rect.
- Rect defaults: fill ARGB `0x80000000`, outline ARGB `0x00000000`, Margin=2.0; margin allowed 0..100.
- Scale defaults 1.5 and is allowed 0.25..4.0.
- RenderOffset defaults `(0,0,0)`.
- ShowTitle defaults false.
- SilentScreen must never hide a container by calling `setScreen(null)`.
- The current `HandledScreen`, `ScreenHandler`, sync id, cursor stack, and slot mapping remain intact while hidden.
- Manual GUI slot interactions are suppressed while hidden; internal Chest Stealer actions through the shared inventory executor remain enabled.
- `UnlockCursor=false`: use game-style cursor lock and allow normal camera/movement input while the hidden screen remains current.
- `UnlockCursor=true`: leave/release a normal GUI cursor but still suppress manual container-slot operations.
- Leaving hidden state restores screen pass-through/cursor state appropriate to the newly active vanilla screen/game state.
- Inventory tag uses live container stacks from the current handler and updates as stealing progresses.
- Tag anchor is the last interacted block plus RenderOffset; double chest anchor is the midpoint between both halves.
- Final release version is `0.4.0`; Minecraft/toolchain versions remain unchanged.
- Do not add anti-cheat bypass claims or behavior.
- Clean-room Java implementation only; do not copy LiquidBounce mixins/render source.

---

### Task 1: Add client Mixin bootstrap and static SilentScreen hook bridge

**Files:**
- Create: `src/main/resources/armor-stand-grabber.mixins.json`
- Modify: `src/main/resources/fabric.mod.json`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/SilentScreenHooks.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/mixins/ScreenMixin.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/mixins/HandledScreenMixin.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/mixins/ScreenPassEventsAccessor.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/SilentScreenHooksTest.java`

**Interfaces:**
- `SilentScreenHooks.install(SilentScreenController controller)` installs exactly one live controller reference.
- `SilentScreenHooks.clear(controller)` removes it only when the same controller owns the hook.
- `SilentScreenHooks.shouldHide(Screen screen)` and `shouldSuppressManualContainerInput(Screen screen)` are side-effect-free queries used by mixins.
- `ScreenPassEventsAccessor` exposes the protected `Screen.passEvents` state for save/restore.

- [ ] **Step 1: Write failing hook-lifecycle tests**

Use a controller-query interface so unit tests do not construct Minecraft screens:

```java
@Test
void hookInstallationAndClearAreOwnerSafe() {
    SilentScreenHooks.resetForTests();
    SilentScreenVisibility first = screen -> true;
    SilentScreenVisibility second = screen -> false;

    SilentScreenHooks.installVisibility(first);
    assertTrue(SilentScreenHooks.queryVisibility(null));
    SilentScreenHooks.clearVisibility(second);
    assertTrue(SilentScreenHooks.queryVisibility(null));
    SilentScreenHooks.clearVisibility(first);
    assertFalse(SilentScreenHooks.queryVisibility(null));
}
```

Production `SilentScreenController` implements `SilentScreenVisibility`; test-only helpers remain package-private.

- [ ] **Step 2: Run and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.SilentScreenHooksTest
```

Expected: hook types do not exist.

- [ ] **Step 3: Add Mixin config**

`src/main/resources/armor-stand-grabber.mixins.json`:

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.arno721.armorstandgrabber.mixins",
  "compatibilityLevel": "JAVA_21",
  "client": [
    "ScreenMixin",
    "HandledScreenMixin",
    "ScreenPassEventsAccessor"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

Add to `fabric.mod.json` at top level:

```json
"mixins": ["armor-stand-grabber.mixins.json"]
```

Keep the existing meteor entrypoint and dependencies unchanged.

- [ ] **Step 4: Implement render/input delegation mixins**

`ScreenMixin` targets `Screen` and injects at HEAD of the 1.21.11 `render` method, cancellable. When `SilentScreenHooks.shouldHide((Screen)(Object)this)` is true, cancel normal screen rendering.

`HandledScreenMixin` targets `HandledScreen<?>` and injects at HEAD into manual container-input methods available in the 1.21.11 Yarn class:

- mouseClicked
- mouseReleased
- mouseDragged
- mouseScrolled

When `shouldSuppressManualContainerInput` is true, return `false` without invoking vanilla slot interaction. Also intercept the handled-screen keyboard path so hotbar/drop/clone actions cannot operate on a hidden hovered slot; allow only vanilla close keys (`Escape` and the bound inventory-key close path) to reach the screen. Movement key state is handled through `passEvents`, not by forwarding slot-key handlers.

`ScreenPassEventsAccessor`:

```java
@Mixin(Screen.class)
public interface ScreenPassEventsAccessor {
    @Accessor("passEvents") boolean armorStandGrabber$getPassEvents();
    @Accessor("passEvents") void armorStandGrabber$setPassEvents(boolean value);
}
```

Do not inject into Chest Stealer's internal `clickSlot` path; mixins affect manual screen methods only.

- [ ] **Step 5: Run hook test and Mixin compile gate**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.SilentScreenHooksTest
gradle build
```

Expected: mixin targets/signatures validate against Minecraft 1.21.11 during build.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/armor-stand-grabber.mixins.json \
        src/main/resources/fabric.mod.json \
        src/main/java/com/arno721/armorstandgrabber/chest/SilentScreenHooks.java \
        src/main/java/com/arno721/armorstandgrabber/mixins \
        src/test/java/com/arno721/armorstandgrabber/chest/SilentScreenHooksTest.java
git commit -m "feat: add silent screen mixin hooks"
```

---

### Task 2: Implement hidden-screen lifecycle, pass-through input, and cursor restoration

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/SilentScreenController.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/CursorMode.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/SilentScreenState.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/SilentScreenStateTest.java`

**Interfaces:**
- `SilentScreenController` owns hidden-screen entry/exit and implements `SilentScreenVisibility`.
- `boolean shouldHide(Screen screen)` is true only when parent Chest Stealer active, SilentScreen enabled, and the exact current screen is eligible through the shared `ContainerDetector`.
- `onRuntimeTick()` updates state even though the Chest Stealer screen remains current.
- `reset()` always restores passEvents/cursor state before clearing local state.

- [ ] **Step 1: Write failing pure state-transition tests**

```java
@Test
void enteringAndLeavingHiddenStatePreserveOriginalPassEvents() {
    SilentScreenState state = new SilentScreenState();
    state.enter(false, false);
    assertTrue(state.hidden());
    assertFalse(state.originalPassEvents());
    assertEquals(CursorMode.LOCKED, state.requestedCursorMode());

    SilentScreenState.Restore restore = state.leave(true);
    assertFalse(restore.passEvents());
    assertEquals(CursorMode.GUI, restore.cursorMode());
}

@Test
void unlockCursorRequestsGuiModeWhileHidden() {
    SilentScreenState state = new SilentScreenState();
    state.enter(true, false);
    assertEquals(CursorMode.GUI, state.requestedCursorMode());
}
```

`leave(boolean anotherScreenActive)` requests GUI cursor when another screen is active, otherwise game-locked cursor.

- [ ] **Step 2: Run and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.SilentScreenStateTest
```

- [ ] **Step 3: Implement enter/exit behavior**

On transition from visible to hidden:

1. save the exact `Screen` instance
2. read and save original `passEvents` through `ScreenPassEventsAccessor`
3. set `passEvents=true`
4. if UnlockCursor=false call the 1.21.11 public mouse cursor-lock API; if true call the public cursor-unlock API
5. do **not** replace `mc.currentScreen`

While hidden, ensure the same screen remains eligible; if screen object, handler, world, or parent module changes, leave hidden state before any other reset action.

On transition out:

1. if the previously hidden screen is still the current screen, restore its original `passEvents`
2. if another vanilla screen is current, request unlocked GUI cursor
3. if no screen is current, request locked game cursor
4. clear cached screen/pass state

Use the public `Mouse` lock/unlock methods first. Do not add a mouse mixin unless compilation/runtime validation proves those APIs cannot reproduce the required cursor mode; such a result is treated as a discovered integration defect and must be fixed within this task before completion, not deferred.

- [ ] **Step 4: Install controller hooks and lifecycle reset**

Controller constructor installs itself:

```java
SilentScreenHooks.install(this);
```

On addon/runtime teardown or replacement call:

```java
controller.reset();
SilentScreenHooks.clear(controller);
```

Chest Stealer disable, SilentScreen disable, disconnect, world/player replacement, or handler close all call `reset()`.

- [ ] **Step 5: Verify tests/build and commit**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.SilentScreenStateTest
gradle build
```

```bash
git add src/main/java/com/arno721/armorstandgrabber/chest/SilentScreenController.java \
        src/main/java/com/arno721/armorstandgrabber/chest/CursorMode.java \
        src/main/java/com/arno721/armorstandgrabber/chest/SilentScreenState.java \
        src/test/java/com/arno721/armorstandgrabber/chest/SilentScreenStateTest.java
git commit -m "feat: manage silent screen lifecycle"
```

---

### Task 3: Track the inventory-tag world anchor, including manual opens and double chests

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/StorageInteractionAnchor.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraController.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/chest/SilentScreenController.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/StorageInteractionAnchorTest.java`

**Interfaces:**
- Shared `StorageInteractionAnchor` stores the last interacted `BlockPos` independently of whether Aura is enabled.
- `anchorPosition(partnerResolver, renderOffset)` returns block center for single storage or midpoint between double-chest halves, then applies offset.
- Existing single `UseBlockCallback` delegates both Aura manual tracking and anchor recording; it remains observational and returns `ActionResult.PASS`.

- [ ] **Step 1: Write failing anchor-math tests**

```java
@Test
void singleStorageUsesBlockCenterPlusOffset() {
    StorageInteractionAnchor anchor = new StorageInteractionAnchor();
    anchor.record(new BlockPos(10, 64, 20));
    Vec3d result = anchor.worldAnchor(pos -> Optional.empty(), new Vec3d(1, 2, 3)).orElseThrow();
    assertEquals(new Vec3d(11.5, 66.5, 23.5), result);
}

@Test
void doubleChestUsesMidpointBetweenBlockCenters() {
    StorageInteractionAnchor anchor = new StorageInteractionAnchor();
    anchor.record(new BlockPos(0, 0, 0));
    Vec3d result = anchor.worldAnchor(
        pos -> Optional.of(new BlockPos(1, 0, 0)),
        Vec3d.ZERO
    ).orElseThrow();
    assertEquals(new Vec3d(1.0, 0.5, 0.5), result);
}
```

- [ ] **Step 2: Run and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.StorageInteractionAnchorTest
```

- [ ] **Step 3: Implement shared anchor ownership**

`StorageInteractionAnchor` API:

```java
public final class StorageInteractionAnchor {
    public void record(BlockPos pos) { ... }
    public Optional<BlockPos> blockPos() { ... }
    public Optional<Vec3d> worldAnchor(Function<BlockPos, Optional<BlockPos>> partnerResolver, Vec3d offset) { ... }
    public void clear() { ... }
}
```

ChestAura records its accepted automatic interaction target before/when the normal interaction is sent. The global `UseBlockCallback` records all local block-use hit positions while Chest Stealer is active, even when Aura is disabled, so a manually opened eligible chest can still anchor SilentScreen's tag.

Do not clear anchor merely because Aura is toggled off. Clear on parent Chest Stealer disable, world/player replacement, disconnect, or after the corresponding handled screen has closed and SilentScreen is no longer hiding it.

- [ ] **Step 4: Run tests/build and commit**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.StorageInteractionAnchorTest
gradle build
```

```bash
git add src/main/java/com/arno721/armorstandgrabber/chest/StorageInteractionAnchor.java \
        src/main/java/com/arno721/armorstandgrabber/chest/ChestAuraController.java \
        src/main/java/com/arno721/armorstandgrabber/chest/SilentScreenController.java \
        src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java \
        src/test/java/com/arno721/armorstandgrabber/chest/StorageInteractionAnchorTest.java
git commit -m "feat: track silent screen world anchor"
```

---

### Task 4: Implement live inventory-tag layout and rendering

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/InventoryTagBackground.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/InventoryTagLayout.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/WorldScreenProjector.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/chest/InventoryTagRenderer.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/modules/ChestStealer.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/InventoryTagLayoutTest.java`

**Interfaces:**
- `InventoryTagBackground { Rect, Texture }`.
- `InventoryTagLayout.measure(int stackCount, boolean texture, boolean showTitle, int titleHeight)` returns immutable dimensions/row metadata.
- `InventoryTagRenderer.render(Render2DEvent event, SilentScreenController controller, ChestStealer settings)` reads live stacks from the exact active handler.
- Renderer executes only when `controller.isHidden()` and DrawInventoryTag=true.

- [ ] **Step 1: Write failing layout tests**

Use fixed constants matching Minecraft item/slot geometry:

```java
@Test
void rectUsesSixteenPixelItemsAndTextureUsesEighteenPixelSlots() {
    InventoryTagLayout rect = InventoryTagLayout.measure(9, false, false, 9);
    InventoryTagLayout texture = InventoryTagLayout.measure(9, true, false, 9);
    assertEquals(16, rect.cellSize());
    assertEquals(18, texture.cellSize());
}

@Test
void titleAddsTitleHeightAndTwoPixelGap() {
    InventoryTagLayout without = InventoryTagLayout.measure(9, false, false, 9);
    InventoryTagLayout with = InventoryTagLayout.measure(9, false, true, 9);
    assertEquals(without.height() + 11, with.height());
}
```

Use row length 9 so standard chest rows align naturally; a non-multiple final row is supported.

- [ ] **Step 2: Run and verify RED**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.InventoryTagLayoutTest
```

- [ ] **Step 3: Implement live container-stack extraction**

At render time:

1. require hidden state and same eligible `HandledScreen`
2. use `ContainerDescriptor.containerSlotIds()` from the shared detector
3. read each corresponding `Slot.getStack()` from the current handler now; do not cache item snapshots
4. preserve empty slot positions so visual layout matches container slot ordering
5. if handler identity changes during render preparation, skip the frame

- [ ] **Step 4: Implement world-to-screen projection**

`WorldScreenProjector` accepts a world position and projects using the matrices/camera available in Meteor's 1.21.11 2D render pipeline. Prefer Meteor's existing world-to-2D/nametag projection utility when present in the linked Meteor version; wrap it behind this class so no other feature depends on Meteor projection details.

The wrapper contract is exact:

```java
public Optional<Vec2d> project(Vec3d worldPos, double scale)
```

Return empty when behind the camera, outside a valid projection, world/camera absent, or projection math yields non-finite values. The render position is the projected `StorageInteractionAnchor.worldAnchor(...)` after RenderOffset is added in world space.

The implementation task is not complete until `gradle compileJava` confirms the exact Meteor 1.21.11 projection API used by the wrapper.

- [ ] **Step 5: Implement Rect and Texture rendering**

Rect mode:

- default fill `0x80000000`
- default outline `0x00000000`
- default margin `2.0`
- use configured Meteor color settings converted to ARGB
- draw fill and optional 1-pixel outline around measured content before item rendering

Texture mode:

- no enclosing Rect background
- draw Minecraft's vanilla `container/slot` GUI sprite at 18x18 for every container slot, then draw the stack centered with a one-pixel inset

Both modes:

- center the full tag on projected screen x/y
- apply configured Scale=1.5 by matrix push/scale/pop
- optionally draw the live handled-screen title centered above item rows when ShowTitle=true
- render stack count and durability/bar decorations through vanilla `DrawContext` item rendering APIs
- never mutate item/handler state from the renderer

- [ ] **Step 6: Subscribe through Chest Stealer Meteor render event**

In `ChestStealer`:

```java
@EventHandler
private void onRender2D(Render2DEvent event) {
    silentScreenController.renderInventoryTag(event);
}
```

If constructor ownership keeps the controller outside the module, expose a setter installed once during addon wiring or pass the renderer delegate at construction; do not use global mutable lookup by module name.

- [ ] **Step 7: Test/build and commit**

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.InventoryTagLayoutTest
gradle build
```

```bash
git add src/main/java/com/arno721/armorstandgrabber/chest/InventoryTagBackground.java \
        src/main/java/com/arno721/armorstandgrabber/chest/InventoryTagLayout.java \
        src/main/java/com/arno721/armorstandgrabber/chest/WorldScreenProjector.java \
        src/main/java/com/arno721/armorstandgrabber/chest/InventoryTagRenderer.java \
        src/main/java/com/arno721/armorstandgrabber/modules/ChestStealer.java \
        src/test/java/com/arno721/armorstandgrabber/chest/InventoryTagLayoutTest.java
git commit -m "feat: render silent chest inventory tag"
```

---

### Task 5: Add exact SilentScreen settings and final runtime wiring

**Files:**
- Modify: `src/main/java/com/arno721/armorstandgrabber/modules/ChestStealer.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/chest/SilentScreenController.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/chest/SilentScreenEligibilityTest.java`

**Interfaces:**
- Parent module is the single settings source.
- SilentScreen controller receives typed getters/config snapshot and the shared `StorageInteractionAnchor`.
- Addon creates exactly one controller and installs hooks once.

- [ ] **Step 1: Add settings to Chest Stealer**

Group `Silent Screen`:

```text
silent-screen = false
unlock-cursor = false
draw-inventory-tag = true
background = Rect
rect-color = #80000000
rect-outline-color = #00000000
rect-margin = 2.0 [0..100]
scale = 1.5 [0.25..4.0]
render-offset-x = 0.0
render-offset-y = 0.0
render-offset-z = 0.0
show-title = false
```

Hide Rect-specific color/margin settings unless Background=Rect. Keep DrawInventoryTag child settings visible only when DrawInventoryTag=true where Meteor setting visibility predicates support it.

- [ ] **Step 2: Write/implement exact eligibility test**

Extract pure eligibility:

```java
@Test
void silentScreenRequiresParentFeatureAndEligibleScreen() {
    assertFalse(SilentScreenController.shouldHide(false, true, true));
    assertFalse(SilentScreenController.shouldHide(true, false, true));
    assertFalse(SilentScreenController.shouldHide(true, true, false));
    assertTrue(SilentScreenController.shouldHide(true, true, true));
}
```

Run:

```bash
gradle test --tests com.arno721.armorstandgrabber.chest.SilentScreenEligibilityTest
```

- [ ] **Step 3: Wire controller after ChestAura creation**

Final construction topology:

```text
ClientRuntime
├─ ArmorStandGrabber
├─ InventoryCleaner
├─ ChestStealer
├─ ChestAuraController
└─ SilentScreenController
```

`SilentScreenController` does not need inventory/rotation ownership and therefore is not inserted into priority arbitration; its `onRuntimeTick` is called once from `ClientRuntime` as a lifecycle observer after module participants, or it implements a zero-priority runtime participant that the runtime explicitly supports for observers. Choose the former to keep `RuntimeOwner` semantics unchanged: add `ClientRuntime.registerObserver(Runnable)` and invoke observers after prioritized participant ticks.

Register:

```java
runtime.registerObserver(silentScreenController::onRuntimeTick);
```

- [ ] **Step 4: Verify disable/world-change restoration**

Add integration assertions in pure state tests for:

- SilentScreen toggle false restores passEvents and cursor
- Chest Stealer toggle false restores
- eligible screen replaced by another GUI restores original hidden screen passEvents and leaves new GUI cursor unlocked
- no current screen after close locks cursor for gameplay
- runtime reset is idempotent

- [ ] **Step 5: Full feature build gate**

```bash
gradle test
gradle build
```

Expected: all feature tests pass with mixins enabled and all three Meteor modules compile in one JAR.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/arno721/armorstandgrabber/modules/ChestStealer.java \
        src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java \
        src/main/java/com/arno721/armorstandgrabber/chest/SilentScreenController.java \
        src/test/java/com/arno721/armorstandgrabber/chest/SilentScreenEligibilityTest.java
git commit -m "feat: complete silent screen integration"
```

---

### Task 6: Bump to v0.4.0 and update user-facing metadata/documentation

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `src/main/resources/fabric.mod.json`
- Modify: `README.md`

- [ ] **Step 1: Bump only addon version**

Change:

```toml
mod-version = "0.3.0"
```

to:

```toml
mod-version = "0.4.0"
```

Do not change Minecraft/Yarn/Fabric/Meteor versions in this release task.

- [ ] **Step 2: Update mod description without changing identity**

Keep:

```json
"id": "armor-stand-grabber",
"entrypoints": {
  "meteor": ["com.arno721.armorstandgrabber.ArmorStandGrabberAddon"]
}
```

Change the description to cover the combined addon, for example:

```text
Meteor Client addon with Armor Stand Grabber, Chest Stealer, ChestAura, SilentScreen, and Inventory Cleaner automation for Minecraft 1.21.11.
```

Do not rename the mod id/package/archive base name.

- [ ] **Step 3: Rewrite README feature/version section**

Document:

- Minecraft 1.21.11 / Java 21 requirement
- same JAR contains Armor Stand Grabber, Chest Stealer, Inventory Cleaner
- Chest Stealer selection modes, move modes, QuickSwaps, OnFull, AutoClose
- ChestAura ranges/retry/manual tracking/rotation modes
- SilentScreen hidden GUI, cursor option, inventory tag
- Inventory Cleaner target slots, amount limits, blacklist, cleanup integration
- warning that server state is authoritative and multiplayer behavior depends on server/container rules
- no anti-cheat bypass/undetectability claim

- [ ] **Step 4: Verify metadata expansion**

```bash
gradle processResources
```

Inspect generated `build/resources/main/fabric.mod.json` and confirm:

```text
version = 0.4.0
minecraft = 1.21.11
java >= 21
fabric-api dependency present
meteor-client dependency present
mixins = armor-stand-grabber.mixins.json
```

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml src/main/resources/fabric.mod.json README.md
git commit -m "chore: prepare v0.4.0 metadata"
```

---

### Task 7: Final automated verification, artifact inspection, and manual-game checklist

**Files:**
- Create: `docs/testing/v0.4.0-manual-test-checklist.md`

- [ ] **Step 1: Run clean automated verification**

```bash
gradle clean test build
```

Expected: `BUILD SUCCESSFUL`; every JUnit test passes; remapped production JAR is generated under `build/libs/`.

- [ ] **Step 2: Inspect built JAR contents**

```bash
jar tf build/libs/armor-stand-grabber-0.4.0.jar
```

Confirm presence of:

```text
fabric.mod.json
armor-stand-grabber.mixins.json
com/arno721/armorstandgrabber/ArmorStandGrabberAddon.class
com/arno721/armorstandgrabber/modules/ArmorStandGrabber.class
com/arno721/armorstandgrabber/modules/ChestStealer.class
com/arno721/armorstandgrabber/modules/InventoryCleaner.class
com/arno721/armorstandgrabber/chest/ChestAuraController.class
com/arno721/armorstandgrabber/chest/SilentScreenController.class
```

If Gradle's actual archive classifier/name differs, inspect the unique non-sources production JAR in `build/libs/`; do not rename a dev/sources JAR into the release artifact.

- [ ] **Step 3: Verify embedded metadata**

```bash
unzip -p build/libs/armor-stand-grabber-0.4.0.jar fabric.mod.json
unzip -p build/libs/armor-stand-grabber-0.4.0.jar armor-stand-grabber.mixins.json
```

Confirm expanded version/dependencies and all three client mixins.

- [ ] **Step 4: Write manual-game checklist before claiming gameplay completion**

`docs/testing/v0.4.0-manual-test-checklist.md` must contain unchecked cases for at least:

```text
[ ] Existing Armor Stand Grabber Normal/Legit/Aggressive regression
[ ] Existing Armor Stand Lock/Silent rotation regression
[ ] Chest Stealer opens normal chest and steals with QuickMove
[ ] Chest Stealer DragAndDrop merges then returns remainder safely
[ ] Distance/Index/Random selection visibly execute
[ ] Cleaner inactive => Chest Stealer takes all transferable container items
[ ] Cleaner active => useful filtering and target slot planning apply
[ ] QuickSwap empty/useful/junk hotbar cases
[ ] OnFull=None leaves player inventory unchanged
[ ] OnFull=Throw discards only Cleaner-marked junk
[ ] AutoClose waits for stable empty/useful-complete state
[ ] ChestAura single chest
[ ] ChestAura double chest tracks both halves
[ ] ChestAura barrel/shulker/furnace-family configured target
[ ] ChestAura AwaitContainer retry exhaustion
[ ] ChestAura pauses for combat/use-item settings
[ ] Linear/Sigmoid/Acceleration rotation modes
[ ] Armor Stand rotation wins when both request rotation
[ ] SilentScreen keeps handler open while GUI hidden
[ ] SilentScreen UnlockCursor=false camera/movement behavior
[ ] SilentScreen UnlockCursor=true cursor behavior
[ ] Hidden screen blocks manual slot mouse/keyboard operations
[ ] Inventory tag updates live while items disappear from chest
[ ] Inventory tag single/double chest anchor and RenderOffset
[ ] Closing/disabling/world change restores cursor and screen state
[ ] Disconnect/reconnect clears runtime/mutex/session state
```

State explicitly in the checklist header: automated build/unit tests do not prove multiplayer/server compatibility; these cases require launching Minecraft 1.21.11 with the produced JAR and its required dependencies.

- [ ] **Step 5: Run final repository diff/status verification**

```bash
git status --short
git diff --check
git log --oneline --decorate -15
```

Expected: no uncommitted implementation files before final feature commit/review; `git diff --check` has no whitespace errors.

- [ ] **Step 6: Commit checklist**

```bash
git add docs/testing/v0.4.0-manual-test-checklist.md
git commit -m "test: add v0.4.0 gameplay checklist"
```

- [ ] **Step 7: Run verification one final time after all commits**

```bash
gradle clean test build
git status --short
```

Do not claim the manual-game checklist passed unless a human/game runtime has actually executed those cases. Automated completion may claim only compile/build/unit-test success.

---

## Self-Review Checklist

- Spec coverage: hidden screen preservation, render suppression, manual slot-input suppression, both cursor modes, Rect/Texture background, exact Rect defaults, Scale, RenderOffset, title, live stack rendering, double-chest midpoint, lifecycle restoration, version bump, documentation, and final verification are explicitly assigned.
- Mixin surface is minimal and client-only; module/runtime state stays outside mixins.
- No code path uses `setScreen(null)` to implement SilentScreen.
- Inventory tag reads current handler state every frame and never performs inventory mutations.
- Anchor works for manual openings even when ChestAura itself is disabled.
- v0.4.0 bump changes addon version only, not Minecraft/toolchain compatibility.
- No `TBD`, `TODO`, or deferred feature decision remains in this plan.
- Final build success is distinguished from actual in-game/server validation.