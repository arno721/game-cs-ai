package com.arno721.armorstandgrabber.chest;

import com.arno721.armorstandgrabber.runtime.InventoryMutex;
import com.arno721.armorstandgrabber.runtime.RuntimeOwner;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;

public final class InventoryTransactionExecutor {
    private final MinecraftClient mc;
    private final InventoryMutex mutex;

    public InventoryTransactionExecutor(MinecraftClient mc, InventoryMutex mutex) {
        this.mc = mc;
        this.mutex = mutex;
    }

    public boolean execute(ContainerDescriptor expected, InventoryTransaction transaction) {
        if (mc.player == null || mc.interactionManager == null || transaction.actions().isEmpty()) return false;
        if (!matches(expected)) return false;
        if (!mutex.isOwnedBy(RuntimeOwner.CHEST_STEALER)) return false;

        mutex.beginAtomic(RuntimeOwner.CHEST_STEALER);
        boolean started = false;
        try {
            for (InventoryAction action : transaction.actions()) {
                if (!matches(expected)) {
                    if (started && matches(expected)) {
                        // Defensive branch kept explicit: never continue against a stale handler.
                        return false;
                    }
                    return false;
                }
                if (action.atomicStart()) started = true;
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    action.slotId(),
                    action.button(),
                    action.actionType(),
                    mc.player
                );
            }
            return true;
        } finally {
            mutex.endAtomic(RuntimeOwner.CHEST_STEALER);
        }
    }

    private boolean matches(ContainerDescriptor expected) {
        if (expected == null || mc.player == null) return false;
        ScreenHandler handler = mc.player.currentScreenHandler;
        return handler != null
            && handler.syncId == expected.syncId()
            && handler.getClass().getName().equals(expected.handlerClassName());
    }
}
