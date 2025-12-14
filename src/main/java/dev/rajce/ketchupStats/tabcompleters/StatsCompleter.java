package dev.rajce.ketchupStats.tabcompleters;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StatsCompleter implements TabCompleter {

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {

        List<String> list = new ArrayList<>();

        if (!(commandSender instanceof Player player)) return list;

        if (strings.length == 1) {

            if (player.hasPermission("")||player.hasPermission(".admin")) {
                list.add("createstat");
            }
            if (player.hasPermission("")||player.hasPermission(".admin")){
                list.add("deletestat");
            }
            if (player.hasPermission("")||player.hasPermission(".admin")){
                list.add("take");
            }
            if (player.hasPermission("")||player.hasPermission(".admin")){
                list.add("give");
            }
            if (player.hasPermission("")||player.hasPermission(".admin")){
                list.add("set");
            }
            if (player.hasPermission("")||player.hasPermission(".admin")){
                list.add("give");
            }
            if (player.hasPermission("")||player.hasPermission(".admin")){
                list.add("reload");
            }

            return list;
        }













        return null;
    }
}

