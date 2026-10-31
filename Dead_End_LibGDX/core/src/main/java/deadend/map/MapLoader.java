package deadend.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.util.Map;

/**
 * Reads the three-file level triplet from assets/maps/:
 *   levelN_ground.txt    — rows × cols space-separated tile IDs
 *   levelN_collision.txt — rows × cols 0/1 flags
 *   levelN_meta.txt      — metadata, LOCATION, ENEMY_REGION, OBJECT records
 */
public class MapLoader {

    private static final Map<String, int[]> OBJECT_SIZES = Map.ofEntries(
        Map.entry("OBJ_HOUSE",       new int[]{3, 3}),
        Map.entry("OBJ_GRAVESTONE",  new int[]{1, 2}),
        Map.entry("OBJ_CAMPFIRE",    new int[]{1, 1}),
        Map.entry("OBJ_WELL",        new int[]{2, 2}),
        Map.entry("OBJ_SHRINE",      new int[]{2, 2}),
        Map.entry("OBJ_GATE",        new int[]{4, 5})
    );
    private static final int[] DEFAULT_SIZE = {1, 1};

    public static MapData loadLevel(int levelId) {
        MapData data = readMeta(levelId);
        readGroundLayer(levelId, data);
        readCollisionLayer(levelId, data);
        return data;
    }

    // -------------------------------------------------------------------------

    private static MapData readMeta(int levelId) {
        String path = "maps/level" + levelId + "_meta.txt";
        FileHandle fh = Gdx.files.internal(path);
        String text = fh.readString();
        String[] lines = text.split("\n");

        // First pass — dimensions
        int width = 50, height = 50, tileSize = 48;
        int id = levelId;
        String name = "Level " + levelId;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split("\\s+");
            switch (t[0]) {
                case "MAP_WIDTH"  -> width    = Integer.parseInt(t[1]);
                case "MAP_HEIGHT" -> height   = Integer.parseInt(t[1]);
                case "TILE_SIZE"  -> tileSize = Integer.parseInt(t[1]);
                case "LEVEL_ID"   -> id       = Integer.parseInt(t[1]);
                case "LEVEL_NAME" -> name     = line.substring(line.indexOf(t[1]));
            }
        }

        MapData data = new MapData(id, name, width, height, tileSize);

        // Second pass — all records
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split("\\s+");
            try {
                parseMetaLine(t, line, data);
            } catch (Exception e) {
                Gdx.app.error("MapLoader", "Skipping malformed line: " + line);
            }
        }
        return data;
    }

    private static void parseMetaLine(String[] t, String line, MapData data) {
        switch (t[0]) {
            case "MAP_WIDTH", "MAP_HEIGHT", "TILE_SIZE", "LEVEL_ID", "LEVEL_NAME" -> {}

            case "PLAYER_SPAWN" -> {
                int c = Integer.parseInt(t[1]), r = Integer.parseInt(t[2]);
                if (inBounds(c, r, data)) data.playerSpawn = new int[]{c, r};
            }
            case "BOSS_DRAGON" -> {
                int c = Integer.parseInt(t[1]), r = Integer.parseInt(t[2]);
                if (inBounds(c, r, data)) data.dragonSpawn = new int[]{c, r};
            }
            case "SCORE_THRESHOLD" -> data.scoreThreshold = Integer.parseInt(t[1]);
            case "NEXT_LEVEL"      -> data.nextLevel       = Integer.parseInt(t[1]);

            case "LOCATION" -> {
                if (t.length < 6) return;
                String name = extractQuoted(line);
                String rest = line.substring(line.lastIndexOf('"') + 1).trim();
                String[] n = rest.split("\\s+");
                data.locations.add(new MapData.LocationRecord(
                    name,
                    Integer.parseInt(n[0]), Integer.parseInt(n[1]),
                    Integer.parseInt(n[2]), Integer.parseInt(n[3])));
            }
            case "ENEMY_REGION" -> {
                data.enemyRegions.add(new MapData.EnemyRegion(
                    Integer.parseInt(t[1]), Integer.parseInt(t[2]),
                    Integer.parseInt(t[3]), Integer.parseInt(t[4]),
                    Integer.parseInt(t[5])));
            }
            case "OBJECT" -> {
                if (t.length < 4) return;
                String typeName = t[1];
                int c = Integer.parseInt(t[2]), r = Integer.parseInt(t[3]);
                int[] sz = OBJECT_SIZES.getOrDefault(typeName, DEFAULT_SIZE);
                if (c >= 0 && r >= 0 && c + sz[0] <= data.mapWidth && r + sz[1] <= data.mapHeight) {
                    data.objectPlacements.add(
                        new MapData.ObjectPlacement(typeName, c, r, sz[0], sz[1]));
                }
            }
        }
    }

    private static void readGroundLayer(int levelId, MapData data) {
        String path = "maps/level" + levelId + "_ground.txt";
        String[] lines = Gdx.files.internal(path).readString().split("\n");
        for (int row = 0; row < data.mapHeight && row < lines.length; row++) {
            String[] nums = lines[row].trim().split("\\s+");
            for (int col = 0; col < data.mapWidth && col < nums.length; col++) {
                try { data.groundLayer[col][row] = Integer.parseInt(nums[col]); }
                catch (NumberFormatException ignored) {}
            }
        }
    }

    private static void readCollisionLayer(int levelId, MapData data) {
        String path = "maps/level" + levelId + "_collision.txt";
        String[] lines = Gdx.files.internal(path).readString().split("\n");
        for (int row = 0; row < data.mapHeight && row < lines.length; row++) {
            String[] nums = lines[row].trim().split("\\s+");
            for (int col = 0; col < data.mapWidth && col < nums.length; col++) {
                try { data.collisionLayer[col][row] = Integer.parseInt(nums[col]) != 0 ? 1 : 0; }
                catch (NumberFormatException ignored) {}
            }
        }
    }

    // -------------------------------------------------------------------------

    private static boolean inBounds(int c, int r, MapData data) {
        return c >= 0 && c < data.mapWidth && r >= 0 && r < data.mapHeight;
    }

    private static String extractQuoted(String line) {
        int a = line.indexOf('"'), b = line.lastIndexOf('"');
        if (a >= 0 && b > a) return line.substring(a + 1, b);
        String[] t = line.trim().split("\\s+");
        return t.length > 1 ? t[1] : "Unknown";
    }
}
