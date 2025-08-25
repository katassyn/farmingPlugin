package org.maks.farmingPlugin.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.database.DatabaseManager;
import org.maks.farmingPlugin.farms.FarmType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles allocation and basic management of player plantation areas.
 * This is a lightweight implementation that provides plot generation,
 * anchor block placement and simple boundary checks.
 */
public class PlantationAreaManager {

    private final FarmingPlugin plugin;
    private final Map<UUID, PlantationArea> areas = new HashMap<>();

    private final World world;
    private final int originX, originY, originZ;
    private final int plotWidth, plotDepth, spacing, gridRows, gridCols;
    private final Material fenceMaterial, gateMaterial;

    public PlantationAreaManager(FarmingPlugin plugin) {
        this.plugin = plugin;

        ConfigurationSection base = plugin.getConfig().getConfigurationSection("plantations.base");
        this.world = Bukkit.getWorld(plugin.getConfig().getString("plantations.world", "world"));
        ConfigurationSection originSec = base.getConfigurationSection("origin");
        this.originX = originSec.getInt("x");
        this.originY = originSec.getInt("y");
        this.originZ = originSec.getInt("z");
        ConfigurationSection plotSec = base.getConfigurationSection("plot");
        this.plotWidth = plotSec.getInt("width");
        this.plotDepth = plotSec.getInt("depth");
        this.fenceMaterial = Material.valueOf(plotSec.getString("fence_material", "OAK_FENCE"));
        this.gateMaterial = Material.valueOf(plotSec.getString("gate_material", "OAK_FENCE_GATE"));
        this.spacing = base.getInt("spacing");
        ConfigurationSection gridSec = base.getConfigurationSection("grid");
        this.gridRows = gridSec.getInt("rows");
        this.gridCols = gridSec.getInt("cols");
    }

    /**
     * Gets existing plantation area for player or creates a new one.
     */
    public PlantationArea getOrCreateArea(Player player) {
        return areas.computeIfAbsent(player.getUniqueId(), uuid -> {
            DatabaseManager db = plugin.getDatabaseManager();
            Location origin = db.loadPlayerPlot(uuid).orElseGet(() -> allocateNewPlot(uuid));
            buildPlot(origin);
            Map<FarmType, Location> anchors = placeAnchors(origin);
            return new PlantationArea(uuid, origin, anchors, plotWidth, plotDepth);
        });
    }

    private Location allocateNewPlot(UUID uuid) {
        int index = areas.size();
        int row = index / gridCols;
        int col = index % gridCols;
        int x = originX + col * (plotWidth + spacing);
        int z = originZ + row * (plotDepth + spacing);
        Location loc = new Location(world, x, originY, z);
        plugin.getDatabaseManager().savePlayerPlot(uuid, world.getName(), x, originY, z);
        return loc;
    }

    private void buildPlot(Location origin) {
        if (world == null) return;
        int x1 = origin.getBlockX();
        int z1 = origin.getBlockZ();
        int x2 = x1 + plotWidth - 1;
        int z2 = z1 + plotDepth - 1;
        int y = origin.getBlockY();

        for (int x = x1; x <= x2; x++) {
            world.getBlockAt(x, y, z1).setType(fenceMaterial);
            world.getBlockAt(x, y, z2).setType(fenceMaterial);
        }
        for (int z = z1; z <= z2; z++) {
            world.getBlockAt(x1, y, z).setType(fenceMaterial);
            world.getBlockAt(x2, y, z).setType(fenceMaterial);
        }
        world.getBlockAt(x1 + plotWidth / 2, y, z1).setType(gateMaterial);
    }

    private Map<FarmType, Location> placeAnchors(Location origin) {
        Map<FarmType, Location> anchors = new EnumMap<>(FarmType.class);
        int[][] offsets = { {2,2}, {4,2}, {6,2}, {2,4}, {4,4}, {6,4}, {4,6} };
        FarmType[] types = FarmType.values();
        for (int i = 0; i < Math.min(types.length, offsets.length); i++) {
            Location loc = origin.clone().add(offsets[i][0], 0, offsets[i][1]);
            if (world != null) {
                world.getBlockAt(loc).setType(types[i].getBlockType());
            }
            anchors.put(types[i], loc);
        }
        return anchors;
    }

    public boolean isLocationInPlantation(UUID owner, Location loc) {
        PlantationArea area = areas.get(owner);
        return area != null && area.contains(loc);
    }

    public int getFarmInstanceFromLocation(UUID owner, FarmType type, Location loc) {
        // Each anchor represents instance #1 for that farm type in this basic implementation
        return 1;
    }

    public Location getAnchor(UUID owner, FarmType type) {
        PlantationArea area = areas.get(owner);
        return area != null ? area.anchors.get(type) : null;
    }

    /** Represents a single player's plantation plot. */
    public static class PlantationArea {
        private final UUID owner;
        private final Location origin;
        private final Map<FarmType, Location> anchors;
        private final int width;
        private final int depth;

        PlantationArea(UUID owner, Location origin, Map<FarmType, Location> anchors, int width, int depth) {
            this.owner = owner;
            this.origin = origin;
            this.anchors = anchors;
            this.width = width;
            this.depth = depth;
        }

        public Location getCenter() {
            return origin.clone().add(width / 2.0, 1, depth / 2.0);
        }

        boolean contains(Location loc) {
            if (loc == null || origin.getWorld() == null) return false;
            if (!loc.getWorld().equals(origin.getWorld())) return false;
            int x = loc.getBlockX();
            int z = loc.getBlockZ();
            int x1 = origin.getBlockX();
            int z1 = origin.getBlockZ();
            return x >= x1 && x < x1 + width && z >= z1 && z < z1 + depth;
        }
    }
}
