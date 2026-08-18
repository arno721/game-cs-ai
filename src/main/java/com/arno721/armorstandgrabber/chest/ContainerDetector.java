package com.arno721.armorstandgrabber.chest;

import com.arno721.armorstandgrabber.inventory.InventoryRegion;
import com.arno721.armorstandgrabber.inventory.SlotRef;
import com.arno721.armorstandgrabber.inventory.SlotResolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ContainerDetector {
    public Optional<ContainerDescriptor> detect(MinecraftClient mc, ContainerFilterConfig config) {
        if (mc == null || mc.player == null || !(mc.currentScreen instanceof HandledScreen<?> screen)) {
            return Optional.empty();
        }

        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null || handler == mc.player.playerScreenHandler) return Optional.empty();

        if (config.checkHandlerType() && !isSupportedHandler(handler)) return Optional.empty();

        String title = screen.getTitle().getString();
        if (config.checkTitle() && !titleAllowed(title, config)) return Optional.empty();

        PlayerInventory playerInventory = mc.player.getInventory();
        List<Integer> containerSlots = new ArrayList<>();
        for (int slotId = 0; slotId < handler.slots.size(); slotId++) {
            SlotRef ref = SlotResolver.classify(handler, slotId, playerInventory);
            if (ref.region() == InventoryRegion.CONTAINER) containerSlots.add(slotId);
        }
        if (containerSlots.isEmpty()) return Optional.empty();

        return Optional.of(new ContainerDescriptor(
            handler.syncId,
            title,
            handler.getClass().getName(),
            containerSlots
        ));
    }

    public static boolean titleAllowed(String title, ContainerFilterConfig config) {
        String normalized = normalize(title);
        for (String blocked : config.titleBlacklist()) {
            if (normalized.equals(normalize(blocked))) return false;
        }
        if (!config.checkTitle()) return true;
        for (String allowed : config.titleWhitelist()) {
            if (normalized.equals(normalize(allowed))) return true;
        }
        return false;
    }

    private static String normalize(String title) {
        return title == null ? "" : title.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean isSupportedHandler(ScreenHandler handler) {
        if (handler instanceof ShulkerBoxScreenHandler) return true;
        if (handler instanceof GenericContainerScreenHandler generic) {
            int rows = generic.getRows();
            return rows == 3 || rows == 6;
        }
        return false;
    }
}
