package com.arno721.armorstandgrabber.inventory;

final class ItemTypeMapping {
    private ItemTypeMapping() {}

    static boolean matches(SortChoice choice, ItemType type) {
        return switch (choice) {
            case Sword -> type == ItemType.SWORD;
            case Weapon -> type == ItemType.WEAPON || type == ItemType.SWORD || type == ItemType.SPEAR
                || type == ItemType.MACE || type == ItemType.AXE;
            case Spear -> type == ItemType.SPEAR;
            case Mace -> type == ItemType.MACE;
            case Bow -> type == ItemType.BOW;
            case Crossbow -> type == ItemType.CROSSBOW;
            case Axe -> type == ItemType.AXE;
            case Pickaxe -> type == ItemType.PICKAXE;
            case Shovel -> type == ItemType.SHOVEL;
            case Hoe -> type == ItemType.HOE;
            case Rod -> type == ItemType.ROD;
            case Shield -> type == ItemType.SHIELD;
            case Water -> type == ItemType.WATER;
            case Lava -> type == ItemType.LAVA;
            case Milk -> type == ItemType.MILK;
            case Pearl -> type == ItemType.PEARL;
            case Gapple -> type == ItemType.GAPPLE;
            case Food -> type == ItemType.FOOD || type == ItemType.GAPPLE;
            case Potion -> type == ItemType.POTION;
            case Block -> type == ItemType.BLOCK;
            case Throwables -> type == ItemType.THROWABLE || type == ItemType.PEARL;
            case Ignore, None -> false;
        };
    }
}
