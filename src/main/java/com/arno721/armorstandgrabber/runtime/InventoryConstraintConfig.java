package com.arno721.armorstandgrabber.runtime;

public record InventoryConstraintConfig(
    int startDelayMinTicks,
    int startDelayMaxTicks,
    int clickDelayMinTicks,
    int clickDelayMaxTicks,
    int closeDelayMinTicks,
    int closeDelayMaxTicks,
    int missChancePercent,
    boolean noMovement,
    boolean noRotation,
    boolean notUsingItem,
    boolean notBreaking,
    boolean notDuringCombat
) {
    public static InventoryConstraintConfig defaults() {
        return new InventoryConstraintConfig(1, 2, 2, 4, 1, 2, 0, true, true, true, true, true);
    }
}
