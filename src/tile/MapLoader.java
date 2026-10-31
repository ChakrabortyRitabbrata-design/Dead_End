package tile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Reads the three-file level triplet (ground, collision, meta) and produces a MapData instance.
 *
 * File naming convention:
 *   res/map/levelN_ground.txt    — 100 rows × 100 cols of space-separated tile IDs
 *   res/map/levelN_collision.txt — 100 rows × 100 cols of 0/1 collision flags
 *   res/map/levelN_meta.txt      — key-value metadata, LOCATION, ENEMY_REGION, OBJECT records
 *
 * Array storage convention: groundLayer[col][row] and collisionLayer[col][row] (column-major),
 * matching the existing TileManager convention.
 */
public class MapLoader {

    // -------------------------------------------------------------------------
    // Object footprint registry — tile width × height for each SuperObject type.
    // Used to validate that the complete footprint stays within map bounds.
    // -------------------------------------------------------------------------
    private static final Map<String, int[]> OBJECT_TILE_SIZES = Map.ofEntries(
        Map.entry("OBJ_HOUSE",        new int[]{3, 3}),
        Map.entry("OBJ_GRAVESTONE",   new int[]{1, 2}),
        Map.entry("OBJ_CAMPFIRE",     new int[]{1, 1}),
        Map.entry("OBJ_RUIN_PILLAR",  new int[]{1, 2}),
        Map.entry("OBJ_WELL",         new int[]{2, 2}),
        Map.entry("OBJ_SHRINE",       new int[]{2, 2}),
        Map.entry("OBJ_CRYSTAL",      new int[]{1, 2}),
        Map.entry("OBJ_CAVE",         new int[]{3, 2}),
        Map.entry("OBJ_PORTAL",       new int[]{3, 3})
    );
    private static final int[] DEFAULT_TILE_SIZE = {1, 1};

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Loads all three files for the given level index (1, 2 or 3).
     * Throws IllegalStateException if any mandatory file is missing or malformed.
     */
    public static MapData loadLevel(int levelId) throws IOException {
        // Read meta first so we know mapWidth / mapHeight before reading layers
        MapData data = readMeta(levelId);
        readGroundLayer(levelId, data);
        readCollisionLayer(levelId, data);
        return data;
    }

    // -------------------------------------------------------------------------
    // Meta parser
    // -------------------------------------------------------------------------

