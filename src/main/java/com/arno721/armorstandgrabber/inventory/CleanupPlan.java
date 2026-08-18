package com.arno721.armorstandgrabber.inventory;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record CleanupPlan(
    List<PlannedMove> moves,
    List<Integer> mergeSourceSlotIds,
    List<Integer> discardSlotIds,
    Set<Integer> usefulSlotIds,
    Map<TargetSlot, Integer> targetAssignments
) {
    public CleanupPlan {
        moves = List.copyOf(moves);
        mergeSourceSlotIds = List.copyOf(mergeSourceSlotIds);
        discardSlotIds = List.copyOf(discardSlotIds);
        usefulSlotIds = Set.copyOf(usefulSlotIds);
        targetAssignments = Map.copyOf(targetAssignments);
    }

    public boolean isUseful(int slotId) {
        return usefulSlotIds.contains(slotId);
    }
}
