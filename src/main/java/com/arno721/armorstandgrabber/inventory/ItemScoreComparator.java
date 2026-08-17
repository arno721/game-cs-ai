package com.arno721.armorstandgrabber.inventory;

import java.util.Comparator;

public enum ItemScoreComparator implements Comparator<ItemScore> {
    INSTANCE;

    @Override
    public int compare(ItemScore left, ItemScore right) {
        int result = Double.compare(left.primary(), right.primary());
        if (result != 0) return result;
        result = Double.compare(left.secondary(), right.secondary());
        if (result != 0) return result;
        result = Double.compare(left.tertiary(), right.tertiary());
        if (result != 0) return result;
        result = Double.compare(left.quaternary(), right.quaternary());
        if (result != 0) return result;
        result = Boolean.compare(left.hotbarPreferred(), right.hotbarPreferred());
        if (result != 0) return result;
        return Integer.compare(right.stableSlotTieBreaker(), left.stableSlotTieBreaker());
    }
}
