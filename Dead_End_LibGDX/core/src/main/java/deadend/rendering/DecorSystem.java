package deadend.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import deadend.map.MapData;
import deadend.world.TileManager;
import deadend.world.TileType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * DecorSystem — manages deterministic placement of all environmental objects.
 *
 * Object types:
 *   TREE_SMALL, TREE_MEDIUM, TREE_LARGE, TREE_ANCIENT
 *   ROCK_SMALL, ROCK_MEDIUM, ROCK_LARGE
 *   BUSH_0..4, FERN, GRASS_TUFT_0..2
 *   FALLEN_LOG
 *
 * Placement rules:
 *   - Only on TREE tiles (trees, rocks) or GRASS tiles (small decor)
 *   - Not on DIRT/WATER/SAND tiles (keep paths clear)
 *   - Min distance from DIRT (paths) for large objects = 1.5 tiles
 *   - Objects with SHADOW flag also draw a ground shadow before main sprite
 *   - All positions are seeded: same map → same decorations every launch
 *
 * Render pass:
 *   1. Ground shadows (opaque dark ovals, before tiles — NOT used here)
 *   2. Decor rendered AFTER ground tiles, sorted by Y (painter's algorithm)
 */
public class DecorSystem {

    // -----------------------------------------------------------------------
    // Decor entry
    // -----------------------------------------------------------------------

    public enum DecorKind {
        TREE_SMALL, TREE_MEDIUM, TREE_LARGE, TREE_ANCIENT,
        ROCK_SMALL, ROCK_MEDIUM, ROCK_LARGE,
        BUSH, FERN, GRASS_TUFT, FALLEN_LOG
    }

    public static class DecorEntry {
        public final float worldX, worldY;     // bottom-left of sprite
        public final float renderW, renderH;   // draw dimensions in world pixels
        public final Texture tex;
        public final boolean hasCollision;
        public final float colRadius;          // approximate circle radius for collision
        public final DecorKind kind;

        DecorEntry(float wx, float wy, float rw, float rh, Texture tex,
                   boolean col, float colR, DecorKind kind) {
            this.worldX = wx; this.worldY = wy;
            this.renderW = rw; this.renderH = rh;
            this.tex = tex;
            this.hasCollision = col; this.colRadius = colR;
            this.kind = kind;
        }
    }

    // -----------------------------------------------------------------------
    // Asset library
    // -----------------------------------------------------------------------

    private Texture[] trees;          // 4 sizes × 6 variants = indexed [variant * 4 + size]
    private Texture[] rocks;          // 3 sizes
    private Texture[] bushes;         // 5 variants
    private Texture   fern;
    private Texture   fallenLog;
    private Texture[] grassTufts;     // 3 variants

    // Terrain transition overlays: grass→dirt
    private Texture transGrassDirt_H, transGrassDirt_V;
    private Texture transGrassSand_H, transGrassSand_V;

    private static final long DECOR_SEED = 0xDEC04500L;
    private final List<DecorEntry> entries = new ArrayList<>();
    private boolean loaded = false;

    // -----------------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------------

    public void load() {
        if (loaded) return;

        // Trees: 6 colour variants × 4 sizes
        trees = new Texture[6 * 4];
        for (int v = 0; v < 6; v++)
            for (int s = 0; s < 4; s++)
                trees[v * 4 + s] = PixmapArtist.treeSprite(v, s);

        // Rocks: 3 sizes
        rocks = new Texture[]{
            PixmapArtist.rockSprite(0, 0xDEAD0011L),
            PixmapArtist.rockSprite(1, 0xDEAD0012L),
            PixmapArtist.rockSprite(2, 0xDEAD0013L)
        };

        // Bushes: 5 variants
        bushes = new Texture[5];
        for (int i = 0; i < 5; i++) bushes[i] = PixmapArtist.bushSprite(i);

        fern       = PixmapArtist.fernSprite();
        fallenLog  = PixmapArtist.fallenLogSprite();

        // Grass tufts: 3 variants
        grassTufts = new Texture[3];
        for (int i = 0; i < 3; i++) grassTufts[i] = PixmapArtist.grassTuftSprite(i);

        // Transition strips
        transGrassDirt_H = PixmapArtist.transitionEdge(
            PixmapArtist.GRASS_BASE, PixmapArtist.DIRT_BASE, true,  0x54CCAL);
        transGrassDirt_V = PixmapArtist.transitionEdge(
            PixmapArtist.GRASS_BASE, PixmapArtist.DIRT_BASE, false, 0x54CCBL);
        transGrassSand_H = PixmapArtist.transitionEdge(
            PixmapArtist.GRASS_BASE, PixmapArtist.SAND_BASE, true,  0xABBA1L);
        transGrassSand_V = PixmapArtist.transitionEdge(
            PixmapArtist.GRASS_BASE, PixmapArtist.SAND_BASE, false, 0xABBA2L);

        loaded = true;
    }

    // -----------------------------------------------------------------------
    // Map population
    // -----------------------------------------------------------------------

    /**
     * Populate decor entries for the given map.
     * Called once when the map loads — entries are stored and reused every frame.
     */
    public void populate(MapData map, TileManager tileM) {
        entries.clear();
        Random rng = new Random(DECOR_SEED ^ (long)map.levelId * 0x9E3779B9L);

        int W = map.mapWidth, H = map.mapHeight;

        for (int col = 1; col < W - 1; col++) {
            for (int row = 1; row < H - 1; row++) {
                int tileId = map.groundLayer[col][row];
                TileType type = TileType.byId(tileId);

                float wx = col * TileManager.TILE_PX;
                float wy = row * TileManager.TILE_PX;

                if (type == TileType.TREE) {
                    // Thick tree clusters: place a large/medium canopy sprite
                    // Only place in tree tiles that are part of forest groups
                    int surroundTrees = countNeighborType(map, col, row, TileType.TREE);
                    if (surroundTrees >= 3) {
                        // Forest cluster — large/ancient trees
                        if (rng.nextFloat() < 0.7f) {
                            int variant = hash(col, row, 0) % 6;
                            int size = (surroundTrees >= 6) ? 2 + (rng.nextFloat() < 0.25f ? 1 : 0) : 1;
                            Texture tex = trees[variant * 4 + size];
                            float dim = switch (size) {
                                case 0 -> 80; case 2 -> 144; case 3 -> 160; default -> 112;
                            };
                            float offset = (dim - TileManager.TILE_PX) / 2f;
                            entries.add(new DecorEntry(
                                wx - offset, wy - offset, dim, dim, tex,
                                true, dim * 0.2f, treeKind(size)));
                        }
                    } else if (surroundTrees >= 1) {
                        // Sparse trees — small/medium
                        if (rng.nextFloat() < 0.55f) {
                            int variant = hash(col, row, 1) % 6;
                            int size = rng.nextFloat() < 0.4f ? 0 : 1;
                            Texture tex = trees[variant * 4 + size];
                            float dim = size == 0 ? 80 : 112;
                            float offset = (dim - TileManager.TILE_PX) / 2f;
                            entries.add(new DecorEntry(
                                wx - offset, wy - offset, dim, dim, tex,
                                true, dim * 0.18f, treeKind(size)));
                        }
                    }
                } else if (type == TileType.GRASS) {
                    // Is this tile near a dirt path?
                    boolean nearDirt = isNearType(map, col, row, TileType.DIRT, 1);
                    boolean nearWater = isNearType(map, col, row, TileType.WATER, 1);

                    if (nearWater) {
                        // Reeds / grass tufts near water
                        if (rng.nextFloat() < 0.35f) {
                            int v = rng.nextInt(3);
                            entries.add(new DecorEntry(
                                wx + rng.nextInt(TileManager.TILE_PX - 16),
                                wy + rng.nextInt(TileManager.TILE_PX - 16),
                                24, 24, grassTufts[v], false, 0f, DecorKind.GRASS_TUFT));
                        }
                    } else if (nearDirt) {
                        // Grass/ferns at path edges
                        float roll = rng.nextFloat();
                        if (roll < 0.18f) {
                            // Fern
                            entries.add(new DecorEntry(
                                wx + 4 + rng.nextInt(TileManager.TILE_PX - 16),
                                wy + 4 + rng.nextInt(TileManager.TILE_PX - 16),
                                48, 48, fern, false, 0f, DecorKind.FERN));
                        } else if (roll < 0.30f) {
                            // Small bush at path edge
                            int v = rng.nextInt(5);
                            int bDim = 56 + v * 8;
                            entries.add(new DecorEntry(
                                wx - 4 + rng.nextInt(TileManager.TILE_PX + 8) - 4,
                                wy + rng.nextInt(TileManager.TILE_PX - 8),
                                bDim, bDim, bushes[v], false, 12f, DecorKind.BUSH));
                        } else if (roll < 0.42f) {
                            // Rock
                            int sz = rng.nextFloat() < 0.7f ? 0 : 1;
                            float rdim = sz == 0 ? 24 : 48;
                            entries.add(new DecorEntry(
                                wx + rng.nextInt(TileManager.TILE_PX - (int)rdim + 8),
                                wy + rng.nextInt(TileManager.TILE_PX - (int)rdim + 8),
                                rdim, rdim, rocks[sz], false, rdim * 0.4f, DecorKind.ROCK_SMALL));
                        } else if (roll < 0.50f) {
                            // Grass tuft
                            int v = rng.nextInt(3);
                            entries.add(new DecorEntry(
                                wx + rng.nextInt(TileManager.TILE_PX - 16),
                                wy + rng.nextInt(TileManager.TILE_PX - 16),
                                24, 24, grassTufts[v], false, 0f, DecorKind.GRASS_TUFT));
                        }
                    } else {
                        // Open grass — scattered natural decor
                        int surroundTrees = countNeighborType(map, col, row, TileType.TREE);
                        float density = 0.12f + surroundTrees * 0.04f;
                        float roll = rng.nextFloat();
                        if (roll < density * 0.3f) {
                            // Fallen log
                            entries.add(new DecorEntry(
                                wx + rng.nextInt(TileManager.TILE_PX - 24),
                                wy + TileManager.TILE_PX / 2 - 14 + rng.nextInt(12),
                                80, 28, fallenLog, false, 0f, DecorKind.FALLEN_LOG));
                        } else if (roll < density * 0.6f) {
                            // Bush
                            int v = rng.nextInt(5);
                            int bDim = 56 + v * 8;
                            entries.add(new DecorEntry(
                                wx + rng.nextInt(TileManager.TILE_PX - 24),
                                wy + rng.nextInt(TileManager.TILE_PX - 24),
                                bDim, bDim, bushes[v], false, 10f, DecorKind.BUSH));
                        } else if (roll < density * 0.9f) {
                            // Fern
                            entries.add(new DecorEntry(
                                wx + rng.nextInt(TileManager.TILE_PX - 16),
                                wy + rng.nextInt(TileManager.TILE_PX - 16),
                                48, 48, fern, false, 0f, DecorKind.FERN));
                        } else if (roll < density * 1.1f) {
                            // Rock cluster
                            int sz = rng.nextFloat() < 0.5f ? 1 : 2;
                            float rdim = sz == 1 ? 48 : 80;
                            entries.add(new DecorEntry(
                                wx + rng.nextInt(TileManager.TILE_PX - 24),
                                wy + rng.nextInt(TileManager.TILE_PX - 24),
                                rdim, rdim, rocks[sz],
                                sz == 2, rdim * 0.35f, sz == 2 ? DecorKind.ROCK_LARGE : DecorKind.ROCK_MEDIUM));
                        } else if (roll < density * 1.3f) {
                            // Grass tuft
                            int v = rng.nextInt(3);
                            entries.add(new DecorEntry(
                                wx + rng.nextInt(TileManager.TILE_PX - 12),
                                wy + rng.nextInt(TileManager.TILE_PX - 12),
                                24, 24, grassTufts[v], false, 0f, DecorKind.GRASS_TUFT));
                        }
                    }
                } else if (type == TileType.SAND) {
                    // Rock or pebble on sand/cemetery area
                    if (rng.nextFloat() < 0.15f) {
                        entries.add(new DecorEntry(
                            wx + rng.nextInt(TileManager.TILE_PX - 20),
                            wy + rng.nextInt(TileManager.TILE_PX - 20),
                            24, 24, rocks[0], false, 0f, DecorKind.ROCK_SMALL));
                    }
                }
            }
        }

        // Sort by Y ascending so higher objects render first (painter's algorithm)
        entries.sort(Comparator.comparingDouble(e -> e.worldY));
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    /**
     * Draw all ground-layer decor (tufts, ferns, logs — below entities).
     * Call between tile render and entity render.
     */
    public void drawGroundDecor(SpriteBatch batch, float camX, float camY, float vpW, float vpH) {
        for (DecorEntry e : entries) {
            if (e.kind == DecorKind.GRASS_TUFT || e.kind == DecorKind.FERN || e.kind == DecorKind.FALLEN_LOG) {
                if (!visible(e, camX, camY, vpW, vpH)) continue;
                batch.draw(e.tex, e.worldX, e.worldY, e.renderW, e.renderH);
            }
        }
    }

    /**
     * Draw above-ground decor (trees, bushes, rocks — above entities at same Y).
     * Should be called in the Y-sorted order so entities and decor interleave correctly.
     * For simplicity we draw all above-ground decor AFTER entities.
     */
    public void drawElevatedDecor(SpriteBatch batch, float camX, float camY, float vpW, float vpH) {
        for (DecorEntry e : entries) {
            if (e.kind == DecorKind.GRASS_TUFT || e.kind == DecorKind.FERN || e.kind == DecorKind.FALLEN_LOG) continue;
            if (!visible(e, camX, camY, vpW, vpH)) continue;
            batch.setColor(Color.WHITE);
            batch.draw(e.tex, e.worldX, e.worldY, e.renderW, e.renderH);
        }
    }

    /** Render transition edge overlays on tiles adjacent to different terrain */
    public void drawTransitionOverlays(SpriteBatch batch, MapData map,
                                       float camX, float camY, float vpW, float vpH) {
        int colStart = Math.max(0, (int)(camX / TileManager.TILE_PX) - 1);
        int rowStart = Math.max(0, (int)(camY / TileManager.TILE_PX) - 1);
        int colEnd   = Math.min(map.mapWidth,  (int)((camX + vpW) / TileManager.TILE_PX) + 2);
        int rowEnd   = Math.min(map.mapHeight, (int)((camY + vpH) / TileManager.TILE_PX) + 2);

        for (int c = colStart; c < colEnd; c++) {
            for (int r = rowStart; r < rowEnd; r++) {
                TileType here = TileType.byId(map.groundLayer[c][r]);
                if (here != TileType.DIRT && here != TileType.SAND) continue;

                float wx = c * TileManager.TILE_PX;
                float wy = r * TileManager.TILE_PX;

                // Check horizontal and vertical neighbours
                // Right neighbour is grass → draw grass→dirt transition
                if (c + 1 < map.mapWidth) {
                    TileType right = TileType.byId(map.groundLayer[c+1][r]);
                    if (right == TileType.GRASS) {
                        batch.setColor(1, 1, 1, 0.65f);
                        batch.draw(here == TileType.DIRT ? transGrassDirt_H : transGrassSand_H,
                            wx, wy, TileManager.TILE_PX, TileManager.TILE_PX);
                    }
                }
                if (r + 1 < map.mapHeight) {
                    TileType above = TileType.byId(map.groundLayer[c][r+1]);
                    if (above == TileType.GRASS) {
                        batch.setColor(1, 1, 1, 0.65f);
                        batch.draw(here == TileType.DIRT ? transGrassDirt_V : transGrassSand_V,
                            wx, wy, TileManager.TILE_PX, TileManager.TILE_PX);
                    }
                }
            }
        }
        batch.setColor(Color.WHITE);
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    public List<DecorEntry> getEntries() { return entries; }

    // -----------------------------------------------------------------------
    // Disposal
    // -----------------------------------------------------------------------

    public void dispose() {
        if (!loaded) return;
        for (Texture t : trees)     if (t != null) t.dispose();
        for (Texture t : rocks)     if (t != null) t.dispose();
        for (Texture t : bushes)    if (t != null) t.dispose();
        for (Texture t : grassTufts) if (t != null) t.dispose();
        if (fern != null)      fern.dispose();
        if (fallenLog != null) fallenLog.dispose();
        if (transGrassDirt_H != null) transGrassDirt_H.dispose();
        if (transGrassDirt_V != null) transGrassDirt_V.dispose();
        if (transGrassSand_H != null) transGrassSand_H.dispose();
        if (transGrassSand_V != null) transGrassSand_V.dispose();
        loaded = false;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private boolean visible(DecorEntry e, float camX, float camY, float vpW, float vpH) {
        return e.worldX + e.renderW > camX &&
               e.worldX < camX + vpW &&
               e.worldY + e.renderH > camY &&
               e.worldY < camY + vpH;
    }

    private int countNeighborType(MapData map, int col, int row, TileType type) {
        int count = 0;
        for (int dc = -1; dc <= 1; dc++)
            for (int dr = -1; dr <= 1; dr++) {
                int nc = col + dc, nr = row + dr;
                if (nc >= 0 && nc < map.mapWidth && nr >= 0 && nr < map.mapHeight)
                    if (TileType.byId(map.groundLayer[nc][nr]) == type) count++;
            }
        return count;
    }

    private boolean isNearType(MapData map, int col, int row, TileType type, int radius) {
        for (int dc = -radius; dc <= radius; dc++)
            for (int dr = -radius; dr <= radius; dr++) {
                int nc = col + dc, nr = row + dr;
                if (nc >= 0 && nc < map.mapWidth && nr >= 0 && nr < map.mapHeight)
                    if (TileType.byId(map.groundLayer[nc][nr]) == type) return true;
            }
        return false;
    }

    private int hash(int x, int y, int salt) {
        int h = x * 73856093 ^ y * 19349663 ^ salt * 83492791;
        return Math.abs(h);
    }

    private static DecorKind treeKind(int size) {
        return switch (size) {
            case 0 -> DecorKind.TREE_SMALL;
            case 2 -> DecorKind.TREE_LARGE;
            case 3 -> DecorKind.TREE_ANCIENT;
            default -> DecorKind.TREE_MEDIUM;
        };
    }
}
