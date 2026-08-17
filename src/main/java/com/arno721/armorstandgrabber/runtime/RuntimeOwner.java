package com.arno721.armorstandgrabber.runtime;

public enum RuntimeOwner {
    ARMOR_STAND_GRABBER(300, 300),
    CHEST_STEALER(200, 0),
    CHEST_AURA(0, 200),
    INVENTORY_CLEANER(100, 0);

    private final int inventoryPriority;
    private final int rotationPriority;

    RuntimeOwner(int inventoryPriority, int rotationPriority) {
        this.inventoryPriority = inventoryPriority;
        this.rotationPriority = rotationPriority;
    }

    public int inventoryPriority() {
        return inventoryPriority;
    }

    public int rotationPriority() {
        return rotationPriority;
    }
}
