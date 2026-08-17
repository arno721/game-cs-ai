package com.arno721.armorstandgrabber.rotation;

public final class RotationMath {
    private RotationMath() {}

    public static double wrapDegrees(double value) {
        value %= 360.0;
        if (value >= 180.0) value -= 360.0;
        if (value < -180.0) value += 360.0;
        return value;
    }

    public static Angles targetAngles(double eyeX, double eyeY, double eyeZ, double targetX, double targetY, double targetZ) {
        double dx = targetX - eyeX;
        double dy = targetY - eyeY;
        double dz = targetZ - eyeZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double pitch = -Math.toDegrees(Math.atan2(dy, horizontal));
        return new Angles(wrapDegrees(yaw), clamp(pitch, -90.0, 90.0));
    }

    public static RotationState advance(RotationState state, double targetYaw, double targetPitch, RotationAlgorithm algorithm, double dtSeconds, RotationConfig config) {
        double dt = clamp(dtSeconds, 0.0, 0.25);
        double desiredPitch = clamp(targetPitch, -90.0, 90.0);
        double yawError = wrapDegrees(targetYaw - state.yaw());
        double pitchError = desiredPitch - state.pitch();
        if (algorithm == RotationAlgorithm.Instant || dt <= 0.0) return new RotationState(state.yaw() + yawError, desiredPitch, 0.0, 0.0);

        double maxYaw = Math.max(0.0, config.maxYawSpeedDegPerSec());
        double maxPitch = Math.max(0.0, config.maxPitchSpeedDegPerSec());
        double accel = Math.max(0.0, config.accelerationDegPerSec2());
        double decel = Math.max(0.0, config.decelerationDegPerSec2());
        double smoothing = clamp(config.smoothing(), 0.0, 1.0);

        return switch (algorithm) {
            case Instant -> throw new IllegalStateException("handled above");
            case Linear -> linear(state, yawError, pitchError, dt, maxYaw, maxPitch);
            case Smoothstep -> interpolated(state, yawError, pitchError, dt, maxYaw, maxPitch, smoothing, false);
            case EaseInOut -> interpolated(state, yawError, pitchError, dt, maxYaw, maxPitch, smoothing, true);
            case Accelerate -> accelerated(state, yawError, pitchError, dt, maxYaw, maxPitch, accel, false, decel);
            case AccelerateDecelerate -> accelerated(state, yawError, pitchError, dt, maxYaw, maxPitch, accel, true, decel);
            case Adaptive -> adaptive(state, yawError, pitchError, dt, maxYaw, maxPitch, smoothing);
        };
    }

    private static RotationState linear(RotationState state, double yawError, double pitchError, double dt, double maxYaw, double maxPitch) {
        double yawStep = clampMagnitude(yawError, maxYaw * dt);
        double pitchStep = clampMagnitude(pitchError, maxPitch * dt);
        return new RotationState(state.yaw() + yawStep, clamp(state.pitch() + pitchStep, -90.0, 90.0), yawStep / dt, pitchStep / dt);
    }

    private static RotationState interpolated(RotationState state, double yawError, double pitchError, double dt, double maxYaw, double maxPitch, double smoothing, boolean easeInOut) {
        double yawNorm = clamp(Math.abs(yawError) / 180.0, 0.0, 1.0);
        double pitchNorm = clamp(Math.abs(pitchError) / 90.0, 0.0, 1.0);
        double yawCurve = easeInOut ? easeInOut(yawNorm) : smoothstep(yawNorm);
        double pitchCurve = easeInOut ? easeInOut(pitchNorm) : smoothstep(pitchNorm);
        double baseFactor = 0.10 + 0.75 * smoothing;
        double yawDesired = yawError * clamp(baseFactor + yawCurve * 0.35, 0.05, 1.0);
        double pitchDesired = pitchError * clamp(baseFactor + pitchCurve * 0.35, 0.05, 1.0);
        double yawStep = clampMagnitude(yawDesired, maxYaw * dt);
        double pitchStep = clampMagnitude(pitchDesired, maxPitch * dt);
        return new RotationState(state.yaw() + yawStep, clamp(state.pitch() + pitchStep, -90.0, 90.0), yawStep / dt, pitchStep / dt);
    }

    private static RotationState accelerated(RotationState state, double yawError, double pitchError, double dt, double maxYaw, double maxPitch, double accel, boolean decelerate, double decel) {
        AxisStep yaw = advanceAxis(state.yawVelocity(), yawError, dt, maxYaw, accel, decelerate, decel);
        AxisStep pitch = advanceAxis(state.pitchVelocity(), pitchError, dt, maxPitch, accel, decelerate, decel);
        return new RotationState(state.yaw() + yaw.step(), clamp(state.pitch() + pitch.step(), -90.0, 90.0), yaw.velocity(), pitch.velocity());
    }

    private static AxisStep advanceAxis(double velocity, double error, double dt, double maxSpeed, double acceleration, boolean decelerate, double deceleration) {
        if (Math.abs(error) < 1e-9 || maxSpeed <= 0.0) return new AxisStep(0.0, 0.0);
        double direction = Math.signum(error);
        if (velocity != 0.0 && Math.signum(velocity) != direction) velocity = moveTowards(velocity, 0.0, Math.max(acceleration, deceleration) * dt);
        boolean shouldBrake = decelerate && deceleration > 0.0 && velocity * velocity / (2.0 * deceleration) >= Math.abs(error);
        double targetVelocity = shouldBrake ? 0.0 : direction * maxSpeed;
        double rate = shouldBrake ? deceleration : acceleration;
        if (rate <= 0.0) rate = maxSpeed / Math.max(dt, 1e-6);
        double nextVelocity = moveTowards(velocity, targetVelocity, rate * dt);
        if (Math.abs(nextVelocity) < 1e-6 && shouldBrake) nextVelocity = direction * Math.min(maxSpeed, Math.sqrt(Math.max(0.0, 2.0 * deceleration * Math.abs(error))));
        double step = nextVelocity * dt;
        if (Math.abs(step) > Math.abs(error)) { step = error; nextVelocity = 0.0; }
        return new AxisStep(step, nextVelocity);
    }

    private static RotationState adaptive(RotationState state, double yawError, double pitchError, double dt, double maxYaw, double maxPitch, double smoothing) {
        double yawSpeed = Math.min(maxYaw, Math.max(5.0, Math.abs(yawError) * (1.5 + smoothing * 2.5)));
        double pitchSpeed = Math.min(maxPitch, Math.max(4.0, Math.abs(pitchError) * (1.5 + smoothing * 2.5)));
        double yawStep = clampMagnitude(yawError, yawSpeed * dt);
        double pitchStep = clampMagnitude(pitchError, pitchSpeed * dt);
        return new RotationState(state.yaw() + yawStep, clamp(state.pitch() + pitchStep, -90.0, 90.0), yawStep / dt, pitchStep / dt);
    }

    private static double clampMagnitude(double value, double maxMagnitude) { return maxMagnitude <= 0.0 ? 0.0 : Math.copySign(Math.min(Math.abs(value), maxMagnitude), value); }
    private static double moveTowards(double current, double target, double maxDelta) { if (maxDelta <= 0.0) return current; double delta = target - current; return Math.abs(delta) <= maxDelta ? target : current + Math.copySign(maxDelta, delta); }
    private static double smoothstep(double t) { t = clamp(t, 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }
    private static double easeInOut(double t) { t = clamp(t, 0.0, 1.0); return 0.5 - 0.5 * Math.cos(Math.PI * t); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    public record Angles(double yaw, double pitch) {}
    private record AxisStep(double step, double velocity) {}
}
