package com.arno721.armorstandgrabber.inventory;

public record SlotRef(int slotId, InventoryRegion region, int logicalIndex) {
    public SlotRef {
        if (slotId < 0) throw new IllegalArgumentException("slotId must be >= 0");
        if (region == null) throw new IllegalArgumentException("region cannot be null");
    }
}
