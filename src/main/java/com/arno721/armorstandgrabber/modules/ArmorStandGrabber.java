package com.arno721.armorstandgrabber.modules;

import com.arno721.armorstandgrabber.ArmorStandGrabberAddon;
import com.arno721.armorstandgrabber.delay.DelayAlgorithm;
import com.arno721.armorstandgrabber.delay.DelaySampler;
import com.arno721.armorstandgrabber.inventory.OverflowMode;
import meteordevelopment.meteorclient.events.entity.player.DoItemUseEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public class ArmorStandGrabber extends Module {
    private static final EquipmentSlot[] SLOT_ORDER = {
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND
    };

    private static final long INTERACTION_TIMEOUT_MS = 1500;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDelay = settings.createGroup("Delay");
    private final SettingGroup sgInventory = settings.createGroup("Inventory");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance used to target armor stands through blocks.")
        .defaultValue(6.0)
        .min(1.0)
        .sliderRange(1.0, 20.0)
        .build()
    );

    private final Setting<Integer> minDelay = sgDelay.add(new IntSetting.Builder()
        .name("min-delay")
        .description("Minimum delay in milliseconds between successfully retrieved items.")
        .defaultValue(80)
        .range(0, 5000)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Integer> maxDelay = sgDelay.add(new IntSetting.Builder()
        .name("max-delay")
        .description("Maximum delay in milliseconds between successfully retrieved items.")
        .defaultValue(180)
        .range(0, 5000)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<DelayAlgorithm> delayAlgorithm = sgDelay.add(new EnumSetting.Builder<DelayAlgorithm>()
        .name("delay-algorithm")
        .description("Distribution used to choose each delay between Min Delay and Max Delay.")
        .defaultValue(DelayAlgorithm.HumanizedDrift)
        .build()
    );

    private final Setting<OverflowMode> overflowMode = sgInventory.add(new EnumSetting.Builder<OverflowMode>()
        .name("overflow-mode")
        .description("What to do when there are no empty hotbar slots left.")
        .defaultValue(OverflowMode.Off)
        .build()
    );

    private final Setting<Boolean> autoClose = sgInventory.add(new BoolSetting.Builder()
        .name("auto-close")
        .description("Closes the inventory after Auto Open finishes or aborts.")
        .defaultValue(true)
        .visible(() -> overflowMode.get() == OverflowMode.AutoOpen)
        .build()
    );

    private final DelaySampler delaySampler = new DelaySampler();
    private final Deque<EquipmentSlot> pendingSlots = new ArrayDeque<>();

    private SessionState sessionState = SessionState.Idle;
    private ArmorStandEntity activeTarget;
    private EquipmentSlot currentEquipmentSlot;
    private int originalSelectedSlot = -1;
    private int currentHotbarSlot = -1;
    private int reservedInventorySlot = -1;
    private boolean overflowTransfer;
    private boolean openedInventoryScreen;
    private long nextActionAtMs;
    private long interactionDeadlineMs;

    public ArmorStandGrabber() {
        super(ArmorStandGrabberAddon.CATEGORY, "armor-stand-grabber", "Right-click an armor stand through blocks to retrieve all equipment with configurable delays and inventory overflow handling.");
    }

    @Override
    public void onDeactivate() {
        finishSession(false, null);
    }

    @EventHandler
    private void onUse(DoItemUseEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!mc.options.useKey.isPressed()) return;

        if (sessionState != SessionState.Idle) {
            event.cancel();
            return;
        }

        ArmorStandEntity armorStand = findTarget();
        if (armorStand == null) return;

        event.cancel();
        beginSession(armorStand);
        processSession(nowMs());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (sessionState != SessionState.Idle) processSession(nowMs());
    }

    private void beginSession(ArmorStandEntity armorStand) {
        pendingSlots.clear();
        for (EquipmentSlot slot : SLOT_ORDER) {
            if (!armorStand.getEquippedStack(slot).isEmpty()) pendingSlots.addLast(slot);
        }

        if (pendingSlots.isEmpty()) {
            warning("The targeted armor stand has no equipment to retrieve.");
            resetSessionState();
            return;
        }

        activeTarget = armorStand;
        originalSelectedSlot = mc.player.getInventory().getSelectedSlot();
        openedInventoryScreen = false;
        nextActionAtMs = nowMs();
        sessionState = SessionState.WaitDelay;
    }

    private void processSession(long now) {
        if (sessionState == SessionState.Idle) return;

        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            finishSession(false, null);
            return;
        }

        if (activeTarget == null || !activeTarget.isAlive()) {
            finishSession(false, "Armor stand is no longer available.");
            return;
        }

        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) {
            finishSession(false, "Another screen interrupted the retrieval session.");
            return;
        }

        if (sessionState == SessionState.WaitDelay) {
            if (now >= nextActionAtMs) startNextTransfer(now);
            return;
        }

        if (sessionState == SessionState.WaitItem) {
            if (currentHotbarSlot >= 0 && !mc.player.getInventory().getStack(currentHotbarSlot).isEmpty()) {
                if (overflowTransfer && !finishOverflowTransfer()) {
                    finishSession(false, "Could not move the retrieved item into the inventory.");
                    return;
                }

                completeCurrentItem(now);
            } else if (now >= interactionDeadlineMs) {
                finishSession(false, "Timed out waiting for the armor stand interaction.");
            }
        }
    }

    private void startNextTransfer(long now) {
        while (!pendingSlots.isEmpty() && activeTarget.getEquippedStack(pendingSlots.peekFirst()).isEmpty()) {
            pendingSlots.removeFirst();
        }

        if (pendingSlots.isEmpty()) {
            finishSession(true, null);
            return;
        }

        currentEquipmentSlot = pendingSlots.peekFirst();
        overflowTransfer = false;
        reservedInventorySlot = -1;

        int hotbarSlot = findEmptyHotbarSlot();
        if (hotbarSlot >= 0) {
            currentHotbarSlot = hotbarSlot;
        } else {
            if (overflowMode.get() == OverflowMode.Off) {
                finishSession(false, "No empty hotbar slots remain.");
                return;
            }

            int inventorySlot = findEmptyMainInventorySlot();
            if (inventorySlot < 0) {
                finishSession(false, "No empty main inventory slots remain.");
                return;
            }

            if (!prepareOverflowScreen()) {
                finishSession(false, "Could not open or use the player inventory.");
                return;
            }

            currentHotbarSlot = originalSelectedSlot >= 0 && originalSelectedSlot <= 8
                ? originalSelectedSlot
                : mc.player.getInventory().getSelectedSlot();
            reservedInventorySlot = inventorySlot;

            if (!swapHotbarWithInventory(currentHotbarSlot, reservedInventorySlot)) {
                finishSession(false, "Could not prepare an inventory overflow slot.");
                return;
            }

            overflowTransfer = true;
            if (!mc.player.getInventory().getStack(currentHotbarSlot).isEmpty()) {
                finishSession(false, "The temporary hotbar buffer did not become empty.");
                return;
            }
        }

        if (!InvUtils.swap(currentHotbarSlot, false)) {
            finishSession(false, "Could not select an empty hotbar slot.");
            return;
        }

        Vec3d hitPos = interactionPoint(activeTarget, currentEquipmentSlot);
        mc.interactionManager.interactEntityAtLocation(
            mc.player,
            activeTarget,
            new EntityHitResult(activeTarget, hitPos),
            Hand.MAIN_HAND
        );
        mc.player.swingHand(Hand.MAIN_HAND);

        interactionDeadlineMs = now + INTERACTION_TIMEOUT_MS;
        sessionState = SessionState.WaitItem;
    }

    private boolean finishOverflowTransfer() {
        if (!overflowTransfer) return true;
        if (currentHotbarSlot < 0 || reservedInventorySlot < 0) return false;

        if (!swapHotbarWithInventory(currentHotbarSlot, reservedInventorySlot)) return false;

        overflowTransfer = false;
        reservedInventorySlot = -1;
        return true;
    }

    private void completeCurrentItem(long now) {
        if (!pendingSlots.isEmpty() && pendingSlots.peekFirst() == currentEquipmentSlot) {
            pendingSlots.removeFirst();
        } else if (currentEquipmentSlot != null) {
            pendingSlots.remove(currentEquipmentSlot);
        }

        currentEquipmentSlot = null;
        currentHotbarSlot = -1;
        interactionDeadlineMs = 0;

        if (pendingSlots.isEmpty()) {
            finishSession(true, null);
            return;
        }

        nextActionAtMs = now + delaySampler.nextDelayMs(delayAlgorithm.get(), minDelay.get(), maxDelay.get());
        sessionState = SessionState.WaitDelay;
    }

    private boolean prepareOverflowScreen() {
        if (overflowMode.get() != OverflowMode.AutoOpen) return true;

        if (mc.currentScreen == null) {
            mc.setScreen(new InventoryScreen(mc.player));
            openedInventoryScreen = true;
            return true;
        }

        return mc.currentScreen instanceof InventoryScreen;
    }

    private boolean swapHotbarWithInventory(int hotbarSlot, int inventorySlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) return false;
        if (inventorySlot < SlotUtils.MAIN_START || inventorySlot > SlotUtils.MAIN_END) return false;

        int slotId = SlotUtils.indexToId(inventorySlot);
        if (slotId < 0) return false;

        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            slotId,
            hotbarSlot,
            SlotActionType.SWAP,
            mc.player
        );
        return true;
    }

    private void finishSession(boolean completed, String message) {
        if (mc.player != null && mc.interactionManager != null && overflowTransfer
            && currentHotbarSlot >= 0 && reservedInventorySlot >= 0) {
            swapHotbarWithInventory(currentHotbarSlot, reservedInventorySlot);
        }

        if (mc.player != null && originalSelectedSlot >= 0 && originalSelectedSlot <= 8) {
            InvUtils.swap(originalSelectedSlot, false);
        }

        if (openedInventoryScreen && autoClose.get() && mc.currentScreen instanceof InventoryScreen) {
            mc.setScreen(null);
        }

        if (!completed && message != null) warning(message);
        resetSessionState();
    }

    private void resetSessionState() {
        pendingSlots.clear();
        sessionState = SessionState.Idle;
        activeTarget = null;
        currentEquipmentSlot = null;
        originalSelectedSlot = -1;
        currentHotbarSlot = -1;
        reservedInventorySlot = -1;
        overflowTransfer = false;
        openedInventoryScreen = false;
        nextActionAtMs = 0;
        interactionDeadlineMs = 0;
    }

    private ArmorStandEntity findTarget() {
        float tickProgress = mc.getRenderTickCounter().getTickProgress(true);
        Vec3d start = mc.player.getCameraPosVec(tickProgress);
        Vec3d direction = mc.player.getRotationVec(tickProgress).normalize();
        Vec3d end = start.add(direction.multiply(range.get()));

        Box searchBox = mc.player.getBoundingBox()
            .stretch(direction.multiply(range.get()))
            .expand(1.0);

        ArmorStandEntity best = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (ArmorStandEntity armorStand : mc.world.getEntitiesByClass(ArmorStandEntity.class, searchBox, entity -> entity.isAlive())) {
            Optional<Vec3d> hit = armorStand.getBoundingBox().expand(0.15).raycast(start, end);
            if (hit.isEmpty()) continue;

            double distanceSq = start.squaredDistanceTo(hit.get());
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = armorStand;
            }
        }

        return best;
    }

    private int findEmptyHotbarSlot() {
        for (int slot = SlotUtils.HOTBAR_START; slot <= SlotUtils.HOTBAR_END; slot++) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) return slot;
        }

        return -1;
    }

    private int findEmptyMainInventorySlot() {
        for (int slot = SlotUtils.MAIN_START; slot <= SlotUtils.MAIN_END; slot++) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) return slot;
        }

        return -1;
    }

    private Vec3d interactionPoint(ArmorStandEntity armorStand, EquipmentSlot slot) {
        double scale = armorStand.isSmall() ? 0.5 : 1.0;
        double y = switch (slot) {
            case HEAD -> 1.80 * scale;
            case CHEST -> 1.35 * scale;
            case LEGS -> 0.80 * scale;
            case FEET -> 0.30 * scale;
            case MAINHAND, OFFHAND -> 0.05 * scale;
            default -> 0.05 * scale;
        };

        return new Vec3d(armorStand.getX(), armorStand.getY() + y, armorStand.getZ());
    }

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    private enum SessionState {
        Idle,
        WaitDelay,
        WaitItem
    }
}
