package com.arno721.armorstandgrabber.runtime;

public final class CombatTracker {
    private final int durationTicks;
    private int remainingTicks;

    public CombatTracker(int durationTicks) {
        if (durationTicks < 1) throw new IllegalArgumentException("durationTicks must be positive");
        this.durationTicks = durationTicks;
    }

    public void recordLocalAttack() {
        remainingTicks = durationTicks;
    }

    public void recordIncomingLivingAttack() {
        remainingTicks = durationTicks;
    }

    public void tick() {
        if (remainingTicks > 0) remainingTicks--;
    }

    public boolean active() {
        return remainingTicks > 0;
    }

    public int remainingTicks() {
        return remainingTicks;
    }

    public void reset() {
        remainingTicks = 0;
    }
}
