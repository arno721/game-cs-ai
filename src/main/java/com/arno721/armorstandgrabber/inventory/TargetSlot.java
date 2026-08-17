package com.arno721.armorstandgrabber.inventory;

public record TargetSlot(TargetSlot.Kind kind, int index) {
    public enum Kind {
        HOTBAR,
        OFFHAND
    }

    public TargetSlot {
        if (kind == null) throw new IllegalArgumentException("kind cannot be null");
        if (kind == Kind.HOTBAR && (index < 0 || index > 8)) {
            throw new IllegalArgumentException("hotbar index must be 0..8");
        }
        if (kind == Kind.OFFHAND && index != 40) {
            throw new IllegalArgumentException("offhand index must be 40");
        }
    }

    public static TargetSlot hotbar(int index) {
        return new TargetSlot(Kind.HOTBAR, index);
    }

    public static TargetSlot offhand() {
        return new TargetSlot(Kind.OFFHAND, 40);
    }
}
