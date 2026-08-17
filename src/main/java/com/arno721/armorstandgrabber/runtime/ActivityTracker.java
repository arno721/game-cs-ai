package com.arno721.armorstandgrabber.runtime;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.util.PlayerInput;

public final class ActivityTracker {
    public ActivitySnapshot snapshot(
        MinecraftClient mc,
        RotationCoordinator rotation,
        CombatTracker combat
    ) {
        if (mc.player == null) {
            return new ActivitySnapshot(false, false, false, false, combat.active(), false);
        }

        PlayerInput input = mc.player.input == null ? PlayerInput.DEFAULT : mc.player.input.playerInput;
        boolean moving = input.forward()
            || input.backward()
            || input.left()
            || input.right()
            || input.jump();
        boolean usingItem = mc.player.isUsingItem();
        boolean breakingBlock = mc.interactionManager != null && mc.interactionManager.isBreakingBlock();
        boolean inventoryOpen = mc.currentScreen instanceof InventoryScreen;

        return new ActivitySnapshot(
            moving,
            rotation.changedThisTick(),
            usingItem,
            breakingBlock,
            combat.active(),
            inventoryOpen
        );
    }

    public void reset() {
        // Currently stateless; retained as an explicit lifecycle hook.
    }
}
