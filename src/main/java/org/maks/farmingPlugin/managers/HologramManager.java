package org.maks.farmingPlugin.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.farms.FarmInstance;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Naprawiony HologramManager z lepszym czyszczeniem i zapobieganiem duplikacji
 */
public class HologramManager {

    private final FarmingPlugin plugin;
    // Map: Farm Key -> List of ArmorStand UUIDs
    private final Map<String, List<UUID>> hologramUUIDs = new ConcurrentHashMap<>();
    // Cache of loaded ArmorStands for quick access
    private final Map<UUID, ArmorStand> armorStandCache = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final double yOffset;
    private BukkitTask updateTask;
    private BukkitTask cleanupTask;

    // Track which players should see holograms
    private final Set<UUID> playersWithHologramsEnabled = ConcurrentHashMap.newKeySet();

    // Track last cleanup time per location to prevent spam
    private final Map<String, Long> lastCleanupTime = new ConcurrentHashMap<>();

    public HologramManager(FarmingPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("plantations.holograms.enabled", true);
        this.yOffset = plugin.getConfig().getDouble("plantations.holograms.y_offset", 1.5);

        if (enabled) {
            // ZAWSZE czyść przy starcie - to kluczowe!
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                performAggressiveCleanup();
                startUpdateTask();
                startCleanupTask();
            }, 20L); // 1 sekunda po starcie

