package com.arno721.armorstandgrabber.rotation;

public record RotationConfig(
    double maxYawSpeedDegPerSec,
    double maxPitchSpeedDegPerSec,
    double accelerationDegPerSec2,
    double decelerationDegPerSec2,
    double smoothing
) {}
