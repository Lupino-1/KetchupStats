package dev.rajce.ketchupStats;

import dev.rajce.ketchupStats.managers.DatabaseManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Map;

public class PlaceholderAPIHook extends PlaceholderExpansion {


    private final KetchupStats plugin;

    private final DatabaseManager databaseManager;


    public PlaceholderAPIHook(KetchupStats plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    @Override
    public String getIdentifier() {
        return "ketchupstats";
    }

    @Override
    public String getAuthor() {
        return "Lupino1";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // zůstane registrován i po /papi reload
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {

        if(databaseManager.isStatRegistered(params)){

            return String.valueOf(databaseManager.getStat(params,offlinePlayer.getUniqueId()));




        }






        return " ";
    }




}