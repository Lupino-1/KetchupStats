package dev.rajce.ketchupStats.commands;

import dev.rajce.ketchupStats.KetchupStats;
import dev.rajce.ketchupStats.managers.DatabaseManager;
import dev.rajce.ketchupStats.managers.MessageManager;
import org.bukkit.Bukkit;
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
        if (args.length < 1) {

            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload":
                if (!hasPermission(sender, args[0])) {
                    return true;
                }

                sender.sendMessage(messageManager.translateColors("&aReloading configuration and restarting database connection..."));

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {

                        plugin.reloadAndRestartDatabase();


                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(messageManager.translateColors("&a[KetchupStats] Successfully reloaded config and restarted database."));
                        });

                    } catch (Exception e) {

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(messageManager.translateColors("&c[KetchupStats] Failed to reload and restart database. Check console for errors!"));
                            e.printStackTrace();
                        });
                    }
                });
                return true;

            case "createstat":
                if (args.length < 2 || !hasPermission(sender, args[0])) {
                    sender.sendMessage(messageManager.translateColors("&cUsage /ketchupstats createstat <statName>"));
                    return true;
                }

                String createStatName = args[1];
                if (databaseManager.isStatRegistered(createStatName)) {
                    sender.sendMessage(messageManager.translateColors("&cThis stat name already exists."));
                    return true;
                }


                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    boolean success = databaseManager.createStat(createStatName);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            sender.sendMessage(messageManager.translateColors("&aYou successfully created " + createStatName + " stat."));
                        } else {
                            sender.sendMessage(messageManager.translateColors("&cError creating stat " + createStatName + ". Check console."));
                        }
                    });
                });
                return true;

            case "deletestat":
                if (args.length < 2 || !hasPermission(sender, args[0])) {
                    sender.sendMessage(messageManager.translateColors("&cUsage /ketchupstats deletestat <statName>"));
                    return true;
                }

                String deleteStatName = args[1];
                if (!databaseManager.isStatRegistered(deleteStatName)) {
                    sender.sendMessage(messageManager.translateColors("&cThis stat doesn't exist."));
                    return true;
                }

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    boolean success = databaseManager.deleteStat(deleteStatName);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            sender.sendMessage(messageManager.translateColors("&aYou successfully deleted " + deleteStatName + " stat."));
                        } else {
                            sender.sendMessage(messageManager.translateColors("&cError deleting stat " + deleteStatName + ". Check console."));
                        }
                    });
                });
                return true;

            case "set":
            case "give":
            case "take":

                if (args.length < 4 || !hasPermission(sender, args[0])) {
                    sender.sendMessage(messageManager.translateColors("&cUsage /ketchupstats "+args[0]+" <player> <value> <statName>"));
                    return true;
                }

                String action = args[0].toLowerCase();
                String strPlayer = args[1];
                Player player = Bukkit.getPlayer(strPlayer);
                String statName = args[3];
                double value;

                if (player == null || !player.isOnline()) {
                    sender.sendMessage(messageManager.translateColors("&cPlayer " + strPlayer + " doesn't exist or is offline."));
                    return true;
                }

                try {
                    value = Double.parseDouble(args[2]);
                    if ( value < 0) {
                        sender.sendMessage(messageManager.translateColors("&cThe number must be positive."));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(messageManager.translateColors("&cInvalid number format."));
                    return true;
                }

                if (!databaseManager.isStatRegistered(statName)) {
                    sender.sendMessage(messageManager.translateColors("&cThis stat doesn't exist."));
                    return true;
                }


                double finalValue = value;
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    String message = "";
                    String targetPlayerName = player.getName();

                    switch (action) {
                        case "set":
                            databaseManager.setStat(statName, player.getUniqueId(), finalValue);
                            message = "&aYou set " + targetPlayerName + " " + statName + " to " + finalValue;
                            break;
                        case "give":
                            databaseManager.addStat(statName, player.getUniqueId(), finalValue);
                            message = "&aYou gave " + targetPlayerName + " " + finalValue + " " + statName + ".";
                            break;
                        case "take":
                            databaseManager.addStat(statName, player.getUniqueId(), -finalValue);
                            message = "&aYou took " + finalValue + " " + statName + " from " + targetPlayerName + ".";
                            break;
                    }


                    final String finalMessage = message;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(messageManager.translateColors(finalMessage));
                    });
                });
                return true;

            case "get":
                if (args.length < 3 || !hasPermission(sender, args[0])) {
                    sender.sendMessage(messageManager.translateColors("&cUsage /ketchupstats get <player> <statName>"));
                    return true;
                }
                String strPlayerGet = args[1];
                Player playerGet = Bukkit.getPlayer(strPlayerGet);
                String getStatName = args[2];

                if (playerGet == null || !playerGet.isOnline()) {
                    sender.sendMessage(messageManager.translateColors("&cPlayer " + strPlayerGet + " doesn't exist or is offline."));
                    return true;
                }
                if (!databaseManager.isStatRegistered(getStatName)) {
                    sender.sendMessage(messageManager.translateColors("&cThis stat doesn't exist."));
                    return true;
                }

                double statValue = databaseManager.getStat(getStatName, playerGet.getUniqueId());
                sender.sendMessage(messageManager.translateColors("&aPlayer " + playerGet.getName() + " has " + statValue + " " + getStatName));
                return true;
        }

        return true;
    }

    private boolean hasPermission(CommandSender sender, String command) {
        boolean hasPerm = sender.hasPermission("ketchupstats.commands." + command.toLowerCase()) || sender.hasPermission("ketchupstats.admin");
        if(!hasPerm){
            sender.sendMessage(messageManager.translateColors("&cYou don't have a permission."));
        }
        return hasPerm;
    }
}