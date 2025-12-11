package dev.rajce.ketchupStats;

import org.bukkit.plugin.java.JavaPlugin;

public final class KetchupStats extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
