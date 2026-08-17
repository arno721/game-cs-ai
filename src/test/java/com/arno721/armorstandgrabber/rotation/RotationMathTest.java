package com.arno721.armorstandgrabber.rotation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RotationMathTest {
    private static final RotationConfig CONFIG = new RotationConfig(60.0, 45.0, 120.0, 180.0, 0.5);

    @Test
    void wrapDegreesUsesShortestPath() {
        assertEquals(-179.0, RotationMath.wrapDegrees(181.0), 1e-9);
        assertEquals(179.0, RotationMath.wrapDegrees(-181.0), 1e-9);
    }

    @Test
    void instantClampsPitch() {
        RotationState next = RotationMath.advance(
            new RotationState(0.0, 0.0, 0.0, 0.0),
            90.0,
            120.0,
            RotationAlgorithm.Instant,
            0.05,
            CONFIG
        );

        assertEquals(90.0, next.yaw(), 1e-9);
        assertEquals(90.0, next.pitch(), 1e-9);
    }

    @Test
    void linearRespectsYawAndPitchSpeedLimits() {
        RotationState next = RotationMath.advance(
            new RotationState(0.0, 0.0, 0.0, 0.0),
            90.0,
            90.0,
            RotationAlgorithm.Linear,
            0.1,
            CONFIG
        );

        assertEquals(6.0, next.yaw(), 1e-6);
        assertEquals(4.5, next.pitch(), 1e-6);
    }

    @Test
    void accelerateBuildsVelocityGradually() {
        RotationState next = RotationMath.advance(
            new RotationState(0.0, 0.0, 0.0, 0.0),
            90.0,
            0.0,
            RotationAlgorithm.Accelerate,
            0.1,
            CONFIG
        );

        assertTrue(next.yawVelocity() > 0.0);
        assertTrue(next.yawVelocity() <= 12.0 + 1e-6);
        assertTrue(next.yaw() > 0.0 && next.yaw() <= 1.2 + 1e-6);
    }

    @Test
    void accelerateDecelerateConvergesWithoutOvershoot() {
        RotationState state = new RotationState(0.0, 0.0, 0.0, 0.0);
        double previousError = 90.0;

        for (int i = 0; i < 200; i++) {
            state = RotationMath.advance(state, 90.0, 0.0, RotationAlgorithm.AccelerateDecelerate, 0.05, CONFIG);
            double error = Math.abs(RotationMath.wrapDegrees(90.0 - state.yaw()));
            assertTrue(error <= previousError + 1e-6, "error grew from " + previousError + " to " + error);
            previousError = error;
        }

        assertEquals(90.0, state.yaw(), 0.25);
    }

    @Test
    void smoothstepConvergesTowardTarget() {
        RotationState state = new RotationState(0.0, 0.0, 0.0, 0.0);
        double initialError = Math.abs(RotationMath.wrapDegrees(75.0 - state.yaw()));

        for (int i = 0; i < 80; i++) {
            state = RotationMath.advance(state, 75.0, -20.0, RotationAlgorithm.Smoothstep, 0.05, CONFIG);
        }

        assertTrue(Math.abs(RotationMath.wrapDegrees(75.0 - state.yaw())) < initialError);
        assertTrue(Math.abs(-20.0 - state.pitch()) < 20.0);
    }

    @Test
    void adaptiveReducesAngularError() {
        RotationState state = new RotationState(-120.0, 35.0, 0.0, 0.0);
        double before = Math.abs(RotationMath.wrapDegrees(80.0 - state.yaw())) + Math.abs(-10.0 - state.pitch());

        RotationState next = RotationMath.advance(state, 80.0, -10.0, RotationAlgorithm.Adaptive, 0.05, CONFIG);
        double after = Math.abs(RotationMath.wrapDegrees(80.0 - next.yaw())) + Math.abs(-10.0 - next.pitch());

        assertTrue(after < before);
    }

    @Test
    void targetAnglesPointsTowardCoordinates() {
        RotationMath.Angles angles = RotationMath.targetAngles(0.0, 1.6, 0.0, 0.0, 1.6, 10.0);
        assertEquals(0.0, angles.yaw(), 1e-6);
        assertEquals(0.0, angles.pitch(), 1e-6);
    }
}
