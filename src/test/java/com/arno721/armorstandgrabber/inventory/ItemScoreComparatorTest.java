package com.arno721.armorstandgrabber.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemScoreComparatorTest {
    @Test
    void primaryUtilityOutranksTieBreakers() {
        ItemScore weaker = ItemScore.weapon(8.0, 1.6, 0, 100, false, 1);
        ItemScore stronger = ItemScore.weapon(9.0, 1.0, 0, 20, false, 99);
        assertTrue(ItemScoreComparator.INSTANCE.compare(stronger, weaker) > 0);
    }

    @Test
    void toolPrimaryThenSecondaryThenDurabilityAreStable() {
        ItemScore base = ItemScore.tool(8.0, false, 0, 100, false, 10);
        ItemScore faster = ItemScore.tool(9.0, false, 0, 20, false, 20);
        assertTrue(ItemScoreComparator.INSTANCE.compare(faster, base) > 0);
    }

    @Test
    void deterministicTieBreakerMakesComparisonTotal() {
        ItemScore a = ItemScore.generic(0, false, 3);
        ItemScore b = ItemScore.generic(0, false, 4);
        assertNotEquals(0, ItemScoreComparator.INSTANCE.compare(a, b));
        assertTrue(ItemScoreComparator.INSTANCE.compare(a, b) > 0);
    }
}
