package org.maks.farmingPlugin.managers;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.farms.FarmType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlantationAreaManager {
    private final FarmingPlugin plugin;
    private final Map<UUID, PlantationArea> playerAreas;
    private final int PLANTATION_SIZE = 64;
    private final int SPACING_BETWEEN_PLANTATIONS = 128;

    public PlantationAreaManager(FarmingPlugin plugin) {
        this.plugin = plugin;
        this.playerAreas = new ConcurrentHashMap<>();
    }

    public PlantationArea getOrCreatePlantationArea(UUID playerId) {
        return playerAreas.computeIfAbsent(playerId, this::createPlantationArea);
    }

    private PlantationArea createPlantationArea(UUID playerId) {
        World world = plugin.getServer().getWorld("world");
        if (world == null) {
            world = plugin.getServer().getWorlds().get(0);
        }

        int hash = Math.abs(playerId.hashCode());
        int gridX = (hash % 1000) * SPACING_BETWEEN_PLANTATIONS;
        int gridZ = ((hash / 1000) % 1000) * SPACING_BETWEEN_PLANTATIONS;

        int startX = gridX - PLANTATION_SIZE / 2;
        int startZ = gridZ - PLANTATION_SIZE / 2;
        int endX = gridX + PLANTATION_SIZE / 2;
        int endZ = gridZ + PLANTATION_SIZE / 2;

        Location center = new Location(world, gridX, 100, gridZ);
        Location corner1 = new Location(world, startX, 50, startZ);
        Location corner2 = new Location(world, endX, 150, endZ);

        return new PlantationArea(playerId, center, corner1, corner2, createFarmAreas());
    }

    private Map<FarmType, FarmAreaLayout> createFarmAreas() {
        Map<FarmType, FarmAreaLayout> farmAreas = new HashMap<>();
        
        farmAreas.put(FarmType.BERRY_ORCHARDS, new FarmAreaLayout(-30, -30, 6, 2, 3));
        farmAreas.put(FarmType.MELON_GROVES, new FarmAreaLayout(-30, -10, 6, 2, 3));
        farmAreas.put(FarmType.FUNGAL_CAVERNS, new FarmAreaLayout(-30, 10, 6, 2, 3));
        farmAreas.put(FarmType.PUMPKIN_PATCHES, new FarmAreaLayout(10, -30, 6, 2, 3));
        farmAreas.put(FarmType.MYSTIC_GARDENS, new FarmAreaLayout(10, -10, 3, 1, 3));
        farmAreas.put(FarmType.ANCIENT_MANGROVES, new FarmAreaLayout(10, 10, 3, 1, 3));
        farmAreas.put(FarmType.DESERT_SANCTUARIES, new FarmAreaLayout(0, 0, 1, 1, 1));
        
        return farmAreas;
    }

    public boolean isLocationInPlantation(UUID playerId, Location location) {
        PlantationArea area = playerAreas.get(playerId);
        if (area == null) {
            area = getOrCreatePlantationArea(playerId);
        }

        return area.contains(location);
    }

    public int getFarmInstanceFromLocation(UUID playerId, FarmType farmType, Location location) {
        PlantationArea area = getOrCreatePlantationArea(playerId);
        FarmAreaLayout layout = area.getFarmArea(farmType);
        
        if (layout == null) return 1;

        Location center = area.getCenter();
        int relativeX = (int) (location.getX() - center.getX() - layout.getOffsetX());
        int relativeZ = (int) (location.getZ() - center.getZ() - layout.getOffsetZ());

        if (relativeX < 0 || relativeZ < 0) return 1;

        int gridX = relativeX / layout.getSpacing();
        int gridZ = relativeZ / layout.getSpacing();

        if (gridX >= layout.getColumns() || gridZ >= layout.getRows()) return 1;

        int instance = (gridZ * layout.getColumns() + gridX) + 1;
        return Math.min(instance, farmType.getMaxInstances());
    }

    public Location getFarmInstanceLocation(UUID playerId, FarmType farmType, int instanceId) {
        PlantationArea area = getOrCreatePlantationArea(playerId);
        FarmAreaLayout layout = area.getFarmArea(farmType);
        
        if (layout == null || instanceId < 1 || instanceId > farmType.getMaxInstances()) {
            return area.getCenter();
        }

        int adjustedId = instanceId - 1;
        int gridX = adjustedId % layout.getColumns();
        int gridZ = adjustedId / layout.getColumns();

        Location center = area.getCenter();
        double x = center.getX() + layout.getOffsetX() + (gridX * layout.getSpacing());
        double z = center.getZ() + layout.getOffsetZ() + (gridZ * layout.getSpacing());

        return new Location(center.getWorld(), x, center.getY(), z);
    }

    public static class PlantationArea {
        private final UUID ownerId;
        private final Location center;
        private final Location corner1;
        private final Location corner2;
        private final Map<FarmType, FarmAreaLayout> farmAreas;

        public PlantationArea(UUID ownerId, Location center, Location corner1, Location corner2, 
                            Map<FarmType, FarmAreaLayout> farmAreas) {
            this.ownerId = ownerId;
            this.center = center;
            this.corner1 = corner1;
            this.corner2 = corner2;
            this.farmAreas = farmAreas;
        }

        public boolean contains(Location location) {
            if (!location.getWorld().equals(center.getWorld())) return false;

            double x = location.getX();
            double z = location.getZ();
            double y = location.getY();

            return x >= Math.min(corner1.getX(), corner2.getX()) &&
                   x <= Math.max(corner1.getX(), corner2.getX()) &&
                   z >= Math.min(corner1.getZ(), corner2.getZ()) &&
                   z <= Math.max(corner1.getZ(), corner2.getZ()) &&
                   y >= Math.min(corner1.getY(), corner2.getY()) &&
                   y <= Math.max(corner1.getY(), corner2.getY());
        }

        public UUID getOwnerId() { return ownerId; }
        public Location getCenter() { return center; }
        public Location getCorner1() { return corner1; }
        public Location getCorner2() { return corner2; }
        public FarmAreaLayout getFarmArea(FarmType farmType) { return farmAreas.get(farmType); }
    }

    public static class FarmAreaLayout {
        private final int offsetX;
        private final int offsetZ;
        private final int maxInstances;
        private final int rows;
        private final int columns;
        private final int spacing;

        public FarmAreaLayout(int offsetX, int offsetZ, int maxInstances, int rows, int columns) {
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
            this.maxInstances = maxInstances;
            this.rows = rows;
            this.columns = columns;
            this.spacing = 8;
        }

        public int getOffsetX() { return offsetX; }
        public int getOffsetZ() { return offsetZ; }
        public int getMaxInstances() { return maxInstances; }
        public int getRows() { return rows; }
        public int getColumns() { return columns; }
        public int getSpacing() { return spacing; }
    }
}