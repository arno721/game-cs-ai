# Armor Stand Grabber Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Minecraft 1.21.11 Meteor Client addon that intercepts right-click while enabled, targets an armor stand through blocks within a configurable range, and attempts to remove every equipped item into distinct empty hotbar slots.

**Architecture:** Use Meteor's `DoItemUseEvent` to replace normal right-click only when a through-wall armor-stand target is found. Targeting is implemented with an entity ray/box test that ignores block collision, while item removal uses normal `ClientPlayerInteractionManager` entity interaction calls so the server remains authoritative. Hotbar slot switching uses Meteor's `InvUtils.swap`.

**Tech Stack:** Java 21, Minecraft 1.21.11, Yarn 1.21.11+build.3, Fabric Loader 0.18.2, Fabric Loom 1.14-SNAPSHOT, Meteor Client 1.21.11-SNAPSHOT, Gradle 9.2.0.

## Global Constraints

- Do not modify Meteor Client itself.
- The addon must be a standalone jar.
- Module OFF leaves vanilla interaction untouched.
- Module ON only cancels the use action when a valid armor stand is targeted.
- Targeting must ignore intervening blocks.
- Range must be configurable.
- One activation attempts to remove all currently equipped armor-stand items.
- Each removed item must use a different currently empty hotbar slot; stop when no empty slot remains.
- Use normal client interaction APIs; do not fabricate inventory contents.
- Build must be verified by GitHub Actions before claiming success.

---

### Task 1: Addon scaffold and build configuration

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `src/main/resources/fabric.mod.json`
- Create: `LICENSE`

**Interfaces:**
- Produces a Fabric/Meteor addon project targeting Minecraft 1.21.11.

- [ ] Add the 1.21.11-compatible Meteor addon Gradle configuration.
- [ ] Set package/group to `com.arno721.armorstandgrabber` and archive name to `armor-stand-grabber`.
- [ ] Verify Gradle can resolve Minecraft, Yarn, Fabric Loader, and Meteor dependencies in CI.

### Task 2: Addon entrypoint and module registration

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/ArmorStandGrabberAddon.java`

**Interfaces:**
- Produces `ArmorStandGrabberAddon.CATEGORY` and registers `ArmorStandGrabber`.

- [ ] Register a dedicated Meteor category.
- [ ] Register the module in `onInitialize()`.
- [ ] Point `getRepo()` at `arno721/game-cs-ai`.

### Task 3: Through-wall targeting and full equipment removal

**Files:**
- Create: `src/main/java/com/arno721/armorstandgrabber/modules/ArmorStandGrabber.java`

**Interfaces:**
- Consumes `DoItemUseEvent`, `InvUtils`, `ArmorStandEntity`, `EntityHitResult`.
- Produces the `armor-stand-grabber` Meteor module.

- [ ] Add a `range` DoubleSetting with a usable slider range.
- [ ] On right-click, ray-test armor stand bounding boxes from the camera to `range` without block raycasts.
- [ ] Select the closest intersected armor stand along the aim ray.
- [ ] Snapshot distinct empty hotbar slots.
- [ ] For each equipped armor-stand slot, select a different empty hotbar slot and send a normal entity-at-location interaction.
- [ ] Cancel the original right-click only when this replacement interaction is actually attempted.
- [ ] Restore the user's originally selected hotbar slot after queuing interactions.

### Task 4: CI build and artifact publishing

**Files:**
- Create: `.github/workflows/build.yml`
- Create: `README.md`

**Interfaces:**
- Produces a GitHub Actions artifact containing the built addon jar.

- [ ] Configure Java 21 and Gradle 9.2.0.
- [ ] Run `gradle build` on pushes to `feature/armor-stand-grabber` and via workflow dispatch.
- [ ] Upload `build/libs/armor-stand-grabber-*.jar` as an artifact.
- [ ] Inspect build logs and fix compile/API errors until green.
- [ ] Download the successful artifact and provide the jar to the user.
