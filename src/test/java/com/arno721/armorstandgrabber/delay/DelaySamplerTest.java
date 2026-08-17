package com.arno721.armorstandgrabber.delay;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelaySamplerTest {
    @Test
    void everyAlgorithmStaysInsideBounds() {
        for (DelayAlgorithm algorithm : DelayAlgorithm.values()) {
            DelaySampler sampler = new DelaySampler(new Random(12345L));

            for (int i = 0; i < 500; i++) {
                long delay = sampler.nextDelayMs(algorithm, 80, 180);
                assertTrue(delay >= 80 && delay <= 180, algorithm + " returned " + delay);
            }
        }
    }

    @Test
    void equalBoundsAlwaysReturnExactValue() {
        DelaySampler sampler = new DelaySampler(new Random(1L));

        for (DelayAlgorithm algorithm : DelayAlgorithm.values()) {
            assertEquals(125, sampler.nextDelayMs(algorithm, 125, 125));
        }
    }

    @Test
    void reversedBoundsAreNormalized() {
        DelaySampler sampler = new DelaySampler(new Random(7L));

        for (DelayAlgorithm algorithm : DelayAlgorithm.values()) {
            for (int i = 0; i < 100; i++) {
                long delay = sampler.nextDelayMs(algorithm, 220, 60);
                assertTrue(delay >= 60 && delay <= 220, algorithm + " returned " + delay);
            }
        }
    }
}
