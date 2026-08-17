package com.arno721.armorstandgrabber.modules;

import com.arno721.armorstandgrabber.ArmorStandGrabberAddon;
import meteordevelopment.meteorclient.events.entity.player.DoItemUseEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
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

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance used to target armor stands through blocks.")
        .defaultValue(6.0)
        .min(1.0)
        .sliderRange(1.0, 20.0)
        .build()
    );

    public ArmorStandGrabber() {
        super(ArmorStandGrabberAddon.CATEGORY, "armor-stand-grabber", "Right-click an armor stand through blocks to move all of its equipment into empty hotbar slots.");
    }

    @EventHandler
    private void onUse(DoItemUseEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!mc.options.useKey.isPressed()) return;

        ArmorStandEntity armorStand = findTarget();
        if (armorStand == null) return;

        // While the module owns an armor-stand right-click, vanilla interaction must not run as well.
        event.cancel();

        List<Integer> emptyHotbarSlots = findEmptyHotbarSlots();
        if (emptyHotbarSlots.isEmpty()) {
            warning("No empty hotbar slots.");
            return;
        }

        int originalSlot = mc.player.getInventory().getSelectedSlot();
        int emptyIndex = 0;
        boolean attempted = false;

        try {
            for (EquipmentSlot equipmentSlot : SLOT_ORDER) {
                if (armorStand.getEquippedStack(equipmentSlot).isEmpty()) continue;
                if (emptyIndex >= emptyHotbarSlots.size()) break;

                int targetHotbarSlot = emptyHotbarSlots.get(emptyIndex++);
                if (!InvUtils.swap(targetHotbarSlot, false)) continue;

                Vec3d hitPos = interactionPoint(armorStand, equipmentSlot);
                mc.interactionManager.interactEntityAtLocation(
                    mc.player,
                    armorStand,
                    new EntityHitResult(armorStand, hitPos),
                    Hand.MAIN_HAND
                );
                mc.player.swingHand(Hand.MAIN_HAND);
                attempted = true;
            }
        } finally {
            InvUtils.swap(originalSlot, false);
        }

        if (!attempted) warning("The targeted armor stand has no removable equipment or no usable hotbar slot.");
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

    private List<Integer> findEmptyHotbarSlots() {
        List<Integer> slots = new ArrayList<>(9);

        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) slots.add(slot);
        }

        return slots;
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
}
