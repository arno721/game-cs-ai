package com.arno721.armorstandgrabber.inventory;

import java.util.List;
import java.util.Map;

public record InventorySnapshot(
    int syncId,
    List<ItemProfile> profiles,
    Map<Integer, SlotRef> bySlotId,
    int cursorCount
) {
    public InventorySnapshot {
        profiles = List.copyOf(profiles);
        bySlotId = Map.copyOf(bySlotId);
        if (cursorCount < 0) throw new IllegalArgumentException("cursorCount must be >= 0");
    }
}
