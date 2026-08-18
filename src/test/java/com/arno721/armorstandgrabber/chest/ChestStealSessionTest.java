package com.arno721.armorstandgrabber.chest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChestStealSessionTest {
    @Test
    void beginAndResetDoNotLeakPreviousHandlerState() {
        ChestStealSession session = new ChestStealSession();
        session.begin(new ContainerDescriptor(3, "Chest", "GenericContainerScreenHandler", List.of(0, 1)), 10);
        assertEquals(ChestStealPhase.StartDelay, session.phase());
        assertEquals(3, session.syncId());
        session.reset();
        assertEquals(ChestStealPhase.Idle, session.phase());
        assertEquals(-1, session.syncId());
        assertEquals(0, session.deadlineTick());
    }

    @Test
    void identityMustMatchBeforeMutation() {
        ChestStealSession session = new ChestStealSession();
        ContainerDescriptor chest = new ContainerDescriptor(3, "Chest", "GenericContainerScreenHandler", List.of(0));
        session.begin(chest, 10);
        assertTrue(session.matches(chest));
        assertFalse(session.matches(new ContainerDescriptor(4, "Chest", "GenericContainerScreenHandler", List.of(0))));
        assertFalse(session.matches(new ContainerDescriptor(3, "Barrel", "GenericContainerScreenHandler", List.of(0))));
    }
}
