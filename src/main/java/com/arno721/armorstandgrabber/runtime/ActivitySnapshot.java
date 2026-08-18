package com.arno721.armorstandgrabber.runtime;

public record ActivitySnapshot(
    boolean moving,
    boolean rotating,
    boolean usingItem,
    boolean breakingBlock,
    boolean combatActive,
    boolean inventoryOpen
) {}
