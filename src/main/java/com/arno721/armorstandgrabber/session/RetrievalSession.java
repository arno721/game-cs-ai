package com.arno721.armorstandgrabber.session;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;

import java.util.ArrayDeque;
import java.util.Deque;

public final class RetrievalSession {
    private final Deque<EquipmentSlot> pendingSlots = new ArrayDeque<>();
    private RetrievalPhase phase = RetrievalPhase.Idle;
    private ArmorStandEntity target;
    private EquipmentSlot currentSlot;
    private int originalSelectedSlot = -1;
    private int currentHotbarSlot = -1;
    private long deadlineMs;
    private long interactionDeadlineMs;

    public void begin(ArmorStandEntity target, int originalSelectedSlot, EquipmentSlot[] order) {
        reset();
        this.target = target;
        this.originalSelectedSlot = originalSelectedSlot;
        for (EquipmentSlot slot : order) if (!target.getEquippedStack(slot).isEmpty()) pendingSlots.addLast(slot);
        phase = pendingSlots.isEmpty() ? RetrievalPhase.Complete : RetrievalPhase.PrepareSlot;
    }

    public void reset() {
        pendingSlots.clear();
        phase = RetrievalPhase.Idle;
        target = null;
        currentSlot = null;
        originalSelectedSlot = -1;
        currentHotbarSlot = -1;
        deadlineMs = 0L;
        interactionDeadlineMs = 0L;
    }

    public void discardEmptyFrontSlots() {
        while (!pendingSlots.isEmpty() && target != null && target.getEquippedStack(pendingSlots.peekFirst()).isEmpty()) pendingSlots.removeFirst();
    }

    public void completeCurrentSlot() {
        if (currentSlot != null) pendingSlots.remove(currentSlot);
        currentSlot = null;
        currentHotbarSlot = -1;
        interactionDeadlineMs = 0L;
    }

    public int pendingCount() { return pendingSlots.size(); }
    public boolean hasPending() { return !pendingSlots.isEmpty(); }
    public EquipmentSlot peekPending() { return pendingSlots.peekFirst(); }
    public RetrievalPhase phase() { return phase; }
    public void phase(RetrievalPhase phase) { this.phase = phase; }
    public ArmorStandEntity target() { return target; }
    public EquipmentSlot currentSlot() { return currentSlot; }
    public void currentSlot(EquipmentSlot slot) { this.currentSlot = slot; }
    public int originalSelectedSlot() { return originalSelectedSlot; }
    public int currentHotbarSlot() { return currentHotbarSlot; }
    public void currentHotbarSlot(int slot) { this.currentHotbarSlot = slot; }
    public long deadlineMs() { return deadlineMs; }
    public void deadlineMs(long value) { this.deadlineMs = value; }
    public long interactionDeadlineMs() { return interactionDeadlineMs; }
    public void interactionDeadlineMs(long value) { this.interactionDeadlineMs = value; }
}
