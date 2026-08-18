package com.arno721.armorstandgrabber.runtime;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class ClientRuntime {
    private final InventoryMutex inventoryMutex = new InventoryMutex();
    private final CombatTracker combatTracker = new CombatTracker(100);
    private final ActivityTracker activityTracker = new ActivityTracker();
    private final RotationCoordinator rotationCoordinator;
    private final List<RuntimeTickParticipant> participants = new ArrayList<>();
    private final List<Runnable> observers = new ArrayList<>();
    private final Set<RuntimeTickParticipant> participantIdentities = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Runnable> observerIdentities = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    private Object lastWorldIdentity;
    private Object lastPlayerIdentity;
    private long tickCount;

    public ClientRuntime(MinecraftClient mc) {
        this(new RotationCoordinator(mc));
    }

    ClientRuntime(RotationCoordinator rotationCoordinator) {
        this.rotationCoordinator = rotationCoordinator;
    }

    public void register(RuntimeTickParticipant participant) {
        if (participant == null) throw new IllegalArgumentException("participant cannot be null");
        if (!participantIdentities.add(participant)) {
            throw new IllegalArgumentException("participant already registered");
        }
        participants.add(participant);
        participants.sort(Comparator
            .comparingInt((RuntimeTickParticipant p) -> Math.max(
                p.runtimeOwner().inventoryPriority(),
                p.runtimeOwner().rotationPriority()
            ))
            .reversed()
            .thenComparingInt(p -> p.runtimeOwner().ordinal()));
    }

    public void registerObserver(Runnable observer) {
        if (observer == null) throw new IllegalArgumentException("observer cannot be null");
        if (!observerIdentities.add(observer)) {
            throw new IllegalArgumentException("observer already registered");
        }
        observers.add(observer);
    }

    public void tick(MinecraftClient mc) {
        if (!prepareTick(mc.world, mc.player)) return;
        runRegistered();
    }

    boolean prepareTick(Object worldIdentity, Object playerIdentity) {
        if (worldIdentity == null || playerIdentity == null) {
            if (lastWorldIdentity != null || lastPlayerIdentity != null || tickCount != 0) reset();
            return false;
        }

        if (worldIdentity != lastWorldIdentity || playerIdentity != lastPlayerIdentity) {
            clearVolatileState();
            lastWorldIdentity = worldIdentity;
            lastPlayerIdentity = playerIdentity;
        }

        tickCount++;
        combatTracker.tick();
        rotationCoordinator.beginTick();
        return true;
    }

    void runRegistered() {
        for (RuntimeTickParticipant participant : participants) participant.onRuntimeTick();
        for (Runnable observer : observers) observer.run();
    }

    static List<RuntimeOwner> sortOwnersForTick(List<RuntimeOwner> owners) {
        return owners.stream()
            .sorted(Comparator
                .comparingInt((RuntimeOwner owner) -> Math.max(owner.inventoryPriority(), owner.rotationPriority()))
                .reversed()
                .thenComparingInt(Enum::ordinal))
            .toList();
    }

    public InventoryMutex inventoryMutex() {
        return inventoryMutex;
    }

    public CombatTracker combatTracker() {
        return combatTracker;
    }

    public ActivityTracker activityTracker() {
        return activityTracker;
    }

    public RotationCoordinator rotationCoordinator() {
        return rotationCoordinator;
    }

    public long tickCount() {
        return tickCount;
    }

    public void reset() {
        clearVolatileState();
        lastWorldIdentity = null;
        lastPlayerIdentity = null;
    }

    private void clearVolatileState() {
        inventoryMutex.reset();
        combatTracker.reset();
        activityTracker.reset();
        rotationCoordinator.reset();
        tickCount = 0;
    }
}
