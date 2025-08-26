package org.maks.farmingPlugin.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.database.DatabaseManager;
import org.maks.farmingPlugin.farms.FarmType;
import org.maks.farmingPlugin.farms.FarmInstance;
import org.bukkit.ChatColor;
import org.bukkit.persistence.PersistentDataType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

public class PlantationAreaManager {

    private final FarmingPlugin plugin;
    private final Map<UUID, PlantationArea> areas = new ConcurrentHashMap<>();
    private final Map<UUID, Map<FarmType, Map<Integer, FarmAnchor>>> playerAnchors = new ConcurrentHashMap<>();

    private final World world;
    private final int originX, originY, originZ;
    private final int plotWidth = 17;
    private final int plotDepth = 15;
    private final int spacing;
    private final int gridRows, gridCols;
    private final Material fenceMaterial, gateMaterial;

    private NamespacedKey PDC_LOCKED;
    private NamespacedKey PDC_FARM_TYPE;
    private NamespacedKey PDC_INSTANCE_ID;

    // Relative positions for farms within each player's plot
    // These are OFFSETS from the player's plot origin
    private static final Map<FarmType, List<int[]>> FARM_LAYOUT = new HashMap<>();

    static {
        // Berry Orchards - 6 instances (right side)
        // Relative positions in a 2x3 grid
        FARM_LAYOUT.put(FarmType.BERRY_ORCHARDS, Arrays.asList(
            new int[]{10, 13}, new int[]{12, 13}, new int[]{14, 13}, // Top row (near spawn)
            new int[]{10, 11}, new int[]{12, 11}, new int[]{14, 11}  // Bottom row
        ));

        // Melon Groves - 6 instances (left side)
        FARM_LAYOUT.put(FarmType.MELON_GROVES, Arrays.asList(
            new int[]{2, 13}, new int[]{4, 13}, new int[]{6, 13},    // Top row
            new int[]{2, 11}, new int[]{4, 11}, new int[]{6, 11}     // Bottom row
        ));

        // Fungal Caverns - 6 instances (right side, middle)
        FARM_LAYOUT.put(FarmType.FUNGAL_CAVERNS, Arrays.asList(
            new int[]{10, 9}, new int[]{12, 9}, new int[]{14, 9},    // Top row
            new int[]{10, 7}, new int[]{12, 7}, new int[]{14, 7}     // Bottom row
        ));

        // Pumpkin Patches - 6 instances (left side, middle)
        FARM_LAYOUT.put(FarmType.PUMPKIN_PATCHES, Arrays.asList(
            new int[]{2, 9}, new int[]{4, 9}, new int[]{6, 9},       // Top row
            new int[]{2, 7}, new int[]{4, 7}, new int[]{6, 7}        // Bottom row
        ));

        // Mystic Gardens - 3 instances (right side, bottom)
        FARM_LAYOUT.put(FarmType.MYSTIC_GARDENS, Arrays.asList(
            new int[]{10, 5}, new int[]{12, 5}, new int[]{14, 5}
        ));

        // Ancient Mangroves - 3 instances (left side, bottom)
        FARM_LAYOUT.put(FarmType.ANCIENT_MANGROVES, Arrays.asList(
            new int[]{2, 5}, new int[]{4, 5}, new int[]{6, 5}
        ));

        // Desert Sanctuaries - 1 instance (center, very bottom)
        FARM_LAYOUT.put(FarmType.DESERT_SANCTUARIES, Arrays.asList(
            new int[]{8, 3}
        ));
    }

    public PlantationAreaManager(FarmingPlugin plugin) {
        this.plugin = plugin;

        // Initialize PDC keys
        this.PDC_LOCKED = new NamespacedKey(plugin, "locked_farm");
        this.PDC_FARM_TYPE = new NamespacedKey(plugin, "farm_type");
        this.PDC_INSTANCE_ID = new NamespacedKey(plugin, "instance_id");

        ConfigurationSection base = plugin.getConfig().getConfigurationSection("plantations.base");
        this.world = Bukkit.getWorld(plugin.getConfig().getString("plantations.world", "world"));

        ConfigurationSection originSec = base.getConfigurationSection("origin");
        this.originX = originSec.getInt("x");
        this.originY = originSec.getInt("y");
        this.originZ = originSec.getInt("z");

        ConfigurationSection plotSec = base.getConfigurationSection("plot");
        this.fenceMaterial = Material.valueOf(plotSec.getString("fence_material", "OAK_FENCE"));
        this.gateMaterial = Material.valueOf(plotSec.getString("gate_material", "OAK_FENCE_GATE"));

        this.spacing = base.getInt("spacing");

        ConfigurationSection gridSec = base.getConfigurationSection("grid");
        this.gridRows = gridSec.getInt("rows");
        this.gridCols = gridSec.getInt("cols");

        loadAllPlayerAreas();
    }

