package com.arno721.armorstandgrabber.inventory;

public record ItemScore(
    double primary,
    double secondary,
    double tertiary,
    double quaternary,
    boolean hotbarPreferred,
    int stableSlotTieBreaker
) {
    public static ItemScore weapon(
        double damage,
        double attackSpeed,
        int enchantWeight,
        int durability,
        boolean hotbar,
        int slot
    ) {
        return new ItemScore(damage, attackSpeed, enchantWeight, durability, hotbar, slot);
    }

    public static ItemScore tool(
        double miningSpeed,
        boolean silkTouch,
        int fortune,
        int durability,
        boolean hotbar,
        int slot
    ) {
        return new ItemScore(miningSpeed, silkTouch ? 1 : 0, fortune, durability, hotbar, slot);
    }

    public static ItemScore generic(double primary, boolean hotbar, int slot) {
        return new ItemScore(primary, 0, 0, 0, hotbar, slot);
    }
}
