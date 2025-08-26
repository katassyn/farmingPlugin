package org.maks.farmingPlugin.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

public class PlantationAreaManager {

    private final FarmingPlugin plugin;
    private final Map<UUID, PlantationArea> areas = new ConcurrentHashMap<>();

    private final World world;
    private final int originX, originY, originZ;
    private final int plotWidth, plotDepth, spacing, gridRows, gridCols;
    private final Material fenceMaterial, gateMaterial;

    private NamespacedKey PDC_LOCKED = new NamespacedKey(FarmingPlugin.getInstance(), "locked_farm");
    private NamespacedKey PDC_FARM_TYPE = new NamespacedKey(FarmingPlugin.getInstance(), "farm_type");
    private NamespacedKey PDC_INSTANCE_ID = new NamespacedKey(FarmingPlugin.getInstance(), "instance_id");

    // Farm layout configuration - positions for different farm types and instances
    private static final Map<FarmType, List<int[]>> FARM_LAYOUT = new HashMap<>();

    static {
        // Berry Orchards - 6 instances (most common, front area)
        FARM_LAYOUT.put(FarmType.BERRY_ORCHARDS, Arrays.asList(
            new int[]{2, 2}, new int[]{4, 2}, new int[]{6, 2},
            new int[]{2, 4}, new int[]{4, 4}, new int[]{6, 4}
        ));

        // Melon Groves - 6 instances (second row)
        FARM_LAYOUT.put(FarmType.MELON_GROVES, Arrays.asList(
            new int[]{8, 2}, new int[]{10, 2}, new int[]{8, 4},
            new int[]{10, 4}, new int[]{8, 6}, new int[]{10, 6}
        ));

        // Fungal Caverns - 6 instances (third area)
        FARM_LAYOUT.put(FarmType.FUNGAL_CAVERNS, Arrays.asList(
            new int[]{2, 6}, new int[]{4, 6}, new int[]{6, 6},
            new int[]{2, 8}, new int[]{4, 8}, new int[]{6, 8}
        ));

        // Pumpkin Patches - 6 instances
        FARM_LAYOUT.put(FarmType.PUMPKIN_PATCHES, Arrays.asList(
            new int[]{8, 8}, new int[]{10, 8}, new int[]{8, 10},
            new int[]{10, 10}, new int[]{7, 9}, new int[]{9, 9}
        ));

        // Mystic Gardens - 3 instances (rare, special area)
        FARM_LAYOUT.put(FarmType.MYSTIC_GARDENS, Arrays.asList(
            new int[]{3, 10}, new int[]{5, 10}, new int[]{4, 11}
        ));

        // Ancient Mangroves - 3 instances (very rare)
        FARM_LAYOUT.put(FarmType.ANCIENT_MANGROVES, Arrays.asList(
            new int[]{2, 11}, new int[]{6, 11}, new int[]{4, 12}
        ));

        // Desert Sanctuaries - 1 instance (legendary, center-back)
        FARM_LAYOUT.put(FarmType.DESERT_SANCTUARIES, Arrays.asList(
            new int[]{6, 9}
        ));
    }

