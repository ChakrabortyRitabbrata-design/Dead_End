package main;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

/**
 * Offline tool — generates the three Level 1 map files:
 *   res/map/level1_ground.txt
 *   res/map/level1_collision.txt
 *   res/map/level1_meta.txt
 *
 * Run once from project root.  Output is committed to res/map/ and read by MapLoader at runtime.
 *
 * Tile ID palette used (all have existing or placeholder artwork):
 *   0 grass  1 wall  2 water  3 dirt_path  4 tree  5 sand
 *
 * New tile IDs (6-19) are architecturally supported but not used here yet
 * since their artwork is pending from Arijit.
 */
public class MapGenerator2 {

    static final int W = 100;
    static final int H = 100;

    // Tile constants
    static final int GRASS  = 0;
    static final int WALL   = 1;
    static final int WATER  = 2;
    static final int DIRT   = 3;
    static final int TREE   = 4;
    static final int SAND   = 5;

    static int[][] ground    = new int[W][H];
    static int[][] collision = new int[W][H];

    static Random rng = new Random(42L); // seeded for reproducibility

    public static void main(String[] args) throws IOException {
        buildGround();
        buildCollision();
        writeGround("res/map/level1_ground.txt");
        writeCollision("res/map/level1_collision.txt");
        writeMeta("res/map/level1_meta.txt");
        System.out.println("Level 1 map files written to res/map/");
    }

    // =========================================================================
    // Ground layer
    // =========================================================================

    static void buildGround() {
        // --- Phase 1: fill everything with TREE ---
        fill(0, 0, W, H, TREE);

        // --- Phase 2: southern open grassland (player spawn area) ---
        fillCircleSmooth(49, 95, 32, GRASS);   // large circle centred near south
        // Widen to cover cols 5-94, rows 62-99
        for (int c = 5; c < 95; c++)
            for (int r = 68; r < H; r++)
                ground[c][r] = GRASS;

        // --- Phase 3: central Forgotten Village clearing ---
        fillCircleSmooth(42, 50, 14, GRASS);

        // --- Phase 4: northern forest clearings (organic blobs) ---
        fillCircleSmooth(8,  20,  8, GRASS);  // Hospital area
        fillCircleSmooth(22, 33,  6, GRASS);  // Old Shrine
        fillCircleSmooth(55, 60,  9, GRASS);  // Abandoned Homes
        fillCircleSmooth(12, 67,  6, GRASS);  // Safehouse

        // --- Phase 5: River (runs east-west through rows 25-28) ---
        buildRiver();

        // --- Phase 6: Stone Bridge (cols 44-54, rows 25-28 — replaces water) ---
        for (int c = 44; c <= 54; c++)
            for (int r = 25; r <= 28; r++)
                ground[c][r] = DIRT;

        // --- Phase 7: Sand shores flanking the river ---
        for (int c = 0; c < W; c++) {
            setIfTree(c, 24, SAND);
            setIfTree(c, 29, SAND);
        }

        // --- Phase 8: Dirt path network ---
        buildPaths();

        // --- Phase 9: Special biome patches ---
        // Cemetery — sand/dead ground patch (NE, around col 66, row 10)
        fillCircleSmooth(66, 10, 9, SAND);
        // Crystal Grove (far NE, col 80, row 15) — sand patch
        fillCircleSmooth(80, 15, 7, SAND);
        // Ruined Keep area (col 86, row 9) — earth/stone floor
        fillCircleSmooth(86, 10, 10, DIRT);
        // Hospital building outline
        buildBuilding(3, 15, 12, 9);
        // Safehouse building outline
        buildBuilding(9, 64, 9, 8);

        // --- Phase 10: Thin forest edges (organic transition) ---
        thinForestEdges();
    }

    // --- River ---

    static void buildRiver() {
        // River is ~3 tiles wide (rows 25-28) with a horizontal wobble ±1
        int[] rowOffsets = {0, 0, 1, 1, 0, -1, -1, 0, 0, 1, 0, 0, -1, 0, 0, 1, 0};
        int segment = 0;
        for (int c = 0; c < W; c++) {
            if (c % 6 == 0) segment = (segment + 1) % rowOffsets.length;
            int baseRow = 26 + rowOffsets[segment];
            for (int rowOff = -1; rowOff <= 1; rowOff++) {
                int r = baseRow + rowOff;
                if (r >= 0 && r < H) ground[c][r] = WATER;
            }
        }
        // Wider water at bridge approaches
        for (int r = 24; r <= 29; r++) {
            for (int c = 30; c < 44; c++) if (ground[c][r] == TREE) ground[c][r] = WATER;
            for (int c = 55; c < 70; c++) if (ground[c][r] == TREE) ground[c][r] = WATER;
        }
    }

