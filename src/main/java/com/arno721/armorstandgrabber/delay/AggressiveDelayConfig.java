package com.arno721.armorstandgrabber.delay;

public record AggressiveDelayConfig(
    int minDelayMs,
    int maxDelayMs,
    double sigma,
    double skew,
    double bias,
    double jitter,
    double correlation,
    double momentum,
    double driftStrength,
    double driftSpeed,
    double burstChance,
    int burstSizeMin,
    int burstSizeMax,
    double burstMultiplier,
    double pauseChance,
    int pauseMinMs,
    int pauseMaxMs,
    double outlierChance,
    double outlierScale,
    int warmupItems,
    int cooldownItems,
    double acceleration,
    double deceleration,
    boolean clampEnabled
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int minDelayMs = 70;
        private int maxDelayMs = 240;
        private double sigma = 0.22;
        private double skew = 0.0;
        private double bias = 0.0;
        private double jitter = 0.08;
        private double correlation = 0.35;
        private double momentum = 0.25;
        private double driftStrength = 0.15;
        private double driftSpeed = 0.35;
        private double burstChance = 0.12;
        private int burstSizeMin = 2;
        private int burstSizeMax = 4;
        private double burstMultiplier = 0.55;
        private double pauseChance = 0.08;
        private int pauseMinMs = 250;
        private int pauseMaxMs = 650;
        private double outlierChance = 0.04;
        private double outlierScale = 1.8;
        private int warmupItems = 2;
        private int cooldownItems = 2;
        private double acceleration = 0.15;
        private double deceleration = 0.25;
        private boolean clampEnabled = true;

        public Builder range(int minMs, int maxMs) { this.minDelayMs = minMs; this.maxDelayMs = maxMs; return this; }
        public Builder sigma(double value) { this.sigma = value; return this; }
        public Builder skew(double value) { this.skew = value; return this; }
        public Builder bias(double value) { this.bias = value; return this; }
        public Builder jitter(double value) { this.jitter = value; return this; }
        public Builder correlation(double value) { this.correlation = value; return this; }
        public Builder momentum(double value) { this.momentum = value; return this; }
        public Builder driftStrength(double value) { this.driftStrength = value; return this; }
        public Builder driftSpeed(double value) { this.driftSpeed = value; return this; }
        public Builder burstChance(double value) { this.burstChance = value; return this; }
        public Builder burstSize(int min, int max) { this.burstSizeMin = min; this.burstSizeMax = max; return this; }
        public Builder burstMultiplier(double value) { this.burstMultiplier = value; return this; }
        public Builder pauseChance(double value) { this.pauseChance = value; return this; }
        public Builder pauseRange(int min, int max) { this.pauseMinMs = min; this.pauseMaxMs = max; return this; }
        public Builder outlierChance(double value) { this.outlierChance = value; return this; }
        public Builder outlierScale(double value) { this.outlierScale = value; return this; }
        public Builder warmupItems(int value) { this.warmupItems = value; return this; }
        public Builder cooldownItems(int value) { this.cooldownItems = value; return this; }
        public Builder acceleration(double value) { this.acceleration = value; return this; }
        public Builder deceleration(double value) { this.deceleration = value; return this; }
        public Builder clampEnabled(boolean value) { this.clampEnabled = value; return this; }

        public AggressiveDelayConfig build() {
            return new AggressiveDelayConfig(minDelayMs, maxDelayMs, sigma, skew, bias, jitter, correlation, momentum, driftStrength, driftSpeed, burstChance, burstSizeMin, burstSizeMax, burstMultiplier, pauseChance, pauseMinMs, pauseMaxMs, outlierChance, outlierScale, warmupItems, cooldownItems, acceleration, deceleration, clampEnabled);
        }
    }
}
