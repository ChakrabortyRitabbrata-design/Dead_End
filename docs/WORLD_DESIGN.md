# DEAD_END — world and progression

The active implementation remains Java 17 Swing/AWT. Existing movement, undead
combat and animation assets remain in use. The earlier map triplet and extracted
artwork are preserved; the new world uses separate `res/world` recipes.

## Geography

The river flows southwest from the mountains, through a poisoned basin, into the
Forgotten Kingdom. The atlas places the kingdom west, wilds in the centre and
dragon territory east. Two ancient ward arches control the passes.

| Region | Layout | Route and landmarks |
| --- | --- | --- |
| Forgotten Kingdom, 64 × 52 | Green river valley; wooded perimeter, western village, eastern lake | Willow Rest → Pilgrim Bridge → Bellwarden Ruins → Blue Ward. Optional cemetery and spring shrine form a second loop. |
| Cursed Wilds, 70 × 54 | Branched swamp channels and island causeways; teal vegetation and purple water | Reedwatch → Glasshouse research ruins → Sunken Temple → Ash Ward. Western refuge and eastern ruined castle provide alternative routes. |
| Dragon's Domain, 76 × 58 | Basalt mountain rim, lava fissures, fortress approaches, central cold lake | Cinder Landing → Broken Bastion → Crown Lake arena. Two approaches lead around the lake, leaving a broad southern combat apron. |

Region recipes separate water ellipses, forest clusters, roads, clearings,
landmarks, enemies, gates and spawn coordinates. Roads carve walkable crossings
over water. Deterministic placement makes saved enemy identities stable.

## Visual direction

Original Graphics2D terrain and environmental geometry: restrained pixel-scale
texture, layered tree silhouettes, pale riverbanks, timber crossings and masonry
landmarks. Each region has a separate palette and silhouette vocabulary. This is
a playable stylized implementation, not a claim of matching the reference art's
production detail. Existing character sprites remain unchanged. No screenshot
fragments or external assets are introduced.

## Progression and state

Score measures performance. Power is cumulative and never spent. Rewards and
gate thresholds live in `res/world/progression.properties`. Each persistent enemy
identity can award once. Unlocking a gate reveals the destination's atlas terrain;
local sight/exploration remains independent. F interacts at gates; both directions
use the same permanent unlock. Returning restores surviving enemies and fog.

Save/load uses an explicit versioned local properties file. F5 saves, F9 loads.
This allows players to choose when to replace a saved session. Gate transitions
use a fade, and map loading is checked before the active region is replaced.

## Integration boundary

The existing dragon is a proximity animation, with no damage or attack system.
World progression exposes `onDragonDefeated` for the combat workstream. This work
does not invent or replace the team's boss mechanics. Final victory remains
conditional on a real defeat notification.
