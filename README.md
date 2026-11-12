# Dead End

Dead End is a Java Swing top-down 2D RPG prototype built with `Graphics2D`.
The project includes tile-based exploration, animated player movement and
combat, directional dodge rolls, and hostile undead monsters with health bars,
pursuit, attacks, blocking, and death animations.

## Requirements

- Java 17 or newer
- Windows PowerShell or another shell capable of running the commands below

## Run

The connected world now uses `res/world/region1.txt` through `region3.txt`.
Previous `res/map` files and artwork are retained. See
[world design](docs/WORLD_DESIGN.md) and [verification report](docs/WORLD_TEST_REPORT.md).

macOS/Linux, from the repository root:

```sh
javac --release 17 -d bin $(find src -name '*.java')
java -cp bin:res main.Window
```

From the project directory:

```powershell
javac --release 17 -d bin (Get-ChildItem -Path src -Recurse -Filter *.java).FullName
java -cp "bin;res" main.Window
```

The game loads assets from the `res` directory when running from the project
root. The compiled classes are written to `bin`.

On Windows, the game can also be compiled and launched with:

```powershell
cmd /c ".\run.bat"
```

## Controls

| Input | Action |
|---|---|
| W | Move up |
| A | Move left |
| S | Move down |
| D | Move right |
| J / Left mouse button | Punch |
| E / K | Kick |
| Space | Attack with the equipped weapon |
| Q | Dodge in the current movement direction |
| Ctrl | Roll |
| Shift | Run while moving |
| R / Right mouse button | Block / guard with an equipped shield |
| F | Interact with a nearby ward / travel |
| Tab | Open / close inventory |
| 1–9 | Select a hotbar slot; weapon selections equip to the main hand |
| Escape | Close the inventory or world atlas |
| M | Open / close world atlas |
| Scroll / drag / Home | Atlas zoom / pan / fit all |
| F5 / F9 | Save / load `saves/world.properties` |

Combat actions are edge-triggered, so holding an attack key does not restart an
animation. The player uses direction-specific idle, walk, run, dodge, roll,
punch, kick, hurt and death animations. Losing window focus clears held input.
Inventory items can be dragged between the main inventory, hotbar and equipment
slots. The gameplay hotbar remains visible during exploration.

## Gameplay

- Undead monsters spawn around lake and road areas.
- Monsters detect and pursue the player.
- Monsters attack when they reach the player.
- Blocking and dodging prevent monster attack damage.
- Punches and kicks reduce monster health.
- Each monster displays a health bar and plays a death animation.
- Player health regenerates at 6 HP per second after 2 seconds without taking
  damage.
- After death, press **R** to respawn at the starting position with full health.
- Authored regions contain persistent enemy encounters; defeated enemies stay
  defeated. Reward values and gate requirements are configurable in
  `res/world/progression.properties`. Power is cumulative and never spent.
- The dragon arena uses the existing proximity animation. Boss combat is not yet
  implemented; its eventual defeat should call `LevelManager.onDragonDefeated()`.

## Verification

```sh
java -Djava.awt.headless=true -cp bin:res main.WorldIntegrationTest
```

Checks navigation, rewards, gates, region state, save/load, fades and the boss
callback. Creates actual Swing render captures in `out/world-checks/`.

The merge check also exercises player animation states, directional combat
hitboxes, monster animation rendering and the health HUD:

```sh
java -Djava.awt.headless=true -cp bin:res main.AnimationMergeIntegrationTest
```

## Project layout

```text
src/    Java source code
res/    Runtime images and map resources
bin/    Compiled classes and copied runtime resources
```

## License

This project is a personal game prototype. Add a project license before
redistributing it.
