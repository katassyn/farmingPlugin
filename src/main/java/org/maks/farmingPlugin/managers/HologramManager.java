package org.maks.farmingPlugin.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.farms.FarmInstance;
import org.maks.farmingPlugin.farms.FarmType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages holograms (armor stands) for farm displays
 */
public class HologramManager {
    
    private final FarmingPlugin plugin;
    private final Map<String, List<ArmorStand>> holograms = new ConcurrentHashMap<>();
    private final boolean enabled;
    private BukkitRunnable updateTask;

    public HologramManager(FarmingPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("plantations.holograms.enabled", true);
        
        if (enabled) {
            startUpdateTask();
            plugin.getLogger().info("Hologram system initialized!");
        }
    }

    /**
     * Create or update hologram for a farm instance
     */
    public void updateHologram(FarmInstance farm) {
        if (!enabled || farm.getLocation() == null) return;
        
        String hologramKey = getHologramKey(farm);
        
        // Remove old hologram if exists
        removeHologram(hologramKey);
        
        // Create new hologram lines
        List<String> lines = generateHologramLines(farm);
        Location baseLocation = farm.getLocation().clone().add(0.5, 2.5, 0.5);
        
        createHologram(hologramKey, baseLocation, lines);
    }

    /**
     * Generate hologram text lines for a farm
     */
    private List<String> generateHologramLines(FarmInstance farm) {
        List<String> lines = new ArrayList<>();
        
        // Farm title
        String titleFormat = plugin.getConfig().getString("plantations.holograms.title", "&e{farm_display}");
        String title = titleFormat.replace("{farm_display}", farm.getFarmType().getDisplayName());
        lines.add(ChatColor.translateAlternateColorCodes('&', title));
        
        // Instance and level info
        lines.add(ChatColor.GRAY + "Instance #" + farm.getInstanceId() + 
                 " | Level " + farm.getLevel());
        
        // Storage status
        int stored = farm.getTotalStoredItems();
        int max = farm.getFarmType().getStorageLimit();
        ChatColor storageColor = stored >= max ? ChatColor.RED : 
                                 stored >= max * 0.75 ? ChatColor.YELLOW : ChatColor.GREEN;
        lines.add(ChatColor.GRAY + "Storage: " + storageColor + stored + "/" + max);
        
        // Growth status
        if (farm.isReadyForHarvest()) {
            String readyFormat = plugin.getConfig().getString("plantations.holograms.ready", 
                                                             "&a&lREADY! &7Click to collect");
            lines.add(ChatColor.translateAlternateColorCodes('&', readyFormat));
        } else {
            long timeLeft = farm.getTimeUntilNextHarvest();
            String timeString = formatTime(timeLeft);
            String runningFormat = plugin.getConfig().getString("plantations.holograms.running", 
                                                               "&7Next: &a{time_left}");
            String running = runningFormat.replace("{time_left}", timeString);
            lines.add(ChatColor.translateAlternateColorCodes('&', running));
        }
        
        // Efficiency bonus
        if (farm.getEfficiency() > 1) {
            lines.add(ChatColor.AQUA + "⚡ Efficiency: " + farm.getEfficiency() + "x");
        }
        
        return lines;
    }

    /**
     * Create hologram at location with specified lines
     */
    private void createHologram(String key, Location baseLocation, List<String> lines) {
        List<ArmorStand> stands = new ArrayList<>();
        
        double yOffset = 0;
        for (String line : lines) {
            if (line == null || line.isEmpty()) continue;
            
            Location lineLocation = baseLocation.clone().add(0, yOffset, 0);
            
            ArmorStand stand = (ArmorStand) baseLocation.getWorld().spawnEntity(lineLocation, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setCanPickupItems(false);
            stand.setCustomNameVisible(true);
            stand.setCustomName(line);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setInvulnerable(true);
            
            stands.add(stand);
            yOffset -= 0.25; // Line spacing
        }
        
        holograms.put(key, stands);
    }

    /**
     * Remove hologram
     */
    public void removeHologram(String key) {
        List<ArmorStand> stands = holograms.remove(key);
        if (stands != null) {
            for (ArmorStand stand : stands) {
                if (stand != null && stand.isValid()) {
                    stand.remove();
                }
            }
        }
    }

    /**
     * Remove hologram for a farm
     */
    public void removeHologram(FarmInstance farm) {
        removeHologram(getHologramKey(farm));
    }

    /**
     * Remove all holograms for a player
     */
    public void removePlayerHolograms(UUID playerUuid) {
        String prefix = playerUuid.toString() + "_";
        Iterator<Map.Entry<String, List<ArmorStand>>> iterator = holograms.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, List<ArmorStand>> entry = iterator.next();
            if (entry.getKey().startsWith(prefix)) {
                for (ArmorStand stand : entry.getValue()) {
                    if (stand != null && stand.isValid()) {
                        stand.remove();
                    }
                }
                iterator.remove();
            }
        }
    }

