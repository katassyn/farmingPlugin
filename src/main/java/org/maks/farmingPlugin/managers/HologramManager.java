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
 * Optimized hologram manager with reduced update frequency
 */
public class HologramManager {
    
    private final FarmingPlugin plugin;
    private final Map<String, List<ArmorStand>> holograms = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateTimes = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final double yOffset;
    private BukkitRunnable updateTask;
    private static final long UPDATE_COOLDOWN = 5000; // 5 seconds minimum between updates

    public HologramManager(FarmingPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("plantations.holograms.enabled", true);
        this.yOffset = plugin.getConfig().getDouble("plantation.holograms.y_offset", 0.5);
        
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
        
        // Check update cooldown to prevent flickering
        Long lastUpdate = lastUpdateTimes.get(hologramKey);
        if (lastUpdate != null && System.currentTimeMillis() - lastUpdate < UPDATE_COOLDOWN) {
            return; // Skip update if too soon
        }
        
        // Remove old hologram if exists
        removeHologram(hologramKey);
        
        // Create new hologram lines
        List<String> lines = generateHologramLines(farm);
        Location baseLocation = holoLoc(farm.getLocation());

        createHologram(hologramKey, baseLocation, lines);
        lastUpdateTimes.put(hologramKey, System.currentTimeMillis());
    }

    private Location holoLoc(Location base) {
        return base.clone().add(0.5, yOffset, 0.5);
    }

    /**
     * Generate hologram text lines for a farm
     */
    private List<String> generateHologramLines(FarmInstance farm) {
        List<String> lines = new ArrayList<>();
        
        // Farm title only
        lines.add(ChatColor.YELLOW + farm.getFarmType().getDisplayName());
        
        // Growth status
        if (farm.isReadyForHarvest()) {
            lines.add(ChatColor.GREEN.toString() + ChatColor.BOLD.toString() + "✔ READY TO HARVEST!");
        } else {
            long timeLeft = farm.getTimeUntilNextHarvest();
            String timeString = formatTime(timeLeft);
            lines.add(ChatColor.YELLOW + "Next: " + ChatColor.WHITE + timeString);
        }
        
        // Efficiency if upgraded
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
        lastUpdateTimes.remove(key);
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
                lastUpdateTimes.remove(entry.getKey());
            }
        }
    }

    /**
     * Update all holograms for online players (with smart updates)
     */
    public void updateAllHolograms() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            List<FarmInstance> farms = plugin.getPlantationManager().getPlayerFarms(player.getUniqueId());
            
            for (FarmInstance farm : farms) {
                if (farm.getLocation() != null) {
                    String key = getHologramKey(farm);
                    
                    // Only update if hologram doesn't exist or needs updating
                    boolean needsUpdate = false;
                    
                    if (!holograms.containsKey(key)) {
                        needsUpdate = true; // Hologram doesn't exist
                    } else {
                        // Check if content has changed significantly
                        Long lastUpdate = lastUpdateTimes.get(key);
                        if (lastUpdate == null || System.currentTimeMillis() - lastUpdate > 30000) {
                            // Update every 30 seconds at most
                            needsUpdate = true;
                        } else if (farm.isReadyForHarvest()) {
                            // Always update when ready for harvest
                            needsUpdate = true;
                        }
                    }
                    
                    if (needsUpdate) {
                        updateHologram(farm);
                    }
                }
            }
        }
    }

    /**
     * Start the hologram update task (reduced frequency)
     */
    private void startUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllHolograms();
            }
        };
        
        // Update every 30 seconds instead of every 5
        updateTask.runTaskTimer(plugin, 100L, 600L);
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
        lastUpdateTimes.clear();
    }

    /**
     * Show floating text temporarily at location
     */
    public void showFloatingText(Location location, String text, long durationMillis) {
        if (!enabled) return;
        
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(
            holoLoc(location), EntityType.ARMOR_STAND);
        
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
        
        String text = "&a&l+" + itemCount + " Fruits Harvested!";
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
}
