# 🌱 Farming Plugin - Advanced Plantation System

## 📋 Opis
Zaawansowany plugin farmingowy dla serwerów Minecraft MMO RPG, oferujący kompleksowy system plantacji z automatycznym wzrostem offline, systemem ulepszeń, hologramami i wieloma innymi funkcjami.

## ✨ Główne Funkcje

### 🏡 System Plantacji
- **7 typów farm** o różnym poziomie zaawansowania
- **System instancji** - każdy typ farmy może mieć wiele instancji
- **Własne obszary plantacji** dla każdego gracza
- **Ochrona terenu** przed griefingiem

### 📈 System Progresji
- **10 poziomów** dla każdej farmy
- **3 typy ulepszeń**: Storage, Speed, Quality
- **System doświadczenia** za zbieranie plonów
- **Wymagania materiałowe** do odblokowania farm

### 🎯 Materiały i Tiers
- **9 typów materiałów** w 3 poziomach rzadkości
- **System tier** (I, II, III) dla każdego materiału
- **Drop rates** zależne od poziomu i ulepszeń
- **Customowe itemy** z NBT tags

### 🤖 Automatyzacja
- **Offline growth** - farmy rosną gdy gracz jest offline
- **Auto-collect** - automatyczne zbieranie przy pełnym storage
- **Hologramy** pokazujące status farm
- **Powiadomienia** o ważnych wydarzeniach

## 📦 Wymagania
- Minecraft Server 1.20+
- Vault (dla ekonomii)
- MySQL/MariaDB
- Java 17+

## 🔧 Instalacja

1. **Pobierz plugin** i umieść w folderze `plugins`
2. **Skonfiguruj bazę danych** w `config.yml`:
```yaml
database:
  host: 'localhost'
  port: 3306
  database: 'plantation'
  username: 'root'
  password: 'your_password'
```
3. **Uruchom serwer** - tabele zostaną utworzone automatycznie
4. **Skonfiguruj ekonomię** - upewnij się że Vault i plugin ekonomii są zainstalowane

## 🎮 Komendy

### Dla Graczy
- `/plantation` - Teleportacja do swojej plantacji
- `/plantation info` - Informacje o farmach
- `/plantation help` - Pomoc

### Dla Adminów
- `/plantation reload` - Przeładowanie konfiguracji
- `/plantation reset <gracz>` - Reset plantacji gracza
- `/plantation give <gracz> <materiał> <tier> [ilość]` - Dawanie materiałów
- `/plantation debug` - Informacje debugowe

## 🏗️ Typy Farm

### 1. Berry Orchards (Starter)
- **Koszt:** Darmowe
- **Max instancji:** 6
- **Czas wzrostu:** 8 godzin
- **Storage:** 500
- **Drops:** Plant Fiber, Herbal Extract

### 2. Melon Groves
- **Koszt:** 250M + materiały
- **Max instancji:** 6  
- **Czas wzrostu:** 10 godzin
- **Storage:** 400
- **Drops:** Seed Pouch, Plant Fiber

### 3. Fungal Caverns
- **Koszt:** 500M + materiały
- **Max instancji:** 6
- **Czas wzrostu:** 14 godzin
- **Storage:** 350
- **Drops:** Mushroom Spores, Compost Dust

### 4. Pumpkin Patches
- **Koszt:** 750M + materiały
- **Max instancji:** 6
- **Czas wzrostu:** 16 godzin
- **Storage:** 300
- **Drops:** Seed Pouch T2-3, Compost Dust

### 5. Mystic Gardens
- **Koszt:** 1.5B + materiały
- **Max instancji:** 3
- **Czas wzrostu:** 20 godzin
- **Storage:** 200
- **Drops:** Herbal Extract, Beeswax Chunk

### 6. Ancient Mangroves
- **Koszt:** 4B + materiały
- **Max instancji:** 3
- **Czas wzrostu:** 30 godzin
- **Storage:** 100
- **Drops:** Druidic Essence, Mushroom Spores T3

### 7. Desert Sanctuaries (Legendary)
- **Koszt:** 10B + materiały
- **Max instancji:** 1
- **Czas wzrostu:** 40 godzin
- **Storage:** 50
- **Drops:** Golden Truffle, Ancient Grain, Druidic Essence

