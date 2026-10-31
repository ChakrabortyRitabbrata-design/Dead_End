package deadend.map;

import java.io.*;
import java.util.Random;

/**
 * Standalone generator — produces the three Level 1 map files for the LibGDX game.
 *
 * Output (written to assets/maps/):
 *   level1_ground.txt      — 50×50 tile IDs
 *   level1_collision.txt   — 50×50 collision flags
 *   level1_meta.txt        — metadata
 *
 * Tile IDs:
 *   0=grass  1=wall  2=water  3=dirt  4=tree  5=sand
 *
 * Run standalone: java -cp . deadend.map.Level1Generator
 * Or via Gradle:  ./gradlew :core:run -PmainClass=deadend.map.Level1Generator
 */
public class Level1Generator {

    static final int W = 50, H = 50;

    static final int GRASS = 0, WALL = 1, WATER = 2, DIRT = 3, TREE = 4, SAND = 5;

    static int[][] ground    = new int[W][H];
    static int[][] collision = new int[W][H];

    static Random rng = new Random(2024L);

    public static void main(String[] args) throws IOException {
        String outDir = args.length > 0 ? args[0] : "assets/maps";
        new File(outDir).mkdirs();

        buildGround();
        buildCollision();
        writeGrid(outDir + "/level1_ground.txt",    ground);
        writeGrid(outDir + "/level1_collision.txt", collision);
        writeMeta(outDir + "/level1_meta.txt");

        validate();
        System.out.println("Level 1 (50x50) written to " + outDir);
    }

    // =========================================================================
    // Ground
    // =========================================================================

