package com.arno721.armorstandgrabber.chest;

import java.util.List;

public record InventoryTransaction(List<InventoryAction> actions, List<Integer> visitedSlotIds, boolean returnsToSource) {
    public InventoryTransaction {
        actions = List.copyOf(actions);
        visitedSlotIds = List.copyOf(visitedSlotIds);
    }
}
