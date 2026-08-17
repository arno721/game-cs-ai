package com.arno721.armorstandgrabber.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientRuntimeOrderTest {
    @Test
    void participantsRunInDescendingInventoryThenRotationPriority() {
        assertEquals(
            List.of(
                RuntimeOwner.ARMOR_STAND_GRABBER,
                RuntimeOwner.CHEST_STEALER,
                RuntimeOwner.CHEST_AURA,
                RuntimeOwner.INVENTORY_CLEANER
            ),
            ClientRuntime.sortOwnersForTick(List.of(
                RuntimeOwner.INVENTORY_CLEANER,
                RuntimeOwner.CHEST_AURA,
                RuntimeOwner.CHEST_STEALER,
                RuntimeOwner.ARMOR_STAND_GRABBER
            ))
        );
    }

    @Test
    void tickCountResetsAcrossSessionReplacement() {
        ClientRuntime runtime = new ClientRuntime(new RotationCoordinator((yaw, pitch, visible) -> {}));
        Object worldA = new Object();
        Object worldB = new Object();
        Object player = new Object();

        assertEquals(0, runtime.tickCount());
        assertTrue(runtime.prepareTick(worldA, player));
        assertEquals(1, runtime.tickCount());
        assertTrue(runtime.prepareTick(worldA, player));
        assertEquals(2, runtime.tickCount());
        assertTrue(runtime.prepareTick(worldB, player));
        assertEquals(1, runtime.tickCount());
    }

    @Test
    void observersRunAfterPrioritizedParticipantsInRegistrationOrder() {
        ClientRuntime runtime = new ClientRuntime(new RotationCoordinator((yaw, pitch, visible) -> {}));
        List<String> calls = new ArrayList<>();

        runtime.register(new TestParticipant(RuntimeOwner.INVENTORY_CLEANER, calls, "cleaner"));
        runtime.register(new TestParticipant(RuntimeOwner.ARMOR_STAND_GRABBER, calls, "armor"));
        runtime.registerObserver(() -> calls.add("observer-1"));
        runtime.registerObserver(() -> calls.add("observer-2"));

        runtime.runRegistered();

        assertEquals(List.of("armor", "cleaner", "observer-1", "observer-2"), calls);
    }

    @Test
    void duplicateIdentityRegistrationsAreRejected() {
        ClientRuntime runtime = new ClientRuntime(new RotationCoordinator((yaw, pitch, visible) -> {}));
        TestParticipant participant = new TestParticipant(RuntimeOwner.CHEST_STEALER, new ArrayList<>(), "chest");
        Runnable observer = () -> {};

        runtime.register(participant);
        assertThrows(IllegalArgumentException.class, () -> runtime.register(participant));
        runtime.registerObserver(observer);
        assertThrows(IllegalArgumentException.class, () -> runtime.registerObserver(observer));
    }

    private record TestParticipant(RuntimeOwner runtimeOwner, List<String> calls, String name)
        implements RuntimeTickParticipant {
        @Override
        public void onRuntimeTick() {
            calls.add(name);
        }
    }
}
