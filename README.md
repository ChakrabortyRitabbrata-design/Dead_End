# DEAD END

> A Top-Down 2D Action RPG built with Java 17+, Swing & Graphics2D, backed by MySQL & JDBC.

![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Swing](https://img.shields.io/badge/GUI-Java%20Swing%20%2F%20Graphics2D-5382A1?style=for-the-badge)
![MySQL](https://img.shields.io/badge/Database-MySQL%208.0%2B-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-Modular%20State%20Driven-00599C?style=for-the-badge)

---

## Table of Contents

1. [Overview](#overview)
2. [Tech Stack & Architecture](#tech-stack--architecture)
3. [Game Controls](#game-controls)
4. [Key Features & Gameplay Mechanics](#key-features--gameplay-mechanics)
   - [State Management & Flow](#state-management--flow)
   - [Authentication & Global Leaderboard](#authentication--global-leaderboard)
   - [Legendary Executioner Sword](#legendary-executioner-sword)
   - [World Regions, Gates & Progression](#world-regions-gates--progression)
   - [Combat, Hitboxes & Enemy AI](#combat-hitboxes--enemy-ai)
   - [Water Dragon Boss Encounters](#water-dragon-boss-encounters)
   - [Inventory & Equipment System](#inventory--equipment-system)
   - [Fog of War & Minimap Atlas](#fog-of-war--minimap-atlas)
5. [Project Structure](#project-structure)
6. [Prerequisites & Installation](#prerequisites--installation)
7. [Database Setup](#database-setup)
8. [Building & Running](#building--running)
9. [Automated Test Suite](#automated-test-suite)
10. [License](#license)

---

## Overview

**DEAD END** is a dark gothic top-down 2D action role-playing game developed in modern Java (17+) using pure Java Swing and `Graphics2D` rendering with zero external graphics or game engine dependencies.

The player explores three interconnected, authored world regions shrouded in dynamic Fog of War, battles aggressive undead monsters, acquires legendary gear, navigates through gated region boundaries, and confronts twin Water Dragons in Region 3's submerged arena. The game features player authentication, persistent world state saving, and a global database-backed high score leaderboard.

- **Target Resolution**: $768 \times 576$ pixels (16:12 tile aspect, $16 \times 12$ tiles visible at $48 \times 48$ px per tile).
- **Framerate**: Deterministic 60 FPS tick loop driven by a Swing Timer with sub-millisecond nanosecond delta timing.
- **Visual Aesthetic**: Hand-crafted pixel art, dark gothic atmosphere, crimson accents, and real-time custom Java2D procedural effects.

---

## Tech Stack & Architecture

### Core Technologies
- **Runtime Environment**: Java 17+ (OpenJDK / Oracle JDK)
- **Module System**: Java Module System (`module-info.java`) declaring explicit dependencies on `java.desktop` and `java.sql`.
- **Rendering Engine**: Pure `java.awt.Graphics2D` with nearest-neighbor interpolation for authentic pixel-art scaling, alpha compositing for lighting/fades, and geometry-anchored sprite drawing.
- **Windowing & GUI**: Java Swing (`JFrame`, `JPanel`, `CardLayout`, custom `RpgButton` components with hover/press audio-visual feedback).
- **Game Loop**: Fixed-rate 60 FPS loop with delta-timing calculations (`java.lang.System.nanoTime()`), edge-triggered input polling, and non-blocking state updates.
- **Database & Persistence**:
  - **RDBMS**: MySQL Server 8.0+
  - **Connectivity**: JDBC with `mysql-connector-j-8.4.0.jar`
  - **Security**: Local `.env` credential management and SHA-256 password hashing with salt.
  - **Local World Saves**: Atomic file persistence with backup recovery (`saves/world.properties`).

### Architectural Layout

```
                                      +--------------------+
                                      |     Window.java    |
                                      |    (CardLayout)    |
                                      +---------+----------+
                                                |
         +------------------+-------------------+------------------+------------------+
         |                  |                   |                  |                  |
+--------v---------+ +------v---------+ +-------v--------+ +-------v--------+ +-------v--------+
|  MainMenuPanel   | |    AuthPanel   | |GamePanel (Play)| |    PauseMenu   | |LeaderboardPanel|
| (Start / Exit)   | | (Login / Reg)  | | (60 FPS Loop)  | |(Resume/Restart)| | (Top 10 MySQL) |
+------------------+ +--------+-------+ +-------+--------+ +----------------+ +----------------+
                              |                 |
                              |         +-------+-------+
                              |         |               |
                              |   +-----v-----+   +-----v-----+
                              |   | Player &  |   | LevelMgr  |
                              |   | Combat    |   | & Regions |
                              |   +-----------+   +-----------+
                              |                         |
                              +------------+------------+
                                           |
                                  +--------v--------+
                                  | DatabaseManager |
                                  |   (MySQL JDBC)  |
                                  +-----------------+
```

---

## Game Controls

### Exploration & Movement
| Key / Input | Action | Description |
|---|---|---|
| `W` / `Up Arrow` | Move North | Moves the player character upward |
| `S` / `Down Arrow` | Move South | Moves the player character downward |
| `A` / `Left Arrow` | Move West | Moves the player character leftward |
| `D` / `Right Arrow` | Move East | Moves the player character rightward |
| `Shift` (Hold) | Sprint | Increases movement speed (6 px/tick on land, 4 px/tick in water). *Disabled while Executioner Sword is equipped.* |
| `Q` | Dodge Dash | Fast directional dash (8 px/tick) in movement direction |
| `Ctrl` | Evade Roll | Evasion roll (6 px/tick) avoiding obstacle collision |
| `F` | Interact / Travel | Interacts with region travel gates / awaken wards |

### Combat & Defense
| Key / Input | Action | Description |
|---|---|---|
| `Space` | Weapon Attack | Swings currently equipped weapon in Main Hand |
| `J` / `Left Click` | Punch Attack | Bare-fist melee attack (10 base damage) |
| `E` / `K` | Kick Attack | Heavy kick melee attack (20 base damage) |
| `R` / `Right Click` | Shield Block | Raises equipped Off-Hand shield; absorbs damage |

### Inventory & Atlas
| Key / Input | Action | Description |
|---|---|---|
| `Tab` | Toggle Inventory | Opens/closes 36-slot inventory & equipment UI |
| `1` – `9` | Select Hotbar Slot | Selects hotbar slot; weapons auto-equip to Main Hand |
| `Mouse Drag` | Drag & Drop | Drag items between inventory, hotbar, and gear slots |
| `M` / `Click Minimap` | World Atlas | Opens full-screen interactive world map |
| `Mouse Wheel` | Atlas Zoom | Zooms full map view between 0.5× and 4.0× |
| `Left Drag (Atlas)` | Atlas Pan | Pans the full-screen map camera |
| `Home` | Atlas Reset | Resets full-screen map zoom and center |

### System & Navigation
| Key / Input | Action | Description |
|---|---|---|
| `Escape` | Pause / Back | Opens in-game Pause Menu during gameplay; closes open overlays |
| `L` | Leaderboard | Opens global leaderboard from Game Complete / Game Over screens |
| `F5` | Quick Save | Atomically saves world state, position, enemies, and gear |
| `F9` | Quick Load | Restores world state, progression, and player inventory |

---

## Key Features & Gameplay Mechanics

### State Management & Flow
Screen navigation uses `CardLayout` across clean decoupled panels:
- `MAIN_MENU`: Minimalist black RPG starting menu with "START GAME", "GLOBAL LEADERBOARD", and "EXIT".
- `AUTH`: In-engine login and registration panel for player accounts.
- `PLAYING`: Active 60 FPS gameplay loop.
- `PAUSED`: In-game pause overlay with "RESUME", "RESTART", and "EXIT". Halts enemy AI, player input, and timers.
- `LEADERBOARD`: Displays top global scores retrieved from MySQL with rank, player name, score, and date.
- `LEVEL_TRANSITION`: Smooth fade-to-black and fade-in between connected world regions.
- `GAME_OVER`: Displays survival summary and leaderboard shortcut upon death.
- `GAME_COMPLETE`: Triggered when both Region 3 Water Dragons are defeated.

---

### Authentication & Global Leaderboard
- **Database Backend**: Integrated MySQL 8.0+ connection pooling via [DatabaseManager.java](file:///e:/Dead_End/Dead_End/src/db/DatabaseManager.java).
- **Credentials**: Loaded dynamically from `.env` using [EnvConfig.java](file:///e:/Dead_End/Dead_End/src/db/EnvConfig.java).
- **Security**: Passwords hashed with SHA-256 and cryptographic salts in [PasswordHasher.java](file:///e:/Dead_End/Dead_End/src/db/PasswordHasher.java).
- **Persistence**: High scores recorded via [ScoreDAO.java](file:///e:/Dead_End/Dead_End/src/db/ScoreDAO.java) upon game over or victory.

---

### Legendary Executioner Sword
A special endgame weapon implemented with custom art assets, unlock logic, and combat rules:
- **Assets**:
  - `res/ExecutionerSword/Inventory-icon.png`: High-resolution inventory, slot, and popup icon.
  - `res/ExecutionerSword/movement.png`: 4-direction, 4-action custom player sprite sheet.
- **Unlock Condition**:
  $$\text{currentRegion} == 3 \quad \land \quad \text{progressionScore} \ge 250$$
  Guaranteed never to unlock in earlier regions or before 250 cumulative score.
- **Visual Notification**: Top-right non-blocking popup notification with smooth fade-in, display duration, and fade-out. Styled with dark gothic translucent backdrop and crimson glowing borders.
- **Combat Mechanics**:
  - **Regular Zombies**: Defeated in **1 hit** (`Math.max(attackDamage, maxHealth)`).
  - **Water Dragons**: Deals exactly **50% max HP** ($150 / 300$ damage) per hit. Defeated in **exactly 2 hits**.
- **Movement Restrictions**: Due to immense weapon weight, sprint (Shift) is disabled while equipped. Normal walk speed (4 px/tick) and dodge/roll functionality remain active. Re-enables immediately when unequipped.
- **Persistence**: Stored across Quick Save/Load and application restarts in `saves/world.properties`.

---

### World Regions, Gates & Progression
The world consists of three connected regions defined in `res/world/`:
1. **Region 1 (Willow Rest)**: Forest starting area with ruined houses, wandering undead monsters, and exploration gates.
2. **Region 2 (The Blighted Crossing)**: Hazardous corrupted pathways with tougher monster spawns and water bridges.
3. **Region 3 (The Sunken Sanctum)**: Deep-water lake arena housing twin Water Dragons.
- **Gates**: Travel gates require cumulative progression scores (e.g. 50 score for Gate 1, 100 for Gate 2). Score is awarded permanently upon defeating monsters and bosses.

---

### Combat, Hitboxes & Enemy AI
- **Hitboxes**: Precision frame-anchored hitboxes calculate directional reach in front of the player.
- **Single-Swing Guard**: Attack sequence IDs prevent multi-hit bugs from registering multiple damage instances in a single swing.
- **Enemy AI**:
  - Aggro range detection and pathfinding toward player.
  - Directional knockback away from the attacker.
  - Stun/hitstun interrupting enemy attacks when damaged.
  - Multi-frame death animations before entity removal.

---

### Water Dragon Boss Encounters
- Two colossal Water Dragons spawn in Region 3's deep-water lakes.
- Bosses utilize three-direction sprite sheets (`Dragon_Sprite_Sheet.png`) with submerged, swimming, and surfacing states.
- **Attacks**:
  - Targeted fireball projectile barrages.
  - Expanding concentric water energy pulses.
- Avoids land obstacles and pursues player across waterways. Defeating both dragons triggers the victory state.

---

### Inventory & Equipment System
- **Capacity**: 36 slots total (27 main inventory slots + 9 hotbar slots).
- **Equipment Slots**: 6 dedicated gear slots:
  - `HEAD` (Helmets)
  - `CHEST` (Chestplates)
  - `LEGS` (Leggings)
  - `FEET` (Boots)
  - `MAIN_HAND` (Weapons)
  - `OFF_HAND` (Shields)
- **Features**: Drag-and-drop item movement, stack counts, slot compatibility validation, dynamic stats calculations, and rarity-colored tooltips.

---

### Fog of War & Minimap Atlas
- **Fog of War**: Dual-layer visibility tracking:
  - `VISIBLE`: Fully lit tile in direct line of sight.
  - `EXPLORED`: Dimmed tile previously seen (60% brightness).
  - `UNEXPLORED`: Faint silhouette (25% brightness; never pitch black).
- **Minimap**:
  - **HUD Minimap**: $180 \times 180$ px square at top-right showing immediate $25 \times 25$ tile radius.
  - **Full-Screen Atlas**: Interactive map overlay with smooth pan, zoom, and fit controls.

---

## Project Structure

```
Dead_End/
├── .env                                 # Local MySQL credentials (git-ignored)
├── .gitignore                           # Git ignore rules
├── README.md                            # Comprehensive project documentation
├── run.bat                              # Windows one-click compile & launch script
├── db/
│   └── schema.sql                       # MySQL tables, indexes, and constraints
├── lib/
│   └── mysql-connector-j-8.4.0.jar      # Official MySQL JDBC Driver
├── res/                                 # Game assets
│   ├── Dragon/                          # Dragon boss sprite sheets
│   ├── ExecutionerSword/                # Legendary sword icon & movement sheet
│   ├── map/                             # Level collision and metadata maps
│   ├── objects/                         # World props (houses, obstacles)
│   ├── player/                          # Player base and equipment overlays
│   ├── tile/                            # Environmental terrain tiles
│   └── world/                           # Authored regions & progression config
├── saves/                               # World save properties (git-ignored)
└── src/                                 # Java source code
    ├── module-info.java                 # Java 17 module definition
    ├── animation/                       # Animation state machine & mapping
    │   ├── Animation.java
    │   ├── AnimationController.java
    │   ├── AnimationFrame.java
    │   ├── AnimationState.java
    │   ├── Direction.java
    │   ├── Hitbox.java
    │   └── SpriteSheetMapping.java
    ├── db/                              # Database connectivity & models
    │   ├── AuthDAO.java
    │   ├── DatabaseManager.java
    │   ├── EnvConfig.java
    │   ├── PasswordHasher.java
    │   ├── ScoreDAO.java
    │   ├── ScoreEntry.java
    │   ├── SessionManager.java
    │   └── User.java
    ├── entities/                        # Living actors & rendering overlays
    │   ├── ArmorAttachment.java
    │   ├── AttachmentPoint.java
    │   ├── CharacterSpriteManager.java
    │   ├── Dragon.java
    │   ├── Entity.java
    │   ├── EquipmentRenderer.java
    │   ├── EquipmentSpriteSheet.java
    │   ├── Monster.java
    │   ├── Player.java
    │   ├── SpriteGenerator.java
    │   ├── SpriteLayer.java
    │   ├── WaterDragonEffects.java
    │   ├── WeaponAnimation.java
    │   ├── WeaponAttachment.java
    │   └── WeaponRenderer.java
    ├── inventory/                       # Inventory, hotbar & items
    │   ├── Armor.java
    │   ├── EquipmentManager.java
    │   ├── EquipmentSlot.java
    │   ├── ExecutionerSword.java
    │   ├── Hotbar.java
    │   ├── Inventory.java
    │   ├── InventoryUI.java
    │   ├── Item.java
    │   └── Weapon.java
    ├── main/                            # Core engine, UI screens & tests
    │   ├── AssetSetter.java
    │   ├── AuthLeaderboardIntegrationTest.java
    │   ├── AuthPanel.java
    │   ├── CollisionChecker.java
    │   ├── DamagePopup.java
    │   ├── DecorRenderer.java
    │   ├── ExecutionerSwordIntegrationTest.java
    │   ├── ExecutionerSwordNotification.java
    │   ├── FogOfWar.java
    │   ├── GamePanel.java
    │   ├── KeyHandler.java
    │   ├── LeaderboardPanel.java
    │   ├── LevelManager.java
    │   ├── MainMenuPanel.java
    │   ├── MainMenuTest.java
    │   ├── Minimap.java
    │   ├── PauseMenuPanel.java
    │   ├── PauseMenuTest.java
    │   ├── Progression.java
    │   ├── RpgButton.java
    │   ├── WaterDragonIntegrationTest.java
    │   ├── Window.java
    │   ├── WorldIntegrationTest.java
    │   └── WorldRenderer.java
    ├── Object/                          # Static map objects
    │   ├── OBJ_House.java
    │   └── SuperObject.java
    └── tile/                            # Tile engine & map loaders
        ├── MapData.java
        ├── MapLoader.java
        ├── Tile.java
        ├── TileManager.java
        └── WorldDefinition.java
```

---

## Prerequisites & Installation

1. **Java Development Kit (JDK 17 or newer)**:
   - Verify by running:
     ```sh
     java -version
     javac -version
     ```
2. **MySQL Server 8.0+**:
   - Running locally on port `3306`.
3. **Operating System**:
   - Windows, macOS, or Linux.

---

## Database Setup

1. Open MySQL Workbench or the MySQL command line client as `root`:
2. Execute the schema script located at `db/schema.sql`:
   ```sh
   mysql -u root -p < db/schema.sql
   ```
3. Create the application user and grant permissions:
   ```sql
   CREATE USER IF NOT EXISTS 'dead_end_app'@'localhost' IDENTIFIED BY 'YOUR_PASSWORD_HERE';
   GRANT ALL PRIVILEGES ON dead_end.* TO 'dead_end_app'@'localhost';
   FLUSH PRIVILEGES;
   ```
4. Create a `.env` file in the project root directory with your credentials:
   ```env
   DB_HOST=localhost
   DB_PORT=3306
   DB_NAME=dead_end
   DB_USER=dead_end_app
   DB_PASSWORD=YOUR_PASSWORD_HERE
   ```

---

## Building & Running

### Windows (Recommended)
Simply double-click or run the provided batch script:
```cmd
.\run.bat
```

### Manual Compilation & Execution

**Windows (PowerShell):**
```powershell
javac -cp "lib/*" -d bin src\module-info.java src\animation\*.java src\main\*.java src\entities\*.java src\tile\*.java src\Object\*.java src\inventory\*.java src\db\*.java
java -cp "bin;lib/*" main.Window
```

**macOS / Linux:**
```bash
javac -cp "lib/*" -d bin $(find src -name "*.java")
java -cp "bin:lib/*" main.Window
```

---

## Automated Test Suite

DEAD END includes a complete automated headless test suite covering all major game mechanics:

### 1. Executioner Sword Verification (17 Test Scenarios)
Verifies unlock region & score bounds, acquisition notification, inventory equipping, custom directional sprite frames, 1-hit zombie kills, 2-hit boss defeat, sprint restriction, and save/load persistence:
```sh
java -cp "bin;lib/*" main.ExecutionerSwordIntegrationTest
```

### 2. Main Menu Verification
Validates initial black screen, title typography, dual-button RPG layout, and gameplay launch transition:
```sh
java -cp "bin;lib/*" main.MainMenuTest
```

### 3. Pause Menu Verification
Validates ESC key toggling, state freezing, triple-button layout, resume without data loss, and restart session:
```sh
java -cp "bin;lib/*" main.PauseMenuTest
```

### 4. Water Dragon Boss & Region 3 Integration Test
Validates player water boundaries, twin Water Dragon spawning, pursuit pathfinding, fireballs, energy pulses, damage formulas, and save/load:
```sh
java -cp "bin;lib/*" main.WaterDragonIntegrationTest
```

### 5. World Integration Test
Validates region travel, gate requirements, cumulative progression score, fog-of-war encoding, and save/load:
```sh
java -cp "bin;lib/*" main.WorldIntegrationTest
```

### 6. MySQL Auth & Leaderboard Test
Validates database connectivity, user registration, authentication, password hashing, score logging, and top-rank leaderboard queries:
```sh
java -cp "bin;lib/*" main.AuthLeaderboardIntegrationTest
```

---

## License

This project is a personal game prototype developed by Ritabbrata Chakraborty. All rights reserved.
