<div align="center">

# 📦 RiftDeposit

**Schowek z limitami przedmiotów dla serwerów EasyHC / HC**

[![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Paper](https://img.shields.io/badge/Paper-1.21--1.21.x-00AA00?style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0id2hpdGUiIGQ9Ik02IDJoMTJhMiAyIDAgMCAxIDIgMnYxNmEyIDIgMCAwIDEtMiAySDZhMiAyIDAgMCAxLTItMlY0YTIgMiAwIDAgMSAyLTJ6Ii8+PC9zdmc+&logoColor=white)](https://papermc.io/)
[![MySQL](https://img.shields.io/badge/MySQL-HikariCP-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://dev.mysql.com/)
[![YAML](https://img.shields.io/badge/Storage-YAML_%7C_MySQL-CB171E?style=for-the-badge&logo=yaml&logoColor=white)](https://yaml.org/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)

<br/>

> Kompletny plugin schowka z limitami przedmiotów PvP: per-grupowe limity (default/VIP/SVIP),
> precyzyjne dopasowanie eliksirów po typie efektu, poziomie i czasie trwania,
> intuicyjne GUI oraz pełne wsparcie MySQL + YAML.

</div>

---

## ✨ Funkcje

- **Limity przedmiotów** — każdy item ma skonfigurowany maksymalny limit w ekwipunku
- **Grupy limitów** — domyślny, VIP, SVIP (i dowolna liczba własnych grup) z oddzielnymi limitami
- **Precyzyjne potki** — dopasowanie po `effect-type`, `amplifier` (poziom I/II) i `duration-ticks`
- **GUI Schowka** — czytelny interfejs z tooltip: ile przechowane, ile limit, ile w ekwipunku, ile można dobrać
- **LPM / PPM** — dobierz 1 sztukę lub wszystkie dostępne za jednym kliknięciem
- **Dobierz wszystkie** — jeden przycisk pobierający wszystko co możliwe
- **Auto-detekcja nadmiaru** — nadmiar przenoszony do schowka natychmiast przy pickup + co N ticków
- **Dwa backendy** — YAML (domyślny, zero konfiguracji) lub MySQL z HikariCP
- **Async I/O** — zapis i odczyt danych nigdy nie blokuje main thread
- **MiniMessage** — wszystkie wiadomości i GUI w pełni konfigurowalne przez MiniMessage
- **Komendy admina** — `/rdadmin reload | inspect | clear | give` z tab-completion

---

## 🔑 Uprawnienia

| Uprawnienie | Opis | Domyślnie |
|---|---|---|
| `riftdeposit.use` | Otwiera GUI schowka | `true` |
| `riftdeposit.bypass` | Omija wszystkie limity | `op` |
| `riftdeposit.admin` | Dostęp do `/rdadmin` | `op` |
| `riftdeposit.limits.svip` | Limity grupy SVIP | `false` |
| `riftdeposit.limits.vip` | Limity grupy VIP | `false` |

> Grupy są sprawdzane malejąco po priorytecie — pierwszy pasujący limit wygrywa.

---

## 📋 Komendy

| Komenda | Opis |
|---|---|
| `/schowek` `/depozyt` `/deposit` `/vault` | Otwiera GUI schowka |
| `/rdadmin reload` | Przeładowuje `config.yml` i `messages.yml` |
| `/rdadmin inspect <gracz>` | Podgląd zawartości schowka gracza |
| `/rdadmin clear <gracz> [item]` | Czyści cały schowek lub konkretny item |
| `/rdadmin give <gracz> <item> <ilość>` | Dodaje przedmioty do schowka gracza |

---

## 🖥️ GUI

```
┌──────────────────────────────────────────────┐
│  ⚙ Schowek — Twoje Limity                   │
├──┬──┬──┬──┬──┬──┬──┬──┬──┐
│  │H1│H2│S1│S2│R1│R1│R2│  │  ← Potki Healing / Regen
│  │  │  │  │  │  │  │  │  │
│  │S1│SE│S2│SS│SP│SP│FI│FI│  ← Siła / Speed / Ogień
│  │  │  │  │  │  │  │  │  │
│  │SW│SW│GA│EG│EP│TT│OB│  │  ← Słabość / Combat
│  │  │  │  │  │  │  │  │  │
│  │→ │❄ │🧊│🧊│  │💎│  │  │  ← Strzały / Lód
│✖ │  │  │  │✔ │  │  │  │  │  ← Zamknij / Dobierz wszystkie
└──┴──┴──┴──┴──┴──┴──┴──┴──┘
```

**Tooltip po najechaniu na item:**
```
❤ Healing I (instant)
──────────────────
Przechowane:  3
Limit:        4
W ekwipunku:  1
Możesz dobrać: 3

⬅ LPM — dobierz 1
➡ PPM — dobierz wszystkie
```

---

## ⚙️ Instalacja

### Wymagania
- **Paper** 1.21 – 1.21.x
- **Java** 21+
- (opcjonalnie) serwer **MySQL 8.0+**

### Kroki

1. Pobierz `RiftDeposit-x.x.x.jar` z [Releases](../../releases)
2. Wrzuć do folderu `plugins/`
3. Restartuj serwer
4. Skonfiguruj `plugins/RiftDeposit/config.yml`

```bash
# Budowanie ze źródeł
./gradlew build
# → build/libs/RiftDeposit-1.0.0.jar
```

---

## 🔧 Konfiguracja

### Wybór backendu (`config.yml`)

```yaml
storage:
  type: YAML   # lub MYSQL

  mysql:
    host: localhost
    port: 3306
    database: riftdeposit
    username: root
    password: haslo
    pool-size: 10
```

### Grupy limitów

```yaml
groups:
  svip:
    permission: riftdeposit.limits.svip
    priority: 30       # wyższy = sprawdzany pierwszy
  vip:
    permission: riftdeposit.limits.vip
    priority: 20
  default:
    permission: null   # null = zawsze pasuje (fallback)
    priority: 0
```

### Dodawanie przedmiotu

```yaml
items:
  # Zwykły item
  ender_pearl:
    material: ENDER_PEARL
    display-name: "<dark_purple>🔮 Perła Końca"
    slot: 39
    limits:
      default: 4
      vip:     6
      svip:    8

  # Eliksir z precyzyjną specyfikacją
  strength_ii:
    material: POTION
    display-name: "<dark_red>⚔⚔ Siła II (1:30)"
    slot: 21
    potion:
      effect-type: STRENGTH   # typ efektu
      amplifier: 1            # 0 = poziom I,  1 = poziom II
      duration-ticks: 1800    # 1:30 (20 ticks = 1 sekunda)
    limits:
      default: 1
      vip:     2
      svip:    3
```

### Tabela czasów eliksirów

| Rodzaj | Czas | Ticki |
|---|---|---|
| Potka zwykła | 3:00 | 3600 |
| Potka wzmocniona (II) | 1:30 | 1800 |
| Potka przedłużona (ext.) | 8:00 | 9600 |
| Regen I zwykła | 0:45 | 900 |
| Regen II mocna | 0:22 | 440 |
| Instant Healing | natychmiastowa | 1 |

> Użyj `min-duration-ticks` i `max-duration-ticks` zamiast `duration-ticks` jeśli chcesz złapać zakres (np. wszystkie potki regen I niezależnie od czasu).

### Dostępne typy efektów (`effect-type`)

```
speed, slowness, haste, mining_fatigue, strength, instant_health,
instant_damage, jump_boost, nausea, regeneration, resistance,
fire_resistance, water_breathing, invisibility, blindness,
night_vision, weakness, poison, wither, health_boost, absorption,
slow_falling, darkness, ...
```

---

## 📁 Struktura projektu

```
src/main/java/pl/tenfajnybartek/riftdeposit/
├── base/
│   └── DepositPlugin.java          ← główna klasa
├── command/
│   ├── DepositCommand.java         ← /schowek
│   └── RdAdminCommand.java         ← /rdadmin
├── config/
│   ├── ConfigManager.java          ← parsowanie config.yml
│   └── MessagesManager.java        ← MiniMessage + placeholdery
├── data/
│   ├── StorageProvider.java        ← interfejs async I/O
│   ├── YamlStorageProvider.java    ← zapis YAML
│   ├── MySQLStorageProvider.java   ← zapis MySQL
│   └── HikariConnectionPool.java   ← pula połączeń
├── deposit/
│   ├── DepositManager.java         ← główna logika biznesowa
│   ├── DepositData.java            ← model danych gracza (thread-safe)
│   ├── ItemLimit.java              ← deskryptor limitu
│   ├── ItemMatcher.java            ← dopasowanie/liczenie/usuwanie itemów
│   ├── LimitGroup.java             ← model grupy (vip/svip/default)
│   └── PotionSpec.java             ← precyzyjna specyfikacja eliksiru
├── gui/
│   ├── DepositGui.java             ← budowanie GUI (InventoryHolder)
│   └── GuiListener.java            ← obsługa kliknięć
└── listener/
    └── InventoryCheckListener.java ← join/quit/pickup/close
```

---

## 🛡️ Thread Safety

Plugin jest napisany z myślą o bezpieczeństwie wątkowym:

- **`DepositData`** — wszystkie metody `synchronized`; `snapshot()` tworzy bezpieczną kopię przed przekazaniem do async I/O
- **`DepositManager.cache`** — `ConcurrentHashMap`
- **Wszystkie modyfikacje ekwipunku** — wyłącznie na main thread (Bukkit scheduler)
- **Cały I/O (YAML / MySQL)** — na dedykowanych executor threads, nigdy na main thread

---

## 🐛 Changelog

### v1.0.0
- Pierwsze wydanie
- Grupy limitów (VIP/SVIP/default)
- Precyzyjne matchowanie eliksirów po `effect-type`, `amplifier`, `duration-ticks`
- GUI z LPM/PPM/Dobierz wszystkie
- YAML + MySQL (HikariCP)
- MiniMessage we wszystkich wiadomościach

---

## 📝 Licencja

Projekt dostępny na licencji [MIT](LICENSE).

---

<div align="center">

Zrobiony z ❤️ dla społeczności HC/EasyHC

</div>