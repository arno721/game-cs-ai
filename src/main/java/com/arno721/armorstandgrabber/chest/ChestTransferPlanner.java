package com.arno721.armorstandgrabber.chest;

import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;

public final class ChestTransferPlanner {
    public InventoryTransaction quickMove(int sourceSlotId) {
        InventoryAction action = new InventoryAction(sourceSlotId, 0, SlotActionType.QUICK_MOVE, true, true);
        return new InventoryTransaction(List.of(action), List.of(sourceSlotId), false);
    }

    public InventoryTransaction dragAndDrop(SourceStack source, List<DestinationStack> destinations) {
        List<InventoryAction> actions = new ArrayList<>();
        List<Integer> visited = new ArrayList<>();
        actions.add(new InventoryAction(source.slotId(), 0, SlotActionType.PICKUP, true, false));
        visited.add(source.slotId());

        int remaining = source.count();
        for (DestinationStack destination : destinations) {
            if (remaining <= 0) break;
            if (!destination.compatibleWith(source)) continue;
            int capacity = destination.maxCount() - destination.count();
            if (capacity <= 0) continue;
            actions.add(new InventoryAction(destination.slotId(), 0, SlotActionType.PICKUP, false, false));
            visited.add(destination.slotId());
            remaining -= Math.min(remaining, capacity);
        }

        boolean returns = remaining > 0;
        if (returns) {
            actions.add(new InventoryAction(source.slotId(), 0, SlotActionType.PICKUP, false, true));
            visited.add(source.slotId());
        } else if (!actions.isEmpty()) {
            InventoryAction last = actions.removeLast();
            actions.add(new InventoryAction(last.slotId(), last.button(), last.actionType(), last.atomicStart(), true));
        }
        return new InventoryTransaction(actions, visited, returns);
    }

    public record SourceStack(int slotId, int count, int maxCount, String key) {}
    public record DestinationStack(int slotId, int count, int maxCount, String key) {
        boolean compatibleWith(SourceStack source) {
            return key != null && key.equals(source.key());
        }
    }
}
