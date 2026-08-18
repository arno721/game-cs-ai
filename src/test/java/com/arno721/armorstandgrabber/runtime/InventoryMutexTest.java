package com.arno721.armorstandgrabber.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryMutexTest {
    @Test
    void higherPriorityOwnerCanPreemptOnlyOutsideAtomicSequence() {
        InventoryMutex mutex = new InventoryMutex();

        assertTrue(mutex.tryAcquire(RuntimeOwner.INVENTORY_CLEANER));
        assertTrue(mutex.tryAcquire(RuntimeOwner.CHEST_STEALER));
        assertEquals(RuntimeOwner.CHEST_STEALER, mutex.owner());

        mutex.beginAtomic(RuntimeOwner.CHEST_STEALER);
        assertFalse(mutex.tryAcquire(RuntimeOwner.ARMOR_STAND_GRABBER));
        assertTrue(mutex.yieldRequested());
        assertEquals(RuntimeOwner.CHEST_STEALER, mutex.owner());

        mutex.endAtomic(RuntimeOwner.CHEST_STEALER);
        assertNull(mutex.owner());
        assertTrue(mutex.tryAcquire(RuntimeOwner.ARMOR_STAND_GRABBER));
    }

    @Test
    void lowerPriorityOwnerCannotStealOwnership() {
        InventoryMutex mutex = new InventoryMutex();
        assertTrue(mutex.tryAcquire(RuntimeOwner.ARMOR_STAND_GRABBER));
        assertFalse(mutex.tryAcquire(RuntimeOwner.CHEST_STEALER));
        assertEquals(RuntimeOwner.ARMOR_STAND_GRABBER, mutex.owner());
    }

    @Test
    void resetClearsOwnerAtomicStateAndYieldRequest() {
        InventoryMutex mutex = new InventoryMutex();
        mutex.tryAcquire(RuntimeOwner.CHEST_STEALER);
        mutex.beginAtomic(RuntimeOwner.CHEST_STEALER);
        mutex.tryAcquire(RuntimeOwner.ARMOR_STAND_GRABBER);
        mutex.reset();

        assertNull(mutex.owner());
        assertFalse(mutex.atomic());
        assertFalse(mutex.yieldRequested());
    }
}
