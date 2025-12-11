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
    private HikariDataSource dataSource; // Connection Pool

    // Thread-safe mapy pro Eager Loading (veškerá data v RAM)
    private final Map<String, Map<UUID, Double>> statsCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> statNameToId = new ConcurrentHashMap<>();
    private final Map<Integer, String> statIdToName = new ConcurrentHashMap<>();

    // Dirty checking (sledování změn pro ukládání)
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();

    public DatabaseManager(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.logger = plugin.getSLF4JLogger();

        setupDataSource(fileName);
        createTables();

        loadAllData(); // Eager Loading
    }

    private void setupDataSource(String fileName) {
        HikariConfig config = new HikariConfig();

        if (plugin.getConfig().getBoolean("use-remote-database")) {
            // Konfigurace pro MySQL/Postgres
            config.setJdbcUrl(plugin.getConfig().getString("url"));
            config.setUsername(plugin.getConfig().getString("username"));
            config.setPassword(plugin.getConfig().getString("password"));
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            // Konfigurace pro SQLite
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

        // Zde můžete vidět, jak Connection Pool architektura nahrazuje jedno připojení pro lepší stabilitu

    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            // Volat v onDisable()
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

        // 2. Načtení VŠECH HODNOT hráčů (i offline) do RAM
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

    // --- LOGIKA UKLÁDÁNÍ ---

    // Uloží konkrétního hráče (pomocná metoda - používá se při Quit nebo v saveDirtyStats)
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
            dirtyPlayers.remove(uuid); // Po úspěšném uložení již není "špinavý"

        } catch (SQLException e) {
            logger.error("Failed to save stats for {}", uuid, e);
        }
    }

    // Uloží JEN ty, co se změnili. Volat v periodickém tasku.
    public void saveDirtyStats() {
        if (dirtyPlayers.isEmpty()) return;

        // Vytvoříme kopii Setu pro bezpečné zpracování, zatímco hráči dál hrají
        Set<UUID> toSave = new HashSet<>(dirtyPlayers);

        logger.info("Auto-saving stats for {} dirty players...", toSave.size());

        for (UUID uuid : toSave) {
            // Opravdu uložíme pouze ty, kteří jsou stále v dirtyPlayers setu (synchronní ochrana)
            if (dirtyPlayers.contains(uuid)) {
                savePlayerStats(uuid);
            }
        }
    }

    public void unloadPlayerStats(UUID uuid) {
        // Při odpojení uložíme data (pokud jsou dirty) a vyčistíme RAM
        if (dirtyPlayers.contains(uuid)) {
            // Musí být voláno ASYNCHRONNĚ
            savePlayerStats(uuid);
        }

        // Vyčištění z RAM (Memory management)
        for (Map<UUID, Double> map : statsCache.values()) {
            map.remove(uuid);
        }
        dirtyPlayers.remove(uuid);
    }

    // --- RAM API (Synchronní a Okamžité pro PAPI) ---

    public double getStat(String statName, UUID uuid) {
        if (!isStatRegistered(statName)) {
            // Pokud není registrována, není ani v cache
            return 0.0;
        }
        // data jsou vždy v RAM, vracíme okamžitě
        return statsCache.getOrDefault(statName, Collections.emptyMap())
                .getOrDefault(uuid, 0.0);
    }

    public void setStat(String statName, UUID uuid, double value) {
        if (!isStatRegistered(statName)) return;

        statsCache.computeIfAbsent(statName, k -> new ConcurrentHashMap<>())
                .put(uuid, value);

        // Označíme hráče jako "Dirty" -> bude uložen při příštím autosave
        dirtyPlayers.add(uuid);
    }

    public void addStat(String statName, UUID uuid, double amount) {
        setStat(statName, uuid, getStat(statName, uuid) + amount);
    }

    public boolean isStatRegistered(String statName) {
        return statNameToId.containsKey(statName);
    }

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

    // --- Gettery pro Placeholders (AJL) ---
    // AJL potřebuje přístup k datům pro všechny hráče.

    public Map<String, Map<UUID, Double>> getStatsCache() {
        // Vracíme původní mapu, aby AJL viděl vše, ale vracíme ji jako nemodifikovatelnou
        return Collections.unmodifiableMap(statsCache);
    }
}