    private static MapData readMeta(int levelId) throws IOException {
        String filename = "map/level" + levelId + "_meta.txt";
        // Temporary mutable holders — replaced as keys are encountered
        int[] widthHolder  = {100};
        int[] heightHolder = {100};
        int[] tileSzHolder = {48};
        int[] levelIdHolder = {levelId};
        String[] nameHolder = {"Level " + levelId};

        // First pass: get dimensions so MapData can be constructed
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(openResource(filename)))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] tokens = line.split("\\s+");
                switch (tokens[0]) {
                    case "MAP_WIDTH"  -> widthHolder[0]  = Integer.parseInt(tokens[1]);
                    case "MAP_HEIGHT" -> heightHolder[0] = Integer.parseInt(tokens[1]);
                    case "TILE_SIZE"  -> tileSzHolder[0] = Integer.parseInt(tokens[1]);
                    case "LEVEL_ID"   -> levelIdHolder[0]= Integer.parseInt(tokens[1]);
                    case "LEVEL_NAME" -> nameHolder[0]   = tokens.length > 1
                            ? line.substring(line.indexOf(tokens[1])) : nameHolder[0];
                }
            }
        }

        MapData data = new MapData(
                levelIdHolder[0], nameHolder[0],
                widthHolder[0], heightHolder[0], tileSzHolder[0]);

        // Second pass: parse all other records
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(openResource(filename)))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] tokens = line.split("\\s+");
                try {
                    parseMetaLine(tokens, line, data);
                } catch (Exception e) {
                    System.err.println("WARN: Skipping malformed meta line: " + line
                            + " (" + e.getMessage() + ")");
                }
            }
        }

        return data;
    }

    @SuppressWarnings("incomplete-switch")
    private static void parseMetaLine(String[] tokens, String line, MapData data) {
        switch (tokens[0]) {
            // Already handled in first pass — skip
            case "MAP_WIDTH", "MAP_HEIGHT", "TILE_SIZE", "LEVEL_ID", "LEVEL_NAME" -> {}

            case "PLAYER_SPAWN" -> {
                int col = Integer.parseInt(tokens[1]);
                int row = Integer.parseInt(tokens[2]);
                if (col >= 0 && col < data.mapWidth && row >= 0 && row < data.mapHeight) {
                    data.playerSpawn = new int[]{col, row};
                } else {
                    System.err.printf("WARN: PLAYER_SPAWN (%d,%d) out of bounds — using default.%n",
                            col, row);
                }
            }

            case "BOSS_DRAGON" -> {
                int col = Integer.parseInt(tokens[1]);
                int row = Integer.parseInt(tokens[2]);
                if (col >= 0 && col < data.mapWidth && row >= 0 && row < data.mapHeight) {
                    data.dragonSpawn = new int[]{col, row};
                } else {
                    System.err.printf("WARN: BOSS_DRAGON (%d,%d) out of bounds — ignored.%n",
                            col, row);
                }
            }

            case "SCORE_THRESHOLD" -> data.scoreThreshold = Integer.parseInt(tokens[1]);
            case "NEXT_LEVEL"      -> data.nextLevel       = Integer.parseInt(tokens[1]);

            case "LOCATION" -> {
                // LOCATION "Name With Spaces" col row width height
                // Reassemble quoted name
                if (tokens.length < 6) return;
                String name = extractQuotedName(line);
                // After the quoted name there are 4 integers
                String rest = line.substring(line.lastIndexOf('"') + 1).trim();
                String[] nums = rest.split("\\s+");
                int col = Integer.parseInt(nums[0]);
                int row = Integer.parseInt(nums[1]);
                int w   = Integer.parseInt(nums[2]);
                int h   = Integer.parseInt(nums[3]);
                data.locations.add(new MapData.LocationRecord(name, col, row, w, h));
            }

            case "ENEMY_REGION" -> {
                // ENEMY_REGION centerCol centerRow radius maxCount respawnDelay
                int cc = Integer.parseInt(tokens[1]);
                int cr = Integer.parseInt(tokens[2]);
                int r  = Integer.parseInt(tokens[3]);
                int mc = Integer.parseInt(tokens[4]);
                int rd = Integer.parseInt(tokens[5]);
                data.enemyRegions.add(new MapData.EnemyRegion(cc, cr, r, mc, rd));
            }

            case "OBJECT" -> {
                // OBJECT TypeName col row
                if (tokens.length < 4) {
                    System.err.println("WARN: Malformed OBJECT line — skipped: " + line);
                    return;
                }
                String typeName = tokens[1];
                int col = Integer.parseInt(tokens[2]);
                int row = Integer.parseInt(tokens[3]);
                int[] sz = OBJECT_TILE_SIZES.getOrDefault(typeName, DEFAULT_TILE_SIZE);
                int endCol = col + sz[0];
                int endRow = row + sz[1];
                if (col < 0 || row < 0
                        || endCol > data.mapWidth
                        || endRow > data.mapHeight) {
                    System.err.printf(
                            "WARN: Object %s (%d×%d tiles) at (%d,%d) extends outside "
                            + "map [%d×%d] — skipped.%n",
                            typeName, sz[0], sz[1], col, row,
                            data.mapWidth, data.mapHeight);
                    return;
                }
                data.objectPlacements.add(
                        new MapData.ObjectPlacement(typeName, col, row, sz[0], sz[1]));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Ground layer
    // -------------------------------------------------------------------------

    private static void readGroundLayer(int levelId, MapData data) throws IOException {
        String filename = "map/level" + levelId + "_ground.txt";
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(openResource(filename)))) {
            for (int row = 0; row < data.mapHeight; row++) {
                String line = br.readLine();
                if (line == null) {
                    System.err.printf("WARN: Ground layer truncated at row %d.%n", row);
                    break;
                }
                String[] nums = line.trim().split("\\s+");
                for (int col = 0; col < data.mapWidth && col < nums.length; col++) {
                    try {
                        data.groundLayer[col][row] = Integer.parseInt(nums[col]);
                    } catch (NumberFormatException e) {
                        System.err.printf("WARN: Non-integer in ground layer at col=%d row=%d.%n",
                                col, row);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Collision layer
    // -------------------------------------------------------------------------

    private static void readCollisionLayer(int levelId, MapData data) throws IOException {
        String filename = "map/level" + levelId + "_collision.txt";
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(openResource(filename)))) {
            for (int row = 0; row < data.mapHeight; row++) {
                String line = br.readLine();
                if (line == null) {
                    System.err.printf("WARN: Collision layer truncated at row %d.%n", row);
                    break;
                }
                String[] nums = line.trim().split("\\s+");
                for (int col = 0; col < data.mapWidth && col < nums.length; col++) {
                    try {
                        int v = Integer.parseInt(nums[col]);
                        data.collisionLayer[col][row] = (v != 0) ? 1 : 0;
                    } catch (NumberFormatException e) {
                        System.err.printf("WARN: Non-integer in collision layer at col=%d row=%d.%n",
                                col, row);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Resource loading (same two-step fallback as TileManager)
    // -------------------------------------------------------------------------

    static InputStream openResource(String resourcePath) throws IOException {
        InputStream stream = MapLoader.class.getResourceAsStream("/" + resourcePath);
        if (stream != null) return stream;

        Path p = Paths.get("res", resourcePath);
        if (!Files.exists(p)) p = Paths.get("bin", resourcePath);
        if (!Files.exists(p))
            throw new IOException("Map resource not found: " + resourcePath);
        return Files.newInputStream(p);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Extracts the text between the first pair of double-quote characters. */
    private static String extractQuotedName(String line) {
        int first = line.indexOf('"');
        int last  = line.lastIndexOf('"');
        if (first >= 0 && last > first) return line.substring(first + 1, last);
        // Fallback: second token (no quotes)
        String[] t = line.trim().split("\\s+");
        return t.length > 1 ? t[1] : "Unknown";
    }
}
