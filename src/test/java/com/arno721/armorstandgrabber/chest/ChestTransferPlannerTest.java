package com.arno721.armorstandgrabber.chest;

import net.minecraft.screen.slot.SlotActionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChestTransferPlannerTest {
    private final ChestTransferPlanner planner = new ChestTransferPlanner();

    @Test
    void quickMoveIsOneShiftClick() {
        InventoryTransaction tx = planner.quickMove(4);
        assertEquals(1, tx.actions().size());
        InventoryAction action = tx.actions().getFirst();
        assertEquals(4, action.slotId());
        assertEquals(SlotActionType.QUICK_MOVE, action.actionType());
    }

    @Test
    void dragAndDropFillsMergeBeforeEmptyAndReturnsNoRemainder() {
        ChestTransferPlanner.SourceStack source = new ChestTransferPlanner.SourceStack(2, 50, 64, "stone");
        List<ChestTransferPlanner.DestinationStack> destinations = List.of(
            new ChestTransferPlanner.DestinationStack(30, 40, 64, "stone"),
            new ChestTransferPlanner.DestinationStack(31, 0, 64, "stone")
        );
        InventoryTransaction tx = planner.dragAndDrop(source, destinations);
        assertEquals(List.of(2, 30, 31), tx.visitedSlotIds());
        assertFalse(tx.returnsToSource());
        assertTrue(tx.actions().getFirst().atomicStart());
        assertTrue(tx.actions().getLast().atomicEnd());
    }

    @Test
    void dragAndDropReturnsCursorRemainderWhenCapacityIsInsufficient() {
        ChestTransferPlanner.SourceStack source = new ChestTransferPlanner.SourceStack(2, 64, 64, "stone");
        List<ChestTransferPlanner.DestinationStack> destinations = List.of(
            new ChestTransferPlanner.DestinationStack(30, 60, 64, "stone")
        );
        InventoryTransaction tx = planner.dragAndDrop(source, destinations);
        assertTrue(tx.returnsToSource());
        assertEquals(2, tx.visitedSlotIds().getLast());
        assertTrue(tx.actions().getLast().atomicEnd());
    }
}
