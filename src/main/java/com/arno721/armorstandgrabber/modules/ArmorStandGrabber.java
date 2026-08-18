package com.arno721.armorstandgrabber.modules;

import com.arno721.armorstandgrabber.ArmorStandGrabberAddon;
import com.arno721.armorstandgrabber.delay.AggressiveAlgorithm;
import com.arno721.armorstandgrabber.delay.AggressiveDelayConfig;
import com.arno721.armorstandgrabber.delay.AggressiveDelayEngine;
import com.arno721.armorstandgrabber.delay.DelayAlgorithm;
import com.arno721.armorstandgrabber.delay.DelayProfile;
import com.arno721.armorstandgrabber.delay.DelaySampler;
import com.arno721.armorstandgrabber.inventory.InventoryOverflowController;
import com.arno721.armorstandgrabber.inventory.OverflowMode;
import com.arno721.armorstandgrabber.rotation.RotationAlgorithm;
import com.arno721.armorstandgrabber.rotation.RotationConfig;
import com.arno721.armorstandgrabber.rotation.RotationController;
import com.arno721.armorstandgrabber.rotation.RotationMode;
import com.arno721.armorstandgrabber.rotation.RotationTarget;
import com.arno721.armorstandgrabber.runtime.InventoryMutex;
import com.arno721.armorstandgrabber.runtime.RotationCoordinator;
import com.arno721.armorstandgrabber.runtime.RuntimeOwner;
import com.arno721.armorstandgrabber.runtime.RuntimeTickParticipant;
import com.arno721.armorstandgrabber.session.RetrievalPhase;
import com.arno721.armorstandgrabber.session.RetrievalSession;
import meteordevelopment.meteorclient.events.entity.player.DoItemUseEvent;
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
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

