package dev.rajce.ketchupStats.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class StatsCommand implements CommandExecutor {


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] args) {
        if (args.length <1){


            return true;
        }

        switch (args[0]) {

            case "reload":
                //todo reload config

            case "createstat":
                if (args.length < 2) {
                    return true;
                }

                return true;
            case "deletestat":
                if (args.length < 2) {
                    return true;
                }

                try {
                    float f = Float.parseFloat("10.0");



                    return true;
                } catch (Exception e) {

                }
                return true;
            case "set":
                if (args.length < 4) {
                    return true;
                }


            case "give":
                if (args.length < 4) {
                    return true;
                }
            case "take":
                if (args.length < 4) {
                    return true;
                }

            case "get":
                if (args.length < 3) {
                    return true;
                }

            }








    return true;
    }



}
