# 🌱 Farming Plugin - Advanced Plantation System

## Overview
An advanced farming plugin for Minecraft MMO RPG servers with offline growth, upgrades, holograms and more.

## Key Features
- 7 farm types with multiple instances
- 10 level progression system
- 3 upgrade paths: Storage, Speed, Quality
- 9 materials across 3 rarity tiers
- Offline growth and auto-collect
- Holograms and notifications
- Full database integration
- Fenced plots topped with barrier blocks to keep drops from flying out

## Requirements
- Minecraft Server 1.20+
- Vault
- MySQL/MariaDB
- Java 17+

## Installation
1. Place the plugin jar in the `plugins` folder.
2. Configure the database in `config.yml`.
3. Start the server to generate tables automatically.

## Commands
- `/plantation` – teleport to your plantation
- `/plantation info` – farm information
- `/plantation help` – help menu
- `/plantation reload` – reload configuration
- `/plantation reset <player>` – reset player farms

## Farm Types
1. Berry Orchards – starter farm, free, 8h growth
2. Melon Groves – 250M + materials, 10h growth
3. Fungal Caverns – 500M + materials, 14h growth
4. Pumpkin Patches – 750M + materials, 16h growth
5. Mystic Gardens – 1.5B + materials, 20h growth
6. Ancient Mangroves – 4B + materials, 30h growth
7. Desert Sanctuaries – 10B + materials, 40h growth

## Upgrade Paths
- Storage: +50 capacity per level
- Speed: +10% growth speed per level
- Quality: +20% drop rate per level

## Developer API
```java
FarmingPlugin plugin = FarmingPlugin.getInstance();
boolean hasFarm = plugin.hasFarm(playerUuid, "berry_orchards");
int level = plugin.getFarmLevel(playerUuid, "melon_groves");
long materials = plugin.getTotalMaterialsProduced(playerUuid);
```

## License
All Rights Reserved – commercial plugin.

## Credits
- Testing team
- Server community
- Spigot/Paper developers

Version 1.0 – Author: maks – Last update: 2024