    // --- Paths ---

    static void buildPaths() {
        // Main spine: player spawn (49,90) winding north to village (42,50)
        windingPath(49, 90, 42, 50, 2);
        // Village to bridge (42,50) → (47,27)
        windingPath(42, 50, 47, 27, 2);
        // Bridge north → Cemetery (66,10) → Keep (86,10)
        windingPath(47, 22, 66, 10, 2);
        windingPath(66, 10, 86, 10, 2);
        // Village west → Shrine (22,33) → Hospital (8,20)
        windingPath(42, 50, 22, 33, 2);
        windingPath(22, 33,  8, 20, 2);
        // Hospital → Ruined Keep (hidden forest path)
        windingPath(8, 20, 86, 10, 2);
        // Village → Crystal Grove (80,15)
        windingPath(50, 45, 80, 15, 2);
        // Safehouse (12,67) → village (42,50)
        windingPath(12, 67, 42, 50, 2);
        // Village → Abandoned Homes (55,60)
        windingPath(42, 50, 55, 60, 2);
    }

    /**
     * Draws a winding 2-tile-wide dirt path from (c0,r0) to (c1,r1),
     * deviating ±1 tile randomly at each step.
     */
    static void windingPath(int c0, int r0, int c1, int r1, int width) {
        int c = c0, r = r0;
        int steps = Math.abs(c1 - c0) + Math.abs(r1 - r0);
        for (int i = 0; i < steps * 2; i++) {
            // Paint the path cell
            for (int dc = 0; dc < width; dc++)
                for (int dr = 0; dr < width; dr++)
                    safePaint(c + dc, r + dr, DIRT);

            // Move toward target with random wobble
            int dC = Integer.signum(c1 - c);
            int dR = Integer.signum(r1 - r);
            if (c == c1) dC = 0;
            if (r == r1) dR = 0;
            if (dC == 0 && dR == 0) break;

            // Probabilistic wobble
            if (dC != 0 && dR != 0) {
                if (rng.nextBoolean()) dC = 0; else dR = 0;
            }
            int wobble = rng.nextInt(3) - 1; // -1, 0, +1
            if (dC != 0) {
                c += dC;
                r = clamp(r + wobble, 1, H - 2);
            } else {
                r += dR;
                c = clamp(c + wobble, 1, W - 2);
            }
        }
    }

    // --- Buildings ---

    /** Draws a rectangular building outline with WALL tiles and DIRT interior. */
    static void buildBuilding(int col, int row, int w, int h) {
        // Interior
        for (int c = col + 1; c < col + w - 1; c++)
            for (int r = row + 1; r < row + h - 1; r++)
                safePaint(c, r, DIRT);
        // Walls
        for (int c = col; c < col + w; c++) {
            safePaint(c, row,         WALL);
            safePaint(c, row + h - 1, WALL);
        }
        for (int r = row + 1; r < row + h - 1; r++) {
            safePaint(col,         r, WALL);
            safePaint(col + w - 1, r, WALL);
        }
        // Doorway (south centre)
        int doorCol = col + w / 2;
        safePaint(doorCol, row + h - 1, DIRT);
    }

    // --- Forest thinning ---

    /**
     * Replaces isolated TREE tiles adjacent to GRASS with GRASS ~40% of the time,
     * creating organic forest edges instead of hard block boundaries.
     */
    static void thinForestEdges() {
        int[][] copy = deepCopy(ground);
        for (int c = 1; c < W - 1; c++) {
            for (int r = 1; r < H - 1; r++) {
                if (copy[c][r] != TREE) continue;
                int grassNeighbours = 0;
                if (copy[c-1][r] == GRASS) grassNeighbours++;
                if (copy[c+1][r] == GRASS) grassNeighbours++;
                if (copy[c][r-1] == GRASS) grassNeighbours++;
                if (copy[c][r+1] == GRASS) grassNeighbours++;
                if (grassNeighbours >= 1 && rng.nextFloat() < 0.4f) {
                    ground[c][r] = GRASS;
                }
            }
        }
    }

    // =========================================================================
    // Collision layer
    // =========================================================================

    static void buildCollision() {
        // Collision defaults to 0 (walkable) everywhere.
        // Tile.collision flags handle the rest (water, tree, wall).
        // The collision layer adds overrides for decorative objects on walkable tiles.
        //
        // Currently Level 1 uses no additional collision-layer overrides beyond the tile types.
        // Object placements in meta.txt define object hitboxes independently.
        // This layer can be authored by Arijit using the collision.txt format.
        for (int c = 0; c < W; c++)
            for (int r = 0; r < H; r++)
                collision[c][r] = 0;
    }