public class ArmorStandGrabber extends Module implements RuntimeTickParticipant {
    private static final EquipmentSlot[] SLOT_ORDER = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND };
    private static final long INTERACTION_TIMEOUT_MS = 1500L;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDelay = settings.createGroup("Delay");
    private final SettingGroup sgLegit = settings.createGroup("Legit");
    private final SettingGroup sgAggressive = settings.createGroup("Aggressive");
    private final SettingGroup sgInventory = settings.createGroup("Inventory");
    private final SettingGroup sgRotation = settings.createGroup("Rotation");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range").description("Maximum distance used to target armor stands through blocks.").defaultValue(6.0).min(1.0).sliderRange(1.0, 20.0).build());
    private final Setting<DelayProfile> delayProfile = sgDelay.add(new EnumSetting.Builder<DelayProfile>().name("delay-profile").description("Normal, Legit, or stateful Aggressive timing.").defaultValue(DelayProfile.Normal).build());

    private final Setting<Integer> minDelay = sgDelay.add(new IntSetting.Builder().name("min-delay").description("Normal profile minimum item delay in milliseconds.").defaultValue(80).range(0, 5000).sliderRange(0, 1000).visible(() -> delayProfile.get() == DelayProfile.Normal).build());
    private final Setting<Integer> maxDelay = sgDelay.add(new IntSetting.Builder().name("max-delay").description("Normal profile maximum item delay in milliseconds.").defaultValue(180).range(0, 5000).sliderRange(0, 1000).visible(() -> delayProfile.get() == DelayProfile.Normal).build());
    private final Setting<DelayAlgorithm> delayAlgorithm = sgDelay.add(new EnumSetting.Builder<DelayAlgorithm>().name("delay-algorithm").description("Normal profile item-delay distribution.").defaultValue(DelayAlgorithm.HumanizedDrift).visible(() -> delayProfile.get() == DelayProfile.Normal).build());

    private final Setting<Integer> legitSwitchMin = legitInt("switch-min-delay-ms", "Minimum delay after visibly switching to an empty hotbar slot.", 70, 0, 5000);
    private final Setting<Integer> legitSwitchMax = legitInt("switch-max-delay-ms", "Maximum delay after visibly switching to an empty hotbar slot.", 140, 0, 5000);
    private final Setting<DelayAlgorithm> legitSwitchAlgorithm = sgLegit.add(new EnumSetting.Builder<DelayAlgorithm>().name("switch-delay-algorithm").description("Legit switch-delay distribution.").defaultValue(DelayAlgorithm.HumanizedDrift).visible(() -> delayProfile.get() == DelayProfile.Legit).build());
    private final Setting<Integer> legitItemMin = legitInt("item-min-delay-ms", "Minimum delay between retrieved items.", 90, 0, 5000);
    private final Setting<Integer> legitItemMax = legitInt("item-max-delay-ms", "Maximum delay between retrieved items.", 210, 0, 5000);
    private final Setting<DelayAlgorithm> legitItemAlgorithm = sgLegit.add(new EnumSetting.Builder<DelayAlgorithm>().name("item-delay-algorithm").description("Legit item-delay distribution.").defaultValue(DelayAlgorithm.HumanizedDrift).visible(() -> delayProfile.get() == DelayProfile.Legit).build());

    private final Setting<AggressiveAlgorithm> aggressiveAlgorithm = sgAggressive.add(new EnumSetting.Builder<AggressiveAlgorithm>().name("algorithm").description("Stateful aggressive timing algorithm.").defaultValue(AggressiveAlgorithm.AdaptiveHumanized).visible(this::aggressiveVisible).build());
    private final Setting<Integer> aggressiveMin = aggressiveInt("min-delay-ms", "Aggressive base minimum delay.", 55, 0, 5000);
    private final Setting<Integer> aggressiveMax = aggressiveInt("max-delay-ms", "Aggressive base maximum delay.", 260, 0, 5000);
    private final Setting<Double> sigma = aggressiveDouble("sigma", "Gaussian/log-normal spread.", 0.22, 0.01, 2.0);
    private final Setting<Double> skew = aggressiveDouble("skew", "Distribution asymmetry from -1 to 1.", 0.0, -1.0, 1.0);
    private final Setting<Double> bias = aggressiveDouble("bias", "Global timing bias from fast (-1) to slow (+1).", 0.0, -1.0, 1.0);
    private final Setting<Double> jitter = aggressiveDouble("jitter", "Independent random jitter strength.", 0.08, 0.0, 1.0);
    private final Setting<Double> correlation = aggressiveDouble("correlation", "How strongly a delay follows the preceding delay.", 0.35, 0.0, 1.0);
    private final Setting<Double> momentum = aggressiveDouble("momentum", "Additional temporal inertia.", 0.25, 0.0, 1.0);
    private final Setting<Double> driftStrength = aggressiveDouble("drift-strength", "Amplitude of slow timing drift.", 0.15, 0.0, 1.0);
    private final Setting<Double> driftSpeed = aggressiveDouble("drift-speed", "Rate at which timing drift evolves.", 0.35, 0.0, 3.0);
    private final Setting<Double> burstChance = aggressiveDouble("burst-chance", "Chance to enter a short faster burst.", 0.12, 0.0, 1.0);
    private final Setting<Integer> burstSizeMin = aggressiveInt("burst-size-min", "Minimum number of samples in a burst.", 2, 1, 12);
    private final Setting<Integer> burstSizeMax = aggressiveInt("burst-size-max", "Maximum number of samples in a burst.", 4, 1, 12);
    private final Setting<Double> burstMultiplier = aggressiveDouble("burst-multiplier", "Compression applied while bursting.", 0.55, 0.05, 1.0);
    private final Setting<Double> pauseChance = aggressiveDouble("pause-chance", "Chance to insert a long pause.", 0.08, 0.0, 1.0);
    private final Setting<Integer> pauseMin = aggressiveInt("pause-min-ms", "Minimum explicit pause length.", 250, 0, 10000);
    private final Setting<Integer> pauseMax = aggressiveInt("pause-max-ms", "Maximum explicit pause length.", 650, 0, 10000);
    private final Setting<Double> outlierChance = aggressiveDouble("outlier-chance", "Chance to produce an outlier.", 0.04, 0.0, 1.0);
    private final Setting<Double> outlierScale = aggressiveDouble("outlier-scale", "Outlier multiplier.", 1.8, 1.0, 6.0);
    private final Setting<Integer> warmupItems = aggressiveInt("warmup-samples", "Early samples affected by warm-up.", 2, 0, 20);
    private final Setting<Integer> cooldownItems = aggressiveInt("cooldown-samples", "Final samples affected by cool-down.", 2, 0, 20);
    private final Setting<Double> acceleration = aggressiveDouble("acceleration", "Warm-up envelope strength.", 0.15, 0.0, 1.0);
    private final Setting<Double> deceleration = aggressiveDouble("deceleration", "Cool-down envelope strength.", 0.25, 0.0, 1.0);
    private final Setting<Boolean> clampAggressive = sgAggressive.add(new BoolSetting.Builder().name("clamp").description("Clamp ordinary aggressive samples to the base range.").defaultValue(true).visible(this::aggressiveVisible).build());

    private final Setting<OverflowMode> overflowMode = sgInventory.add(new EnumSetting.Builder<OverflowMode>().name("overflow-mode").description("Behavior when the hotbar is full.").defaultValue(OverflowMode.Off).build());
    private final Setting<Boolean> autoClose = sgInventory.add(new BoolSetting.Builder().name("auto-close").description("Close an inventory opened by Auto Open when finished.").defaultValue(true).visible(() -> overflowMode.get() == OverflowMode.AutoOpen).build());

    private final Setting<Boolean> rotationEnabled = sgRotation.add(new BoolSetting.Builder().name("rotation-enabled").description("Keep rotation aimed at the active armor stand until retrieval finishes.").defaultValue(false).build());
    private final Setting<RotationMode> rotationMode = sgRotation.add(new EnumSetting.Builder<RotationMode>().name("rotation-mode").description("Lock changes the visible camera; Silent only changes server-side look packets.").defaultValue(RotationMode.Lock).visible(rotationEnabled::get).build());
    private final Setting<RotationTarget> rotationTarget = sgRotation.add(new EnumSetting.Builder<RotationTarget>().name("rotation-target").description("Point on the armor stand tracked by rotation.").defaultValue(RotationTarget.InteractionPoint).visible(rotationEnabled::get).build());
    private final Setting<RotationAlgorithm> rotationAlgorithm = sgRotation.add(new EnumSetting.Builder<RotationAlgorithm>().name("rotation-algorithm").description("Tracking interpolation/acceleration algorithm.").defaultValue(RotationAlgorithm.Adaptive).visible(rotationEnabled::get).build());
    private final Setting<Double> maxYawSpeed = rotationDouble("max-yaw-speed", "Maximum yaw speed in degrees per second.", 240.0, 10.0, 1080.0);
    private final Setting<Double> maxPitchSpeed = rotationDouble("max-pitch-speed", "Maximum pitch speed in degrees per second.", 180.0, 10.0, 1080.0);
    private final Setting<Double> rotationAcceleration = rotationDouble("acceleration", "Angular acceleration in degrees per second squared.", 720.0, 10.0, 3000.0);
    private final Setting<Double> rotationDeceleration = rotationDouble("deceleration", "Angular deceleration in degrees per second squared.", 900.0, 10.0, 3000.0);
    private final Setting<Double> rotationSmoothing = rotationDouble("smoothing", "Interpolation strength for smooth/adaptive algorithms.", 0.55, 0.0, 1.0);

    private final DelaySampler delaySampler = new DelaySampler();
    private final AggressiveDelayEngine aggressiveDelayEngine = new AggressiveDelayEngine();
    private final RetrievalSession session = new RetrievalSession();
    private final InventoryOverflowController overflowController = new InventoryOverflowController(mc);
    private final InventoryMutex inventoryMutex;
    private final RotationController rotationController;
    private String abortMessage;

    public ArmorStandGrabber(InventoryMutex inventoryMutex, RotationCoordinator rotationCoordinator) {
        super(ArmorStandGrabberAddon.CATEGORY, "armor-stand-grabber", "Retrieve armor-stand equipment through blocks with configurable timing, overflow, and rotation tracking.");
        this.inventoryMutex = inventoryMutex;
        this.rotationController = new RotationController(mc, rotationCoordinator);
    }

    @Override
    public void onDeactivate() {
        finishSession(false, null);
    }

    @Override
    public RuntimeOwner runtimeOwner() {
        return RuntimeOwner.ARMOR_STAND_GRABBER;
    }

    @Override
    public void onRuntimeTick() {
        if (!isActive() || session.phase() == RetrievalPhase.Idle) return;
        if (!validateSession()) return;
        if (rotationEnabled.get() && rotationController.isActive()) {
            rotationController.tick(session.currentSlot(), rotationMode.get(), rotationTarget.get(), rotationAlgorithm.get(), rotationConfig());
        }
        processSession(nowMs());
    }

    @EventHandler
    private void onUse(DoItemUseEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || !mc.options.useKey.isPressed()) return;
        if (session.phase() != RetrievalPhase.Idle) {
            event.cancel();
            return;
        }
        ArmorStandEntity armorStand = findTarget();
        if (armorStand == null) return;
        event.cancel();
        beginSession(armorStand);
        processSession(nowMs());
    }

    private void beginSession(ArmorStandEntity armorStand) {
        if (!inventoryMutex.tryAcquire(RuntimeOwner.ARMOR_STAND_GRABBER)) {
            warning("Another inventory automation is finishing an atomic transaction.");
            return;
        }

        session.begin(armorStand, mc.player.getInventory().getSelectedSlot(), SLOT_ORDER);
        if (!session.hasPending()) {
            warning("The targeted armor stand has no equipment to retrieve.");
            session.reset();
            releaseInventoryOwnership();
            return;
        }
        if (delayProfile.get() == DelayProfile.Aggressive) aggressiveDelayEngine.beginSession(session.pendingCount() * 2);
        if (rotationEnabled.get()) rotationController.begin(armorStand);
        abortMessage = null;
    }

    private void processSession(long now) {
        for (int guard = 0; guard < 10 && session.phase() != RetrievalPhase.Idle; guard++) {
            switch (session.phase()) {
                case PrepareSlot -> {
                    session.discardEmptyFrontSlots();
                    if (!session.hasPending()) {
                        session.phase(RetrievalPhase.Complete);
                        continue;
                    }
                    session.currentSlot(session.peekPending());
                    int hotbarSlot = findEmptyHotbarSlot();
                    if (hotbarSlot < 0) {
                        if (overflowMode.get() == OverflowMode.Off) {
                            abort("No empty hotbar slots remain.");
                            continue;
                        }
                        hotbarSlot = overflowController.prepareOverflow(
                            session.originalSelectedSlot(),
                            overflowMode.get(),
                            this::beginInventoryAtomic,
                            this::endInventoryAtomic
                        );
                        if (hotbarSlot < 0) {
                            abort("No usable inventory overflow destination remains.");
                            continue;
                        }
                    }
                    session.currentHotbarSlot(hotbarSlot);
                    if (!InvUtils.swap(hotbarSlot, false)) {
                        abort("Could not select the destination hotbar slot.");
                        continue;
                    }
                    if (delayProfile.get() == DelayProfile.Normal) session.phase(RetrievalPhase.RotateForInteraction);
                    else {
                        session.deadlineMs(now + sampleSwitchDelay());
                        session.phase(RetrievalPhase.WaitSwitchDelay);
                        return;
                    }
                }
                case WaitSwitchDelay -> {
                    if (now < session.deadlineMs()) return;
                    session.phase(RetrievalPhase.RotateForInteraction);
                }
                case RotateForInteraction -> {
                    if (rotationEnabled.get()) {
                        rotationController.prepareInteraction(session.currentSlot(), rotationMode.get(), rotationTarget.get(), rotationAlgorithm.get(), rotationConfig());
                    }
                    session.phase(RetrievalPhase.Interact);
                }
                case Interact -> {
                    Vec3d hitPos = interactionPoint(session.target(), session.currentSlot());
                    mc.interactionManager.interactEntityAtLocation(mc.player, session.target(), new EntityHitResult(session.target(), hitPos), Hand.MAIN_HAND);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    session.interactionDeadlineMs(now + INTERACTION_TIMEOUT_MS);
                    session.phase(RetrievalPhase.WaitItem);
                    return;
                }
                case WaitItem -> {
                    int slot = session.currentHotbarSlot();
                    if (slot >= 0 && !mc.player.getInventory().getStack(slot).isEmpty()) {
                        session.phase(RetrievalPhase.FinishOverflow);
                        continue;
                    }
                    if (now >= session.interactionDeadlineMs()) {
                        abort("Timed out waiting for the armor stand interaction.");
                        continue;
                    }
                    return;
                }
                case FinishOverflow -> {
                    if (!overflowController.finishTransfer(this::beginInventoryAtomic, this::endInventoryAtomic)) {
                        abort("Could not move the retrieved item into the inventory.");
                        continue;
                    }
                    session.completeCurrentSlot();
                    session.discardEmptyFrontSlots();
                    if (!session.hasPending()) {
                        session.phase(RetrievalPhase.Complete);
                        continue;
                    }
                    session.deadlineMs(now + sampleItemDelay());
                    session.phase(RetrievalPhase.WaitItemDelay);
                    return;
                }
                case WaitItemDelay -> {
                    if (now < session.deadlineMs()) return;
                    session.phase(RetrievalPhase.PrepareSlot);
                }
                case Complete -> {
                    finishSession(true, null);
                    return;
                }
                case Abort -> {
                    finishSession(false, abortMessage);
                    return;
                }
                case Idle -> {
                    return;
                }
            }
        }
    }

    private boolean validateSession() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            finishSession(false, null);
            return false;
        }
        if (session.target() == null || !session.target().isAlive()) {
            finishSession(false, "Armor stand is no longer available.");
            return false;
        }
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) {
            finishSession(false, "Another screen interrupted the retrieval session.");
            return false;
        }
        return true;
    }

    private void abort(String message) {
        abortMessage = message;
        session.phase(RetrievalPhase.Abort);
    }

    private void finishSession(boolean completed, String message) {
        overflowController.abortTransfer(this::beginInventoryAtomic, this::endInventoryAtomic);
        if (mc.player != null && session.originalSelectedSlot() >= 0 && session.originalSelectedSlot() <= 8) {
            InvUtils.swap(session.originalSelectedSlot(), false);
        }
        overflowController.finishScreen(autoClose.get());
        rotationController.stop();
        aggressiveDelayEngine.beginSession(0);
        if (!completed && message != null) warning(message);
        session.reset();
        abortMessage = null;
        releaseInventoryOwnership();
    }

    private void beginInventoryAtomic() {
        if (!inventoryMutex.isOwnedBy(RuntimeOwner.ARMOR_STAND_GRABBER)) {
            if (!inventoryMutex.tryAcquire(RuntimeOwner.ARMOR_STAND_GRABBER)) {
                throw new IllegalStateException("Armor Stand Grabber could not reacquire inventory ownership");
            }
        }
        inventoryMutex.beginAtomic(RuntimeOwner.ARMOR_STAND_GRABBER);
    }

    private void endInventoryAtomic() {
        inventoryMutex.endAtomic(RuntimeOwner.ARMOR_STAND_GRABBER);
    }

    private void releaseInventoryOwnership() {
        if (inventoryMutex.isOwnedBy(RuntimeOwner.ARMOR_STAND_GRABBER) && !inventoryMutex.atomic()) {
            inventoryMutex.release(RuntimeOwner.ARMOR_STAND_GRABBER);
        }
    }

    private long sampleSwitchDelay() {
        return switch (delayProfile.get()) {
            case Normal -> 0L;
            case Legit -> delaySampler.nextDelayMs(legitSwitchAlgorithm.get(), legitSwitchMin.get(), legitSwitchMax.get());
            case Aggressive -> aggressiveDelayEngine.nextDelayMs(aggressiveAlgorithm.get(), aggressiveConfig());
        };
    }

    private long sampleItemDelay() {
        return switch (delayProfile.get()) {
            case Normal -> delaySampler.nextDelayMs(delayAlgorithm.get(), minDelay.get(), maxDelay.get());
            case Legit -> delaySampler.nextDelayMs(legitItemAlgorithm.get(), legitItemMin.get(), legitItemMax.get());
            case Aggressive -> aggressiveDelayEngine.nextDelayMs(aggressiveAlgorithm.get(), aggressiveConfig());
        };
    }

    private AggressiveDelayConfig aggressiveConfig() {
        return AggressiveDelayConfig.builder().range(aggressiveMin.get(), aggressiveMax.get()).sigma(sigma.get()).skew(skew.get()).bias(bias.get()).jitter(jitter.get()).correlation(correlation.get()).momentum(momentum.get()).driftStrength(driftStrength.get()).driftSpeed(driftSpeed.get()).burstChance(burstChance.get()).burstSize(burstSizeMin.get(), burstSizeMax.get()).burstMultiplier(burstMultiplier.get()).pauseChance(pauseChance.get()).pauseRange(pauseMin.get(), pauseMax.get()).outlierChance(outlierChance.get()).outlierScale(outlierScale.get()).warmupItems(warmupItems.get()).cooldownItems(cooldownItems.get()).acceleration(acceleration.get()).deceleration(deceleration.get()).clampEnabled(clampAggressive.get()).build();
    }

    private RotationConfig rotationConfig() {
        return new RotationConfig(maxYawSpeed.get(), maxPitchSpeed.get(), rotationAcceleration.get(), rotationDeceleration.get(), rotationSmoothing.get());
    }

    private ArmorStandEntity findTarget() {
        float tickProgress = mc.getRenderTickCounter().getTickProgress(true);
        Vec3d start = mc.player.getCameraPosVec(tickProgress);
        Vec3d direction = mc.player.getRotationVec(tickProgress).normalize();
        Vec3d end = start.add(direction.multiply(range.get()));
        Box searchBox = mc.player.getBoundingBox().stretch(direction.multiply(range.get())).expand(1.0);
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

    private Setting<Integer> legitInt(String name, String description, int defaultValue, int min, int max) {
        return sgLegit.add(new IntSetting.Builder().name(name).description(description).defaultValue(defaultValue).range(min, max).sliderRange(min, Math.min(max, 1500)).visible(() -> delayProfile.get() == DelayProfile.Legit).build());
    }

    private Setting<Integer> aggressiveInt(String name, String description, int defaultValue, int min, int max) {
        return sgAggressive.add(new IntSetting.Builder().name(name).description(description).defaultValue(defaultValue).range(min, max).sliderRange(min, max).visible(this::aggressiveVisible).build());
    }

    private Setting<Double> aggressiveDouble(String name, String description, double defaultValue, double min, double max) {
        return sgAggressive.add(new DoubleSetting.Builder().name(name).description(description).defaultValue(defaultValue).range(min, max).sliderRange(min, max).visible(this::aggressiveVisible).build());
    }

    private Setting<Double> rotationDouble(String name, String description, double defaultValue, double min, double max) {
        return sgRotation.add(new DoubleSetting.Builder().name(name).description(description).defaultValue(defaultValue).range(min, max).sliderRange(min, max).visible(rotationEnabled::get).build());
    }

    private boolean aggressiveVisible() {
        return delayProfile.get() == DelayProfile.Aggressive;
    }

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }
}
