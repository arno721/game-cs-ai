package com.arno721.armorstandgrabber.inventory;

import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.slot.SlotActionType;

public final class InventoryOverflowController {
    private final MinecraftClient mc;
    private int hotbarSlot = -1;
    private int inventorySlot = -1;
    private boolean transferActive;
    private boolean openedInventoryScreen;

    public InventoryOverflowController(MinecraftClient mc) {
        this.mc = mc;
    }

    public int prepareOverflow(int originalSelectedSlot, OverflowMode mode) {
        if (mc.player == null || mc.interactionManager == null || mode == OverflowMode.Off) return -1;
        int destination = findEmptyMainInventorySlot();
        if (destination < 0) return -1;

        if (mode == OverflowMode.AutoOpen) {
            if (mc.currentScreen == null) {
                mc.setScreen(new InventoryScreen(mc.player));
                openedInventoryScreen = true;
            } else if (!(mc.currentScreen instanceof InventoryScreen)) return -1;
        }

        int buffer = originalSelectedSlot >= 0 && originalSelectedSlot <= 8 ? originalSelectedSlot : mc.player.getInventory().getSelectedSlot();
        hotbarSlot = buffer;
        inventorySlot = destination;
        if (!swapHotbarWithInventory(hotbarSlot, inventorySlot)) {
            resetTransfer();
            return -1;
        }

        transferActive = true;
        if (!mc.player.getInventory().getStack(hotbarSlot).isEmpty()) {
            abortTransfer();
            return -1;
        }
        return hotbarSlot;
    }

    public boolean finishTransfer() {
        if (!transferActive) return true;
        if (!swapHotbarWithInventory(hotbarSlot, inventorySlot)) return false;
        resetTransfer();
        return true;
    }

    public void abortTransfer() {
        if (transferActive) swapHotbarWithInventory(hotbarSlot, inventorySlot);
        resetTransfer();
    }

    public void finishScreen(boolean autoClose) {
        if (openedInventoryScreen && autoClose && mc.currentScreen instanceof InventoryScreen) mc.setScreen(null);
        openedInventoryScreen = false;
    }

    private int findEmptyMainInventorySlot() {
        for (int slot = SlotUtils.MAIN_START; slot <= SlotUtils.MAIN_END; slot++) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private boolean swapHotbarWithInventory(int hotbar, int inventory) {
        if (mc.player == null || mc.interactionManager == null) return false;
        if (hotbar < SlotUtils.HOTBAR_START || hotbar > SlotUtils.HOTBAR_END) return false;
        if (inventory < SlotUtils.MAIN_START || inventory > SlotUtils.MAIN_END) return false;
        int slotId = SlotUtils.indexToId(inventory);
        if (slotId < 0) return false;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotId, hotbar, SlotActionType.SWAP, mc.player);
        return true;
    }

    private void resetTransfer() {
        hotbarSlot = -1;
        inventorySlot = -1;
        transferActive = false;
    }
}