    static void buildGround() {

        // Step 1 — CA forest base (natural clusters, ~35% tree density)
        for (int c = 0; c < W; c++)
            for (int r = 0; r < H; r++)
                ground[c][r] = rng.nextFloat() < 0.45f ? TREE : GRASS;

        for (int pass = 0; pass < 4; pass++) {
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

        // Step 2 — Open key areas
        // Southern arrival area (player spawn zone)
        clearCircle(25, 42, 9, GRASS);
        clearRect(12, 36, 38, 48, GRASS);

        // Village clearing (centre-west)
        clearCircle(16, 28, 8, GRASS);

        // Other location clearings
        clearCircle( 5, 14,  5, GRASS);   // Hospital NW
        clearCircle(28, 14,  5, GRASS);   // Old Shrine north-centre
        clearCircle(38, 32,  6, GRASS);   // Abandoned Homes east
        clearCircle( 6, 38,  4, GRASS);   // Safehouse SW
        clearCircle(40,  8,  5, GRASS);   // Crystal Grove NE
        clearCircle(44,  5,  5, GRASS);   // Ruined Keep far NE
        clearCircle(42, 10,  4, SAND);    // Cemetery east, sand ground

        // Step 3 — River (rows 19-21, winding)
        buildRiver();

        // Step 4 — Bridge at cols 22-26, rows 19-21
        for (int c = 22; c <= 26; c++)
            for (int r = 19; r <= 21; r++)
                ground[c][r] = DIRT;

        // Step 5 — Sand shores beside river
        for (int c = 1; c < W-1; c++) {
            setIfGrass(c, 18, SAND);
            setIfGrass(c, 22, SAND);
        }

        // Step 6 — Winding path network
        buildPaths();

        // Step 7 — Ancient Forest NW corner (denser)
        for (int c = 0; c < 10; c++)
            for (int r = 0; r < 18; r++)
                if (ground[c][r] == GRASS && rng.nextFloat() < 0.6f)
                    ground[c][r] = TREE;

        // Step 8 — Gate area (NE, col 43-48, rows 2-7) — sand clearing + wall arch
        clearCircle(45, 5, 4, SAND);
        // Wall pillars
        safePaint(43, 4, WALL); safePaint(43, 5, WALL); safePaint(43, 6, WALL);
        safePaint(47, 4, WALL); safePaint(47, 5, WALL); safePaint(47, 6, WALL);
        // Passable arch centre
        safePaint(44, 5, DIRT); safePaint(45, 5, DIRT); safePaint(46, 5, DIRT);

        // Step 9 — Buildings
        buildBuilding( 3, 12, 6, 5);   // Hospital
        buildBuilding( 4, 35, 5, 4);   // Safehouse

        // Step 10 — Thin forest edges (soften tree–grass transitions)
        thinEdges();

        // Step 11 — Hard borders (tree wall prevents walking off map)
        for (int c = 0; c < W; c++) { ground[c][0] = TREE; ground[c][H-1] = TREE; }
        for (int r = 0; r < H; r++) { ground[0][r] = TREE; ground[W-1][r] = TREE; }
    }

    // -------------------------------------------------------------------------

    static void buildRiver() {
        int[] offsets = {0, 0, 1, 1, 0, -1, 0, 0, 1, 0, -1};
        int seg = 0;
        for (int c = 1; c < W-1; c++) {
            if (c % 4 == 0) seg = (seg + 1) % offsets.length;
            int base = 20 + offsets[seg];
            for (int off = -1; off <= 1; off++) {
                int r = base + off;
                if (r >= 1 && r < H-1) ground[c][r] = WATER;
            }
        }
        // Widen near bridge
        for (int r = 18; r <= 22; r++) {
            for (int c = 16; c < 22; c++) if (ground[c][r] != DIRT) ground[c][r] = WATER;
            for (int c = 27; c < 33; c++) if (ground[c][r] != DIRT) ground[c][r] = WATER;
        }
    }

    static void buildPaths() {
        // Spine: spawn (25,42) → village (16,28)
        path(25, 42, 16, 28);
        // Village → bridge south (23,23)
        path(16, 28, 23, 23);
        // Bridge north → Shrine (28,14)
        path(23, 18, 28, 14);
        // Shrine → Crystal Grove (40,8)
        path(28, 14, 40, 8);
        // Crystal Grove → Ruined Keep (44,5)
        path(40,  8, 44, 5);
        // Keep → Gate (45,5)  [already adjacent]
        // Village → Hospital (5,14)
        path(16, 28,  5, 14);
        // Hospital → Shrine shortcut
        path( 5, 14, 28, 14);
        // Village → Cemetery (42,10)
        path(28, 14, 42, 10);
        // Cemetery → Keep
        path(42, 10, 44, 5);
        // Village → Safehouse (6,38)
        path(16, 28,  6, 38);
        // Village → Abandoned Homes (38,32)
        path(16, 28, 38, 32);
        // Abandoned Homes → spawn alt route
        path(38, 32, 35, 44);
        // Second path to spawn from east
        path(35, 44, 25, 42);
    }

    static void path(int c0, int r0, int c1, int r1) {
        int c = c0, r = r0;
        int steps = Math.abs(c1 - c0) + Math.abs(r1 - r0);
        for (int i = 0; i < steps * 2; i++) {
            safePaint(c,   r,   DIRT);
            safePaint(c+1, r,   DIRT);
            safePaint(c,   r+1, DIRT);

            int dC = Integer.signum(c1 - c);
            int dR = Integer.signum(r1 - r);
            if (c == c1) dC = 0;
            if (r == r1) dR = 0;
            if (dC == 0 && dR == 0) break;
            if (dC != 0 && dR != 0) { if (rng.nextBoolean()) dC = 0; else dR = 0; }
            int wob = rng.nextInt(3) - 1;
            if (dC != 0) { c += dC; r = clamp(r + wob, 1, H-2); }
            else         { r += dR; c = clamp(c + wob, 1, W-2); }
        }
    }

    static void buildBuilding(int col, int row, int w, int h) {
        for (int c = col+1; c < col+w-1; c++)
            for (int r = row+1; r < row+h-1; r++) safePaint(c, r, DIRT);
        for (int c = col; c < col+w; c++) { safePaint(c, row, WALL); safePaint(c, row+h-1, WALL); }
        for (int r = row+1; r < row+h-1; r++) { safePaint(col, r, WALL); safePaint(col+w-1, r, WALL); }
        safePaint(col + w/2, row+h-1, DIRT); // door
    }

    static void thinEdges() {
        int[][] copy = deepCopy(ground);
        for (int c = 1; c < W-1; c++) {
            for (int r = 1; r < H-1; r++) {
                if (copy[c][r] != TREE) continue;
                int open = 0;
                if (copy[c-1][r] != TREE) open++;
                if (copy[c+1][r] != TREE) open++;
                if (copy[c][r-1] != TREE) open++;
                if (copy[c][r+1] != TREE) open++;
                float chance = open >= 3 ? 0.80f : open == 2 ? 0.50f : open == 1 ? 0.28f : 0f;
                if (rng.nextFloat() < chance) ground[c][r] = GRASS;
            }
        }
    }

    // =========================================================================
    // Collision
    // =========================================================================

    static void buildCollision() {
        // Tile types drive collision — collision layer is all 0
        for (int c = 0; c < W; c++)
            for (int r = 0; r < H; r++)
                collision[c][r] = 0;
    }

    // =========================================================================
    // Meta
    // =========================================================================

    static void writeMeta(String path) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("# ================================================================");
            pw.println("# DEAD_END Level 1 — The Forgotten Kingdom (LibGDX, 50x50)");
            pw.println("# ================================================================");
            pw.println();
            pw.println("LEVEL_ID       1");
            pw.println("LEVEL_NAME     The Forgotten Kingdom");
            pw.println("MAP_WIDTH      50");
            pw.println("MAP_HEIGHT     50");
            pw.println("TILE_SIZE      48");
            pw.println();
            pw.println("PLAYER_SPAWN   25  42");
            pw.println();
            pw.println("SCORE_THRESHOLD  0");
            pw.println("NEXT_LEVEL       2");
            pw.println();
            pw.println("# --- Named locations ---");
            pw.println("LOCATION  \"Ancient Forest\"          0   0  10  18");
            pw.println("LOCATION  \"Forgotten Village\"       9  22  15  13");
            pw.println("LOCATION  \"Cemetery\"               38   7  10  10");
            pw.println("LOCATION  \"Hospital\"                1  10   8   7");
            pw.println("LOCATION  \"Stone Bridge\"           21  18   7   5");
            pw.println("LOCATION  \"Crystal Grove\"          36   5   9   8");
            pw.println("LOCATION  \"Old Shrine\"             24  11   9   8");
            pw.println("LOCATION  \"Safehouse\"               2  33   7   6");
            pw.println("LOCATION  \"Abandoned Homes\"        32  27  11  11");
            pw.println("LOCATION  \"Ruined Keep\"            41   2   7   7");
            pw.println("LOCATION  \"Level 2 Gate\"           41   1   8   9");
            pw.println();
            pw.println("# --- Enemy regions (centerCol centerRow radius maxCount respawnDelay) ---");
            pw.println("ENEMY_REGION    5  10   4  3  0");
            pw.println("ENEMY_REGION   40  10   4  3  0");
            pw.println("ENEMY_REGION   44   5   3  2  0");
            pw.println("ENEMY_REGION   16  24   5  3  0");
            pw.println("ENEMY_REGION   38  30   4  3  0");
            pw.println();
            pw.println("# --- Object placements ---");
            pw.println("OBJECT  OBJ_HOUSE  11  24");
            pw.println("OBJECT  OBJ_HOUSE  15  24");
            pw.println("OBJECT  OBJ_HOUSE  11  29");
            pw.println("OBJECT  OBJ_HOUSE  16  30");
        }
    }

    // =========================================================================
    // Validation
    // =========================================================================

    static void validate() {
        int[] counts = new int[6];
        for (int c = 0; c < W; c++)
            for (int r = 0; r < H; r++)
                if (ground[c][r] < 6) counts[ground[c][r]]++;

        System.out.printf("Tile distribution (total %d):%n", W * H);
        System.out.printf("  Grass:%d Tree:%d Water:%d Dirt:%d Sand:%d Wall:%d%n",
                counts[0], counts[4], counts[2], counts[3], counts[5], counts[1]);
        System.out.printf("  Tree density: %.1f%%%n", 100.0 * counts[4] / (W * H));

        int spawnTile = ground[25][42];
        System.out.printf("  Player spawn (25,42) tile: %d [%s]%n",
                spawnTile, spawnTile == DIRT ? "OK (dirt)" :
                           spawnTile == GRASS ? "OK (grass)" : "WARNING");

        int gateTile = ground[45][5];
        System.out.printf("  Gate centre (45,5) tile: %d [%s]%n",
                gateTile, gateTile == DIRT ? "OK" : "tile=" + gateTile);
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    static void clearCircle(int cx, int cy, int r, int tile) {
        for (int c = cx-r-1; c <= cx+r+1; c++) {
            for (int rr = cy-r-1; rr <= cy+r+1; rr++) {
                if (c <= 0 || c >= W-1 || rr <= 0 || rr >= H-1) continue;
                double d = Math.hypot(c-cx, rr-cy);
                if (d <= r) ground[c][rr] = tile;
                else if (d <= r+1.5 && rng.nextFloat() < 0.5f) ground[c][rr] = tile;
            }
        }
    }

    static void clearRect(int c0, int r0, int c1, int r1, int tile) {
        for (int c = c0; c <= c1; c++)
            for (int r = r0; r <= r1; r++)
                if (c > 0 && c < W-1 && r > 0 && r < H-1) ground[c][r] = tile;
    }

    static void safePaint(int c, int r, int tile) {
        if (c > 0 && c < W-1 && r > 0 && r < H-1) ground[c][r] = tile;
    }

    static void setIfGrass(int c, int r, int tile) {
        if (c >= 0 && c < W && r >= 0 && r < H && ground[c][r] != WATER) ground[c][r] = tile;
    }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    static int[][] deepCopy(int[][] s) {
        int[][] d = new int[s.length][s[0].length];
        for (int i = 0; i < s.length; i++) d[i] = s[i].clone();
        return d;
    }

    static void writeGrid(String path, int[][] grid) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            for (int r = 0; r < H; r++) {
                StringBuilder sb = new StringBuilder();
                for (int c = 0; c < W; c++) {
                    sb.append(grid[c][r]);
                    if (c < W-1) sb.append(' ');
                }
                pw.println(sb);
            }
        }
    }
}
