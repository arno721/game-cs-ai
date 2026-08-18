package com.arno721.armorstandgrabber.chest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class SelectionPlanner {
    public List<SelectionCandidate> order(List<SelectionCandidate> candidates, SelectionConfig config) {
        if (candidates.isEmpty()) return List.of();
        List<SelectionCandidate> copy = new ArrayList<>(candidates);
        return switch (config.mode()) {
            case Index -> index(copy, config.indexOrder());
            case Random -> {
                config.random().shuffle(copy);
                yield List.copyOf(copy);
            }
            case Distance -> distance(copy, config);
        };
    }

    private List<SelectionCandidate> index(List<SelectionCandidate> candidates, IndexOrder order) {
        Comparator<SelectionCandidate> comparator = Comparator.comparingInt(SelectionCandidate::slotId);
        if (order == IndexOrder.Descending) comparator = comparator.reversed();
        candidates.sort(comparator);
        return List.copyOf(candidates);
    }

    private List<SelectionCandidate> distance(List<SelectionCandidate> candidates, SelectionConfig config) {
        SelectionCandidate current = switch (config.distanceStartItem()) {
            case Default -> candidates.getFirst();
            case Random -> candidates.get(config.random().nextInt(candidates.size()));
            case MinSlot -> candidates.stream().min(Comparator.comparingInt(SelectionCandidate::slotId)).orElseThrow();
            case MaxSlot -> candidates.stream().max(Comparator.comparingInt(SelectionCandidate::slotId)).orElseThrow();
        };

        List<SelectionCandidate> remaining = new ArrayList<>(candidates);
        remaining.remove(current);
        List<SelectionCandidate> result = new ArrayList<>(candidates.size());
        result.add(current);

        while (!remaining.isEmpty()) {
            SelectionCandidate best = null;
            double bestScore = Double.POSITIVE_INFINITY;
            for (SelectionCandidate candidate : remaining) {
                double factor = sampleFactor(config.random(), config.randomFactorMin(), config.randomFactorMax());
                double score = current.distanceTo(candidate) * factor;
                if (best == null || score < bestScore || (Double.compare(score, bestScore) == 0 && candidate.slotId() < best.slotId())) {
                    best = candidate;
                    bestScore = score;
                }
            }
            result.add(best);
            remaining.remove(best);
            current = best;
        }
        return List.copyOf(result);
    }

    private static double sampleFactor(Random random, double min, double max) {
        double low = Math.min(min, max);
        double high = Math.max(min, max);
        if (Double.compare(low, high) == 0) return low;
        return low + random.nextDouble() * (high - low);
    }
}
