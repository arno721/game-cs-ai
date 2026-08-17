package com.arno721.armorstandgrabber.rotation;

import com.arno721.armorstandgrabber.runtime.RotationCoordinator;
import com.arno721.armorstandgrabber.runtime.RuntimeOwner;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Vec3d;

public final class RotationController {
    private final MinecraftClient mc;
    private final RotationCoordinator coordinator;
    private ArmorStandEntity target;
    private RotationState state;
    private long lastUpdateNs;

    public RotationController(MinecraftClient mc, RotationCoordinator coordinator) {
        this.mc = mc;
        this.coordinator = coordinator;
    }

    public void begin(ArmorStandEntity target) {
        this.target = target;
        if (mc.player != null) state = new RotationState(mc.player.getYaw(), mc.player.getPitch(), 0.0, 0.0);
        else state = new RotationState(0.0, 0.0, 0.0, 0.0);
        lastUpdateNs = System.nanoTime();
    }

    public void tick(EquipmentSlot currentSlot, RotationMode mode, RotationTarget targetMode, RotationAlgorithm algorithm, RotationConfig config) {
        update(currentSlot, mode, targetMode, algorithm, config, false);
    }

    public void prepareInteraction(EquipmentSlot currentSlot, RotationMode mode, RotationTarget targetMode, RotationAlgorithm algorithm, RotationConfig config) {
        update(currentSlot, mode, targetMode, algorithm, config, true);
    }

    public void stop() {
        target = null;
        state = null;
        lastUpdateNs = 0L;
    }

    public boolean isActive() {
        return target != null;
    }

    private void update(EquipmentSlot currentSlot, RotationMode mode, RotationTarget targetMode, RotationAlgorithm algorithm, RotationConfig config, boolean interactionUpdate) {
        if (target == null || mc.player == null || !target.isAlive()) return;

        Vec3d point = targetPoint(target, currentSlot, targetMode);
        double eyeY = mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose());
        RotationMath.Angles desired = RotationMath.targetAngles(mc.player.getX(), eyeY, mc.player.getZ(), point.x, point.y, point.z);

        long now = System.nanoTime();
        double dt = lastUpdateNs == 0L ? 0.05 : (now - lastUpdateNs) / 1_000_000_000.0;
        if (interactionUpdate) dt = Math.max(dt, 0.001);
        state = RotationMath.advance(state, desired.yaw(), desired.pitch(), algorithm, dt, config);
        lastUpdateNs = now;

        coordinator.tryApply(
            RuntimeOwner.ARMOR_STAND_GRABBER,
            state.yaw(),
            state.pitch(),
            mode == RotationMode.Lock
        );
    }

    private static Vec3d targetPoint(ArmorStandEntity armorStand, EquipmentSlot slot, RotationTarget mode) {
        if (mode == RotationTarget.Center) {
            return new Vec3d(armorStand.getX(), armorStand.getY() + armorStand.getHeight() * 0.5, armorStand.getZ());
        }
        if (mode == RotationTarget.UpperBody || slot == null) {
            return new Vec3d(armorStand.getX(), armorStand.getY() + armorStand.getHeight() * 0.72, armorStand.getZ());
        }

        double scale = armorStand.isSmall() ? 0.5 : 1.0;
        double y = switch (slot) {
            case HEAD -> 1.80 * scale;
            case CHEST -> 1.35 * scale;
            case LEGS -> 0.80 * scale;
            case FEET -> 0.30 * scale;
            case MAINHAND, OFFHAND -> 0.05 * scale;
            default -> armorStand.getHeight() * 0.72;
        };
        return new Vec3d(armorStand.getX(), armorStand.getY() + y, armorStand.getZ());
    }
}
