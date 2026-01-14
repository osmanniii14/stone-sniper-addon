package com.example.addon;

import com.example.addon.modules.StoneSniper;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("Stone Sniper");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Stone Sniper Addon");
        Modules.get().add(new StoneSniper());
    }

    @Override
    public void onRegisterCategories() {
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("osmanniii14", "stone-sniper-addon");
    }
}