    private void loadAllPlayerAreas() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String sql = "SELECT DISTINCT uuid FROM farming_player_plots";
                PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    loadPlayerArea(uuid);
                }

                rs.close();
                stmt.close();

                plugin.getLogger().info("Loaded " + areas.size() + " player plantation areas");
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load player areas: " + e.getMessage());
            }
        });
    }

    private void loadPlayerArea(UUID uuid) {
        Optional<Location> savedLocation = plugin.getDatabaseManager().loadPlayerPlot(uuid);
        if (savedLocation.isPresent()) {
            Location origin = savedLocation.get();
            areas.put(uuid, new PlantationArea(uuid, origin, plotWidth, plotDepth));
        }
    }

    public PlantationArea getOrCreateArea(Player player) {
        PlantationArea area = areas.computeIfAbsent(player.getUniqueId(), uuid -> {
            DatabaseManager db = plugin.getDatabaseManager();
            Location origin = db.loadPlayerPlot(uuid).orElseGet(() -> allocateNewPlot(uuid));
            buildPlotStructure(origin);
            return new PlantationArea(uuid, origin, plotWidth, plotDepth);
        });
        return area;
    }

    private Location allocateNewPlot(UUID uuid) {
        int index = areas.size();
        int row = index / gridCols;
        int col = index % gridCols;
        int x = originX + col * (plotWidth + spacing);
        int z = originZ + row * (plotDepth + spacing);
        Location loc = new Location(world, x, originY, z);
        
        plugin.getLogger().info("Allocating new plot for " + uuid + " at coordinates: " + x + ", " + originY + ", " + z);
        plugin.getDatabaseManager().savePlayerPlot(uuid, world.getName(), x, originY, z);
        
        return loc;
    }

    private void buildPlotStructure(Location origin) {
        if (world == null) return;

        int x1 = origin.getBlockX();
        int z1 = origin.getBlockZ();
        int x2 = x1 + plotWidth - 1;
        int z2 = z1 + plotDepth - 1;
        int y = origin.getBlockY();

        // Create the floor
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                world.getBlockAt(x, y - 1, z).setType(Material.GRASS_BLOCK);

                // Clear above ground
                world.getBlockAt(x, y, z).setType(Material.AIR);
                world.getBlockAt(x, y + 1, z).setType(Material.AIR);
                world.getBlockAt(x, y + 2, z).setType(Material.AIR);
            }
        }

        // Build fence perimeter
        for (int x = x1; x <= x2; x++) {
            world.getBlockAt(x, y, z1).setType(fenceMaterial);
            world.getBlockAt(x, y, z2).setType(fenceMaterial);
        }
        for (int z = z1 + 1; z < z2; z++) {
            world.getBlockAt(x1, y, z).setType(fenceMaterial);
            world.getBlockAt(x2, y, z).setType(fenceMaterial);
        }

        // Add gate at front center
        int gateX = x1 + plotWidth / 2;
        world.getBlockAt(gateX, y, z2).setType(gateMaterial);

        // Build central path from spawn to back
        Material pathMat = Material.matchMaterial(
            plugin.getConfig().getString("blocks.path", "DIRT_PATH")
        );
        if (pathMat == null) pathMat = Material.DIRT_PATH;

        for (int z = z1 + 1; z < z2; z++) {
            world.getBlockAt(x1 + 8, y, z).setType(pathMat);
        }

        // Add some decorative elements
        world.getBlockAt(gateX - 1, y + 1, z2).setType(Material.LANTERN);
        world.getBlockAt(gateX + 1, y + 1, z2).setType(Material.LANTERN);
    }

    public Location getOrCreateFarmAnchor(UUID owner, FarmType type, int instanceId) {
        PlantationArea area = areas.get(owner);
        if (area == null) {
            Player player = Bukkit.getPlayer(owner);
            if (player != null) {
                area = getOrCreateArea(player);
            } else {
                return null;
            }
        }

        // Get the relative position for this farm type and instance
        List<int[]> positions = FARM_LAYOUT.get(type);
        if (positions == null || instanceId < 1 || instanceId > positions.size()) {
            return null;
        }

        int[] relativePos = positions.get(instanceId - 1);
        Location anchorLoc = area.origin.clone().add(relativePos[0], 0, relativePos[1]);

        return anchorLoc;
    }

    public boolean isLocationInPlantation(UUID owner, Location loc) {
        PlantationArea area = areas.get(owner);
        return area != null && area.contains(loc);
    }

    public int getFarmInstanceFromLocation(UUID owner, FarmType type, Location loc) {
        PlantationArea area = areas.get(owner);
        if (area == null) return -1;

        List<int[]> positions = FARM_LAYOUT.get(type);
        if (positions != null) {
            for (int i = 0; i < positions.size(); i++) {
                int[] pos = positions.get(i);
                Location farmLoc = area.origin.clone().add(pos[0], 0, pos[1]);

                if (farmLoc.getBlockX() == loc.getBlockX() &&
                    farmLoc.getBlockY() == loc.getBlockY() &&
                    farmLoc.getBlockZ() == loc.getBlockZ()) {
                    return i + 1;
                }
            }
        }

        return -1;
    }

    public int getMaxInstances(FarmType type) {
        List<int[]> positions = FARM_LAYOUT.get(type);
        return positions != null ? positions.size() : 0;
    }

    /** Regenerate player's plantation area with proper layout */
    public void regeneratePlayerArea(Player player) {
        if (world == null) {
            plugin.getLogger().warning("World not found for regeneration!");
            return;
        }

        PlantationArea area = getOrCreateArea(player);
        if (area == null) return;

        Location origin = area.origin;
        UUID uid = player.getUniqueId();
        
        // Clear and rebuild the basic structure
        buildPlotStructure(origin);

        // Get player's owned farms
        List<FarmInstance> owned = plugin.getPlantationManager().getPlayerFarms(uid);
        Set<String> ownedKeys = owned.stream()
            .map(fi -> key(fi.getFarmType(), fi.getInstanceId()))
            .collect(Collectors.toSet());

        // Process each farm type
        for (Map.Entry<FarmType, List<int[]>> entry : FARM_LAYOUT.entrySet()) {
            FarmType type = entry.getKey();
            List<int[]> positions = entry.getValue();
            
            for (int i = 0; i < positions.size(); i++) {
                int[] relativePos = positions.get(i);
                Location farmLoc = origin.clone().add(relativePos[0], 0, relativePos[1]);
                String k = key(type, i + 1);
                
                // Place grass underneath
                world.getBlockAt(farmLoc.getBlockX(), farmLoc.getBlockY() - 1, farmLoc.getBlockZ())
                    .setType(Material.GRASS_BLOCK);
                
                if (ownedKeys.contains(k)) {
                    // Player owns this farm - place the farm block
                    placeFarmBlock(farmLoc, type);
                    
                    // Update hologram
                    FarmInstance farm = plugin.getPlantationManager()
                        .getFarmInstance(uid, type, i + 1);
                    if (farm != null) {
                        farm.setLocation(farmLoc);
                        if (plugin.getHologramManager() != null) {
                            plugin.getHologramManager().updateHologram(farm);
                        }
                    }
                } else if (type == FarmType.BERRY_ORCHARDS || 
                          plugin.getDatabaseManager().isFarmUnlocked(uid, type.getId())) {
                    // Farm type unlocked but instance not created - show farm block dim
                    placeFarmBlock(farmLoc, type);
                } else {
                    // Farm type is locked - place locked sign
                    placeLockedSign(farmLoc, type, i + 1);
                }
            }
        }
    }

    private String key(FarmType t, int id) {
        return t.getId() + "#" + id;
    }

    public void placeFarmBlock(Location loc, FarmType type) {
        Block farmBlock = world.getBlockAt(loc);
        farmBlock.setType(type.getBlockType(), false);
    }

    private void placeLockedSign(Location loc, FarmType type, int instanceId) {
        Block block = world.getBlockAt(loc);
        block.setType(Material.OAK_SIGN, false);
        
        BlockState state = block.getState();
        if (state instanceof Sign sign) {
            sign.setLine(0, ChatColor.RED + "LOCKED");
            sign.setLine(1, ChatColor.YELLOW + type.getDisplayName());
            sign.setLine(2, ChatColor.GRAY + "Right-click to");
            sign.setLine(3, ChatColor.GRAY + "unlock");
            
            sign.getPersistentDataContainer().set(PDC_LOCKED, PersistentDataType.INTEGER, 1);
            sign.getPersistentDataContainer().set(PDC_FARM_TYPE, PersistentDataType.STRING, type.getId());
            sign.getPersistentDataContainer().set(PDC_INSTANCE_ID, PersistentDataType.INTEGER, instanceId);
            
            sign.update(true, false);
        }
    }

    public static class PlantationArea {
        private final UUID owner;
        private final Location origin;
        private final int width;
        private final int depth;

        PlantationArea(UUID owner, Location origin, int width, int depth) {
            this.owner = owner;
            this.origin = origin;
            this.width = width;
            this.depth = depth;
        }

        public Location getCenter() {
            return origin.clone().add(width / 2.0, 1, depth / 2.0);
        }

        public Location getSpawnPoint() {
            // Spawn point is at the center-back of the plot
            return origin.clone().add(8, 1, depth - 1);
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

        public UUID getOwner() {
            return owner;
        }

        public Location getOrigin() {
            return origin;
        }
    }

    private static class FarmAnchor {
        final Location location;
        final FarmType type;
        final int instanceId;

        FarmAnchor(Location location, FarmType type, int instanceId) {
            this.location = location;
            this.type = type;
            this.instanceId = instanceId;
        }
    }
}
