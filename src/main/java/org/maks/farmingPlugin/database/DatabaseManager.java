package org.maks.farmingPlugin.database;

import org.bukkit.Bukkit;
import org.maks.farmingPlugin.FarmingPlugin;

import java.sql.*;
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
        host = plugin.getConfig().getString("database.host", "localhost");
        port = plugin.getConfig().getInt("database.port", 3306);
        database = plugin.getConfig().getString("database.database", "plantation");
        username = plugin.getConfig().getString("database.username", "root");
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

                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(
                    "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC",
                    username, password
                );
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
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not check connection status!", e);
        }
        return connection;
    }

    public void createTables() {
        String playerPlantationsTable = """
            CREATE TABLE IF NOT EXISTS player_plantations (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                farm_type VARCHAR(50) NOT NULL,
                instance_id INT NOT NULL,
                level INT DEFAULT 1,
                efficiency INT DEFAULT 1,
                last_harvest BIGINT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_uuid (uuid),
                INDEX idx_farm_type (farm_type),
                UNIQUE KEY unique_farm_instance (uuid, farm_type, instance_id)
            )
            """;

        String playerMaterialsTable = """
            CREATE TABLE IF NOT EXISTS player_materials (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                material_type VARCHAR(50) NOT NULL,
                tier INT NOT NULL,
                amount INT NOT NULL DEFAULT 0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_uuid (uuid),
                INDEX idx_material (material_type),
                UNIQUE KEY unique_material (uuid, material_type, tier)
            )
            """;

        String plantationStorageTable = """
            CREATE TABLE IF NOT EXISTS plantation_storage (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                farm_type VARCHAR(50) NOT NULL,
                instance_id INT NOT NULL,
                stored_materials_json TEXT,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_uuid (uuid),
                UNIQUE KEY unique_storage (uuid, farm_type, instance_id)
            )
            """;

        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(playerPlantationsTable);
            stmt.executeUpdate(playerMaterialsTable);
            stmt.executeUpdate(plantationStorageTable);
            plugin.getLogger().info("Database tables created successfully!");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create database tables!", e);
        }
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return getConnection().prepareStatement(sql);
    }
}