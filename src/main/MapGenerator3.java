package main;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

/**
 * Generates the redesigned 65×65 Level 1 map files:
 *   res/map/level1_ground.txt
 *   res/map/level1_collision.txt
 *   res/map/level1_meta.txt
 *
 * Design goals:
 *   - Compact but complete RPG environment.
 *   - Organic winding paths, multiple routes.
 *   - Natural forest clusters with walkable gaps (not a wall of trees).
 *   - River with bridge crossing.
 *   - All named locations: Ancient Forest, Village, Cemetery, Hospital,
 *     Stone Bridge, Crystal Grove, Old Shrine, Safehouse, Abandoned Homes,
 *     Ruined Keep, Level 2 Gate.
 *   - Clear player spawn in south-centre on walkable dirt path.
 *   - Gate near north-east with open approach path.
 *
 * Tile ID palette (matching TileManager):
 *   0 = grass   1 = wall   2 = water  3 = dirt/path
 *   4 = tree    5 = sand   (6-19 reserved, not used yet)
 *
 * Run once from project root to regenerate the map files.
 */
public class MapGenerator3 {

    static final int W = 65;
    static final int H = 65;

    static final int GRASS = 0;
    static final int WALL  = 1;
    static final int WATER = 2;
    static final int DIRT  = 3;
    static final int TREE  = 4;
    static final int SAND  = 5;

    static int[][] ground    = new int[W][H];
    static int[][] collision = new int[W][H];

    static Random rng = new Random(1337L); // seeded for reproducibility

    public static void main(String[] args) throws IOException {
        buildGround();
        buildCollision();
        writeGround("res/map/level1_ground.txt");
        writeCollision("res/map/level1_collision.txt");
        writeMeta("res/map/level1_meta.txt");
        System.out.println("Level 1 (65x65) map files written to res/map/");
        validateMap();
    }

    // =========================================================================
    // Ground layer
    // =========================================================================

