package com.arno721.armorstandgrabber.chest;

import java.util.Objects;
import java.util.Random;

public record SelectionConfig(
    ChestSelectionMode mode,
    DistanceStartItem distanceStartItem,
    double randomFactorMin,
    double randomFactorMax,
    IndexOrder indexOrder,
    Random random
) {
    public SelectionConfig {
        mode = Objects.requireNonNull(mode);
        distanceStartItem = Objects.requireNonNull(distanceStartItem);
        indexOrder = Objects.requireNonNull(indexOrder);
        random = Objects.requireNonNull(random);
        if (!Double.isFinite(randomFactorMin) || !Double.isFinite(randomFactorMax)) {
            throw new IllegalArgumentException("random factors must be finite");
        }
    }

    public static SelectionConfig distance(DistanceStartItem start, double min, double max, Random random) {
        return new SelectionConfig(ChestSelectionMode.Distance, start, min, max, IndexOrder.Ascending, random);
    }

    public static SelectionConfig index(IndexOrder order) {
        return new SelectionConfig(ChestSelectionMode.Index, DistanceStartItem.Default, 1.0, 1.0, order, new Random(0));
    }

    public static SelectionConfig random(Random random) {
        return new SelectionConfig(ChestSelectionMode.Random, DistanceStartItem.Default, 1.0, 1.0, IndexOrder.Ascending, random);
    }
}
