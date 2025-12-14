package dev.rajce.ketchupStats.tabcompleters;

import dev.rajce.ketchupStats.KetchupStats;
import dev.rajce.ketchupStats.managers.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StatsCompleter implements TabCompleter {
    private final KetchupStats plugin;
    private  final DatabaseManager databaseManager;

    public StatsCompleter(KetchupStats plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] args) {

        List<String> list = new ArrayList<>();

        if (!(sender instanceof Player player)) {
            return list;
        }


        List<String> playerNames = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            playerNames.add(online.getName());
        }


        if (args.length == 1) {

            if (hasPermission(player, "createstat")) list.add("createstat");
            if (hasPermission(player, "deletestat")) list.add("deletestat");
            if (hasPermission(player, "take")) list.add("take");
            if (hasPermission(player, "give")) list.add("give");
            if (hasPermission(player, "set")) list.add("set");
            if (hasPermission(player, "get")) list.add("get");
            if (hasPermission(player, "reload")) list.add("reload");

            return list;
        }


        if (args.length == 2) {

            switch (args[0].toLowerCase()) {
                case "deletestat" -> {
                    if (hasPermission(player, "deletestat"))
                        return databaseManager.getAllStatNames();
                }

                case "createstat"-> {
                    if(hasPermission(player,"createstat"))
                        return List.of("<statName>");
                }

                case "set", "get", "take", "give" -> {
                    if (hasPermission(player, args[0].toLowerCase()))
                        return playerNames;
                }
            }
        }


        if (args.length == 3) {

            switch (args[0].toLowerCase()) {
                case "set", "take", "give" -> {
                    if (hasPermission(player, args[0].toLowerCase()))
                        return List.of("<value>");
                }

                case "get" -> {
                    if (hasPermission(player, "get"))
                        return databaseManager.getAllStatNames();
                }
            }
        }


        if (args.length == 4) {

            switch (args[0].toLowerCase()) {
                case "set", "take", "give" -> {
                    if (hasPermission(player, args[0].toLowerCase()))
                        return databaseManager.getAllStatNames();
                }
            }
        }

        return list;
    }


    private boolean hasPermission(Player player, String command) {
        return player.hasPermission("ketchupstats.commands." + command) || player.hasPermission("ketchupstats.admin");
    }

}

