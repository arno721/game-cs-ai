package com.arno721.armorstandgrabber.chest;

public final class ChestStealSession {
    private ChestStealPhase phase = ChestStealPhase.Idle;
    private int syncId = -1;
    private String handlerClassName;
    private String title;
    private long deadlineTick;
    private long lastMutationTick = -1;

    public void begin(ContainerDescriptor descriptor, long deadlineTick) {
        this.syncId = descriptor.syncId();
        this.handlerClassName = descriptor.handlerClassName();
        this.title = descriptor.title();
        this.deadlineTick = deadlineTick;
        this.lastMutationTick = -1;
        this.phase = ChestStealPhase.StartDelay;
    }

    public boolean matches(ContainerDescriptor descriptor) {
        return descriptor != null
            && syncId == descriptor.syncId()
            && java.util.Objects.equals(handlerClassName, descriptor.handlerClassName())
            && java.util.Objects.equals(title, descriptor.title());
    }

    public void markMutation(long tick) {
        lastMutationTick = tick;
        phase = ChestStealPhase.ClickDelay;
    }

    public void enterPlanning() { phase = ChestStealPhase.Planning; }
    public void enterCloseDelay(long deadlineTick) {
        this.deadlineTick = deadlineTick;
        phase = ChestStealPhase.CloseDelay;
    }
    public void enterClosing() { phase = ChestStealPhase.Closing; }

    public void reset() {
        phase = ChestStealPhase.Idle;
        syncId = -1;
        handlerClassName = null;
        title = null;
        deadlineTick = 0;
        lastMutationTick = -1;
    }

    public ChestStealPhase phase() { return phase; }
    public int syncId() { return syncId; }
    public String handlerClassName() { return handlerClassName; }
    public String title() { return title; }
    public long deadlineTick() { return deadlineTick; }
    public long lastMutationTick() { return lastMutationTick; }
}
