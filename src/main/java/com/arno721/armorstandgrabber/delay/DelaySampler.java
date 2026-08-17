package com.arno721.armorstandgrabber.delay;

import java.util.Random;

public final class DelaySampler {
    private final Random random;
    private boolean alternatingHigh;
    private double humanizedDrift;

    public DelaySampler() {
        this(new Random());
    }

    public DelaySampler(Random random) {
        this.random = random;
    }

    public long nextDelayMs(DelayAlgorithm algorithm, int minMs, int maxMs) {
        int low = Math.min(minMs, maxMs);
        int high = Math.max(minMs, maxMs);
        if (low == high) return low;

        double t = switch (algorithm) {
            case FixedMidpoint -> 0.5;
            case UniformRandom -> random.nextDouble();
            case Gaussian -> clamp01(0.5 + random.nextGaussian() * 0.18);
            case Triangular -> (random.nextDouble() + random.nextDouble()) * 0.5;
            case FastBiased -> {
                double u = random.nextDouble();
                yield u * u;
            }
            case SlowBiased -> {
                double u = random.nextDouble();
                yield 1.0 - (1.0 - u) * (1.0 - u);
            }
            case MidpointJitter -> 0.25 + random.nextDouble() * 0.5;
            case Alternating -> {
                alternatingHigh = !alternatingHigh;
                double base = alternatingHigh ? 0.72 : 0.28;
                yield clamp01(base + (random.nextDouble() - 0.5) * 0.18);
            }
            case HumanizedDrift -> {
                humanizedDrift = clamp(random.nextGaussian() * 0.10 + humanizedDrift * 0.72, -0.28, 0.28);
                yield clamp01(0.5 + humanizedDrift + (random.nextDouble() - 0.5) * 0.14);
            }
        };

        long value = Math.round(low + (high - low) * clamp01(t));
        return Math.max(low, Math.min(high, value));
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
