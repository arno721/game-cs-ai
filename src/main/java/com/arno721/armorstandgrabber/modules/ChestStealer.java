package com.arno721.armorstandgrabber.modules;

import com.arno721.armorstandgrabber.ArmorStandGrabberAddon;
import com.arno721.armorstandgrabber.chest.*;
import com.arno721.armorstandgrabber.runtime.*;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ChestStealer extends Module implements RuntimeTickParticipant {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgConstraints = settings.createGroup("Constraints");
    private final SettingGroup sgSelection = settings.createGroup("Selection");
    private final SettingGroup sgContainer = settings.createGroup("Container");
    private final SettingGroup sgAura = settings.createGroup("Aura");
    private final SettingGroup sgSilent = settings.createGroup("Silent Screen");

    private final Setting<Boolean> autoClose = sgGeneral.add(new BoolSetting.Builder().name("auto-close").defaultValue(true).build());
    private final Setting<ChestMoveMode> moveMode = sgGeneral.add(new EnumSetting.Builder<ChestMoveMode>().name("move-mode").defaultValue(ChestMoveMode.QuickMove).build());
    private final Setting<Boolean> quickSwaps = sgGeneral.add(new BoolSetting.Builder().name("quick-swaps").defaultValue(true).build());
    private final Setting<OnFullMode> onFull = sgGeneral.add(new EnumSetting.Builder<OnFullMode>().name("on-full").defaultValue(OnFullMode.Throw).build());

    private final Setting<Integer> startDelayMin = sgConstraints.add(new IntSetting.Builder().name("start-delay-min").defaultValue(1).range(0, 80).sliderRange(0, 20).build());
    private final Setting<Integer> startDelayMax = sgConstraints.add(new IntSetting.Builder().name("start-delay-max").defaultValue(2).range(0, 80).sliderRange(0, 20).build());
    private final Setting<Integer> clickDelayMin = sgConstraints.add(new IntSetting.Builder().name("click-delay-min").defaultValue(2).range(0, 80).sliderRange(0, 20).build());
    private final Setting<Integer> clickDelayMax = sgConstraints.add(new IntSetting.Builder().name("click-delay-max").defaultValue(4).range(0, 80).sliderRange(0, 20).build());
    private final Setting<Integer> closeDelayMin = sgConstraints.add(new IntSetting.Builder().name("close-delay-min").defaultValue(1).range(0, 80).sliderRange(0, 20).build());
    private final Setting<Integer> closeDelayMax = sgConstraints.add(new IntSetting.Builder().name("close-delay-max").defaultValue(2).range(0, 80).sliderRange(0, 20).build());
    private final Setting<Integer> missChance = sgConstraints.add(new IntSetting.Builder().name("miss-chance").defaultValue(0).range(0, 100).sliderRange(0, 100).build());
    private final Setting<Boolean> noMovement = sgConstraints.add(new BoolSetting.Builder().name("no-movement").defaultValue(true).build());
    private final Setting<Boolean> noRotation = sgConstraints.add(new BoolSetting.Builder().name("no-rotation").defaultValue(true).build());
    private final Setting<Boolean> notUsingItem = sgConstraints.add(new BoolSetting.Builder().name("not-using-item").defaultValue(true).build());
    private final Setting<Boolean> notBreaking = sgConstraints.add(new BoolSetting.Builder().name("not-breaking").defaultValue(true).build());
    private final Setting<Boolean> notDuringCombat = sgConstraints.add(new BoolSetting.Builder().name("not-during-combat").defaultValue(true).build());

    private final Setting<ChestSelectionMode> selectionMode = sgSelection.add(new EnumSetting.Builder<ChestSelectionMode>().name("selection-mode").defaultValue(ChestSelectionMode.Distance).build());
    private final Setting<DistanceStartItem> distanceStartItem = sgSelection.add(new EnumSetting.Builder<DistanceStartItem>().name("distance-start-item").defaultValue(DistanceStartItem.Default).visible(() -> selectionMode.get() == ChestSelectionMode.Distance).build());
    private final Setting<Double> randomFactorMin = sgSelection.add(new DoubleSetting.Builder().name("random-factor-min").defaultValue(1.0).range(0.25, 2.0).sliderRange(0.25, 2.0).visible(() -> selectionMode.get() == ChestSelectionMode.Distance).build());
    private final Setting<Double> randomFactorMax = sgSelection.add(new DoubleSetting.Builder().name("random-factor-max").defaultValue(1.0).range(0.25, 2.0).sliderRange(0.25, 2.0).visible(() -> selectionMode.get() == ChestSelectionMode.Distance).build());
    private final Setting<IndexOrder> indexOrder = sgSelection.add(new EnumSetting.Builder<IndexOrder>().name("index-order").defaultValue(IndexOrder.Ascending).visible(() -> selectionMode.get() == ChestSelectionMode.Index).build());

    private final Setting<Boolean> checkHandlerType = sgContainer.add(new BoolSetting.Builder().name("check-screen-handler-type").defaultValue(true).build());
    private final Setting<Boolean> checkTitle = sgContainer.add(new BoolSetting.Builder().name("check-screen-title").defaultValue(true).build());
    private final Setting<String> titleWhitelist = sgContainer.add(new StringSetting.Builder().name("title-whitelist").defaultValue("Chest,Large Chest,Shulker Box,Barrel,Chest Minecart,Chest Boat").build());
    private final Setting<String> titleBlacklist = sgContainer.add(new StringSetting.Builder().name("title-blacklist").defaultValue("").build());

    private final Setting<Boolean> auraEnabled = sgAura.add(new BoolSetting.Builder().name("enabled").defaultValue(true).build());
    private final Setting<Boolean> silentScreenEnabled = sgSilent.add(new BoolSetting.Builder().name("enabled").defaultValue(false).build());

    private final Random random = new Random();
    private final ChestStealSession session = new ChestStealSession();
    private final SelectionPlanner selectionPlanner = new SelectionPlanner();
    private final ContainerDetector containerDetector = new ContainerDetector();
    private final InventoryTransactionExecutor executor;
    private final ClientRuntime runtime;

    public ChestStealer(ClientRuntime runtime) {
        super(ArmorStandGrabberAddon.CATEGORY, "chest-stealer", "Automatically transfers items from eligible containers with safe handler validation.");
        this.runtime = runtime;
        this.executor = new InventoryTransactionExecutor(mc, runtime.inventoryMutex());
    }

    @Override
    public RuntimeOwner runtimeOwner() {
        return RuntimeOwner.CHEST_STEALER;
    }

    @Override
    public void onRuntimeTick() {
        if (!isActive() || mc.player == null) {
            resetSession();
            return;
        }

        ContainerFilterConfig filter = new ContainerFilterConfig(
            checkHandlerType.get(),
            checkTitle.get(),
            parseTitles(titleWhitelist.get()),
            parseTitles(titleBlacklist.get())
        );
        var detected = containerDetector.detect(mc, filter);
        if (detected.isEmpty()) {
            resetSession();
            return;
        }
        ContainerDescriptor descriptor = detected.get();

        if (session.phase() == ChestStealPhase.Idle || !session.matches(descriptor)) {
            if (!runtime.inventoryMutex().tryAcquire(RuntimeOwner.CHEST_STEALER)) return;
            session.begin(descriptor, runtime.tickCount() + sampleTicks(startDelayMin.get(), startDelayMax.get()));
            return;
        }

        InventoryConstraintConfig constraints = new InventoryConstraintConfig(
            startDelayMin.get(), startDelayMax.get(), clickDelayMin.get(), clickDelayMax.get(),
            closeDelayMin.get(), closeDelayMax.get(), missChance.get(), noMovement.get(),
            noRotation.get(), notUsingItem.get(), notBreaking.get(), notDuringCombat.get()
        );
        ActivitySnapshot activity = runtime.activityTracker().snapshot(mc, runtime.rotationCoordinator(), runtime.combatTracker());
        if (!ConstraintGate.allowed(constraints, activity, false)) return;
        if (runtime.inventoryMutex().owner() != RuntimeOwner.CHEST_STEALER && !runtime.inventoryMutex().tryAcquire(RuntimeOwner.CHEST_STEALER)) return;

        if (runtime.tickCount() < session.deadlineTick()) return;
        if (session.phase() == ChestStealPhase.StartDelay || session.phase() == ChestStealPhase.ClickDelay) session.enterPlanning();

        if (session.phase() == ChestStealPhase.CloseDelay) {
            if (runtime.tickCount() >= session.deadlineTick() && session.matches(descriptor) && autoClose.get()) {
                mc.player.closeHandledScreen();
                resetSession();
            }
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;
        List<SelectionCandidate> candidates = new ArrayList<>();
        for (int slotId : descriptor.containerSlotIds()) {
            ItemStack stack = handler.getSlot(slotId).getStack();
            if (!stack.isEmpty()) {
                int index = candidates.size();
                candidates.add(new SelectionCandidate(slotId, index % 9, index / 9));
            }
        }

        if (candidates.isEmpty()) {
            if (autoClose.get()) session.enterCloseDelay(runtime.tickCount() + sampleTicks(closeDelayMin.get(), closeDelayMax.get()));
            else resetSession();
            return;
        }

        if (random.nextInt(100) < missChance.get()) {
            session.markMutation(runtime.tickCount());
            session.enterPlanning();
            session.markMutation(runtime.tickCount() + sampleTicks(clickDelayMin.get(), clickDelayMax.get()));
            return;
        }

        SelectionConfig selectionConfig = switch (selectionMode.get()) {
            case Distance -> SelectionConfig.distance(distanceStartItem.get(), randomFactorMin.get(), randomFactorMax.get(), random);
            case Index -> SelectionConfig.index(indexOrder.get());
            case Random -> SelectionConfig.random(random);
        };
        SelectionCandidate target = selectionPlanner.order(candidates, selectionConfig).getFirst();
        InventoryTransaction transaction = moveMode.get() == ChestMoveMode.QuickMove
            ? new ChestTransferPlanner().quickMove(target.slotId())
            : new ChestTransferPlanner().quickMove(target.slotId());

        if (executor.execute(descriptor, transaction)) {
            session.markMutation(runtime.tickCount());
            session.enterPlanning();
            session.markMutation(runtime.tickCount() + sampleTicks(clickDelayMin.get(), clickDelayMax.get()));
        }
    }

    public ContainerDetector containerDetector() { return containerDetector; }
    public boolean silentScreenEnabled() { return silentScreenEnabled.get(); }
    public boolean auraEnabled() { return auraEnabled.get(); }

    private void resetSession() {
        session.reset();
        if (runtime.inventoryMutex().isOwnedBy(RuntimeOwner.CHEST_STEALER) && !runtime.inventoryMutex().atomic()) {
            runtime.inventoryMutex().release(RuntimeOwner.CHEST_STEALER);
        }
    }

    private int sampleTicks(int min, int max) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        return low == high ? low : low + random.nextInt(high - low + 1);
    }

    private static List<String> parseTitles(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
