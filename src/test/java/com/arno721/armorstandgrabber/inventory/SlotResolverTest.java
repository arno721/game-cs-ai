package com.arno721.armorstandgrabber.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlotResolverTest {
    @Test
    void playerInventoryIndicesClassifyWithoutAssumingHandlerSlotId() {
        assertEquals(InventoryRegion.PLAYER_HOTBAR, SlotResolver.regionForPlayerIndex(0));
        assertEquals(InventoryRegion.PLAYER_HOTBAR, SlotResolver.regionForPlayerIndex(8));
        assertEquals(InventoryRegion.PLAYER_MAIN, SlotResolver.regionForPlayerIndex(9));
        assertEquals(InventoryRegion.PLAYER_MAIN, SlotResolver.regionForPlayerIndex(35));
        assertEquals(InventoryRegion.PLAYER_ARMOR, SlotResolver.regionForPlayerIndex(36));
        assertEquals(InventoryRegion.PLAYER_ARMOR, SlotResolver.regionForPlayerIndex(39));
        assertEquals(InventoryRegion.PLAYER_OFFHAND, SlotResolver.regionForPlayerIndex(40));
        assertEquals(InventoryRegion.OTHER, SlotResolver.regionForPlayerIndex(41));
    }
}
