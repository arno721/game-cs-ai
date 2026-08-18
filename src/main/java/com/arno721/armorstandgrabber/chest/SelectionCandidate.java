package com.arno721.armorstandgrabber.chest;

public record SelectionCandidate(int slotId, int column, int row) {
    public double distanceTo(SelectionCandidate other) {
        return Math.hypot(column - other.column, row - other.row);
    }
}