    static void buildGround() {

        // Phase 1 — Base fill: scattered trees with natural gaps
        //   Instead of filling ALL with tree (which makes impassable walls),
        //   use a probabilistic forest that leaves natural glades.
        fillForestWithGlades();

        // Phase 2 — Open southern arrival area (player spawn zone)
        // Large open grassland at bottom — col 18-50, rows 52-64
        for (int c = 10; c < 56; c++)
            for (int r = 52; r < H; r++)
                ground[c][r] = GRASS;
        // Smooth the north edge of the arrival area with scattered trees
        for (int c = 8; c < 58; c++) {
            if (rng.nextFloat() < 0.3f) ground[c][52] = TREE;
            if (rng.nextFloat() < 0.15f) ground[c][53] = TREE;
        }

        // Phase 3 — Forgotten Village clearing (centre-west, col 14-32, row 30-48)
        fillCircleSmooth(22, 39, 11, GRASS);

        // Phase 4 — Other location clearings
        fillCircleSmooth(7,  18, 6, GRASS);  // Hospital / Sanctuary (NW)
        fillCircleSmooth(38, 18, 5, GRASS);  // Old Shrine (north-centre)
        fillCircleSmooth(50, 42, 7, GRASS);  // Abandoned Homes (east-centre)
        fillCircleSmooth(8,  50, 5, GRASS);  // Safehouse (SW)
        fillCircleSmooth(54, 14, 5, GRASS);  // Crystal Grove (NE)
        fillCircleSmooth(58,  8, 6, GRASS);  // Ruined Keep (far NE)

        // Phase 5 — Cemetery (east, row 6-18, col 43-55) — sand/dirt ground
        fillCircleSmooth(48, 12, 6, SAND);

        // Phase 6 — River running west-east through rows 22-24
        buildRiver();

        // Phase 7 — Stone Bridge (cols 28-33, rows 22-24)
        for (int c = 28; c <= 33; c++)
            for (int r = 22; r <= 24; r++)
                ground[c][r] = DIRT;

        // Phase 8 — Sand shores beside river
        for (int c = 0; c < W; c++) {
            paintIfGrassOrTree(c, 21, SAND);
            paintIfGrassOrTree(c, 25, SAND);
        }

        // Phase 9 — Dirt path network (multiple winding routes)
        buildPaths();

        // Phase 10 — Ancient Forest (NW quadrant) — heavier tree coverage
        // Reinforce trees in the NW to make it feel like a dense ancient forest
        for (int c = 0; c < 14; c++)
            for (int r = 0; r < 24; r++)
                if (ground[c][r] == GRASS && rng.nextFloat() < 0.55f)
                    ground[c][r] = TREE;

        // Phase 11 — Level 2 Gate area (NE corner, col 58-62, rows 3-7)
        // Open sandy approach area
        fillCircleSmooth(59, 5, 4, SAND);
        // Gate arch tiles (wall used as visual "gate" until gate artwork is ready)
        safePaint(58, 4, WALL);
        safePaint(58, 5, WALL);
        safePaint(58, 6, WALL);
        safePaint(62, 4, WALL);
        safePaint(62, 5, WALL);
        safePaint(62, 6, WALL);
        // Doorway through the gate (DIRT = passable arch centre)
        safePaint(59, 5, DIRT);
        safePaint(60, 5, DIRT);
        safePaint(61, 5, DIRT);

        // Phase 12 — Building outlines (Hospital, Safehouse)
        buildBuilding(4, 15, 7, 6);   // Hospital: 7×6 tiles
        buildBuilding(5, 47, 6, 5);   // Safehouse: 6×5 tiles

        // Phase 13 — Thin forest edges (blend tree-grass boundaries)
        thinForestEdges();

        // Phase 14 — Ensure border row/col is tree or wall (natural boundary)
        for (int c = 0; c < W; c++) {
            ground[c][0] = TREE;
            ground[c][H-1] = TREE;
        }
        for (int r = 0; r < H; r++) {
            ground[0][r] = TREE;
            ground[W-1][r] = TREE;
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Fills the map with a probabilistic forest pattern instead of solid trees.
     * Uses a cellular-automata-inspired approach for natural-looking clusters
     * with walkable glades.
     */
    static void fillForestWithGlades() {
        // Step 1: random initial placement — ~50% tree density
        for (int c = 0; c < W; c++)
            for (int r = 0; r < H; r++)
                ground[c][r] = (rng.nextFloat() < 0.50f) ? TREE : GRASS;

        // Step 2: smooth with cellular automata (3 passes)
        //  if a cell has >= 5 tree neighbours (of 8), it becomes tree; else grass.
        for (int pass = 0; pass < 3; pass++) {
            int[][] copy = deepCopy(ground);
            for (int c = 1; c < W-1; c++) {
                for (int r = 1; r < H-1; r++) {
                    int trees = 0;
                    for (int dc = -1; dc <= 1; dc++)
                        for (int dr = -1; dr <= 1; dr++)
                            if (copy[c+dc][r+dr] == TREE) trees++;
                    ground[c][r] = (trees >= 5) ? TREE : GRASS;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // River
    // -------------------------------------------------------------------------

    static void buildRiver() {
        // Winding river through rows 22-24, full width
        int[] offsets = {0, 0, 1, 0, -1, 0, 0, 1, 0, -1, 0, 0, 1};
        int seg = 0;
        for (int c = 1; c < W-1; c++) {
            if (c % 5 == 0) seg = (seg + 1) % offsets.length;
            int base = 23 + offsets[seg];
            for (int off = -1; off <= 1; off++) {
                int r = base + off;
                if (r >= 1 && r < H-1) ground[c][r] = WATER;
            }
        }
        // Widen near bridge approaches (cols 22-27 and 34-40)
        for (int r = 21; r <= 25; r++) {
            for (int c = 22; c < 28; c++) if (ground[c][r] != DIRT) ground[c][r] = WATER;
            for (int c = 34; c < 41; c++) if (ground[c][r] != DIRT) ground[c][r] = WATER;
        }
    }

    // -------------------------------------------------------------------------
    // Paths
    // -------------------------------------------------------------------------

    static void buildPaths() {
        // Primary spine: spawn area (32,58) → village centre (22,39)
        windingPath(32, 58, 22, 39, 2);

        // Village → bridge south (30, 26)
        windingPath(22, 39, 30, 26, 2);
        // Bridge north → Old Shrine (38,18)
        windingPath(30, 21, 38, 18, 2);
        // Old Shrine → Crystal Grove (54,14)
        windingPath(38, 18, 54, 14, 2);
        // Crystal Grove → Ruined Keep (58,8)
        windingPath(54, 14, 58, 8, 2);
        // Ruined Keep → Gate area (60,5)
        windingPath(58, 8, 60, 6, 2);

        // Village west → Hospital (7,18)
        windingPath(22, 39, 7, 18, 2);

        // Village → Cemetery approach (48,12)
        windingPath(38, 18, 48, 12, 2);
        // Cemetery → Keep
        windingPath(48, 12, 58, 8, 2);

        // Village → Safehouse (8,50)
        windingPath(22, 39, 8, 50, 2);

        // Village → Abandoned Homes (50,42)
        windingPath(22, 39, 50, 42, 2);
        // Abandoned Homes → spawn area
        windingPath(50, 42, 45, 58, 2);

        // Secondary route: Hospital → Shrine (bypasses village)
        windingPath(7, 18, 38, 18, 2);

        // Alternative eastern path: spawn → Abandoned Homes → Shrine
        windingPath(45, 58, 50, 42, 2);
    }

    static void windingPath(int c0, int r0, int c1, int r1, int width) {
        int c = c0, r = r0;
        int steps = Math.abs(c1 - c0) + Math.abs(r1 - r0);
        for (int i = 0; i < steps * 2; i++) {
            for (int dc = 0; dc < width; dc++)
                for (int dr = 0; dr < width; dr++)
                    safePaint(c + dc, r + dr, DIRT);

            int dC = Integer.signum(c1 - c);
            int dR = Integer.signum(r1 - r);
            if (c == c1) dC = 0;
            if (r == r1) dR = 0;
            if (dC == 0 && dR == 0) break;

            if (dC != 0 && dR != 0) {
                if (rng.nextBoolean()) dC = 0; else dR = 0;
            }
            int wobble = rng.nextInt(3) - 1;
            if (dC != 0) {
                c += dC;
                r = clamp(r + wobble, 1, H - 2);
            } else {
                r += dR;
                c = clamp(c + wobble, 1, W - 2);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Buildings
    // -------------------------------------------------------------------------

    static void buildBuilding(int col, int row, int w, int h) {
        for (int c = col + 1; c < col + w - 1; c++)
            for (int r = row + 1; r < row + h - 1; r++)
                safePaint(c, r, DIRT);
        for (int c = col; c < col + w; c++) {
            safePaint(c, row,         WALL);
            safePaint(c, row + h - 1, WALL);
        }
        for (int r = row + 1; r < row + h - 1; r++) {
            safePaint(col,         r, WALL);
            safePaint(col + w - 1, r, WALL);
        }
        // Doorway south-centre
        safePaint(col + w / 2, row + h - 1, DIRT);
    }

    // -------------------------------------------------------------------------
    // Forest thinning — create natural edges
    // -------------------------------------------------------------------------

    static void thinForestEdges() {
        int[][] copy = deepCopy(ground);
        for (int c = 1; c < W-1; c++) {
            for (int r = 1; r < H-1; r++) {
                if (copy[c][r] != TREE) continue;
                // Count non-tree neighbours
                int openNeighbours = 0;
                if (copy[c-1][r] != TREE) openNeighbours++;
                if (copy[c+1][r] != TREE) openNeighbours++;
                if (copy[c][r-1] != TREE) openNeighbours++;
                if (copy[c][r+1] != TREE) openNeighbours++;
                // Higher chance to open up isolated trees
                float chance = openNeighbours >= 3 ? 0.75f :
                               openNeighbours == 2 ? 0.45f :
                               openNeighbours == 1 ? 0.25f : 0.0f;
                if (rng.nextFloat() < chance) ground[c][r] = GRASS;
            }
        }
    }

    // =========================================================================
    // Collision layer
    // =========================================================================

    static void buildCollision() {
        // Tile collision flags handle trees/walls/water.
        // This layer is all 0 (walkable override) — tile type alone drives collision.
        for (int c = 0; c < W; c++)
            for (int r = 0; r < H; r++)
                collision[c][r] = 0;
    }

    // =========================================================================
    // Metadata
    // =========================================================================

    static void writeMeta(String path) throws IOException {
        new File(path).getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("# ================================================================");
            pw.println("# DEAD_END Level 1 Metadata — The Forgotten Kingdom (65x65)");
            pw.println("# ================================================================");
            pw.println();
            pw.println("LEVEL_ID       1");
            pw.println("LEVEL_NAME     The Forgotten Kingdom");
            pw.println("MAP_WIDTH      65");
            pw.println("MAP_HEIGHT     65");
            pw.println("TILE_SIZE      48");
            pw.println();
            pw.println("# Player arrives at the southern open area on a dirt path");
            pw.println("PLAYER_SPAWN   32  58");
            pw.println();
            pw.println("# Dragon: disabled on Level 1 until confirmed by team");
            pw.println("# BOSS_DRAGON  30  23");
            pw.println();
            pw.println("SCORE_THRESHOLD  0");
            pw.println("NEXT_LEVEL       2");
            pw.println();
            pw.println("# --- Named locations (col row width height) ---");
            pw.println("LOCATION  \"Ancient Forest\"          1   0  13  22");
            pw.println("LOCATION  \"Forgotten Village\"      13  30  18  16");
            pw.println("LOCATION  \"Cemetery\"               41   6  14  14");
            pw.println("LOCATION  \"Hospital\"                2  13  10   8");
            pw.println("LOCATION  \"Stone Bridge\"           27  21   8   5");
            pw.println("LOCATION  \"Crystal Grove\"          49  10  10   9");
            pw.println("LOCATION  \"Old Shrine\"             34  14   9   9");
            pw.println("LOCATION  \"Safehouse\"               3  45   9   7");
            pw.println("LOCATION  \"Abandoned Homes\"        43  36  14  13");
            pw.println("LOCATION  \"Ruined Keep\"            54   3  10  10");
            pw.println("LOCATION  \"Level 2 Gate\"           56   1   8   9");
            pw.println();
            pw.println("# --- Enemy spawn regions (centerCol centerRow radius maxCount respawnDelay) ---");
            // Ancient Forest area (NW) — moderate
            pw.println("ENEMY_REGION    6  10   5  3  0");
            // Cemetery (NE centre)
            pw.println("ENEMY_REGION   48  12   5  3  0");
            // Ruined Keep (far NE)
            pw.println("ENEMY_REGION   57   8   4  3  0");
            // Near village outskirts
            pw.println("ENEMY_REGION   22  35   6  3  0");
            // Abandoned Homes area
            pw.println("ENEMY_REGION   50  41   5  3  0");
            pw.println();
            pw.println("# --- Object placements (TypeName col row) ---");
            pw.println("# Forgotten Village houses");
            pw.println("OBJECT  OBJ_HOUSE  15  33");
            pw.println("OBJECT  OBJ_HOUSE  20  33");
            pw.println("OBJECT  OBJ_HOUSE  15  39");
            pw.println("OBJECT  OBJ_HOUSE  21  40");
        }
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

    // =========================================================================
    // Validation
    // =========================================================================

    static void validateMap() {
        System.out.println("\n=== Validation ===");
        // Check player spawn tile
        int spawnTile = ground[32][58];
        System.out.printf("Player spawn (32,58) tile: %d [%s]%n", spawnTile,
                spawnTile == DIRT ? "DIRT/path OK" : "WARNING: not a path tile");

        // Check gate area
        System.out.printf("Gate centre (60,5) tile: %d [%s]%n", ground[60][5],
                ground[60][5] == DIRT ? "DIRT OK" : "tile=" + ground[60][5]);

        // Count tile types
        int[] counts = new int[6];
        for (int c = 0; c < W; c++)
            for (int r = 0; r < H; r++)
                if (ground[c][r] < 6) counts[ground[c][r]]++;
        int total = W * H;
        System.out.printf("Total tiles: %d x %d = %d%n", W, H, total);
        System.out.printf("  Grass: %d  Tree: %d  Water: %d  Dirt: %d  Sand: %d  Wall: %d%n",
                counts[0], counts[4], counts[2], counts[3], counts[5], counts[1]);
        double treeRatio = (double) counts[4] / total;
        System.out.printf("Tree density: %.1f%%%n", treeRatio * 100);
        if (treeRatio > 0.45)
            System.out.println("  WARNING: tree density > 45% — may feel cramped.");
        else
            System.out.println("  Tree density OK.");

        // Check all rows have correct width
        System.out.println("Row width check: all " + H + " rows have " + W + " cols (written by generator — OK)");
        System.out.println("=== Validation complete ===");
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    static void fillCircleSmooth(int centerC, int centerR, int radius, int tile) {
        for (int c = centerC - radius - 1; c <= centerC + radius + 1; c++) {
            for (int r = centerR - radius - 1; r <= centerR + radius + 1; r++) {
                if (c <= 0 || c >= W-1 || r <= 0 || r >= H-1) continue;
                double dist = Math.hypot(c - centerC, r - centerR);
                if (dist <= radius) {
                    ground[c][r] = tile;
                } else if (dist <= radius + 1.5 && rng.nextFloat() < 0.5f) {
                    ground[c][r] = tile;
                }
            }
        }
    }

    static void safePaint(int c, int r, int tile) {
        if (c > 0 && c < W-1 && r > 0 && r < H-1) ground[c][r] = tile;
    }

    static void paintIfGrassOrTree(int c, int r, int tile) {
        if (c >= 0 && c < W && r >= 0 && r < H
                && (ground[c][r] == GRASS || ground[c][r] == TREE))
            ground[c][r] = tile;
    }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    static int[][] deepCopy(int[][] src) {
        int[][] copy = new int[src.length][src[0].length];
        for (int i = 0; i < src.length; i++) copy[i] = src[i].clone();
        return copy;
    }
}
