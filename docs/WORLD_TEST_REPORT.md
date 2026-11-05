# World implementation and verification — 2026-11-01

## Implemented

* Three authored region recipes with different dimensions, terrain, locations,
  enemy territories and two reciprocal gate connections.
* Original Swing/AWT environment renderer, neighbour-aware road shoulders and
  shoreline edges, timber crossings, deterministic vegetation and mountain forms.
* Separate terrain and collision arrays; house walls, water and forest barriers.
  Existing enemy movement now respects these barriers. Its pursuit strategy is
  otherwise retained; it does not pathfind around obstacles.
* Cumulative, configurable Power independent from Score; once-only rewards keyed
  by stable enemy identity. Finite encounters provide 2,800 Power in region one
  and 5,100 additional Power in region two with the default configuration.
* Nearby gate unlocks and F interaction, persistent unlocks, animated energy,
  notifications, fades and return travel.
* Three-state fog retained per region; atlas with all region positions, locked
  region concealment, reveal on unlock, pan, zoom, Home fit and location labels
  at higher zoom. Local minimap honours fog and includes enemy/gate/landmark markers.
* Versioned F5/F9 save/load for Score, Power, unlocks, current region, position,
  health, visited regions, explored tiles and surviving enemy positions/health.
  Defeated enemy rewards remain claimed. Save replacement is atomic where the
  filesystem supports it. Invalid saves are rejected before state replacement.
* Camera clamping; Swing updates and rendering share the EDT to prevent region
  replacement from racing the paint loop.

## Verified

Java 17 compilation and `git diff --check` pass. `WorldIntegrationTest` verifies:

* Flood-fill reachability of all landmark approaches and gates in all regions.
* Walkable transition arrivals and reciprocal gate routes.
* Expected enemy encounter counts and sufficient default Power budgets.
* Actual monster death rewards, duplicate prevention, independent totals and
  non-consuming permanent unlocks.
* Movement input through the game update loop.
* Fog demotion after distant movement and exploration serialization.
* Region return state, defeated enemy persistence, all-region save/load.
* Fade completion, valid arrivals and invalid-save rejection.
* Victory through the boss callback, never merely from accumulated Power.

Rendered and visually inspected actual `GamePanel.paint` output:

* `out/world-checks/level1-start.png`
* `out/world-checks/level1-gate.png`
* `out/world-checks/level2-temple.png`
* `out/world-checks/level3-mountains.png`
* `out/world-checks/level3-arena.png`
* `out/world-checks/atlas-locked.png`
* `out/world-checks/atlas-second-region.png`
* `out/world-checks/atlas-unlocked.png`

The Swing GUI process launched without startup errors. The desktop-control tool
listed the Java process but rejected both its app name and identifier, so live
keyboard/mouse operation could not be verified through desktop automation.
The images above are headless Swing render captures, not desktop screenshots.

## Partial / not implemented

These are playable world/progression prototypes, not finished reference-quality
pixel-art maps. The environment art uses deliberately simple procedural shapes;
additional art production and playtesting are needed for the requested polish.

The inherited dragon has proximity animation only. Its arena and victory callback
are integrated, but damage, attacks and a playable final boss fight are not
implemented. No end-to-end combat victory is claimed.

No interactive loot/object system exists; landmarks are static and reconstruct
deterministically. Persistent state for future mutable objects must be added with
that system. Active combat animation timers and dragon hover phase are not saved.
Poison/lava have blocking terrain, not damage-over-time mechanics. Forest collision
is tile-based, not individual sub-tile trunk geometry. Tall environment art is
sorted relative to the player, not every enemy separately.

Existing uncommitted map/tile files and `DecorRenderer` were preserved. They are
not newly created deliverables from this change. No external asset licences are
asserted; see `ASSET_CREDITS.md`.
