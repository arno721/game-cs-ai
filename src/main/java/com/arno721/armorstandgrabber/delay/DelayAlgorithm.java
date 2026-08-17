package com.arno721.armorstandgrabber.delay;

public enum DelayAlgorithm {
    FixedMidpoint("Fixed Midpoint"),
    UniformRandom("Uniform Random"),
    Gaussian("Gaussian"),
    Triangular("Triangular"),
    FastBiased("Fast Biased"),
    SlowBiased("Slow Biased"),
    MidpointJitter("Midpoint Jitter"),
    Alternating("Alternating"),
    HumanizedDrift("Humanized Drift");

    private final String title;

    DelayAlgorithm(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return title;
    }
}
