package dev.rajce.ketchupStats.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private final Logger logger;
    private HikariDataSource dataSource;

    private final String fileName;


    private final Map<String, Map<UUID, Double>> statsCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> statNameToId = new ConcurrentHashMap<>();
    private final Map<Integer, String> statIdToName = new ConcurrentHashMap<>();


    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();

    public DatabaseManager(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.logger = plugin.getSLF4JLogger();
        this.fileName = fileName;

        initialize();
    }


    private void initialize() {
        setupDataSource(this.fileName);
        createTables();
        loadAllData();
    }

    public void reload() {
        saveDirtyStats();
        close();
        initialize();
    }
    private void setupDataSource(String fileName) {
        HikariConfig config = new HikariConfig();

        if (plugin.getConfig().getBoolean("use-remote-database")) {
            config.setJdbcUrl(plugin.getConfig().getString("url"));
            config.setUsername(plugin.getConfig().getString("username"));
            config.setPassword(plugin.getConfig().getString("password"));
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {

            File databaseFile = new File(plugin.getDataFolder(), "database/" + fileName);
            if (!databaseFile.getParentFile().exists()) {
                databaseFile.getParentFile().mkdirs();
            }
            config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
        }

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setPoolName("KetchupStats-Pool");

        this.dataSource = new HikariDataSource(config);
    }

    /**
     * Closes the Hikari connection pool.
     * Must be called in JavaPlugin#onDisable().
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS stats (" +
                    "stat_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "stat_name TEXT UNIQUE NOT NULL" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_stats (" +
                    "uuid TEXT NOT NULL," +
                    "stat_id INTEGER NOT NULL," +
                    "value REAL NOT NULL," +
                    "PRIMARY KEY(uuid, stat_id)," +
                    "FOREIGN KEY(stat_id) REFERENCES stats(stat_id)" +
                    ");");

        } catch (SQLException e) {
            logger.error("Failed to create tables", e);
        }
    }


    /**
     * Loads all stat definitions and all player data into the cache (Eager Loading).
     * Must be called synchronously in JavaPlugin#onEnable() before any API usage.
     */
    public void loadAllData() {
        statNameToId.clear();
        statIdToName.clear();
        statsCache.clear();


        String sqlDef = "SELECT stat_id, stat_name FROM stats";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement psDef = conn.prepareStatement(sqlDef);
             ResultSet rsDef = psDef.executeQuery()) {

            while (rsDef.next()) {
                int id = rsDef.getInt("stat_id");
                String name = rsDef.getString("stat_name");
                statNameToId.put(name, id);
                statIdToName.put(id, name);
                statsCache.putIfAbsent(name, new ConcurrentHashMap<>());
            }
        } catch (SQLException e) {
            logger.error("Could not load stat definitions!", e);
        }

        String sqlData = "SELECT uuid, stat_id, value FROM player_stats";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement psData = conn.prepareStatement(sqlData);
             ResultSet rsData = psData.executeQuery()) {

            while (rsData.next()) {
                UUID uuid = UUID.fromString(rsData.getString("uuid"));
                int statId = rsData.getInt("stat_id");
                double value = rsData.getDouble("value");

                String statName = statIdToName.get(statId);

                if (statName != null) {
                    statsCache.get(statName).put(uuid, value);
                } else {
                    logger.warn("Found obsolete player data for unknown stat ID: {}", statId);
                }
            }
            logger.info("Successfully loaded all data into RAM (Eager Loading).");
        } catch (SQLException e) {
            logger.error("Could not load ALL player data!", e);
        }
    }


    private void savePlayerStats(UUID uuid) {
        String sql = "INSERT OR REPLACE INTO player_stats (uuid, stat_id, value) VALUES (?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (Map.Entry<String, Map<UUID, Double>> entry : statsCache.entrySet()) {
                String statName = entry.getKey();
                Map<UUID, Double> playerMap = entry.getValue();

                if (!playerMap.containsKey(uuid)) continue;

                Integer statId = statNameToId.get(statName);
                if (statId == null) continue;

                ps.setString(1, uuid.toString());
                ps.setInt(2, statId);
                ps.setDouble(3, playerMap.get(uuid));
                ps.addBatch();
            }
            ps.executeBatch();
            dirtyPlayers.remove(uuid);

        } catch (SQLException e) {
            logger.error("Failed to save stats for {}", uuid, e);
        }
    }

    /**
     * Saves stats for all 'dirty' players to the database.
     * Must be called ASYNCHRONOUSLY via a repeating Bukkit scheduler task (e.g., every 5 minutes).
     */
    public void saveDirtyStats() {
        if (dirtyPlayers.isEmpty()) return;

        Set<UUID> toSave = new HashSet<>(dirtyPlayers);

        logger.info("Auto-saving stats for {} dirty players...", toSave.size());

        for (UUID uuid : toSave) {
            if (dirtyPlayers.contains(uuid)) {
                savePlayerStats(uuid);
            }
        }
    }

    /**
     * Saves player data and removes it from the RAM cache.
     * Must be called ASYNCHRONOUSLY in a PlayerQuitEvent listener.
     */
    public void unloadPlayerStats(UUID uuid) {

        if (dirtyPlayers.contains(uuid)) {

            savePlayerStats(uuid);
        }


        dirtyPlayers.remove(uuid);
    }



    /**
     * Gets a player's stat value from the RAM cache.
     * Can be called synchronously by PAPI, API methods, or command executors.
     */
    public double getStat(String statName, UUID uuid) {
        if (!isStatRegistered(statName)) {

            return 0.0;
        }

        return statsCache.getOrDefault(statName, Collections.emptyMap()).getOrDefault(uuid, 0.0);
    }

    /**
     * Sets a player's stat value and marks the player as 'dirty'.
     * Can be called synchronously from any thread (events, commands).
     */
    public void setStat(String statName, UUID uuid, double value) {
        if (!isStatRegistered(statName)) return;

        // KOREKCE: Zajištění, že hodnota nikdy neklesne pod nulu
        double finalValue = Math.max(0, value);

        statsCache.computeIfAbsent(statName, k -> new ConcurrentHashMap<>()).put(uuid, finalValue);

        dirtyPlayers.add(uuid);
    }

    /**
     * Increments a player's stat value and marks the player as 'dirty'.
     * Can be called synchronously from any thread (events, commands).
     */
    public void addStat(String statName, UUID uuid, double amount) {
        setStat(statName, uuid, getStat(statName, uuid) + amount);
    }

    /**
     * Checks if a statistic is defined.
     * Can be called synchronously.
     */
    public boolean isStatRegistered(String statName) {
        return statNameToId.containsKey(statName);
    }

    /**
     * Creates a new statistic definition in the database and registers it in the cache.
     * Must be called ASYNCHRONOUSLY to prevent lag.
     */
    public boolean createStat(String statName){
        synchronized (statNameToId) {
            if (isStatRegistered(statName)) return false;

            String sql = "INSERT INTO stats (stat_name) VALUES (?)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, statName);
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        int statId = keys.getInt(1);

                        statNameToId.put(statName, statId);
                        statIdToName.put(statId, statName);
                        statsCache.put(statName, new ConcurrentHashMap<>());

                        logger.info("Created new stat: {} (ID: {})", statName, statId);
                        return true;
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to create stat '{}'", statName, e);
            }
            return false;
        }
    }

    /**
     * Deletes a statistic definition from the database and removes all associated player data.
     * Must be called ASYNCHRONOUSLY to prevent server lag.
     */
    public boolean deleteStat(String statName) {
        synchronized (statNameToId) {
            if (!isStatRegistered(statName)) {
                return true;
            }

            Integer statId = statNameToId.get(statName);
            if (statId == null) return false;


            String sqlDeletePlayerStats = "DELETE FROM player_stats WHERE stat_id = ?";
            String sqlDeleteStat = "DELETE FROM stats WHERE stat_id = ?";

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try (PreparedStatement psData = conn.prepareStatement(sqlDeletePlayerStats)) {
                    psData.setInt(1, statId);
                    psData.executeUpdate();
                }

                try (PreparedStatement psStat = conn.prepareStatement(sqlDeleteStat)) {
                    psStat.setInt(1, statId);
                    psStat.executeUpdate();
                }

                conn.commit();

                statNameToId.remove(statName);
                statIdToName.remove(statId);
                statsCache.remove(statName);

                logger.info("Successfully deleted stat: {} (ID: {}) and all associated player data.", statName, statId);
                return true;

            } catch (SQLException e) {
                logger.error("Failed to delete stat '{}'", statName, e);
                return false;
            }
        }
    }

    /**
     * Provides an unmodifiable map of the entire stats cache.
     * Used mainly by external leaderboard plugins (AJLeaderboards) to read all player data.
     */
    public Map<String, Map<UUID, Double>> getStatsCache() {

        return Collections.unmodifiableMap(statsCache);
    }

    public List<String> getAllStatNames() {

        return new ArrayList<>(statNameToId.keySet());
    }
}