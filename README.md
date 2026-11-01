# Dead End

Dead End is a Java Swing top-down 2D RPG prototype built with `Graphics2D`.
It includes tile-based exploration, directional player movement, running,
dodge/roll movement, unarmed combat, enemy pursuit, and health systems.

## Requirements

- Java 17 or newer
- Windows PowerShell or another shell capable of running the commands below

## Run

From `Dead_End`:

```powershell
javac --release 17 -d bin (Get-ChildItem -Path src -Recurse -Filter *.java).FullName
java --module-path bin -m Myproject/main.Window
```

Runtime assets are loaded from `res`, with fallback lookup support when the
game is launched from the parent directory.

## Controls

| Input | Action |
|---|---|
| W / A / S / D | Move |
| Shift | Run |
| J / Left mouse button | Punch |
| K / Right mouse button | Kick |
| Space / Q | Dodge |
| Ctrl | Roll |

Combat actions are edge-triggered, so holding a mouse button does not restart
an attack. Losing window focus clears held input.

## Player animation and combat

`animation.AnimationController` advances one selected animation with nanosecond
delta time. Player assets in `res/player` provide four-direction idle, walking,
running, dodge, punch/kick, and death animations. Punch and kick use directional
hitboxes, deal 10 and 20 damage respectively, and damage each monster only once
per attack. Player damage includes a short recovery/invulnerability window so
multiple monsters cannot permanently lock movement.

When health reaches zero, the player plays the four-frame directional death
animation once and holds the final fallen frame. A screen-fixed health bar is
shown in the bottom-left corner.

## Gameplay

- Undead monsters spawn around lake and road areas.
- Monsters detect and pursue the player.
- Monsters attack when they reach the player.
- Dodge and roll provide temporary damage immunity.
- Punches and kicks reduce monster health.
- Each monster displays a health bar and plays a death animation.
- Defeated monsters are replaced after a short respawn delay.

## Project layout

```text
src/    Java source code
res/    Runtime images and map resources
bin/    Compiled classes and copied runtime resources
```

## License

This project is a personal game prototype. Add a project license before
redistributing it.
