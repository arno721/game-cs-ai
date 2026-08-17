package com.arno721.armorstandgrabber;

import com.arno721.armorstandgrabber.modules.ArmorStandGrabber;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class ArmorStandGrabberAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Armor Stand");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Armor Stand Grabber");
        Modules.get().add(new ArmorStandGrabber());
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
