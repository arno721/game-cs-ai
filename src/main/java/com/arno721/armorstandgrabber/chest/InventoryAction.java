package com.arno721.armorstandgrabber.chest;

import net.minecraft.screen.slot.SlotActionType;

public record InventoryAction(
    int slotId,
    int button,
    SlotActionType actionType,
    boolean atomicStart,
    boolean atomicEnd
) {}
