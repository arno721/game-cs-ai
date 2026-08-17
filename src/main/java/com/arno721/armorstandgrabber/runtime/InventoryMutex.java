package com.arno721.armorstandgrabber.runtime;

public final class InventoryMutex {
    private RuntimeOwner owner;
    private boolean atomic;
    private boolean yieldRequested;

    public boolean tryAcquire(RuntimeOwner candidate) {
        if (candidate.inventoryPriority() <= 0) return false;
        if (owner == null || owner == candidate) {
            owner = candidate;
            return true;
        }
        if (candidate.inventoryPriority() <= owner.inventoryPriority()) return false;
        if (atomic) {
            yieldRequested = true;
            return false;
        }
        owner = candidate;
        yieldRequested = false;
        return true;
    }

    public void beginAtomic(RuntimeOwner candidate) {
        if (owner != candidate) {
            throw new IllegalStateException("inventory mutex is not owned by " + candidate);
        }
        atomic = true;
    }

    public void endAtomic(RuntimeOwner candidate) {
        if (owner != candidate) return;
        atomic = false;
        if (yieldRequested) {
            owner = null;
            yieldRequested = false;
        }
    }

    public void release(RuntimeOwner candidate) {
        if (owner != candidate) return;
        if (atomic) {
            throw new IllegalStateException("cannot release inventory mutex during atomic sequence");
        }
        owner = null;
        yieldRequested = false;
    }

    public RuntimeOwner owner() {
        return owner;
    }

    public boolean isOwnedBy(RuntimeOwner candidate) {
        return owner == candidate;
    }

    public boolean atomic() {
        return atomic;
    }

    public boolean yieldRequested() {
        return yieldRequested;
    }

    public void reset() {
        owner = null;
        atomic = false;
        yieldRequested = false;
    }
}
