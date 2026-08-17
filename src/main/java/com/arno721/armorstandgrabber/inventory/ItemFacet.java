package com.arno721.armorstandgrabber.inventory;

public record ItemFacet(
    ItemType type,
    ItemScore score,
    int allocationPriority,
    int unitsPerItem
) {
    public ItemFacet {
        if (type == null) throw new IllegalArgumentException("type cannot be null");
        if (score == null) throw new IllegalArgumentException("score cannot be null");
        if (unitsPerItem < 0) throw new IllegalArgumentException("unitsPerItem must be >= 0");
    }
}
