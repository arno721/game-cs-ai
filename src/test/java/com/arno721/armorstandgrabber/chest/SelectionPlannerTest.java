package com.arno721.armorstandgrabber.chest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectionPlannerTest {
    private final SelectionPlanner planner = new SelectionPlanner();

    @Test
    void indexAscendingAndDescendingAreExact() {
        List<SelectionCandidate> input = List.of(
            new SelectionCandidate(8, 2, 0),
            new SelectionCandidate(2, 0, 0),
            new SelectionCandidate(5, 1, 0)
        );
        assertEquals(List.of(2, 5, 8), slotIds(planner.order(input, SelectionConfig.index(IndexOrder.Ascending))));
        assertEquals(List.of(8, 5, 2), slotIds(planner.order(input, SelectionConfig.index(IndexOrder.Descending))));
    }

    @Test
    void distanceWithFixedFactorUsesNearestNeighborFromMinSlot() {
        List<SelectionCandidate> input = List.of(
            new SelectionCandidate(0, 0, 0),
            new SelectionCandidate(1, 1, 0),
            new SelectionCandidate(9, 0, 1),
            new SelectionCandidate(17, 8, 1)
        );
        SelectionConfig config = SelectionConfig.distance(DistanceStartItem.MinSlot, 1.0, 1.0, new Random(1));
        assertEquals(List.of(0, 1, 9, 17), slotIds(planner.order(input, config)));
    }

    @Test
    void randomModeIsDeterministicWithSeededRandom() {
        List<SelectionCandidate> candidates = List.of(
            new SelectionCandidate(0, 0, 0),
            new SelectionCandidate(1, 1, 0),
            new SelectionCandidate(2, 2, 0),
            new SelectionCandidate(3, 3, 0)
        );
        List<Integer> first = slotIds(planner.order(candidates, SelectionConfig.random(new Random(123))));
        List<Integer> second = slotIds(planner.order(candidates, SelectionConfig.random(new Random(123))));
        assertEquals(first, second);
    }

    private static List<Integer> slotIds(List<SelectionCandidate> candidates) {
        return candidates.stream().map(SelectionCandidate::slotId).toList();
    }
}
