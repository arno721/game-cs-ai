package com.arno721.armorstandgrabber.runtime;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public final class RotationCoordinator {
    @FunctionalInterface
    interface RotationSink {
        void apply(double yaw, double pitch, boolean visible);
    }

    private final RotationSink sink;
    private RuntimeOwner winner;
    private double serverYaw = Double.NaN;
    private double serverPitch = Double.NaN;
    private boolean changedThisTick;

    public RotationCoordinator(MinecraftClient mc) {
        this((yaw, pitch, visible) -> {
            if (mc.player == null || mc.getNetworkHandler() == null) return;
            if (visible) {
                mc.player.setYaw((float) yaw);
                mc.player.setPitch((float) pitch);
            } else {
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    (float) yaw,
                    (float) pitch,
                    mc.player.isOnGround(),
                    mc.player.horizontalCollision
                ));
            }
        });
    }

    RotationCoordinator(RotationSink sink) {
        this.sink = sink;
    }

    public boolean tryApply(RuntimeOwner owner, double yaw, double pitch, boolean visible) {
        if (owner == null || owner.rotationPriority() <= 0) return false;
        if (winner != null && owner.rotationPriority() < winner.rotationPriority()) return false;

        winner = owner;
        serverYaw = yaw;
        serverPitch = pitch;
        changedThisTick = true;
        sink.apply(yaw, pitch, visible);
        return true;
    }

    public double serverYaw() {
        return serverYaw;
    }

    public double serverPitch() {
        return serverPitch;
    }

    public boolean changedThisTick() {
        return changedThisTick;
    }

    public void beginTick() {
        winner = null;
        changedThisTick = false;
    }

    public void reset() {
        winner = null;
        serverYaw = Double.NaN;
        serverPitch = Double.NaN;
        changedThisTick = false;
    }
}
