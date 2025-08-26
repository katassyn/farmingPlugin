package org.maks.farmingPlugin.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.maks.farmingPlugin.FarmingPlugin;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {
    private final FarmingPlugin plugin;
    private Connection connection;

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
            if (connection != null && !connection.isClosed()) {
                return;
            }

            synchronized (this) {
                if (connection != null && !connection.isClosed()) {
                    return;
                }

                // Try relocated driver first, then fall back to original
                try {
                    Class.forName("org.maks.farmingPlugin.libs.mysql.cj.jdbc.Driver");
                    plugin.getLogger().info("Using relocated MySQL driver");
                } catch (ClassNotFoundException e) {
                    plugin.getLogger().warning("Relocated MySQL driver not found, trying original...");
                    Class.forName("com.mysql.cj.jdbc.Driver");
                    plugin.getLogger().info("Using original MySQL driver");
                }
                
                String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + 
                    "?useSSL=false&serverTimezone=UTC&autoReconnect=true";
                plugin.getLogger().info("Connecting to database: " + host + ":" + port + "/" + database);
                
                connection = DriverManager.getConnection(jdbcUrl, username, password);
                
                // Enable auto-reconnect
                connection.setAutoCommit(true);
                
                plugin.getLogger().info("Successfully connected to MySQL database!");
            }
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not connect to MySQL database!", e);
        }
    }

    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("Disconnected from MySQL database!");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not disconnect from MySQL database!", e);
            }
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(1)) {
                connect();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not check connection status!", e);
            connect();
        }
        return connection;
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

        try (Statement stmt = getConnection().createStatement()) {
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

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute("DROP PROCEDURE IF EXISTS GetPlayerMaterialCount");
            stmt.execute(getMaterialCountProc);
            
            stmt.execute("DROP PROCEDURE IF EXISTS ProcessHarvest");
            stmt.execute(processHarvestProc);
            
            plugin.getLogger().info("Stored procedures created successfully!");
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not create stored procedures: " + e.getMessage());
        }
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return getConnection().prepareStatement(sql);
    }

    // Player plot management
    public void savePlayerPlot(UUID uuid, String world, int x, int y, int z) {
        String sql = "INSERT INTO farming_player_plots (uuid, world, origin_x, origin_y, origin_z) " +
                    "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                    "world = VALUES(world), origin_x = VALUES(origin_x), " +
                    "origin_y = VALUES(origin_y), origin_z = VALUES(origin_z)";
        
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
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
        
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
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
        
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
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
        
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setObject(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save player setting: " + e.getMessage());
        }
    }

    public boolean getPlayerBooleanSetting(UUID uuid, String setting, boolean defaultValue) {
        String sql = "SELECT " + setting + " FROM farming_player_settings WHERE uuid = ?";
        
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
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
        
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
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
        
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
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
        
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
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
        
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
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
