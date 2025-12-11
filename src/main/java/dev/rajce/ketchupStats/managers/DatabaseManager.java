package dev.rajce.ketchupStats.managers;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private final File databaseFile;
    private Connection connection; // Globální instance připojení

    private final Map<String, Map<UUID,Double>> statsCache = new HashMap<>();
    private final Map<String, Integer> statNameToId = new HashMap<>();
    private final Map<Integer, String> statIdToName = new HashMap<>();

    public DatabaseManager(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), "database/" + fileName);

        prepareDatabaseFile();
        connect();
        createTables();

        if(connection != null){
            loadStatDefinitions();
        }
    }

    private void prepareDatabaseFile() {
        try {
            if (!plugin.getDataFolder().exists())
                plugin.getDataFolder().mkdirs();

            if (!databaseFile.getParentFile().exists())
                databaseFile.getParentFile().mkdirs();

            if (!databaseFile.exists()) {
                databaseFile.createNewFile();
                Bukkit.getLogger().info("[KetchupStats] Created SQLite file: " + databaseFile.getAbsolutePath());
            }

        } catch (Exception e) {
            Bukkit.getLogger().severe("[KetchupStats] Could not prepare database file!");
            e.printStackTrace();
        }
    }

    private void connect() {
        if(plugin.getConfig().getBoolean("use-remote-database")){
            try {
                connection = DriverManager.getConnection(plugin.getConfig().getString("url",""));
                Bukkit.getLogger().info("[KetchupStats] Connected to database");
            } catch (SQLException e) {
                Bukkit.getLogger().severe("[KetchupStats] Could not connect to database!");
                e.printStackTrace();
            }
            return;
        }
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            Bukkit.getLogger().info("[KetchupStats] Connected to SQLite.");
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[KetchupStats] Could not connect to SQLite DB!");
            e.printStackTrace();
        }
    }

    public void loadStatDefinitions() {
        statNameToId.clear();
        statIdToName.clear();
        statsCache.clear();

        // 1. Načtení DEFINIC statistik (stats)
        String sqlDef = "SELECT stat_id, stat_name FROM stats";
        try (PreparedStatement psDef = connection.prepareStatement(sqlDef);
             ResultSet rsDef = psDef.executeQuery()) {

            while (rsDef.next()) {
                int id = rsDef.getInt("stat_id");
                String name = rsDef.getString("stat_name");
                statNameToId.put(name, id);
                statIdToName.put(id, name);
                statsCache.putIfAbsent(name, new HashMap<>());
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[KetchupStats] Could not load stat definitions!");
            e.printStackTrace();
        }

        // 2. Načtení HODNOT hráčů (player_stats)
        String sqlData = "SELECT uuid, stat_id, value FROM player_stats";
        try (PreparedStatement psData = connection.prepareStatement(sqlData);
             ResultSet rsData = psData.executeQuery()) {

            while (rsData.next()) {
                UUID uuid = UUID.fromString(rsData.getString("uuid"));
                int statId = rsData.getInt("stat_id");
                double value = rsData.getDouble("value");

                String statName = statIdToName.get(statId);

                if (statName != null) {
                    statsCache.get(statName).put(uuid, value);
                } else {
                    plugin.getLogger().warning("[KetchupStats] Found obsolete player data for unknown stat ID: " + statId);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[KetchupStats] Could not load player data!");
            e.printStackTrace();
        }
    }

    public void saveAllStatsAsyncSimplified() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            // Používáme existující připojení, abychom jej nezavřeli (OPRAVA)
            Connection conn = this.connection;

            // STAT DEFINICE: Ukládáme jen hodnoty, protože definice jsou atomicky zapsány v createStat().

            // UKLÁDÁNÍ HODNOT HRÁČŮ (INSERT OR REPLACE)
            final String PLAYER_SQL = "INSERT OR REPLACE INTO player_stats (uuid, stat_id, value) VALUES (?, ?, ?)";

            try (PreparedStatement psPlayer = conn.prepareStatement(PLAYER_SQL)) {

                for (Map.Entry<String, Map<UUID, Double>> statEntry : statsCache.entrySet()) {
                    String statName = statEntry.getKey();
                    Map<UUID, Double> playerMap = statEntry.getValue();

                    Integer statId = statNameToId.get(statName);
                    if (statId == null) continue;

                    for (Map.Entry<UUID, Double> playerValue : playerMap.entrySet()) {
                        psPlayer.setString(1, playerValue.getKey().toString());
                        psPlayer.setInt(2, statId);
                        psPlayer.setDouble(3, playerValue.getValue());
                        psPlayer.addBatch();
                    }
                }
                psPlayer.executeBatch();

            } catch (SQLException e) {
                plugin.getLogger().severe("SQL Critical failure during simplified batch data save.");
                e.printStackTrace();
            }
        });
    }

    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                Bukkit.getLogger().info("[KetchupStats] Disconnected from database.");
            } catch (SQLException e) {
                Bukkit.getLogger().severe("[KetchupStats] Could not close connection!");
                e.printStackTrace();
            }
        }
    }

    private void createTables() {
        try (Statement stmt = connection.createStatement()) {

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS stats (" +
                            "stat_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "stat_name TEXT UNIQUE NOT NULL" +
                            ");"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS player_stats (" +
                            "uuid TEXT NOT NULL," +
                            "stat_id INTEGER NOT NULL," +
                            "value REAL NOT NULL," +
                            "PRIMARY KEY(uuid, stat_id)," +
                            "FOREIGN KEY(stat_id) REFERENCES stats(stat_id)" +
                            ");"
            );

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // --- RAM API ---

    public double getStat(String statName, UUID uuid) {
        if (!isStatRegistered(statName)) {
            plugin.getLogger().warning("Attempted to read unregistered stat: " + statName + " by " + uuid);
            return 0.0;
        }
        // Statika musí existovat v cache (zajištěno metodami loadStatDefinitions/createStat)
        return statsCache.get(statName).getOrDefault(uuid,0.0);
    }

    public void setStat(String statName, UUID uuid, double value) {
        if (!isStatRegistered(statName)) {
            plugin.getLogger().warning("Attempted to set value for unregistered stat: " + statName + " by " + uuid);
            return;
        }
        statsCache.get(statName).put(uuid, value);
    }

    public void addStat(String statName, UUID uuid, double amount) {
        setStat(statName, uuid, getStat(statName, uuid) + amount);
    }

    public boolean isStatRegistered(String statName) {
        return statNameToId.containsKey(statName);
    }

    // METODA JE ATOMICKÁ A STABILNÍ (ID generuje DB, ne RAM)
    public boolean createStat(String statName){
        // Zajišťuje, že se dvě statiky nevytvoří se stejným ID
        synchronized (statNameToId) {
            if (isStatRegistered(statName)) return false;

            final String SQL = "INSERT INTO stats (stat_name) VALUES (?)";
            try (PreparedStatement ps = connection.prepareStatement(SQL, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, statName);
                ps.executeUpdate();

                // Získání trvalého ID generovaného databází
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        int statId = keys.getInt(1);

                        // Synchronizace RAM s trvalým ID
                        statNameToId.put(statName, statId);
                        statIdToName.put(statId, statName);
                        statsCache.put(statName, new HashMap<>());

                        plugin.getLogger().info("[KetchupStats] Created new stat: " + statName + " (ID = " + statId + ")");
                        return true;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to create stat '" + statName + "'.");
                e.printStackTrace();
            }
            return false;
        }
    }

    // --- Gettery ---

    public Connection getConnection() {
        return connection;
    }

    public Map<String, Map<UUID, Double>> getStatsCache() {
        return statsCache;
    }

    public Map<String, Integer> getStatNameToId() {
        return statNameToId;
    }

    public Map<Integer, String> getStatIdToName() {
        return statIdToName;
    }
}