## ⚙️ System Ulepszeń

### Storage Upgrade (5 poziomów)
- +50 pojemności na poziom
- Zwiększa limit przechowywanych materiałów

### Speed Upgrade (5 poziomów)
- +1 efficiency na poziom
- Skraca czas wzrostu

### Quality Upgrade (5 poziomów)
- +15% szansy na drop na poziom
- Zwiększa szansę na rzadkie materiały

## 📊 System Poziomów

Każda farma może osiągnąć poziom 10:
- **Poziom 2:** +10% storage, +5% speed, +20% drops
- **Poziom 5:** +40% storage, +20% speed, +80% drops
- **Poziom 10:** +90% storage, +45% speed, +180% drops

## 🎨 GUI System

### Główne GUI Farmy
- Informacje o farmie
- Przechowywane materiały
- Przyciski akcji (Collect, Upgrade, Settings)
- Statystyki

### GUI Ulepszeń
- 3 typy ulepszeń z kosztami
- Level up system
- Informacje o benefitach

### GUI Ustawień
- Auto-collect toggle
- Hologramy toggle
- Powiadomienia toggle
- Efekty cząsteczkowe toggle
- Drop do ekwipunku toggle

## 🗄️ Struktura Bazy Danych

### Główne Tabele
- `farming_player_plantations` - Dane farm graczy
- `farming_player_materials` - Materiały graczy
- `farming_plantation_storage` - Przechowywane materiały w farmach
- `farming_player_plots` - Lokalizacje plantacji
- `farming_farm_anchors` - Pozycje farm
- `farming_farm_upgrades` - Historia ulepszeń
- `farming_player_stats` - Statystyki graczy
- `farming_harvest_log` - Logi zbiorów
- `farming_farm_unlocks` - Odblokowane farmy
- `farming_player_settings` - Ustawienia graczy

## 🔐 Permisje

- `plantation.use` - Dostęp do systemu plantacji
- `plantation.admin` - Komendy administracyjne
- `plantation.admin.build` - Budowanie na cudzych plantacjach
- `plantation.bypass.limit` - Bypass limitów instancji

## 📝 Konfiguracja

### config.yml
```yaml
# Główne ustawienia
debug: false
auto_save:
  enabled: true
  interval_minutes: 5

# Hologramy
plantations:
  holograms:
    enabled: true
    update_interval: 5
    
# Ochrona
protection:
  block_build: true
  block_pvp: false
```

## 🐛 Rozwiązywanie Problemów

### Plugin nie startuje
1. Sprawdź połączenie z bazą danych
2. Upewnij się że Vault jest zainstalowany
3. Sprawdź logi serwera

### Farmy nie rosną offline
1. Sprawdź czy `offline_growth.enabled: true`
2. Zwiększ `offline_growth.max_cycles` jeśli potrzeba

### Hologramy nie pokazują się
1. Sprawdź czy `holograms.enabled: true`
2. Sprawdź odległość widoczności

## 📊 Statystyki i Metryki

Plugin śledzi:
- Całkowita liczba farm
- Liczba zbiorów
- Zebrane materiały
- Wydane pieniądze
- Czas gry

## 🔄 API dla Developerów

```java
FarmingPlugin plugin = FarmingPlugin.getInstance();

// Sprawdź czy gracz ma farmę
boolean hasFarm = plugin.hasFarm(playerUuid, "berry_orchards");

// Pobierz poziom farmy
int level = plugin.getFarmLevel(playerUuid, "melon_groves");

// Pobierz wyprodukowane materiały
long materials = plugin.getTotalMaterialsProduced(playerUuid);
```

## 📞 Wsparcie

- Discord: [Link do discorda]
- GitHub Issues: [Link do repo]
- Wiki: [Link do wiki]

## 📜 Licencja

All Rights Reserved - Plugin komercyjny

## 🙏 Podziękowania

- Zespół testowy za feedback
- Społeczność serwera za sugestie
- Deweloperzy Spigot/Paper

---

**Wersja:** 1.0  
**Autor:** maks  
**Ostatnia aktualizacja:** 2024
