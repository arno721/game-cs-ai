# Armor Stand Grabber Delay and Inventory Overflow Design

## Goal

Extend the Minecraft 1.21.11 Meteor Client addon so armor-stand equipment retrieval happens one item at a time with configurable millisecond delays, multiple delay algorithms, and optional inventory overflow handling.

## Delay behavior

- Delay unit: milliseconds.
- The first equipment interaction may happen immediately after the initiating right click.
- Every subsequent equipment item is scheduled after a newly sampled delay.
- Settings:
  - `min-delay` in ms.
  - `max-delay` in ms.
  - `delay-algorithm`.
- If max is configured below min, runtime sampling normalizes the two values rather than producing an invalid delay.
- Algorithms:
  - Fixed Midpoint
  - Uniform Random
  - Gaussian
  - Triangular
  - Fast Biased
  - Slow Biased
  - Midpoint Jitter
  - Alternating
  - Humanized Drift
- All algorithms must always return a value inside the normalized `[min, max]` interval.

## Inventory overflow behavior

Setting: `overflow-mode`.

### Off

Use empty hotbar slots only. When no empty hotbar slot remains, stop the retrieval session.

### Silent

When no empty hotbar slot remains, automatically place subsequent retrieved equipment into empty main-inventory slots without opening the inventory GUI.

For each overflow item:
1. Find one empty main inventory slot.
2. Temporarily move the selected hotbar buffer item into that empty inventory slot, making the hotbar buffer empty.
3. Interact with the armor stand using the empty buffer slot.
4. Wait for the server/client inventory update that places the armor-stand item in the buffer.
5. Move the newly retrieved item into the chosen inventory slot while restoring the original hotbar buffer item.
6. Continue after the configured inter-item delay.

### Auto Open

Uses the same automatic transfer logic as Silent, but when overflow is first needed it opens the player's Inventory screen automatically. No manual dragging is required.

Setting: `auto-close`, visible only for Auto Open.
- ON: close the inventory screen when the retrieval session finishes or aborts, but only if the addon opened it.
- OFF: leave the inventory screen open after the session.

## Retrieval state machine

A right click that acquires an armor-stand target starts one session. While a session is active, additional intercepted right clicks do not start duplicate sessions.

Session phases:
- READY: choose the next armor-stand equipment slot and destination.
- WAIT_DELAY: wait until the sampled millisecond deadline.
- WAIT_ITEM: after sending an interaction, wait for the expected client inventory update.
- FINISH: restore the originally selected hotbar slot and apply Auto Close behavior.

The addon verifies target validity and times out a pending item transfer rather than blindly sending all interactions in one frame.

## Compatibility and constraints

- Minecraft 1.21.11.
- Meteor Client 1.21.11-SNAPSHOT.
- Java 21.
- No Meteor Client source modification.
- Through-wall targeting behavior remains unchanged.
- Server-side reach and interaction validation still applies.
- Existing Range setting remains.
