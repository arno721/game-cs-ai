package com.arno721.armorstandgrabber.delay;

import java.util.Objects;
import java.util.Random;

public final class AggressiveDelayEngine {
    private enum RhythmState { Fast, Normal, Slow }

    private final Random random;
    private long previousDelayMs = -1;
    private double driftPhase;
    private RhythmState rhythmState = RhythmState.Normal;
    private int burstRemaining;
    private int processedItems;
    private int totalItems;

    public AggressiveDelayEngine() { this(new Random()); }
    public AggressiveDelayEngine(Random random) { this.random = Objects.requireNonNull(random, "random"); }

    public void beginSession(int totalItems) {
        previousDelayMs = -1;
        driftPhase = 0.0;
        rhythmState = RhythmState.Normal;
        burstRemaining = 0;
        processedItems = 0;
        this.totalItems = Math.max(0, totalItems);
    }

    public long nextDelayMs(AggressiveAlgorithm algorithm, AggressiveDelayConfig config) {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(config, "config");
        int min = Math.max(0, Math.min(config.minDelayMs(), config.maxDelayMs()));
        int max = Math.max(min, Math.max(config.minDelayMs(), config.maxDelayMs()));
        boolean specialBranch = false;
        double value;

        if (chance(config.pauseChance())) {
            int pauseMin = Math.max(0, Math.min(config.pauseMinMs(), config.pauseMaxMs()));
            int pauseMax = Math.max(pauseMin, Math.max(config.pauseMinMs(), config.pauseMaxMs()));
            value = randomBetween(pauseMin, pauseMax);
            specialBranch = true;
        } else {
            value = switch (algorithm) {
                case MixtureDistribution -> mixture(min, max, config);
                case CorrelatedRandom -> correlated(min, max, config);
                case DriftedGaussian -> driftedGaussian(min, max, config);
                case LogNormal -> logNormal(min, max, config);
                case Gamma -> gammaDelay(min, max, config);
                case BurstPause -> burstPause(min, max, config);
                case MarkovRhythm -> markov(min, max, config);
                case AdaptiveHumanized -> adaptive(min, max, config);
            };
        }

        boolean fullCorrelation = algorithm == AggressiveAlgorithm.CorrelatedRandom
            && previousDelayMs >= 0
            && clamp(config.correlation(), 0.0, 1.0) >= 1.0
            && !specialBranch;

        if (!specialBranch && !fullCorrelation) value = applySessionEnvelope(value, config);
        if (chance(config.outlierChance())) {
            value *= Math.max(0.0, config.outlierScale());
            specialBranch = true;
            fullCorrelation = false;
        }
        if (!specialBranch && !fullCorrelation) {
            value += (max - min) * clamp(config.bias(), -1.0, 1.0) * 0.25;
            value += (max - min) * random.nextGaussian() * Math.max(0.0, config.jitter());
        }
        if (config.clampEnabled() && !specialBranch) value = clamp(value, min, max);

        long result = Math.max(0L, Math.round(value));
        previousDelayMs = result;
        processedItems++;
        return result;
    }

    public StateSnapshot snapshot() {
        return new StateSnapshot(previousDelayMs, driftPhase, rhythmState.name(), burstRemaining, processedItems, totalItems);
    }

    private double mixture(int min, int max, AggressiveDelayConfig c) {
        double selector = random.nextDouble();
        if (selector < 0.35) return gaussian(min, max, c, midpoint(min, max));
        if (selector < 0.65) return randomBetween(min, max);
        if (selector < 0.82) return logNormal(min, max, c);
        return triangular(min, max, c.skew());
    }

    private double correlated(int min, int max, AggressiveDelayConfig c) {
        double fresh = randomBetween(min, max);
        if (previousDelayMs < 0) return fresh;
        double correlation = clamp(c.correlation(), 0.0, 1.0);
        return previousDelayMs * correlation + fresh * (1.0 - correlation);
    }

    private double driftedGaussian(int min, int max, AggressiveDelayConfig c) {
        driftPhase += Math.max(0.0, c.driftSpeed());
        double span = max - min;
        double center = midpoint(min, max) + Math.sin(driftPhase) * span * clamp(c.driftStrength(), 0.0, 1.0);
        return gaussian(min, max, c, center);
    }

    private double logNormal(int min, int max, AggressiveDelayConfig c) {
        double z = random.nextGaussian() * Math.max(0.01, c.sigma()) + c.skew();
        double sample = Math.exp(clamp(z, -6.0, 6.0));
        double normalized = sample / (1.0 + sample);
        return min + (max - min) * normalized;
    }

    private double gammaDelay(int min, int max, AggressiveDelayConfig c) {
        double shape = clamp(2.0 + c.skew(), 0.25, 8.0);
        double gamma = sampleGamma(shape);
        double normalized = gamma / (gamma + shape);
        return min + (max - min) * normalized;
    }

