package dev.rajce.ketchupStats;

import dev.rajce.ketchupStats.managers.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class KetchupStats extends JavaPlugin {


    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        databaseManager = new DatabaseManager(this,"stats.db");

        saveDefaultConfig();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI")!=null){
            new PlaceholderAPIHook(this).register();
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }


    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
