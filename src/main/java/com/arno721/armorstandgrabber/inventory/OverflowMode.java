package com.arno721.armorstandgrabber.inventory;

public enum OverflowMode {
    Off("Off"),
    Silent("Silent"),
    AutoOpen("Auto Open");

    private final String title;

    OverflowMode(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return title;
    }
}
