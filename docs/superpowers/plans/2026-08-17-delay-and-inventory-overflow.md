# Armor Stand Grabber Delay and Inventory Overflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-item millisecond scheduling, multiple bounded delay algorithms, and Off/Silent/Auto Open inventory overflow modes to the Minecraft 1.21.11 Armor Stand Grabber addon.

**Architecture:** Extract delay sampling into a small pure-Java helper that can be unit tested. Convert the module's one-shot equipment loop into a frame-polled session state machine that waits for inventory acknowledgements before proceeding. Overflow handling uses Meteor's existing inventory utilities and PlayerScreenHandler mappings; Auto Open only adds visible InventoryScreen lifecycle management around the same transfer logic.

**Tech Stack:** Java 21, Fabric Loom 1.14, Minecraft/Yarn 1.21.11, Meteor Client 1.21.11-SNAPSHOT, JUnit 5, GitHub Actions.

## Global Constraints

- Minecraft 1.21.11.
- Meteor Client 1.21.11-SNAPSHOT.
- Java 21.
- Do not modify Meteor Client itself.
- Preserve configurable through-wall Range targeting.
- Delay settings use milliseconds.
- Auto Open must automatically move items; no manual drag operation is required.

---

### Task 1: Delay sampling API and tests

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/delay/DelayAlgorithm.java`
- Create: `src/main/java/com/arno721/armorstandgrabber/delay/DelaySampler.java`
- Create: `src/test/java/com/arno721/armorstandgrabber/delay/DelaySamplerTest.java`
- Modify: `build.gradle.kts`

**Interfaces:**
- Produces: `DelaySampler#nextDelayMs(DelayAlgorithm algorithm, int minMs, int maxMs): long`.
- `DelaySampler` owns the small amount of state needed by Alternating and Humanized Drift.

- [ ] Add JUnit 5 test dependency and enable JUnit Platform.
- [ ] Write tests proving every algorithm stays within normalized bounds, equal min/max returns that exact value, and reversed bounds are normalized.
- [ ] Run `gradle test --tests '*DelaySamplerTest'` and verify the tests fail before the production helper exists.
- [ ] Implement the nine delay algorithms with all outputs clamped into bounds.
- [ ] Run the delay tests and verify they pass.

### Task 2: Settings and retrieval state machine

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/inventory/OverflowMode.java`
- Modify: `src/main/java/com/arno721/armorstandgrabber/modules/ArmorStandGrabber.java`

**Interfaces:**
- Consumes: `DelaySampler#nextDelayMs(...)`.
- Produces settings `min-delay`, `max-delay`, `delay-algorithm`, `overflow-mode`, and `auto-close`.
- Produces a single active retrieval session with queued `EquipmentSlot` values.

- [ ] Replace the synchronous equipment `for` loop with a session containing target, queued slots, original selected hotbar slot, next-action deadline, and current transfer state.
- [ ] Keep the first item immediate; sample a new delay after each completed item before the next interaction.
- [ ] Poll the state machine from `Render3DEvent` so millisecond settings are not restricted to 20 TPS tick resolution.
- [ ] While a session is active, cancel duplicate right-click handling instead of creating another session.
- [ ] Restore the original selected slot on completion, timeout, module disable, invalid target, or world loss.

### Task 3: Silent and Auto Open overflow transfers

**Files:**
- Modify: `src/main/java/com/arno721/armorstandgrabber/modules/ArmorStandGrabber.java`

**Interfaces:**
- Uses `InvUtils.move()` and `InvUtils.swap()` with player-inventory slot indices.
- Uses `InventoryScreen` only for `OverflowMode.AutoOpen`.

- [ ] Prefer empty hotbar slots while available.
- [ ] For `Off`, finish when no empty hotbar slot remains.
- [ ] For Silent/Auto Open, find an empty main-inventory slot (indices 9..35) and use the selected hotbar slot as a temporary buffer.
- [ ] Move the buffer's original item to the empty main slot, interact using the now-empty buffer, and wait until the retrieved item appears in the buffer.
- [ ] Move the retrieved item into the reserved main slot, which restores the original hotbar buffer item through the two-slot pickup/swap sequence.
- [ ] In Auto Open, open `InventoryScreen` only when overflow is first required; track whether the addon opened it.
- [ ] Apply `auto-close` only to a screen opened by this addon.
- [ ] Abort with a warning if no main inventory slot is available or an interaction result does not arrive before the timeout.

### Task 4: Documentation and CI verification

**Files:**
- Modify: `README.md`

**Interfaces:**
- User-visible settings and mode behavior must match the implementation.

- [ ] Document all delay algorithms and inventory modes.
- [ ] Run `gradle test`.
- [ ] Run `gradle build --stacktrace`.
- [ ] Confirm GitHub Actions build and artifact upload succeed.
- [ ] Download the resulting `armor-stand-grabber-1.21.11` artifact and deliver the compiled jar.
