package com.arno721.armorstandgrabber.delay;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AggressiveDelayEngineTest {
    @Test
    void allAlgorithmsStayNonNegativeAndBoundedWhenSpecialBranchesAreDisabled() {
        AggressiveDelayConfig config = AggressiveDelayConfig.builder()
            .range(40, 220)
            .pauseChance(0.0)
            .outlierChance(0.0)
            .clampEnabled(true)
            .build();

        for (AggressiveAlgorithm algorithm : AggressiveAlgorithm.values()) {
            AggressiveDelayEngine engine = new AggressiveDelayEngine(new Random(12345L));
            engine.beginSession(64);

            for (int i = 0; i < 256; i++) {
                long value = engine.nextDelayMs(algorithm, config);
                assertTrue(value >= 40, algorithm + " returned below min: " + value);
                assertTrue(value <= 220, algorithm + " returned above max: " + value);
            }
        }
    }

    @Test
    void reversedRangeIsNormalized() {
        AggressiveDelayConfig config = AggressiveDelayConfig.builder()
            .range(300, 100)
            .pauseChance(0.0)
            .outlierChance(0.0)
            .clampEnabled(true)
            .build();
        AggressiveDelayEngine engine = new AggressiveDelayEngine(new Random(1L));
        engine.beginSession(16);

        for (int i = 0; i < 64; i++) {
            long value = engine.nextDelayMs(AggressiveAlgorithm.MixtureDistribution, config);
            assertTrue(value >= 100 && value <= 300);
        }
    }

    @Test
    void fullCorrelationUsesPreviousDelay() {
        AggressiveDelayConfig config = AggressiveDelayConfig.builder()
            .range(50, 250)
            .correlation(1.0)
            .pauseChance(0.0)
            .outlierChance(0.0)
            .clampEnabled(true)
            .build();
        AggressiveDelayEngine engine = new AggressiveDelayEngine(new Random(99L));
        engine.beginSession(10);

        long first = engine.nextDelayMs(AggressiveAlgorithm.CorrelatedRandom, config);
        long second = engine.nextDelayMs(AggressiveAlgorithm.CorrelatedRandom, config);

        assertEquals(first, second);
    }

    @Test
    void driftPhaseEvolvesAcrossSamples() {
        AggressiveDelayConfig config = AggressiveDelayConfig.builder()
            .range(80, 180)
            .driftStrength(0.4)
            .driftSpeed(0.6)
            .pauseChance(0.0)
            .outlierChance(0.0)
            .build();
        AggressiveDelayEngine engine = new AggressiveDelayEngine(new Random(7L));
        engine.beginSession(10);

        double before = engine.snapshot().driftPhase();
        engine.nextDelayMs(AggressiveAlgorithm.DriftedGaussian, config);
        double after = engine.snapshot().driftPhase();

        assertNotEquals(before, after);
    }

    @Test
    void burstCounterEntersAndExitsDeterministically() {
        AggressiveDelayConfig config = AggressiveDelayConfig.builder()
            .range(100, 200)
            .burstChance(1.0)
            .burstSize(2, 2)
            .burstMultiplier(0.5)
            .pauseChance(0.0)
            .outlierChance(0.0)
            .build();
        AggressiveDelayEngine engine = new AggressiveDelayEngine(new Random(2L));
        engine.beginSession(8);

        engine.nextDelayMs(AggressiveAlgorithm.BurstPause, config);
        assertEquals(1, engine.snapshot().burstRemaining());
        engine.nextDelayMs(AggressiveAlgorithm.BurstPause, config);
        assertEquals(0, engine.snapshot().burstRemaining());
    }

    @Test
    void forcedPauseUsesPauseBounds() {
        AggressiveDelayConfig config = AggressiveDelayConfig.builder()
            .range(40, 100)
            .pauseChance(1.0)
            .pauseRange(500, 700)
            .outlierChance(0.0)
            .build();
        AggressiveDelayEngine engine = new AggressiveDelayEngine(new Random(3L));
        engine.beginSession(5);

        for (int i = 0; i < 20; i++) {
            long delay = engine.nextDelayMs(AggressiveAlgorithm.BurstPause, config);
            assertTrue(delay >= 500 && delay <= 700, "pause delay=" + delay);
        }
    }

    @Test
    void beginSessionResetsState() {
        AggressiveDelayEngine engine = new AggressiveDelayEngine(new Random(4L));
        AggressiveDelayConfig config = AggressiveDelayConfig.builder().build();
        engine.beginSession(6);
        engine.nextDelayMs(AggressiveAlgorithm.AdaptiveHumanized, config);
        engine.nextDelayMs(AggressiveAlgorithm.AdaptiveHumanized, config);
        assertTrue(engine.snapshot().processedItems() > 0);

        engine.beginSession(9);
        AggressiveDelayEngine.StateSnapshot snapshot = engine.snapshot();
        assertEquals(0, snapshot.processedItems());
        assertEquals(0, snapshot.burstRemaining());
        assertEquals(9, snapshot.totalItems());
        assertEquals(-1L, snapshot.previousDelayMs());
    }

    @Test
    void warmupAndCooldownEnvelopeCanChangeDelay() {
        AggressiveDelayConfig config = AggressiveDelayConfig.builder()
            .range(100, 300)
            .warmupItems(2)
            .cooldownItems(2)
            .acceleration(0.5)
            .deceleration(0.5)
            .pauseChance(0.0)
            .outlierChance(0.0)
            .clampEnabled(false)
            .build();
        AggressiveDelayEngine engine = new AggressiveDelayEngine(new Random(15L));
        engine.beginSession(5);

        long warmup = engine.nextDelayMs(AggressiveAlgorithm.AdaptiveHumanized, config);
        engine.nextDelayMs(AggressiveAlgorithm.AdaptiveHumanized, config);
        engine.nextDelayMs(AggressiveAlgorithm.AdaptiveHumanized, config);
        long cooldown = engine.nextDelayMs(AggressiveAlgorithm.AdaptiveHumanized, config);

        assertNotEquals(warmup, cooldown);
    }
}