            plugin.getLogger().info("Hologram system initialized with aggressive cleanup");
        }
    }

    /**
     * Agresywne czyszczenie WSZYSTKICH armor standów które wyglądają jak hologramy
     * Wywołane przy starcie pluginu i przy reload
     */
    private void performAggressiveCleanup() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::performAggressiveCleanup);
            return;
        }

        plugin.getLogger().info("Starting aggressive hologram cleanup...");

        int totalRemoved = 0;

        // Czyść WSZYSTKO co wygląda jak hologram farmingowy
        for (World world : Bukkit.getWorlds()) {
            List<ArmorStand> toRemove = new ArrayList<>();

            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                // Sprawdź czy to nasz hologram po właściwościach
                if (stand.isMarker() && stand.isSmall() && !stand.isVisible() &&
                        stand.isCustomNameVisible() && stand.getCustomName() != null) {

                    String name = stand.getCustomName();
                    // Sprawdź czy nazwa zawiera słowa kluczowe farmingu
                    if (containsFarmKeywords(name)) {
                        toRemove.add(stand);
                    }
                }
            }

            // Usuń wszystkie znalezione
            for (ArmorStand stand : toRemove) {
                stand.remove();
                totalRemoved++;
            }
        }

        // Wyczyść wszystkie mapy
        hologramUUIDs.clear();
        armorStandCache.clear();
        playersWithHologramsEnabled.clear();
        lastCleanupTime.clear();

        plugin.getLogger().info("Aggressive cleanup complete! Removed " + totalRemoved + " old holograms");
    }

    /**
     * Sprawdź czy nazwa zawiera słowa kluczowe farmingu
     */
    private boolean containsFarmKeywords(String name) {
        if (name == null) return false;

        // Lista słów kluczowych które identyfikują hologramy farmingu
        String[] keywords = {
                "Orchards", "Groves", "Caverns", "Patches", "Gardens", "Mangroves", "Sanctuaries",
                "READY", "Next:", "Storage:", "Efficiency:", "Quality", "Speed", "harvest",
                "Lv.", "[ Lv", "] Lv", "⏱", "✦", "⚡", "✔"
        };

        for (String keyword : keywords) {
            if (name.contains(keyword)) {
                return true;
            }
        }

        // Sprawdź też czy nazwa zawiera progress bar
        if (name.contains("[") && name.contains("]") && (name.contains("█") || name.contains("▢"))) {
            return true;
        }

        return false;
    }

    /**
     * Czyść obszar wokół lokacji PRZED utworzeniem nowego hologramu
     */
    private void cleanAreaBeforeCreation(Location location, int radius) {
        if (location == null || location.getWorld() == null) return;

        String locKey = locationToKey(location);
        Long lastClean = lastCleanupTime.get(locKey);

        // Nie czyść zbyt często tej samej lokacji (max raz na 5 sekund)
        if (lastClean != null && System.currentTimeMillis() - lastClean < 5000) {
            return;
        }

        Collection<Entity> nearbyEntities = location.getWorld().getNearbyEntities(
                location, radius, radius, radius,
                entity -> entity instanceof ArmorStand
        );

        int removed = 0;
        for (Entity entity : nearbyEntities) {
            ArmorStand stand = (ArmorStand) entity;

            // Usuń WSZYSTKIE hologramy w tym obszarze które nie są w naszym cache
            if (stand.isMarker() && stand.isSmall() && !stand.isVisible() &&
                    stand.isCustomNameVisible() && stand.getCustomName() != null) {

                // Jeśli nie jest w naszym cache, usuń
                if (!armorStandCache.containsKey(stand.getUniqueId())) {
                    if (containsFarmKeywords(stand.getCustomName())) {
                        stand.remove();
                        removed++;
                    }
                }
            }
        }

        if (removed > 0) {
            plugin.debug("Cleaned " + removed + " orphaned holograms at " + locKey);
        }

        lastCleanupTime.put(locKey, System.currentTimeMillis());
    }

    /**
     * Utwórz lub zaktualizuj hologram dla farmy - z lepszym czyszczeniem
     */
    public void updateHologram(FarmInstance farm, boolean force) {
        if (!enabled || farm == null || farm.getLocation() == null) return;

        // Ensure we're on main thread
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> updateHologram(farm, force));
            return;
        }

        // Check if player wants holograms
        Player player = Bukkit.getPlayer(farm.getOwnerId());
        if (player == null || !player.isOnline()) {
            removeHologram(farm);
            return;
        }

        // Check if player has holograms enabled
        boolean hologramsEnabled = plugin.getDatabaseManager()
                .getPlayerBooleanSetting(farm.getOwnerId(), "hologram_enabled", true);

        if (!hologramsEnabled) {
            removeHologram(farm);
            return;
        }

        // Check if player is in their plantation
        if (!plugin.getPlantationAreaManager().isLocationInPlantation(farm.getOwnerId(), player.getLocation())) {
            removeHologram(farm);
            return;
        }

        String hologramKey = getHologramKey(farm);

        // ZAWSZE usuń stary hologram całkowicie
        removeExistingHologram(hologramKey);

        // ZAWSZE czyść obszar przed utworzeniem nowego
        cleanAreaBeforeCreation(farm.getLocation(), 10);

        // Krótkie opóźnienie przed utworzeniem nowego
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Generate new lines
            List<String> lines = generateHologramLines(farm);
            Location baseLocation = getHologramLocation(farm.getLocation());

            // Create new hologram
            createNewHologram(hologramKey, baseLocation, lines);
        }, 2L);
    }

    /**
     * Helper do konwersji lokacji na klucz
     */
    private String locationToKey(Location loc) {
        return loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    /**
     * Usuń istniejący hologram i upewnij się że jest naprawdę usunięty
     */
    private void removeExistingHologram(String key) {
        List<UUID> uuids = hologramUUIDs.remove(key);
        if (uuids != null) {
            for (UUID uuid : uuids) {
                ArmorStand stand = armorStandCache.remove(uuid);
                if (stand != null) {
                    // Upewnij się że jest usunięty
                    if (stand.isValid()) {
                        stand.remove();
                    }

                    // Double-check przez UUID
                    for (World world : Bukkit.getWorlds()) {
                        Entity entity = world.getEntity(uuid);
                        if (entity instanceof ArmorStand) {
                            entity.remove();
                        }
                    }
                }
            }
        }
    }

    /**
     * Okresowe czyszczenie sierot - bardziej agresywne
     */
    private void cleanupOrphanedArmorStands() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::cleanupOrphanedArmorStands);
            return;
        }

        // Remove invalid cached armor stands
        armorStandCache.entrySet().removeIf(entry ->
                entry.getValue() == null || !entry.getValue().isValid()
        );

        // Find and remove orphaned armor stands
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            List<ArmorStand> toRemove = new ArrayList<>();

            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (stand.isMarker() && stand.isSmall() && !stand.isVisible() &&
                        stand.isCustomNameVisible() && stand.getCustomName() != null) {

                    // Jeśli zawiera słowa kluczowe farmingu i NIE jest w cache - usuń
                    if (containsFarmKeywords(stand.getCustomName()) &&
                            !armorStandCache.containsKey(stand.getUniqueId())) {
                        toRemove.add(stand);
                    }
                }
            }

            for (ArmorStand stand : toRemove) {
                stand.remove();
                removed++;
            }
        }

        if (removed > 0) {
            plugin.debug("Periodic cleanup removed " + removed + " orphaned holograms");
        }
    }

    /**
     * Start periodic cleanup task - częściej
     */
    private void startCleanupTask() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }

        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupOrphanedArmorStands();
            }
        }.runTaskTimer(plugin, 200L, 200L); // Co 10 sekund zamiast 30
    }

    /**
     * Cleanup przy wyłączaniu
     */
    public void cleanup() {
        plugin.getLogger().info("Cleaning up hologram system...");

        // Cancel tasks
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
            updateTask = null;
        }

        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        // Agresywne końcowe czyszczenie
        performAggressiveCleanup();

        plugin.getLogger().info("Hologram cleanup completed");
    }

    // === Reszta metod pozostaje bez zmian (generateHologramLines, createNewHologram, etc.) ===
    // [Skopiuj pozostałe metody z oryginalnego pliku]

    private Location getHologramLocation(Location farmLocation) {
        return farmLocation.clone().add(0.5, yOffset, 0.5).setDirection(farmLocation.getDirection());
    }

    private List<String> generateHologramLines(FarmInstance farm) {
        List<String> lines = new ArrayList<>();

        String title = ChatColor.YELLOW + "" + ChatColor.BOLD + farm.getFarmType().getDisplayName();
        if (farm.getLevel() > 1) {
            title += ChatColor.GRAY + " [Lv." + farm.getLevel() + "]";
        }
        lines.add(title);

        if (farm.isReadyForHarvest()) {
            lines.add(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ READY!");
            if (System.currentTimeMillis() % 2000 < 1000) {
                lines.add(ChatColor.YELLOW + "⚡ Click to harvest!");
            }
        } else {
            long timeLeft = farm.getTimeUntilNextHarvest();
            String timeString = formatTime(timeLeft);
            double progress = farm.getHarvestProgress();
            String progressBar = createProgressBar(progress);

            lines.add(ChatColor.GOLD + "⏱ " + ChatColor.WHITE + timeString);
            lines.add(progressBar);
        }

        int stored = farm.getCurrentStoredItems();
        int maxStorage = farm.getMaxStorage();
        double storagePercent = (double) stored / maxStorage * 100;

        ChatColor storageColor = ChatColor.GREEN;
        if (storagePercent >= 90) {
            storageColor = ChatColor.RED;
        } else if (storagePercent >= 75) {
            storageColor = ChatColor.YELLOW;
        }

        lines.add(ChatColor.GRAY + "Storage: " + storageColor + stored + "/" + maxStorage);

        if (farm.getEfficiency() > 1) {
            lines.add(ChatColor.AQUA + "⚡ " + farm.getEfficiency() + "x Speed");
        }

        if (farm.getQualityUpgradeLevel() > 0) {
            lines.add(ChatColor.LIGHT_PURPLE + "✦ +" +
                    String.format("%.0f%%", (farm.getQualityModifier() - 1) * 100) + " Quality");
        }

        return lines;
    }

    private String createProgressBar(double percent) {
        int filled = (int) (percent / 10);
        StringBuilder bar = new StringBuilder();

        bar.append(ChatColor.DARK_GRAY).append("[");
        for (int i = 0; i < 10; i++) {
            if (i < filled) {
                bar.append(ChatColor.GREEN).append("█");
            } else {
                bar.append(ChatColor.GRAY).append("▢");
            }
        }
        bar.append(ChatColor.DARK_GRAY).append("] ");
        bar.append(ChatColor.WHITE).append(String.format("%.1f%%", percent));

        return bar.toString();
    }

    private void createNewHologram(String key, Location baseLocation, List<String> lines) {
        if (baseLocation == null || baseLocation.getWorld() == null) return;

        List<UUID> standUUIDs = new ArrayList<>();
        double lineSpacing = 0.23;
        double currentOffset = 0;

        for (String line : lines) {
            if (line == null || line.isEmpty()) continue;

            Location lineLocation = baseLocation.clone().add(0, currentOffset, 0);

            try {
                ArmorStand stand = (ArmorStand) baseLocation.getWorld().spawnEntity(
                        lineLocation, EntityType.ARMOR_STAND
                );

                stand.setVisible(false);
                stand.setGravity(false);
                stand.setCanPickupItems(false);
                stand.setCustomNameVisible(true);
                stand.setCustomName(line);
                stand.setMarker(true);
                stand.setSmall(true);
                stand.setInvulnerable(true);
                stand.setRemoveWhenFarAway(false);
                stand.setBasePlate(false);
                stand.setArms(false);

                UUID standUUID = stand.getUniqueId();
                standUUIDs.add(standUUID);
                armorStandCache.put(standUUID, stand);

                currentOffset -= lineSpacing;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to create hologram line: " + e.getMessage());
                for (UUID uuid : standUUIDs) {
                    ArmorStand stand = armorStandCache.remove(uuid);
                    if (stand != null && stand.isValid()) {
                        stand.remove();
                    }
                }
                return;
            }
        }

        hologramUUIDs.put(key, standUUIDs);
        plugin.debug("Created hologram with " + standUUIDs.size() + " lines for " + key);
    }

    private void startUpdateTask() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }

        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllHolograms();
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    public void updateAllHolograms() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::updateAllHolograms);
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUuid = player.getUniqueId();

            if (plugin.getPlantationAreaManager().isLocationInPlantation(playerUuid, player.getLocation())) {
                List<FarmInstance> farms = plugin.getPlantationManager().getPlayerFarms(playerUuid);

                for (FarmInstance farm : farms) {
                    if (farm.getLocation() != null) {
                        updateHologram(farm, false);
                    }
                }

                playersWithHologramsEnabled.add(playerUuid);
            } else {
                if (playersWithHologramsEnabled.contains(playerUuid)) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Player delayedPlayer = Bukkit.getPlayer(playerUuid);
                        if (delayedPlayer == null ||
                                !plugin.getPlantationAreaManager().isLocationInPlantation(playerUuid, delayedPlayer.getLocation())) {

                            if (playersWithHologramsEnabled.remove(playerUuid)) {
                                removePlayerHolograms(playerUuid);
                            }
                        }
                    }, 60L);
                }
            }
        }
    }

    public void removePlayerHolograms(UUID playerUuid) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> removePlayerHolograms(playerUuid));
            return;
        }

        String prefix = playerUuid.toString() + "_";
        List<String> keysToRemove = new ArrayList<>();

        for (String key : hologramUUIDs.keySet()) {
            if (key.startsWith(prefix)) {
                keysToRemove.add(key);
            }
        }

        for (String key : keysToRemove) {
            removeExistingHologram(key);
        }

        List<FarmInstance> farms = plugin.getPlantationManager().getPlayerFarms(playerUuid);
        for (FarmInstance farm : farms) {
            if (farm.getLocation() != null) {
                cleanAreaBeforeCreation(farm.getLocation(), 5);
            }
        }

        plugin.debug("Removed " + keysToRemove.size() + " holograms for player " + playerUuid);
    }

    public void removeHologram(FarmInstance farm) {
        if (farm == null) return;
        removeHologram(getHologramKey(farm));
    }

    public void removeHologram(String key) {
        removeExistingHologram(key);
    }

    private String getHologramKey(FarmInstance farm) {
        return farm.getOwnerId().toString() + "_" +
                farm.getFarmType().getId() + "_" +
                farm.getInstanceId();
    }

    public boolean hasHologram(FarmInstance farm) {
        return hologramUUIDs.containsKey(getHologramKey(farm));
    }

    public void updateHologram(FarmInstance farm) {
        updateHologram(farm, false);
    }

    public void forceRefreshAll() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::forceRefreshAll);
            return;
        }

        plugin.getLogger().info("Force refreshing all holograms...");

        for (String key : new ArrayList<>(hologramUUIDs.keySet())) {
            removeExistingHologram(key);
        }

        cleanupOrphanedArmorStands();
        updateAllHolograms();

        plugin.getLogger().info("Force refresh completed");
    }

    public void showFloatingText(Location location, String text, long durationMillis) {
        if (!enabled || location == null) return;

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> showFloatingText(location, text, durationMillis));
            return;
        }

        try {
            Location spawnLoc = location.clone().add(0.5, yOffset + 0.5, 0.5);
            ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(
                    spawnLoc, EntityType.ARMOR_STAND
            );

            stand.setVisible(false);
            stand.setGravity(false);
            stand.setCanPickupItems(false);
            stand.setCustomNameVisible(true);
            stand.setCustomName(ChatColor.translateAlternateColorCodes('&', text));
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setInvulnerable(true);
            stand.setRemoveWhenFarAway(false);

            new BukkitRunnable() {
                int ticks = 0;
                final int maxTicks = (int) (durationMillis / 50);

                @Override
                public void run() {
                    if (!stand.isValid() || ticks >= maxTicks) {
                        if (stand.isValid()) {
                            stand.remove();
                        }
                        cancel();
                        return;
                    }

                    double progress = (double) ticks / maxTicks;
                    double yMove = 0.03 * (1 - progress);
                    Location newLoc = stand.getLocation().add(0, yMove, 0);
                    stand.teleport(newLoc);

                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create floating text: " + e.getMessage());
        }
    }

    public void showHarvestAnimation(Location location, int itemCount) {
        if (!enabled) return;

        String text = "&a&l+" + itemCount + " Fruits!";
        showFloatingText(location, text, 2500);

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> showHarvestParticles(location));
        } else {
            showHarvestParticles(location);
        }
    }

    private void showHarvestParticles(Location location) {
        try {
            location.getWorld().spawnParticle(
                    org.bukkit.Particle.VILLAGER_HAPPY,
                    location.clone().add(0.5, 1, 0.5),
                    30, 0.5, 0.5, 0.5, 0.1
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn harvest particles: " + e.getMessage());
        }
    }

    public void showLevelUpAnimation(Location location, int newLevel) {
        if (!enabled) return;

        String text = "&6&l⬆ LEVEL " + newLevel + "!";
        showFloatingText(location, text, 3500);

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> showLevelUpParticles(location));
        } else {
            showLevelUpParticles(location);
        }
    }

    private void showLevelUpParticles(Location location) {
        try {
            location.getWorld().spawnParticle(
                    org.bukkit.Particle.TOTEM,
                    location.clone().add(0.5, 1, 0.5),
                    50, 0.5, 1, 0.5, 0.1
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn level up particles: " + e.getMessage());
        }
    }

    private String formatTime(long millis) {
        if (millis <= 0) return "Ready!";

        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}