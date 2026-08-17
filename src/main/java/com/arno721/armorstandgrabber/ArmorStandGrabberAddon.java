package com.arno721.armorstandgrabber;

import com.arno721.armorstandgrabber.modules.ArmorStandGrabber;
import com.arno721.armorstandgrabber.runtime.ClientRuntime;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;

public class ArmorStandGrabberAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Armor Stand");

    private static ClientRuntime runtime;

    @Override
    public void onInitialize() {
        LOG.info("Initializing Armor Stand Grabber");

        runtime = new ClientRuntime(MinecraftClient.getInstance());
        ArmorStandGrabber armorStandGrabber = new ArmorStandGrabber(
            runtime.inventoryMutex(),
            runtime.rotationCoordinator()
        );

        runtime.register(armorStandGrabber);
        Modules.get().add(armorStandGrabber);
        ClientTickEvents.END_CLIENT_TICK.register(runtime::tick);
    }

    public static ClientRuntime runtime() {
        return runtime;
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.arno721.armorstandgrabber";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("arno721", "game-cs-ai");
    }
}
