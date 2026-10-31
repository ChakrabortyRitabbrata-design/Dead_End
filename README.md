# Dead End

Dead End is a Java Swing top-down 2D RPG prototype built with `Graphics2D`.
The project includes tile-based exploration, animated player movement and
combat, directional dodge rolls, and hostile undead monsters with health bars,
pursuit, attacks, blocking, and death animations.

## Requirements

- Java 17 or newer
- Windows PowerShell or another shell capable of running the commands below

## Run

From the project directory:

```powershell
javac --release 17 -d bin (Get-ChildItem -Path src -Recurse -Filter *.java).FullName
java --module-path bin -m Myproject/main.Window
```

The game loads assets from the `res` directory when running from the project
root. The compiled classes are written to `bin`.

## Controls

| Input | Action |
|---|---|
| W | Move up |
| A | Move left |
| S | Move down |
| D | Move right |
| Left mouse button | Punch |
| E | Kick |
| Q | Dodge roll in the current movement direction |
| Right mouse button | Block / guard |

Combat actions are edge-triggered where appropriate, so holding an attack key
does not repeatedly start attacks. Losing window focus clears held input.

## Gameplay

- Undead monsters spawn around lake and road areas.
- Monsters detect and pursue the player.
- Monsters attack when they reach the player.
- Blocking and dodging prevent monster attack damage.
- Punches and kicks reduce monster health.
- Each monster displays a health bar and plays a death animation.
- Defeated monsters are continuously replaced after a short respawn delay.

## Project layout

```text
src/    Java source code
res/    Runtime images and map resources
bin/    Compiled classes and copied runtime resources
```

## License

This project is a personal game prototype. Add a project license before
redistributing it.
