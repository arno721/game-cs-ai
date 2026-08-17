package com.arno721.armorstandgrabber.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RotationCoordinatorTest {
    @Test
    void higherPriorityRotationWinsWithinTick() {
        List<String> applied = new ArrayList<>();
        RotationCoordinator coordinator = new RotationCoordinator(
            (yaw, pitch, visible) -> applied.add(yaw + ":" + pitch + ":" + visible)
        );

        coordinator.beginTick();
        assertTrue(coordinator.tryApply(RuntimeOwner.CHEST_AURA, 20, 5, false));
        assertTrue(coordinator.tryApply(RuntimeOwner.ARMOR_STAND_GRABBER, 50, 10, true));
        assertEquals(50.0, coordinator.serverYaw());
        assertEquals(10.0, coordinator.serverPitch());
        assertTrue(coordinator.changedThisTick());
        assertEquals(List.of("20.0:5.0:false", "50.0:10.0:true"), applied);
    }

    @Test
    void lowerPriorityRequestIsRejectedAfterHigherPriorityClaim() {
        RotationCoordinator coordinator = new RotationCoordinator((yaw, pitch, visible) -> {});
        coordinator.beginTick();
        assertTrue(coordinator.tryApply(RuntimeOwner.ARMOR_STAND_GRABBER, 1, 2, false));
        assertFalse(coordinator.tryApply(RuntimeOwner.CHEST_AURA, 3, 4, false));
        assertEquals(1.0, coordinator.serverYaw());
        assertEquals(2.0, coordinator.serverPitch());
    }

    @Test
    void beginTickClearsWinnerButPreservesLastServerRotation() {
        RotationCoordinator coordinator = new RotationCoordinator((yaw, pitch, visible) -> {});
        assertTrue(coordinator.tryApply(RuntimeOwner.CHEST_AURA, 25, -5, false));
        coordinator.beginTick();
        assertFalse(coordinator.changedThisTick());
        assertEquals(25.0, coordinator.serverYaw());
        assertEquals(-5.0, coordinator.serverPitch());
        assertTrue(coordinator.tryApply(RuntimeOwner.CHEST_AURA, 30, 0, false));
    }

    @Test
    void resetClearsLastServerRotation() {
        RotationCoordinator coordinator = new RotationCoordinator((yaw, pitch, visible) -> {});
        coordinator.tryApply(RuntimeOwner.CHEST_AURA, 25, -5, false);
        coordinator.reset();
        assertTrue(Double.isNaN(coordinator.serverYaw()));
        assertTrue(Double.isNaN(coordinator.serverPitch()));
        assertFalse(coordinator.changedThisTick());
    }
}