    /**
     * Update all holograms for online players
     */
    private void updateAllHolograms() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            List<FarmInstance> farms = plugin.getPlantationManager().getPlayerFarms(player.getUniqueId());
            
            for (FarmInstance farm : farms) {
                if (farm.getLocation() != null) {
                    updateHologram(farm);
                }
            }
        }
    }

    /**
     * Start the hologram update task
     */
    private void startUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllHolograms();
            }
        };
        
        // Update every 5 seconds
        updateTask.runTaskTimer(plugin, 100L, 100L);
    }

    /**
     * Cleanup all holograms and stop tasks
     */
    public void cleanup() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }
        
        // Remove all holograms
        for (List<ArmorStand> stands : holograms.values()) {
            for (ArmorStand stand : stands) {
                if (stand != null && stand.isValid()) {
                    stand.remove();
                }
            }
        }
        
        holograms.clear();
    }

    /**
     * Show floating text temporarily at location
     */
    public void showFloatingText(Location location, String text, long durationMillis) {
        if (!enabled) return;
        
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(
            location.clone().add(0.5, 1, 0.5), EntityType.ARMOR_STAND);
        
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setCanPickupItems(false);
        stand.setCustomNameVisible(true);
        stand.setCustomName(ChatColor.translateAlternateColorCodes('&', text));
        stand.setMarker(true);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        
        // Animate upward and fade
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = (int) (durationMillis / 50);
            
            @Override
            public void run() {
                if (ticks >= maxTicks || !stand.isValid()) {
                    if (stand.isValid()) {
                        stand.remove();
                    }
                    cancel();
                    return;
                }
                
                // Move up slightly
                Location newLoc = stand.getLocation().add(0, 0.02, 0);
                stand.teleport(newLoc);
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Show harvest animation
     */
    public void showHarvestAnimation(Location location, int itemCount) {
        if (!enabled) return;
        
        String text = "&a&l+" + itemCount + " Items Harvested!";
        showFloatingText(location, text, 2000);
        
        // Particle effect
        location.getWorld().spawnParticle(
            org.bukkit.Particle.VILLAGER_HAPPY, 
            location.clone().add(0.5, 1, 0.5), 
            30, 0.5, 0.5, 0.5, 0.1
        );
    }

    /**
     * Show level up animation
     */
    public void showLevelUpAnimation(Location location, int newLevel) {
        if (!enabled) return;
        
        String text = "&6&l⬆ LEVEL UP! &e&lLevel " + newLevel;
        showFloatingText(location, text, 3000);
        
        // Particle effect
        location.getWorld().spawnParticle(
            org.bukkit.Particle.TOTEM, 
            location.clone().add(0.5, 1, 0.5), 
            50, 0.5, 1, 0.5, 0.1
        );
    }

    /**
     * Format time for display
     */
    private String formatTime(long millis) {
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

    /**
     * Get unique key for hologram
     */
    private String getHologramKey(FarmInstance farm) {
        return farm.getOwnerId().toString() + "_" + 
               farm.getFarmType().getId() + "_" + 
               farm.getInstanceId();
    }

    /**
     * Check if hologram exists for farm
     */
    public boolean hasHologram(FarmInstance farm) {
        return holograms.containsKey(getHologramKey(farm));
    }

    /**
     * Get nearby players to a location
     */
    private List<Player> getNearbyPlayers(Location location, double radius) {
        List<Player> nearby = new ArrayList<>();
        double radiusSquared = radius * radius;
        
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= radiusSquared) {
                nearby.add(player);
            }
        }
        
        return nearby;
    }

    /**
     * Update hologram visibility based on player distance
     */
    public void updateHologramVisibility() {
        double viewDistance = 32.0; // Distance at which holograms are visible
        
        for (Map.Entry<String, List<ArmorStand>> entry : holograms.entrySet()) {
            List<ArmorStand> stands = entry.getValue();
            if (stands.isEmpty()) continue;
            
            ArmorStand firstStand = stands.get(0);
            if (firstStand == null || !firstStand.isValid()) continue;
            
            List<Player> nearbyPlayers = getNearbyPlayers(firstStand.getLocation(), viewDistance);
            boolean shouldBeVisible = !nearbyPlayers.isEmpty();
            
            for (ArmorStand stand : stands) {
                if (stand != null && stand.isValid()) {
                    stand.setCustomNameVisible(shouldBeVisible);
                }
            }
        }
    }
}
