package com.arno721.armorstandgrabber.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConstraintGateTest {
    @Test
    void genericRequirementsRejectOnlyEnabledConditions() {
        InventoryConstraintConfig config = new InventoryConstraintConfig(
            1, 2, 2, 4, 1, 2, 0,
            true, true, true, true, true
        );

        assertTrue(ConstraintGate.allowed(config,
            new ActivitySnapshot(false, false, false, false, false, false), false));
        assertFalse(ConstraintGate.allowed(config,
            new ActivitySnapshot(true, false, false, false, false, false), false));
        assertFalse(ConstraintGate.allowed(config,
            new ActivitySnapshot(false, true, false, false, false, false), false));
        assertFalse(ConstraintGate.allowed(config,
            new ActivitySnapshot(false, false, true, false, false, false), false));
        assertFalse(ConstraintGate.allowed(config,
            new ActivitySnapshot(false, false, false, true, false, false), false));
        assertFalse(ConstraintGate.allowed(config,
            new ActivitySnapshot(false, false, false, false, true, false), false));
    }

    @Test
    void standaloneCleanerCanRequireInventoryOpen() {
        InventoryConstraintConfig config = InventoryConstraintConfig.defaults();
        ActivitySnapshot closed = new ActivitySnapshot(false, false, false, false, false, false);
        ActivitySnapshot open = new ActivitySnapshot(false, false, false, false, false, true);
        assertFalse(ConstraintGate.allowed(config, closed, true));
        assertTrue(ConstraintGate.allowed(config, open, true));
    }
}
