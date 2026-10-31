package tile;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable data container for a loaded level.
 * Populated by MapLoader; consumed by TileManager, AssetSetter, LevelManager, GamePanel.
 *
 * Array convention: groundLayer[col][row] and collisionLayer[col][row] — column-major,
 * matching the existing TileManager convention.
 */
public class MapData {

    // -------------------------------------------------------------------------
    // Core dimensions
    // -------------------------------------------------------------------------
    public final int levelId;
    public final String levelName;
    public final int mapWidth;   // in tiles (columns)
    public final int mapHeight;  // in tiles (rows)
    public final int tileSize;   // in pixels (informational; engine uses GamePanel.tileSize)

    // -------------------------------------------------------------------------
    // Tile layers — both are [col][row] (column-major)
    // -------------------------------------------------------------------------
    public int[][] groundLayer;     // tile IDs 0-19
    public int[][] collisionLayer;  // 0 = walkable, 1 = blocked

    // -------------------------------------------------------------------------
    // Spawn data
    // -------------------------------------------------------------------------
    /** Player spawn tile, [col, row]. Never null after a successful load. */
    public int[] playerSpawn = {49, 90};

    /** Dragon spawn tile, [col, row], or null if this level has no dragon. */
    public int[] dragonSpawn = null;

    // -------------------------------------------------------------------------
    // Progression
    // -------------------------------------------------------------------------
    /** Score needed to leave this level (0 = not yet configured / disabled). */
    public int scoreThreshold = 0;

    /** Level ID to transition to after completion (0 = end of game / not used here). */
    public int nextLevel = 0;

    // -------------------------------------------------------------------------
    // Named locations (used by full-screen map overlay)
    // -------------------------------------------------------------------------
    public final List<LocationRecord> locations = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Enemy spawn regions
    // -------------------------------------------------------------------------
    public final List<EnemyRegion> enemyRegions = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Object placements
    // -------------------------------------------------------------------------
    public final List<ObjectPlacement> objectPlacements = new ArrayList<>();

    // =========================================================================
    // Constructor
    // =========================================================================

    public MapData(int levelId, String levelName, int mapWidth, int mapHeight, int tileSize) {
        this.levelId   = levelId;
        this.levelName = levelName;
        this.mapWidth  = mapWidth;
        this.mapHeight = mapHeight;
        this.tileSize  = tileSize;
        this.groundLayer    = new int[mapWidth][mapHeight];
        this.collisionLayer = new int[mapWidth][mapHeight];
    }

    // =========================================================================
    // Nested records
    // =========================================================================

    /**
     * A named region shown on the full-screen map overlay.
     * Bounding box is in tile coordinates (col/row = top-left corner).
     */
    public record LocationRecord(String name, int col, int row, int width, int height) {}

    /**
     * Circular enemy spawn region.
     * Spawns are placed randomly within radius tiles of (centerCol, centerRow).
     */
    public record EnemyRegion(int centerCol, int centerRow, int radius,
                               int maxCount, int respawnDelay) {}

    /**
     * A single world-object placement.
     * widthTiles / heightTiles are the tile footprint of this object type,
     * validated by MapLoader against the map bounds before insertion.
     */
    public record ObjectPlacement(String typeName, int col, int row,
                                   int widthTiles, int heightTiles) {}
}
