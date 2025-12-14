package dev.rajce.ketchupStats.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MessageManager {
    private final File messagesFile;
    private FileConfiguration messagesConfig;

    public MessageManager(JavaPlugin plugin) {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        if (!messagesFile.exists()) {
            try {
                messagesFile.getParentFile().mkdirs();
                plugin.saveResource("messages.yml", false);
            } catch (Exception e) {
                //e.printStackTrace();
            }
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }
    public String getPrefix(){
        String message = messagesConfig.getString("prefix");
        return (message != null ) ? translateColors(message) : null;

    }

    public String addPrefixAndTranslateColors(String message){

        if(getPrefix()!=null) {
            message = getPrefix() + message;
        }

        message = translateColors(message);


        return message;
    }
    public void sendMessageFromConfig(Player player,String path){
        if(player ==null||!player.isOnline())return;
        String string = getMessage(path);
        if(string!=null) {
            player.sendMessage(string);
        }


    }
    public void sendMessage(Player player,String message) {
        if(message ==null)return;
        player.sendMessage(translateColors(message));

    }
    public void broadcastMessage(String message) {
        if(message ==null)return;
        Bukkit.broadcastMessage(translateColors(message));

    }
    public void sendMessageFromConfig(Player player,String path, Map<String, String> placeholders){
        if(player ==null||!player.isOnline())return;
        String string = getMessage(path,placeholders);
        if(string!=null) {
            player.sendMessage(string);
        }


    }
    public String createStringFromList(List<String> list){
        String string = "";

        for (String s:list){
            string=string+s;
            if(!s.equalsIgnoreCase(list.get(list.size()-1))){
                string =string+" ";

            }


        }

        return string;
    }
    public String getMessageString(String key,String def) {
        String message = messagesConfig.getString(key);
        return (message != null ) ? translateColors(message) : def;
    }
    public String getMessageString(String key,String def, Map<String, String> placeholders) {
        String message = getMessageString(key,def);
        if (message == null ) {
            return null;
        }

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }

        return message;
    }
    public String getMessage(String key) {
        String message = messagesConfig.getString(key);
        if (message == null) return null;
        if (getPrefix() != null) {
            message = getPrefix() + message;
        }
        return (message != null ) ? translateColors(message) : null;
    }
    public  String replacePlaceholders (String message, Map<String, String> placeholders){
        if (message == null ) {
            return null;
        }
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return translateColors(message);
    }
    public String getMessage(String key, Map<String, String> placeholders) {
        String message = getMessage(key);
        if (message == null ) {
            return null;
        }

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }

        return message;
    }
    public String translateColors(String message) {
        message = message.replaceAll("&#([A-Fa-f0-9])([A-Fa-f0-9])([A-Fa-f0-9])([A-Fa-f0-9])([A-Fa-f0-9])([A-Fa-f0-9])",
                "§x§$1§$2§$3§$4§$5§$6");
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    public List<String> translateLore(List<String> configLore) {
        List<String> lore = new ArrayList<>();
        if (configLore != null) {
            for (String line : configLore) {
                lore.add(translateColors(line));
            }
        }

        return lore;
    }
    public List<String> replacePlaceholdersInLore(List<String> configLore,Map<String,String> map) {
        List<String> lore = new ArrayList<>();
        if (configLore != null) {
            for (String line : configLore) {
                lore.add(replacePlaceholders(line,map));
            }
        }

        return lore;
    }
    public void loadMessagesConfig() {
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }


    public void reloadMessages() {
        if (!messagesFile.exists()) {
            try {
                messagesFile.getParentFile().mkdirs();
                messagesFile.createNewFile();
                loadMessagesConfig();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            loadMessagesConfig();
        }
    }


}