package com.stonesniper.addon;

import com.stonesniper.addon.modules.StoneSniper;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class StoneSniperAddon extends MeteorAddon {
    @Override
    public void onInitialize() {
        Modules.get().add(new StoneSniper());
    }

    @Override
    public void onRegisterCategories() {
    }

    @Override
    public String getPackage() {
        return "com.stonesniper.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("yourname", "stone-sniper");
    }
}
