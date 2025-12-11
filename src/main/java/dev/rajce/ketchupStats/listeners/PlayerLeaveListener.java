package dev.rajce.ketchupStats.listeners;

import dev.rajce.ketchupStats.KetchupStats;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerLeaveListener implements Listener {


    private final KetchupStats plugin;

    public PlayerLeaveListener(KetchupStats plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event){

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().unloadPlayerStats(event.getPlayer().getUniqueId());
        });

    }





}