    public PlantationAreaManager(FarmingPlugin plugin) {
        this.plugin = plugin;

        ConfigurationSection base = plugin.getConfig().getConfigurationSection("plantations.base");
        this.world = Bukkit.getWorld(plugin.getConfig().getString("plantations.world", "world"));

        ConfigurationSection originSec = base.getConfigurationSection("origin");
        this.originX = originSec.getInt("x");
        this.originY = originSec.getInt("y");
        this.originZ = originSec.getInt("z");

        ConfigurationSection plotSec = base.getConfigurationSection("plot");
        this.plotWidth = plotSec.getInt("width", 16);
        this.plotDepth = plotSec.getInt("depth", 16);
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
            Map<FarmType, Map<Integer, FarmAnchor>> anchors = loadFarmAnchors(uuid, origin);
            areas.put(uuid, new PlantationArea(uuid, origin, anchors, plotWidth, plotDepth));
        }
    }

    private Map<FarmType, Map<Integer, FarmAnchor>> loadFarmAnchors(UUID uuid, Location origin) {
        Map<FarmType, Map<Integer, FarmAnchor>> anchors = new EnumMap<>(FarmType.class);

        try {
            String sql = "SELECT farm_type, instance_id, anchor_x, anchor_y, anchor_z FROM farming_farm_anchors WHERE uuid = ?";
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql);
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                FarmType type = FarmType.fromId(rs.getString("farm_type"));
                int instanceId = rs.getInt("instance_id");

                if (type != null) {
                    Location anchorLoc = new Location(
                        world,
                        rs.getInt("anchor_x"),
                        rs.getInt("anchor_y"),
                        rs.getInt("anchor_z")
                    );

                    anchors.computeIfAbsent(type, k -> new HashMap<>())
                           .put(instanceId, new FarmAnchor(anchorLoc, type, instanceId));
                }
            }

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load farm anchors: " + e.getMessage());
        }

        return anchors;
    }

    public PlantationArea getOrCreateArea(Player player) {
        PlantationArea area = areas.computeIfAbsent(player.getUniqueId(), uuid -> {
            DatabaseManager db = plugin.getDatabaseManager();
            Location origin = db.loadPlayerPlot(uuid).orElseGet(() -> allocateNewPlot(uuid));
            buildPlotStructure(origin);
            Map<FarmType, Map<Integer, FarmAnchor>> anchors = new EnumMap<>(FarmType.class);
            return new PlantationArea(uuid, origin, anchors, plotWidth, plotDepth);
        });
        ensureAllAnchors(player.getUniqueId());
        return area;
    }

    public void ensureAllAnchors(UUID owner) {
        for (FarmType type : FarmType.values()) {
            for (int i = 1; i <= type.getMaxInstances(); i++) {
                getOrCreateFarmAnchor(owner, type, i);
            }
        }
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

        FarmAnchor anchor = area.getFarmAnchor(type, instanceId);
        if (anchor != null) {
            if (world != null) {
                world.getBlockAt(anchor.location).setType(type.getBlockType());
            }
            return anchor.location;
        }

        // Create new anchor
        List<int[]> positions = FARM_LAYOUT.get(type);
        if (positions == null || instanceId > positions.size() || instanceId < 1) {
            return null;
        }

        int[] pos = positions.get(instanceId - 1);
        Location anchorLoc = area.origin.clone().add(pos[0], 0, pos[1]);

        // Place the farm block
        if (world != null) {
            Block block = world.getBlockAt(anchorLoc);
            block.setType(type.getBlockType());
        }

        // Save anchor to area and database
        FarmAnchor newAnchor = new FarmAnchor(anchorLoc, type, instanceId);
        area.addFarmAnchor(type, instanceId, newAnchor);
        saveFarmAnchor(owner, type, instanceId, anchorLoc);

        return anchorLoc;
    }

    private void saveFarmAnchor(UUID uuid, FarmType type, int instanceId, Location loc) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String sql = "INSERT INTO farming_farm_anchors (uuid, farm_type, instance_id, anchor_x, anchor_y, anchor_z) " +
                           "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                           "anchor_x = VALUES(anchor_x), anchor_y = VALUES(anchor_y), anchor_z = VALUES(anchor_z)";
                PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql);
                stmt.setString(1, uuid.toString());
                stmt.setString(2, type.getId());
                stmt.setInt(3, instanceId);
                stmt.setInt(4, loc.getBlockX());
                stmt.setInt(5, loc.getBlockY());
                stmt.setInt(6, loc.getBlockZ());
                stmt.executeUpdate();
                stmt.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save farm anchor: " + e.getMessage());
            }
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
                world.getBlockAt(x, y - 2, z).setType(Material.DIRT);
                world.getBlockAt(x, y - 3, z).setType(Material.STONE);

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
        world.getBlockAt(gateX, y, z1).setType(gateMaterial);

        // Add decorative path
        for (int z = z1 + 1; z < z1 + 4; z++) {
            world.getBlockAt(gateX, y - 1, z).setType(Material.DIRT_PATH);
        }

        // Add lanterns
        world.getBlockAt(gateX - 1, y + 1, z1).setType(Material.LANTERN);
        world.getBlockAt(gateX + 1, y + 1, z1).setType(Material.LANTERN);
        world.getBlockAt(x1, y + 1, z1).setType(Material.LANTERN);
        world.getBlockAt(x2, y + 1, z1).setType(Material.LANTERN);
        world.getBlockAt(x1, y + 1, z2).setType(Material.LANTERN);
        world.getBlockAt(x2, y + 1, z2).setType(Material.LANTERN);
    }

    public boolean isLocationInPlantation(UUID owner, Location loc) {
        PlantationArea area = areas.get(owner);
        return area != null && area.contains(loc);
    }

    public int getFarmInstanceFromLocation(UUID owner, FarmType type, Location loc) {
        PlantationArea area = areas.get(owner);
        if (area == null) return -1;

        // Check all possible positions for this farm type
        List<int[]> positions = FARM_LAYOUT.get(type);
        if (positions != null) {
            for (int i = 0; i < positions.size(); i++) {
                int[] pos = positions.get(i);
                Location farmLoc = area.origin.clone().add(pos[0], 0, pos[1]);

                if (farmLoc.getBlockX() == loc.getBlockX() &&
                    farmLoc.getBlockY() == loc.getBlockY() &&
                    farmLoc.getBlockZ() == loc.getBlockZ()) {
                    return i + 1; // Instance IDs start at 1
                }
            }
        }

        return -1;
    }

    public int getMaxInstances(FarmType type) {
        List<int[]> positions = FARM_LAYOUT.get(type);
        return positions != null ? positions.size() : 0;
    }

    public boolean isInstanceAvailable(UUID owner, FarmType type, int instanceId) {
        if (instanceId < 1 || instanceId > getMaxInstances(type)) {
            return false;
        }

        PlantationArea area = areas.get(owner);
        if (area == null) return true;

        return area.getFarmAnchor(type, instanceId) == null;
    }

    public Map<FarmType, Map<Integer, Location>> getAllFarmAnchors(UUID owner) {
        PlantationArea area = areas.get(owner);
        if (area == null) return new EnumMap<>(FarmType.class);

        Map<FarmType, Map<Integer, Location>> result = new EnumMap<>(FarmType.class);
        for (Map.Entry<FarmType, Map<Integer, FarmAnchor>> typeEntry : area.anchors.entrySet()) {
            Map<Integer, Location> locations = new HashMap<>();
            for (Map.Entry<Integer, FarmAnchor> anchorEntry : typeEntry.getValue().entrySet()) {
                locations.put(anchorEntry.getKey(), anchorEntry.getValue().location);
            }
            result.put(typeEntry.getKey(), locations);
        }
        return result;
    }

    /** Zbuduj layout i postaw "LOCKED" dla nieodblokowanych */
    public void regeneratePlayerArea(Player player) {
        World w = Bukkit.getWorld(plugin.getConfig().getString("plantation.world", "world"));
        int baseX = plugin.getConfig().getInt("plantation.spawn.x");
        int baseY = plugin.getConfig().getInt("plantation.spawn.y");
        int baseZ = plugin.getConfig().getInt("plantation.spawn.z");

        if (w == null) {
            plugin.getLogger().warning("World not found for plantation.world!");
            return;
        }

        buildPath(w, baseX, baseY, 998, 989);

        Map<FarmType, List<Location>> anchors = computeAnchors(w, baseY);

        UUID uid = player.getUniqueId();
        List<FarmInstance> owned = plugin.getPlantationManager().getPlayerFarms(uid);
        Set<String> ownedKeys = owned.stream()
            .map(fi -> key(fi.getFarmType(), fi.getInstanceId()))
            .collect(Collectors.toSet());

        for (Map.Entry<FarmType, List<Location>> e : anchors.entrySet()) {
            FarmType type = e.getKey();
            List<Location> list = e.getValue();
            for (int i = 0; i < list.size(); i++) {
                Location loc = list.get(i);
                String k = key(type, i + 1);
                if (ownedKeys.contains(k)) {
                    placeFarmBlock(loc, type);
                } else {
                    placeLockedSign(loc, type, i + 1);
                }
            }
        }
    }

    private String key(FarmType t, int id) {
        return t.getId() + "#" + id;
    }

    private void buildPath(World w, int x, int y, int zFrom, int zTo) {
        Material pathMat = Material.matchMaterial(
            plugin.getConfig().getString("blocks.path", "DIRT_PATH")
        );
        if (pathMat == null) pathMat = Material.DIRT_PATH;

        int dz = zFrom <= zTo ? 1 : -1;
        for (int z = zFrom; z != zTo + dz; z += dz) {
            Block b = w.getBlockAt(x, y, z);
            b.setType(pathMat, false);
        }
    }

    /** Wylicza dokładne pozycje wg Twoich koordów (Y z configu) */
    private Map<FarmType, List<Location>> computeAnchors(World w, int y) {
        Map<FarmType, List<Location>> map = new LinkedHashMap<>();

        Function<int[], Location> L = arr -> new Location(w, arr[0], y, arr[1]);
        BiFunction<int[], int[], List<Location>> rect6 = (a, b) -> {
            int x1 = Math.min(a[0], b[0]), x2 = Math.max(a[0], b[0]);
            int z1 = Math.min(a[1], b[1]), z2 = Math.max(a[1], b[1]);
            int[] xs = new int[]{x1, x1 + 2, x1 + 4};
            int[] zs = (z2 - z1 == 2) ? new int[]{z2, z1} : new int[]{z1, z2};
            return Arrays.asList(
                L.apply(new int[]{xs[0], zs[0]}),
                L.apply(new int[]{xs[1], zs[0]}),
                L.apply(new int[]{xs[2], zs[0]}),
                L.apply(new int[]{xs[0], zs[1]}),
                L.apply(new int[]{xs[1], zs[1]}),
                L.apply(new int[]{xs[2], zs[1]})
            );
        };
        BiFunction<int[], int[], List<Location>> line3 = (a, b) -> {
            int x1 = Math.min(a[0], b[0]), x2 = Math.max(a[0], b[0]);
            int z = a[1];
            return Arrays.asList(
                L.apply(new int[]{x1, z}),
                L.apply(new int[]{x1 + 2, z}),
                L.apply(new int[]{x2, z})
            );
        };

        map.put(FarmType.BERRY_ORCHARDS, rect6.apply(new int[]{1010, 997}, new int[]{1014, 995}));
        map.put(FarmType.MELON_GROVES, rect6.apply(new int[]{1002, 995}, new int[]{1006, 997}));
        map.put(FarmType.FUNGAL_CAVERNS, rect6.apply(new int[]{1010, 993}, new int[]{1014, 991}));
        map.put(FarmType.PUMPKIN_PATCHES, rect6.apply(new int[]{1002, 991}, new int[]{1006, 993}));
        map.put(FarmType.MYSTIC_GARDENS, line3.apply(new int[]{1010, 989}, new int[]{1014, 989}));
        map.put(FarmType.ANCIENT_MANGROVES, line3.apply(new int[]{1002, 989}, new int[]{1006, 989}));
        map.put(FarmType.DESERT_SANCTUARIES, Arrays.asList(L.apply(new int[]{1008, 987})));

        return map;
    }

    public void placeFarmBlock(Location loc, FarmType type) {
        Material ground = Material.matchMaterial(plugin.getConfig().getString("blocks.ground", "GRASS_BLOCK"));
        if (ground == null) ground = Material.GRASS_BLOCK;
        loc.getBlock().getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ()).setType(ground, false);

        loc.getBlock().setType(type.getBlockType(), false);
    }

    private void placeLockedSign(Location loc, FarmType type, int instanceId) {
        loc.getBlock().setType(Material.AIR, false);
        loc.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ()).setType(Material.GRASS_BLOCK, false);

        loc.getBlock().setType(Material.OAK_SIGN, false);
        BlockState state = loc.getBlock().getState();
        if (state instanceof Sign sign) {
            sign.setLine(0, ChatColor.RED + "LOCKED");
            sign.setLine(1, ChatColor.YELLOW + type.getDisplayName());
            sign.setLine(2, ChatColor.GRAY + "Prawy klik aby");
            sign.setLine(3, ChatColor.GRAY + "odblokować");
            sign.getPersistentDataContainer().set(PDC_LOCKED, PersistentDataType.INTEGER, 1);
            sign.getPersistentDataContainer().set(PDC_FARM_TYPE, PersistentDataType.STRING, type.getId());
            sign.getPersistentDataContainer().set(PDC_INSTANCE_ID, PersistentDataType.INTEGER, instanceId);
            sign.update(true, false);
        }
    }

    public static class PlantationArea {
        private final UUID owner;
        private final Location origin;
        private final Map<FarmType, Map<Integer, FarmAnchor>> anchors;
        private final int width;
        private final int depth;

        PlantationArea(UUID owner, Location origin, Map<FarmType, Map<Integer, FarmAnchor>> anchors,
                      int width, int depth) {
            this.owner = owner;
            this.origin = origin;
            this.anchors = anchors != null ? anchors : new EnumMap<>(FarmType.class);
            this.width = width;
            this.depth = depth;
        }

        public Location getCenter() {
            return origin.clone().add(width / 2.0, 1, depth / 2.0);
        }

        public Location getSpawnPoint() {
            return origin.clone().add(width / 2.0, 1, 2);
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

        FarmAnchor getFarmAnchor(FarmType type, int instanceId) {
            Map<Integer, FarmAnchor> typeAnchors = anchors.get(type);
            return typeAnchors != null ? typeAnchors.get(instanceId) : null;
        }

        void addFarmAnchor(FarmType type, int instanceId, FarmAnchor anchor) {
            anchors.computeIfAbsent(type, k -> new HashMap<>()).put(instanceId, anchor);
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

