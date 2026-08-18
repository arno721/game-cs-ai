package com.arno721.armorstandgrabber.inventory;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class InventorySnapshotFactory {
    public InventorySnapshot capture(MinecraftClient mc, ItemProfiler profiler, Set<String> blacklist) {
        if (mc.player == null || mc.interactionManager == null) return null;

        ScreenHandler handler = mc.player.currentScreenHandler;
        List<ItemProfile> profiles = new ArrayList<>();
        Map<Integer, SlotRef> bySlotId = new HashMap<>();

        for (int slotId = 0; slotId < handler.slots.size(); slotId++) {
            SlotRef ref = SlotResolver.classify(handler, slotId, mc.player.getInventory());
            bySlotId.put(slotId, ref);
            ItemStack stack = handler.slots.get(slotId).getStack();
            if (!stack.isEmpty()) profiles.add(profiler.profile(stack, ref, blacklist));
        }

        return new InventorySnapshot(
            handler.syncId,
            profiles,
            bySlotId,
            handler.getCursorStack().getCount()
        );
    }
}
