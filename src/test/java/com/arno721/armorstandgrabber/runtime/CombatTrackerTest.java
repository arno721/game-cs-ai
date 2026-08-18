package com.arno721.armorstandgrabber.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatTrackerTest {
    @Test
    void combatWindowLastsExactlyOneHundredTicksAfterRefresh() {
        CombatTracker tracker = new CombatTracker(100);
        tracker.recordLocalAttack();
        for (int i = 0; i < 99; i++) {
            assertTrue(tracker.active());
            tracker.tick();
        }
        assertTrue(tracker.active());
        tracker.tick();
        assertFalse(tracker.active());
    }

    @Test
    void incomingLivingAttackRefreshesWindowAndResetClearsIt() {
        CombatTracker tracker = new CombatTracker(100);
        tracker.recordIncomingLivingAttack();
        tracker.tick();
        tracker.recordIncomingLivingAttack();
        assertTrue(tracker.active());
        tracker.reset();
        assertFalse(tracker.active());
    }
}
