package com.arno721.armorstandgrabber.inventory;

public record PlannedMove(int sourceSlotId, int targetSlotId, Kind kind) {
    public enum Kind {
        SWAP,
        QUICK_MOVE,
        PICKUP_MOVE,
        MERGE
    }
}
