package dev.rajce.ketchupStats.commands;

import dev.rajce.ketchupStats.KetchupStats;
import dev.rajce.ketchupStats.managers.DatabaseManager;
import dev.rajce.ketchupStats.managers.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class StatsCommand implements CommandExecutor {

    private final DatabaseManager databaseManager;

    private final KetchupStats plugin;

    private final MessageManager messageManager;

    public StatsCommand(KetchupStats plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.messageManager = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] args) {
        if (args.length <1){

            return true;
        }

        switch (args[0]) {

            case "reload":
                //todo reload config
                if (args.length < 2||!sender.hasPermission("ketchupstats.commands.reload")) {
                    return true;
                }
                return true;


            case "createstat":
                if (args.length < 2||!sender.hasPermission("ketchupstats.commands.createstat")) {
                    return true;
                }

                if(databaseManager.isStatRegistered(args[1])){
                    sender.sendMessage(messageManager.translateColors("&cThis name already exist."));
                    return true;
                }
                Bukkit.getScheduler().runTaskAsynchronously(plugin,() ->{
                    databaseManager.createStat(args[1]);
                });
                sender.sendMessage(messageManager.translateColors("&aYou successfully created "+args[1]+" stat."));
                return true;


            case "deletestat":
                if (args.length < 2||!sender.hasPermission("ketchupstats.commands.deletestat")) {
                    return true;
                }

                if(!databaseManager.isStatRegistered(args[1])){
                    sender.sendMessage(messageManager.translateColors("&cThis stat doesn't exist."));
                    return true;
                }

                Bukkit.getScheduler().runTaskAsynchronously(plugin, ()-> {
                   databaseManager.deleteStat(args[1]);
                });
                sender.sendMessage(messageManager.translateColors("&aYou successfully deleted "+args[1]+" stat."));
                return true;


            case "set":
                if (args.length < 4||!sender.hasPermission("ketchupstats.commands.set")) {
                    return true;
                }
                String strPlayer = args[1];
                Player player = Bukkit.getPlayer(strPlayer);
                if (player==null||!player.isOnline()){
                    sender.sendMessage(messageManager.translateColors("&cPlayer "+strPlayer+" doesn't exist."));
                    return true;
                }
                try{
                    double value = Double.parseDouble(args[2]);
                    if (value<1){
                        sender.sendMessage(messageManager.translateColors("&cThe number is too small."));
                        return true;
                    }
                    if(!databaseManager.isStatRegistered(args[3])){
                        sender.sendMessage(messageManager.translateColors("&cThis stat doesn't exist."));
                        return true;
                    }
                    databaseManager.setStat(args[3],player.getUniqueId(),value);
                    sender.sendMessage(messageManager.translateColors("&aYou set " + strPlayer +" "+args[3]+" to "+value));
                }catch (Exception e){
                    return true;
                }
                return true;


            case "give":
                if (args.length < 4||!sender.hasPermission("ketchupstats.commands.give")) {
                    return true;
                }
                String strPlayerGive = args[1];
                Player playerGive = Bukkit.getPlayer(strPlayerGive);
                if (playerGive==null||!playerGive.isOnline()){
                    sender.sendMessage(messageManager.translateColors("&cPlayer "+strPlayerGive+" doesn't exist."));
                    return true;
                }
                try{
                    double value = Double.parseDouble(args[2]);
                    if (value<1){
                        sender.sendMessage(messageManager.translateColors("&cThe number is too small."));
                        return true;
                    }
                    if(!databaseManager.isStatRegistered(args[3])){
                        sender.sendMessage(messageManager.translateColors("&cThis stat doesn't exist."));
                        return true;
                    }
                    databaseManager.addStat(args[3],playerGive.getUniqueId(),value);
                    sender.sendMessage(messageManager.translateColors("&aYou gave " + strPlayerGive +" "+value+" "+args[3]+"."));
                }catch (Exception e){
                    return true;
                }
                return true;


            case "take":
                if (args.length < 4||!sender.hasPermission("ketchupstats.commands.take")) {
                    return true;
                }
                String strPlayerTake = args[1];
                Player playerTake = Bukkit.getPlayer(strPlayerTake);
                if (playerTake==null||!playerTake.isOnline()){
                    sender.sendMessage(messageManager.translateColors("&cPlayer "+strPlayerTake+" doesn't exist."));
                    return true;
                }
                try{
                    double value = Double.parseDouble(args[2]);
                    if (value<1){
                        sender.sendMessage(messageManager.translateColors("&cThe number is too small."));
                        return true;
                    }
                    if(!databaseManager.isStatRegistered(args[3])){
                        sender.sendMessage(messageManager.translateColors("&cThis stat doesn't exist."));
                        return true;
                    }
                    databaseManager.addStat(args[3],playerTake.getUniqueId(),-value);
                    sender.sendMessage(messageManager.translateColors("&aYou took "+value+" "+args[3] +" from "+ strPlayerTake+"." ));
                }catch (Exception e){
                    return true;
                }
                return true;


            case "get":
                if (args.length < 3||!sender.hasPermission("ketchupstats.commands.get")) {
                    return true;
                }
                String strPlayerGet = args[1];
                Player playerGet = Bukkit.getPlayer(strPlayerGet);
                if (playerGet==null||!playerGet.isOnline()){
                    sender.sendMessage(messageManager.translateColors("&cPlayer "+strPlayerGet+" doesn't exist."));
                    return true;
                }
                if(!databaseManager.isStatRegistered(args[2])){
                    sender.sendMessage(messageManager.translateColors("&cThis stat doesn't exist."));
                    return true;
                }

                double value = databaseManager.getStat(args[2],playerGet.getUniqueId());
                sender.sendMessage(messageManager.translateColors("&aPlayer " + strPlayerGet + " has "+ value + " "+args[2]));
                return true;
            }

    return true;
    }



}