    // =========================================================================
    // File writers
    // =========================================================================

    static void writeGround(String path) throws IOException {
        writeGrid(path, ground);
    }

    static void writeCollision(String path) throws IOException {
        writeGrid(path, collision);
    }

    static void writeGrid(String path, int[][] grid) throws IOException {
        new File(path).getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            for (int r = 0; r < H; r++) {
                StringBuilder sb = new StringBuilder();
                for (int c = 0; c < W; c++) {
                    sb.append(grid[c][r]);
                    if (c < W - 1) sb.append(' ');
                }
                pw.println(sb);
            }
        }
    }

    static void writeMeta(String path) throws IOException {
        new File(path).getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("# ================================================================");
            pw.println("# DEAD_END Level 1 Metadata — The Forgotten Kingdom");
            pw.println("# ================================================================");
            pw.println();
            pw.println("LEVEL_ID       1");
            pw.println("LEVEL_NAME     The Forgotten Kingdom");
            pw.println("MAP_WIDTH      100");
            pw.println("MAP_HEIGHT     100");
            pw.println("TILE_SIZE      48");
            pw.println();
            pw.println("PLAYER_SPAWN   49  90");
            pw.println();
            pw.println("# Dragon: disabled on Level 1 until confirmed by team");
            pw.println("# BOSS_DRAGON  48  25");
            pw.println();
            pw.println("SCORE_THRESHOLD  0");
            pw.println("NEXT_LEVEL       2");
            pw.println();
            pw.println("# --- Named locations ---");
            pw.println("LOCATION  \"Ancient Forest\"        2   2  28  52");
            pw.println("LOCATION  \"Forgotten Village\"    33  42  22  20");
            pw.println("LOCATION  \"Cemetery\"             58   3  18  20");
            pw.println("LOCATION  \"Hospital\"              3  14  14  12");
            pw.println("LOCATION  \"Stone Bridge\"         44  23  12   8");
            pw.println("LOCATION  \"Crystal Grove\"        74  10  14  12");
            pw.println("LOCATION  \"Old Shrine\"           18  29   8   8");
            pw.println("LOCATION  \"Safehouse\"             8  62  10  10");
            pw.println("LOCATION  \"Abandoned Homes\"      48  55  20  12");
            pw.println("LOCATION  \"Ruined Keep\"          78   3  20  20");
            pw.println();
            pw.println("# --- Enemy spawn regions (centerCol centerRow radius maxCount respawnDelay) ---");
            pw.println("ENEMY_REGION   14  22  10  4  0");
            pw.println("ENEMY_REGION   68  12   8  3  0");
            pw.println("ENEMY_REGION   86  12   9  5  0");
            pw.println("ENEMY_REGION   42  55  10  4  0");
            pw.println("ENEMY_REGION   55  62   8  3  0");
            pw.println();
            pw.println("# --- Object placements (TypeName col row) ---");
            pw.println("# Houses in Forgotten Village");
            pw.println("OBJECT  OBJ_HOUSE  38  46");
            pw.println("OBJECT  OBJ_HOUSE  44  44");
            pw.println("OBJECT  OBJ_HOUSE  36  52");
            pw.println("OBJECT  OBJ_HOUSE  44  54");
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    static void fill(int c0, int r0, int cEnd, int rEnd, int tile) {
        for (int c = c0; c < cEnd; c++)
            for (int r = r0; r < rEnd; r++)
                ground[c][r] = tile;
    }

    /** Clears a circle of radius r to the given tile, with soft edges. */
    static void fillCircleSmooth(int centerC, int centerR, int radius, int tile) {
        for (int c = centerC - radius - 1; c <= centerC + radius + 1; c++) {
            for (int r = centerR - radius - 1; r <= centerR + radius + 1; r++) {
                if (c < 0 || c >= W || r < 0 || r >= H) continue;
                double dist = Math.hypot(c - centerC, r - centerR);
                if (dist <= radius) {
                    ground[c][r] = tile;
                } else if (dist <= radius + 1.5 && rng.nextFloat() < 0.5f) {
                    // Soft edge — 50% chance to include border tiles
                    ground[c][r] = tile;
                }
            }
        }
    }

    static void safePaint(int c, int r, int tile) {
        if (c >= 0 && c < W && r >= 0 && r < H) ground[c][r] = tile;
    }

    static void setIfTree(int c, int r, int tile) {
        if (c >= 0 && c < W && r >= 0 && r < H && ground[c][r] == TREE) ground[c][r] = tile;
    }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    static int[][] deepCopy(int[][] src) {
        int[][] copy = new int[src.length][src[0].length];
        for (int i = 0; i < src.length; i++) copy[i] = src[i].clone();
        return copy;
    }
}
