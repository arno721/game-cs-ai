package com.arno721.armorstandgrabber.inventory;

public enum SortChoice {
    Sword, Weapon, Spear, Mace, Bow, Crossbow, Axe, Pickaxe, Shovel, Hoe,
    Rod, Shield, Water, Lava, Milk, Pearl, Gapple, Food, Potion, Block,
    Throwables, Ignore, None;

    public boolean protectsSlot() {
        return this == Ignore;
    }
}
