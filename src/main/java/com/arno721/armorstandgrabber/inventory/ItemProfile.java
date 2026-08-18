package com.arno721.armorstandgrabber.inventory;

import java.util.List;

public record ItemProfile(
    SlotRef slot,
    String itemId,
    String stackCompatibilityKey,
    int count,
    int maxCount,
    boolean blacklisted,
    List<ItemFacet> facets
) {
    public ItemProfile {
        if (slot == null) throw new IllegalArgumentException("slot cannot be null");
        if (itemId == null) throw new IllegalArgumentException("itemId cannot be null");
        if (stackCompatibilityKey == null) throw new IllegalArgumentException("stackCompatibilityKey cannot be null");
        if (count < 0 || maxCount < 0) throw new IllegalArgumentException("counts must be >= 0");
        facets = List.copyOf(facets);
    }

    public boolean supports(SortChoice choice) {
        return facets.stream().anyMatch(facet -> ItemTypeMapping.matches(choice, facet.type()));
    }

    public ItemFacet bestFacetFor(SortChoice choice) {
        return facets.stream()
            .filter(facet -> ItemTypeMapping.matches(choice, facet.type()))
            .max((left, right) -> ItemScoreComparator.INSTANCE.compare(left.score(), right.score()))
            .orElse(null);
    }
}