    private double burstPause(int min, int max, AggressiveDelayConfig c) {
        if (burstRemaining > 0) {
            burstRemaining--;
            return compressForBurst(randomBetween(min, max), min, c.burstMultiplier());
        }
        if (chance(c.burstChance())) {
            int low = Math.max(1, Math.min(c.burstSizeMin(), c.burstSizeMax()));
            int high = Math.max(low, Math.max(c.burstSizeMin(), c.burstSizeMax()));
            int size = low + random.nextInt(high - low + 1);
            burstRemaining = Math.max(0, size - 1);
            return compressForBurst(randomBetween(min, max), min, c.burstMultiplier());
        }
        return randomBetween(min, max);
    }

    private double markov(int min, int max, AggressiveDelayConfig c) {
        double r = random.nextDouble();
        rhythmState = switch (rhythmState) {
            case Fast -> r < 0.62 ? RhythmState.Fast : r < 0.90 ? RhythmState.Normal : RhythmState.Slow;
            case Normal -> r < 0.20 ? RhythmState.Fast : r < 0.78 ? RhythmState.Normal : RhythmState.Slow;
            case Slow -> r < 0.12 ? RhythmState.Fast : r < 0.48 ? RhythmState.Normal : RhythmState.Slow;
        };
        double base = randomBetween(min, max);
        return switch (rhythmState) {
            case Fast -> min + (base - min) * 0.62;
            case Normal -> base;
            case Slow -> midpoint(min, max) + (base - min) * 0.72;
        };
    }

    private double adaptive(int min, int max, AggressiveDelayConfig c) {
        driftPhase += Math.max(0.0, c.driftSpeed());
        double fresh = mixture(min, max, c);
        fresh += Math.sin(driftPhase) * (max - min) * clamp(c.driftStrength(), 0.0, 1.0);
        if (previousDelayMs >= 0) {
            double correlation = clamp(c.correlation(), 0.0, 1.0);
            double momentum = clamp(c.momentum(), 0.0, 1.0);
            double correlated = previousDelayMs * correlation + fresh * (1.0 - correlation);
            fresh = correlated * (1.0 - momentum * 0.5) + previousDelayMs * (momentum * 0.5);
        }
        if (burstRemaining > 0) {
            burstRemaining--;
            fresh = compressForBurst(fresh, min, c.burstMultiplier());
        } else if (chance(c.burstChance())) {
            int low = Math.max(1, Math.min(c.burstSizeMin(), c.burstSizeMax()));
            int high = Math.max(low, Math.max(c.burstSizeMin(), c.burstSizeMax()));
            int size = low + random.nextInt(high - low + 1);
            burstRemaining = Math.max(0, size - 1);
            fresh = compressForBurst(fresh, min, c.burstMultiplier());
        }
        return fresh;
    }

    private double gaussian(int min, int max, AggressiveDelayConfig c, double center) {
        double span = max - min;
        double sigma = Math.max(0.01, c.sigma()) * Math.max(1.0, span);
        double g = random.nextGaussian();
        double skewTerm = Math.signum(g) * Math.pow(Math.abs(g), 1.0 + Math.abs(c.skew())) * c.skew() * span * 0.08;
        return center + random.nextGaussian() * sigma + skewTerm;
    }

    private double triangular(int min, int max, double skew) {
        double u = random.nextDouble();
        double mode = clamp(0.5 + clamp(skew, -1.0, 1.0) * 0.35, 0.05, 0.95);
        double x = u < mode ? Math.sqrt(u * mode) : 1.0 - Math.sqrt((1.0 - u) * (1.0 - mode));
        return min + (max - min) * x;
    }

    private double sampleGamma(double shape) {
        if (shape < 1.0) {
            double u = Math.max(1e-12, random.nextDouble());
            return sampleGamma(shape + 1.0) * Math.pow(u, 1.0 / shape);
        }
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        for (int i = 0; i < 64; i++) {
            double x = random.nextGaussian();
            double v = 1.0 + c * x;
            if (v <= 0.0) continue;
            v = v * v * v;
            double u = random.nextDouble();
            if (u < 1.0 - 0.0331 * x * x * x * x) return d * v;
            if (Math.log(Math.max(1e-12, u)) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) return d * v;
        }
        return shape;
    }

    private double applySessionEnvelope(double value, AggressiveDelayConfig c) {
        if (c.warmupItems() > 0 && processedItems < c.warmupItems()) {
            double progress = (processedItems + 1.0) / c.warmupItems();
            value *= 1.0 + Math.max(0.0, c.acceleration()) * (1.0 - progress);
        }
        if (totalItems > 0 && c.cooldownItems() > 0) {
            int remainingAfterThis = Math.max(0, totalItems - (processedItems + 1));
            if (remainingAfterThis < c.cooldownItems()) {
                double progress = 1.0 - remainingAfterThis / (double) c.cooldownItems();
                value *= 1.0 + Math.max(0.0, c.deceleration()) * progress;
            }
        }
        return value;
    }

    private boolean chance(double probability) { return random.nextDouble() < clamp(probability, 0.0, 1.0); }
    private double randomBetween(int min, int max) { return max <= min ? min : min + random.nextDouble() * (max - min); }
    private static double compressForBurst(double value, double min, double multiplier) { return min + (value - min) * clamp(multiplier, 0.0, 1.0); }
    private static double midpoint(double min, double max) { return min + (max - min) * 0.5; }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    public record StateSnapshot(long previousDelayMs, double driftPhase, String rhythmState, int burstRemaining, int processedItems, int totalItems) {}
}
