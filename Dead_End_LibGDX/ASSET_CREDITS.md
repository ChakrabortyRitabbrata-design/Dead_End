# DEAD_END — Asset Credits

## Procedurally Generated Art (Original — This Project)

All terrain textures, environmental objects, and visual effects created in this session
are **original procedurally generated artwork** authored by the `PixmapArtist.java` class.
No external image files are required for terrain or environment objects.

| Asset Class | Generator | Resolution | Description |
|---|---|---|---|
| Grass (6 variants) | `PixmapArtist.grassVariant(0..5)` | 96×96 RGBA | Value-noise base, blade strokes, leaves, pebbles, flowers |
| Dirt path (4 variants) | `PixmapArtist.dirtVariant(0..3)` | 96×96 RGBA | Layered soil noise, erosion lines, footprints, pebbles |
| Sand | `PixmapArtist.sandTexture()` | 96×96 RGBA | Wind ripples, specular dots, shell pebbles |
| Water | `PixmapArtist.waterTexture()` | 96×96 RGBA | Deep/mid colour blend, foam highlights, shimmer animation |
| Stone wall | `PixmapArtist.wallTexture()` | 96×96 RGBA | Block mortar pattern, moss patches, cracks |
| Tree canopy (6 colour × 4 sizes) | `PixmapArtist.treeSprite(v, s)` | 80–160px RGBA | Organic blob canopy, dappled light, shadow, trunk hint |
| Rock (small/medium/large) | `PixmapArtist.rockSprite(sz, seed)` | 24–80px RGBA | Directional lighting, moss, highlight |
| Bush (5 variants) | `PixmapArtist.bushSprite(v)` | 56–88px RGBA | Multi-blob canopy, berries (variants 2–3) |
| Fern | `PixmapArtist.fernSprite()` | 48×48 RGBA | Frond strokes with leaflets |
| Fallen log | `PixmapArtist.fallenLogSprite()` | 80×28 RGBA | Bark noise, wood-grain rings, shadow |
| Grass tuft (3 variants) | `PixmapArtist.grassTuftSprite(v)` | 24×24 RGBA | Blade strokes, depth shading |
| Terrain transitions (4) | `PixmapArtist.transitionEdge(...)` | 96×96 RGBA | Noise-warped smooth-step blend strips |

**All procedural art is CC0 / original project artwork. No third-party images required.**

---

## Reused from Existing Swing Project

| Asset | Original Path | LibGDX Path | Status |
|---|---|---|---|
| Player walk spritesheet | `res/player/player_walk_strip.png` | `assets/player/` | ✅ Reused |
| Monster spritesheet | `res/player/Pixel-Art Undead Monster Spritesheet.png` | `assets/enemies/` | ✅ Reused |
| Combat spritesheet | `res/player/Pixel Art RPG Combat Sprite Sheet.png` | — | Pending |
| Dragon spritesheet | `res/Dragon/Dragon_Sprite_Sheet.png` | — | Deferred (Level 3) |

### Original Swing tile images (no longer used for terrain rendering)

The following 16×16 PNG files were copied from the Swing project but are **no longer
used** — all terrain is rendered from procedural textures generated at startup:

| File | Status |
|---|---|
| `assets/textures/tiles/grass.png` | Superseded by procedural grass variants |
| `assets/textures/tiles/earth.png` | Superseded by procedural dirt variants |
| `assets/textures/tiles/sand.png` | Superseded by procedural sand texture |
| `assets/textures/tiles/water.png` | Superseded by procedural water texture |
| `assets/textures/tiles/wall.png` | Superseded by procedural wall texture |
| `assets/textures/tiles/tree.png` | Superseded by procedural tree sprites |

These files may be deleted without affecting the LibGDX build.
Source/license of the original 16×16 tiles: unknown — verify before distribution.

---

## Libraries

| Library | Version | License |
|---|---|---|
| LibGDX | 1.12.1 | Apache 2.0 |
| LWJGL3 | via LibGDX | BSD 3-Clause |
| Java | 17 | GPL with Classpath Exception |

---

## Notes

- All procedurally generated textures use deterministic seeding — same result every launch.
- No runtime random variation in terrain appearance.
- Tree sprites use organic blob algorithm with per-variant colour palette.
- Terrain transitions use noise-warped smooth-step blending for organic edges.
- All textures disposed correctly on screen/game close.
