package dev.rajce.ketchupStats;

import dev.rajce.ketchupStats.managers.DatabaseManager;
import dev.rajce.ketchupStats.managers.MessageManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class KetchupStats extends JavaPlugin {

    private DatabaseManager databaseManager;

    private MessageManager messageManager;

    @Override
    public void onEnable() {
        messageManager = new MessageManager(this);
        databaseManager = new DatabaseManager(this,"stats.db");
        saveDefaultConfig();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIHook(this).register();
            getLogger().info("PlaceholderAPI hook registered.");
        }

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {

                    if (databaseManager != null) {
                        databaseManager.saveDirtyStats();
                    }

                }, 20L * 60 * 5, 20L * 60 * 5);
        }

    @Override
    public void onDisable() {

        getServer().getScheduler().cancelTasks(this);

        if (databaseManager != null) {
            databaseManager.saveDirtyStats();
            databaseManager.close();
        }
    }


    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

}