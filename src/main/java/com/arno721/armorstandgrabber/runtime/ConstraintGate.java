package com.arno721.armorstandgrabber.runtime;

public final class ConstraintGate {
    private ConstraintGate() {}

    public static boolean allowed(
        InventoryConstraintConfig config,
        ActivitySnapshot snapshot,
        boolean requireInventoryOpen
    ) {
        if (requireInventoryOpen && !snapshot.inventoryOpen()) return false;
        if (config.noMovement() && snapshot.moving()) return false;
        if (config.noRotation() && snapshot.rotating()) return false;
        if (config.notUsingItem() && snapshot.usingItem()) return false;
        if (config.notBreaking() && snapshot.breakingBlock()) return false;
        return !config.notDuringCombat() || !snapshot.combatActive();
    }
}
