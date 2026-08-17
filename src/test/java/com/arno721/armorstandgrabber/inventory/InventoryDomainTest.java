package com.arno721.armorstandgrabber.inventory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InventoryDomainTest {
    @Test
    void ignoreAndNoneHaveDifferentProtectionSemantics() {
        assertTrue(SortChoice.Ignore.protectsSlot());
        assertFalse(SortChoice.None.protectsSlot());
    }

    @Test
    void oneProfileCanExposeMultipleFacets() {
        SlotRef slot = new SlotRef(12, InventoryRegion.PLAYER_MAIN, 5);
        ItemProfile profile = new ItemProfile(
            slot,
            "minecraft:diamond_axe",
            "minecraft:diamond_axe|components:test",
            1,
            1,
            false,
            List.of(
                new ItemFacet(ItemType.WEAPON, ItemScore.generic(90, false, 12), 0, 0),
                new ItemFacet(ItemType.AXE, ItemScore.generic(100, false, 12), 0, 0)
            )
        );
        assertEquals(2, profile.facets().size());
        assertTrue(profile.supports(SortChoice.Weapon));
        assertTrue(profile.supports(SortChoice.Axe));
    }

    @Test
    void cleanupPlanDefensivelyCopiesAllCallerCollections() {
        List<PlannedMove> moves = new ArrayList<>();
        List<Integer> mergeSources = new ArrayList<>();
        List<Integer> discard = new ArrayList<>();
        Map<TargetSlot, Integer> assignments = new HashMap<>();
        CleanupPlan plan = new CleanupPlan(moves, mergeSources, discard, Set.of(12), assignments);

        moves.add(new PlannedMove(1, 2, PlannedMove.Kind.SWAP));
        mergeSources.add(3);
        discard.add(4);
        assignments.put(TargetSlot.hotbar(0), 12);

        assertTrue(plan.moves().isEmpty());
        assertTrue(plan.mergeSourceSlotIds().isEmpty());
        assertTrue(plan.discardSlotIds().isEmpty());
        assertTrue(plan.targetAssignments().isEmpty());
        assertTrue(plan.isUseful(12));
    }

    @Test
    void scoreComparatorPrefersLowerStableSlotOnOtherwiseEqualScores() {
        ItemScore lowerSlot = ItemScore.generic(10, false, 3);
        ItemScore higherSlot = ItemScore.generic(10, false, 4);
        assertTrue(ItemScoreComparator.INSTANCE.compare(lowerSlot, higherSlot) > 0);
    }

    @Test
    void targetSlotValidatesHotbarAndOffhandIndices() {
        assertEquals(TargetSlot.Kind.HOTBAR, TargetSlot.hotbar(8).kind());
        assertEquals(40, TargetSlot.offhand().index());
        assertThrows(IllegalArgumentException.class, () -> TargetSlot.hotbar(9));
    }
}
