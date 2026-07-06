package org.maks.farmingPlugin.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.maks.farmingPlugin.FarmingPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {
    private final FarmingPlugin plugin;
    private HikariDataSource dataSource;

    private String host, database, username, password;
    private int port;

    public DatabaseManager(FarmingPlugin plugin) {
        this.plugin = plugin;
        loadDatabaseSettings();
    }

    private void loadDatabaseSettings() {
        // Reload configuration to ensure we're reading latest values
        plugin.reloadConfig();

        // Support both legacy and new config keys
        host = plugin.getConfig().getString("database.host",
            plugin.getConfig().getString("database.hostname", "localhost"));
        port = plugin.getConfig().getInt("database.port", 3306);
        database = plugin.getConfig().getString("database.name",
            plugin.getConfig().getString("database.database", "plantation"));
        username = plugin.getConfig().getString("database.user",
            plugin.getConfig().getString("database.username", "root"));
        password = plugin.getConfig().getString("database.password", "");
    }

    public void connect() {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                return;
            }

            synchronized (this) {
                if (dataSource != null && !dataSource.isClosed()) {
                    return;
                }

                // Configure HikariCP
                HikariConfig config = new HikariConfig();
                
                // Try relocated driver first, then fall back to original
                try {
                    Class.forName("org.maks.farmingPlugin.libs.mysql.cj.jdbc.Driver");
                    config.setDriverClassName("org.maks.farmingPlugin.libs.mysql.cj.jdbc.Driver");
                    plugin.getLogger().info("Using relocated MySQL driver");
                } catch (ClassNotFoundException e) {
                    plugin.getLogger().warning("Relocated MySQL driver not found, trying original...");
                    config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                    plugin.getLogger().info("Using original MySQL driver");
                }
                
                String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + 
                    "?useSSL=false&serverTimezone=UTC&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8";
                
                config.setJdbcUrl(jdbcUrl);
                config.setUsername(username);
                config.setPassword(password);
                
                // Connection pool settings from config or defaults
                config.setMaximumPoolSize(plugin.getConfig().getInt("database.hikari.maximum_pool_size", 10));
                config.setMinimumIdle(plugin.getConfig().getInt("database.hikari.minimum_idle", 5));
                config.setConnectionTimeout(plugin.getConfig().getLong("database.hikari.connection_timeout", 30000));
                config.setIdleTimeout(plugin.getConfig().getLong("database.hikari.idle_timeout", 600000));
                config.setMaxLifetime(plugin.getConfig().getLong("database.hikari.max_lifetime", 1800000));
                config.setLeakDetectionThreshold(plugin.getConfig().getLong("database.hikari.leak_detection_threshold", 60000));
                
                // Connection properties
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                config.addDataSourceProperty("useServerPrepStmts", "true");
                config.addDataSourceProperty("rewriteBatchedStatements", "true");
                config.addDataSourceProperty("cacheResultSetMetadata", "true");
                config.addDataSourceProperty("cacheServerConfiguration", "true");
                config.addDataSourceProperty("elideSetAutoCommits", "true");
                config.addDataSourceProperty("maintainTimeStats", "false");
                
                config.setPoolName("FarmingPlugin-HikariCP");
                
                plugin.getLogger().info("Initializing HikariCP connection pool to: " + host + ":" + port + "/" + database);
                
                dataSource = new HikariDataSource(config);
                
                // Test the connection
                try (Connection testConnection = dataSource.getConnection()) {
                    if (testConnection.isValid(5)) {
                        plugin.getLogger().info("Successfully connected to MySQL database with HikariCP!");
                        plugin.getLogger().info("Connection pool initialized with " + config.getMaximumPoolSize() + " max connections");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not connect to MySQL database!", e);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error initializing database connection!", e);
        }
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
                plugin.getLogger().info("HikariCP connection pool closed!");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not close HikariCP connection pool!", e);
            }
        }
    }

    public Connection getConnection() {
        try {
            if (dataSource == null || dataSource.isClosed()) {
                connect();
            }
            return dataSource.getConnection();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not get connection from pool!", e);
            // Try to reconnect
            connect();
            try {
                return dataSource != null ? dataSource.getConnection() : null;
            } catch (SQLException e2) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get connection after reconnect!", e2);
                return null;
            }
        }
    }

    public void createTables() {
        // Main player plantations table
        String playerPlantationsTable = """
            CREATE TABLE IF NOT EXISTS farming_player_plantations (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                farm_type VARCHAR(50) NOT NULL,
                instance_id INT NOT NULL,
                level INT DEFAULT 1,
                efficiency INT DEFAULT 1,
                last_harvest BIGINT NOT NULL,
                total_harvests INT DEFAULT 0,
                exp INT DEFAULT 0,
                storage_upgrade_level INT DEFAULT 0,
                speed_upgrade_level INT DEFAULT 0,
                quality_upgrade_level INT DEFAULT 0,
                current_stored_items INT DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_uuid (uuid),
                INDEX idx_farm_type (farm_type),
                UNIQUE KEY unique_farm_instance (uuid, farm_type, instance_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

        // Player materials inventory
        String playerMaterialsTable = """
            CREATE TABLE IF NOT EXISTS farming_player_materials (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                material_type VARCHAR(50) NOT NULL,
                tier INT NOT NULL,
                amount INT NOT NULL DEFAULT 0,
                total_collected BIGINT DEFAULT 0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_uuid (uuid),
                INDEX idx_material (material_type),
                UNIQUE KEY unique_material (uuid, material_type, tier)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

        // Farm storage table
        String plantationStorageTable = """
            CREATE TABLE IF NOT EXISTS farming_plantation_storage (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                farm_type VARCHAR(50) NOT NULL,
                instance_id INT NOT NULL,
                stored_materials_json TEXT,
                auto_collect BOOLEAN DEFAULT FALSE,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_uuid (uuid),
                UNIQUE KEY unique_storage (uuid, farm_type, instance_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

        // Player plots locations
        String playerPlotsTable = """
            CREATE TABLE IF NOT EXISTS farming_player_plots (
                uuid VARCHAR(36) PRIMARY KEY,
                world VARCHAR(64) NOT NULL,
                origin_x INT NOT NULL,
                origin_y INT NOT NULL,
                origin_z INT NOT NULL,
                plot_size INT DEFAULT 16,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_world (world)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

        // Farm anchors (exact positions of farms)
        String farmAnchorsTable = """
            CREATE TABLE IF NOT EXISTS farming_farm_anchors (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                farm_type VARCHAR(50) NOT NULL,
                instance_id INT NOT NULL,
                anchor_x INT NOT NULL,
                anchor_y INT NOT NULL,
                anchor_z INT NOT NULL,
                active BOOLEAN DEFAULT TRUE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_uuid (uuid),
                UNIQUE KEY unique_anchor (uuid, farm_type, instance_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

        // Farm upgrades history
        String farmUpgradesTable = """
            CREATE TABLE IF NOT EXISTS farming_farm_upgrades (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                farm_type VARCHAR(50) NOT NULL,
                instance_id INT NOT NULL,
                upgrade_type VARCHAR(50) NOT NULL,
                old_value INT,
                new_value INT,
                cost DECIMAL(20, 2),
                materials_used_json TEXT,
                upgraded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_uuid (uuid),
                INDEX idx_farm (farm_type, instance_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

        // Player statistics
        String playerStatsTable = """
            CREATE TABLE IF NOT EXISTS farming_player_stats (
                uuid VARCHAR(36) PRIMARY KEY,
                total_farms_created INT DEFAULT 0,
                total_harvests BIGINT DEFAULT 0,
                total_materials_collected BIGINT DEFAULT 0,
                total_money_spent DECIMAL(20, 2) DEFAULT 0,
                total_money_earned DECIMAL(20, 2) DEFAULT 0,
                play_time_minutes INT DEFAULT 0,
                last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

        // Harvest log for analytics
        String harvestLogTable = """
            CREATE TABLE IF NOT EXISTS farming_harvest_log (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                farm_type VARCHAR(50) NOT NULL,
                instance_id INT NOT NULL,
                materials_json TEXT,
                harvest_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_uuid (uuid),
                INDEX idx_time (harvest_time),
                INDEX idx_farm (farm_type)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

        // Farm unlock history
        String farmUnlocksTable = """
            CREATE TABLE IF NOT EXISTS farming_farm_unlocks (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                farm_type VARCHAR(50) NOT NULL,
                unlock_cost DECIMAL(20, 2),
                materials_used_json TEXT,
                unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_uuid (uuid),
                UNIQUE KEY unique_unlock (uuid, farm_type)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

        // Settings per player
        String playerSettingsTable = """
            CREATE TABLE IF NOT EXISTS farming_player_settings (
                uuid VARCHAR(36) PRIMARY KEY,
                auto_collect_enabled BOOLEAN DEFAULT FALSE,
                hologram_enabled BOOLEAN DEFAULT TRUE,
                notifications_enabled BOOLEAN DEFAULT TRUE,
                particle_effects_enabled BOOLEAN DEFAULT TRUE,
                drop_to_inventory BOOLEAN DEFAULT FALSE,
                language VARCHAR(10) DEFAULT 'en',
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            // Execute all table creation statements
            stmt.executeUpdate(playerPlantationsTable);
            stmt.executeUpdate(playerMaterialsTable);
            stmt.executeUpdate(plantationStorageTable);
            stmt.executeUpdate(playerPlotsTable);
            stmt.executeUpdate(farmAnchorsTable);
            stmt.executeUpdate(farmUpgradesTable);
            stmt.executeUpdate(playerStatsTable);
            stmt.executeUpdate(harvestLogTable);
            stmt.executeUpdate(farmUnlocksTable);
            stmt.executeUpdate(playerSettingsTable);
            
            plugin.getLogger().info("All database tables created successfully!");

            // Create stored procedures for complex operations
            createStoredProcedures();
            
            // Run database migrations
            runDatabaseMigrations();
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create database tables!", e);
        }
    }

    private void createStoredProcedures() {
        // Stored procedure to get player's total material count
        String getMaterialCountProc = """
            CREATE PROCEDURE IF NOT EXISTS GetPlayerMaterialCount(IN player_uuid VARCHAR(36))
            BEGIN
                SELECT material_type, tier, amount 
                FROM farming_player_materials 
                WHERE uuid = player_uuid AND amount > 0
                ORDER BY material_type, tier;
            END
            """;

        // Stored procedure to process harvest
        String processHarvestProc = """
            CREATE PROCEDURE IF NOT EXISTS ProcessHarvest(
                IN player_uuid VARCHAR(36),
                IN p_farm_type VARCHAR(50),
                IN p_instance_id INT,
                IN materials_json TEXT
            )
            BEGIN
                -- Update harvest count
                UPDATE farming_player_plantations 
                SET total_harvests = total_harvests + 1,
                    last_harvest = UNIX_TIMESTAMP() * 1000
                WHERE uuid = player_uuid 
                  AND farm_type = p_farm_type 
                  AND instance_id = p_instance_id;
                
                -- Log harvest
                INSERT INTO farming_harvest_log (uuid, farm_type, instance_id, materials_json)
                VALUES (player_uuid, p_farm_type, p_instance_id, materials_json);
                
                -- Update player stats
                UPDATE farming_player_stats 
                SET total_harvests = total_harvests + 1
                WHERE uuid = player_uuid;
            END
            """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP PROCEDURE IF EXISTS GetPlayerMaterialCount");
            stmt.execute(getMaterialCountProc);
            
            stmt.execute("DROP PROCEDURE IF EXISTS ProcessHarvest");
            stmt.execute(processHarvestProc);
            
            plugin.getLogger().info("Stored procedures created successfully!");
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not create stored procedures: " + e.getMessage());
        }
    }

    private void runDatabaseMigrations() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            // Migration 1: Add upgrade level columns to farming_player_plantations if they don't exist
            try {
                stmt.executeUpdate("ALTER TABLE farming_player_plantations ADD COLUMN storage_upgrade_level INT DEFAULT 0");
                plugin.getLogger().info("Added storage_upgrade_level column to farming_player_plantations");
            } catch (SQLException e) {
                if (!e.getMessage().contains("Duplicate column name")) {
                    plugin.getLogger().warning("Failed to add storage_upgrade_level column: " + e.getMessage());
                }
            }
            
            try {
                stmt.executeUpdate("ALTER TABLE farming_player_plantations ADD COLUMN speed_upgrade_level INT DEFAULT 0");
                plugin.getLogger().info("Added speed_upgrade_level column to farming_player_plantations");
            } catch (SQLException e) {
                if (!e.getMessage().contains("Duplicate column name")) {
                    plugin.getLogger().warning("Failed to add speed_upgrade_level column: " + e.getMessage());
                }
            }
            
            try {
                stmt.executeUpdate("ALTER TABLE farming_player_plantations ADD COLUMN quality_upgrade_level INT DEFAULT 0");
                plugin.getLogger().info("Added quality_upgrade_level column to farming_player_plantations");
            } catch (SQLException e) {
                if (!e.getMessage().contains("Duplicate column name")) {
                    plugin.getLogger().warning("Failed to add quality_upgrade_level column: " + e.getMessage());
                }
            }
            
            try {
                stmt.executeUpdate("ALTER TABLE farming_player_plantations ADD COLUMN current_stored_items INT DEFAULT 0");
                plugin.getLogger().info("Added current_stored_items column to farming_player_plantations");
            } catch (SQLException e) {
                if (!e.getMessage().contains("Duplicate column name")) {
                    plugin.getLogger().warning("Failed to add current_stored_items column: " + e.getMessage());
                }
            }
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not run database migrations!", e);
        }
    }


    // Player plot management
    public void savePlayerPlot(UUID uuid, String world, int x, int y, int z) {
        String sql = "INSERT INTO farming_player_plots (uuid, world, origin_x, origin_y, origin_z) " +
                    "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                    "world = VALUES(world), origin_x = VALUES(origin_x), " +
                    "origin_y = VALUES(origin_y), origin_z = VALUES(origin_z)";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, world);
            ps.setInt(3, x);
            ps.setInt(4, y);
            ps.setInt(5, z);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player plot", e);
        }
    }

    public Optional<Location> loadPlayerPlot(UUID uuid) {
        String sql = "SELECT world, origin_x, origin_y, origin_z FROM farming_player_plots WHERE uuid = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    World world = Bukkit.getWorld(rs.getString("world"));
                    if (world != null) {
                        return Optional.of(new Location(
                            world,
                            rs.getInt("origin_x"),
                            rs.getInt("origin_y"),
                            rs.getInt("origin_z")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load player plot", e);
        }
        
        return Optional.empty();
    }

    // Player statistics
    public void updatePlayerStats(UUID uuid, String statType, long value) {
        String sql = "INSERT INTO farming_player_stats (uuid, " + statType + ") VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE " + statType + " = " + statType + " + VALUES(" + statType + ")";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not update player stats: " + e.getMessage());
        }
    }

    // Player settings
    public void savePlayerSetting(UUID uuid, String setting, Object value) {
        String sql = "INSERT INTO farming_player_settings (uuid, " + setting + ") VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE " + setting + " = VALUES(" + setting + ")";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setObject(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save player setting: " + e.getMessage());
        }
    }

    public boolean getPlayerBooleanSetting(UUID uuid, String setting, boolean defaultValue) {
        String sql = "SELECT " + setting + " FROM farming_player_settings WHERE uuid = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load player setting: " + e.getMessage());
        }
        
        return defaultValue;
    }

    // Material management
    public int getPlayerMaterialAmount(UUID uuid, String materialType, int tier) {
        String sql = "SELECT amount FROM farming_player_materials WHERE uuid = ? AND material_type = ? AND tier = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, materialType);
            ps.setInt(3, tier);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("amount");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not get material amount: " + e.getMessage());
        }
        
        return 0;
    }

    public void updatePlayerMaterial(UUID uuid, String materialType, int tier, int amount) {
        String sql = "INSERT INTO farming_player_materials (uuid, material_type, tier, amount, total_collected) " +
                    "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                    "amount = amount + VALUES(amount), " +
                    "total_collected = total_collected + IF(VALUES(amount) > 0, VALUES(amount), 0)";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, materialType);
            ps.setInt(3, tier);
            ps.setInt(4, amount);
            ps.setInt(5, amount > 0 ? amount : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not update player material: " + e.getMessage());
        }
    }

    // Farm unlock tracking
    public void saveFarmUnlock(UUID uuid, String farmType, double cost, String materialsJson) {
        String sql = "INSERT INTO farming_farm_unlocks (uuid, farm_type, unlock_cost, materials_used_json) " +
                    "VALUES (?, ?, ?, ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, farmType);
            ps.setDouble(3, cost);
            ps.setString(4, materialsJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save farm unlock: " + e.getMessage());
        }
    }

    public boolean isFarmUnlocked(UUID uuid, String farmType) {
        String sql = "SELECT 1 FROM farming_farm_unlocks WHERE uuid = ? AND farm_type = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, farmType);
            
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not check farm unlock: " + e.getMessage());
        }
        
        return false;
    }
}
