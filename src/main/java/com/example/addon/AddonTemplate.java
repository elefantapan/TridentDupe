package com.example.addon;

import com.example.addon.commands.CommandExample;
import com.example.addon.hud.HudExample;
import com.example.addon.modules.TridentDupeModule; // Uppdaterad till din trident dupe
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    
    // Skapar din kategori i Meteor-menyn för version 26.2/26.3
    public static final Category CATEGORY = new Category("Dupe 26");
    public static final HudGroup HUD_GROUP = new HudGroup("Dupe 26 HUD");

    @Override
    public void onInitialize() {
        LOG.info("Initierar Trident Dupe Addon för 26.2 och 26.3...");

        // 1. Registrera moduler
        Modules.get().add(new TridentDupeModule(CATEGORY));

        // 2. Registrera kommandon (om du har kvar exempelkommandot)
        Commands.add(new CommandExample());

        // 3. Registrera HUD-element (om du har kvar exempel-HUD)
        Hud.get().register(HudExample.INFO);
    }

    @Override
    public void onRegisterCategories() {
        // Registrerar fliken i Meteor GUI
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public GithubRepo getGithubRepo() {
        // Valfritt: Länkar till din GitHub om du vill ha automatiska uppdateringar
        return new GithubRepo("MeteorDevelopment", "addon-template");
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }
}
