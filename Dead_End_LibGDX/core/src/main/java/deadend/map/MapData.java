package deadend.map;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable data container for a loaded level.
 * Array convention: groundLayer[col][row] — column-major.
 */
public class MapData {

    public final int levelId;
    public final String levelName;
    public final int mapWidth;   // tiles (columns)
    public final int mapHeight;  // tiles (rows)
    public final int tileSize;   // pixels (logical tile size at scale 1)

    public int[][] groundLayer;     // tile IDs
    public int[][] collisionLayer;  // 0=walkable, 1=blocked

    public int[] playerSpawn = {25, 45};
    public int[] dragonSpawn = null;
    public int   scoreThreshold = 0;
    public int   nextLevel = 0;

    public final List<LocationRecord>   locations        = new ArrayList<>();
    public final List<EnemyRegion>      enemyRegions     = new ArrayList<>();
    public final List<ObjectPlacement>  objectPlacements = new ArrayList<>();

    public MapData(int levelId, String levelName, int mapWidth, int mapHeight, int tileSize) {
        this.levelId   = levelId;
        this.levelName = levelName;
        this.mapWidth  = mapWidth;
        this.mapHeight = mapHeight;
        this.tileSize  = tileSize;
        this.groundLayer    = new int[mapWidth][mapHeight];
        this.collisionLayer = new int[mapWidth][mapHeight];
    }

    public record LocationRecord(String name, int col, int row, int width, int height) {}
    public record EnemyRegion(int centerCol, int centerRow, int radius, int maxCount, int respawnDelay) {}
    public record ObjectPlacement(String typeName, int col, int row, int widthTiles, int heightTiles) {}
}
