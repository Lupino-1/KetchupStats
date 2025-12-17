package dev.rajce.ketchupStats.listeners;

import dev.rajce.ketchupStats.KetchupStats;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final KetchupStats plugin;

    public PlayerJoinListener(KetchupStats plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin (PlayerJoinEvent event){


        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().loadPlayerStats(event.getPlayer().getUniqueId());
        });


    }




}
