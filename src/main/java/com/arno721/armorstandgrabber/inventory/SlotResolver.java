package com.arno721.armorstandgrabber.inventory;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public final class SlotResolver {
    private SlotResolver() {}

    public static InventoryRegion regionForPlayerIndex(int playerIndex) {
        if (playerIndex >= 0 && playerIndex <= 8) return InventoryRegion.PLAYER_HOTBAR;
        if (playerIndex >= 9 && playerIndex <= 35) return InventoryRegion.PLAYER_MAIN;
        if (playerIndex >= 36 && playerIndex <= 39) return InventoryRegion.PLAYER_ARMOR;
        if (playerIndex == 40) return InventoryRegion.PLAYER_OFFHAND;
        return InventoryRegion.OTHER;
    }

    public static SlotRef classify(ScreenHandler handler, int slotId, PlayerInventory playerInventory) {
        if (slotId < 0 || slotId >= handler.slots.size()) {
            throw new IllegalArgumentException("slotId outside handler range: " + slotId);
        }

        Slot slot = handler.slots.get(slotId);
        if (slot.inventory == playerInventory) {
            int playerIndex = slot.getIndex();
            return new SlotRef(slotId, regionForPlayerIndex(playerIndex), playerIndex);
        }

        int firstPlayerSlotId = firstPlayerOwnedSlotId(handler, playerInventory);
        InventoryRegion region = firstPlayerSlotId < 0 || slotId < firstPlayerSlotId
            ? InventoryRegion.CONTAINER
            : InventoryRegion.OTHER;
        return new SlotRef(slotId, region, slot.getIndex());
    }

    public static int playerHotbarSlotId(ScreenHandler handler, PlayerInventory playerInventory, int hotbarIndex) {
        if (hotbarIndex < 0 || hotbarIndex > 8) throw new IllegalArgumentException("hotbarIndex must be 0..8");
        for (int slotId = 0; slotId < handler.slots.size(); slotId++) {
            Slot slot = handler.slots.get(slotId);
            if (slot.inventory == playerInventory && slot.getIndex() == hotbarIndex) return slotId;
        }
        return -1;
    }

    public static int playerOffhandSlotId(ScreenHandler handler, PlayerInventory playerInventory) {
        for (int slotId = 0; slotId < handler.slots.size(); slotId++) {
            Slot slot = handler.slots.get(slotId);
            if (slot.inventory == playerInventory && slot.getIndex() == 40) return slotId;
        }
        return -1;
    }

    private static int firstPlayerOwnedSlotId(ScreenHandler handler, PlayerInventory playerInventory) {
        for (int slotId = 0; slotId < handler.slots.size(); slotId++) {
            if (handler.slots.get(slotId).inventory == playerInventory) return slotId;
        }
        return -1;
    }
}
