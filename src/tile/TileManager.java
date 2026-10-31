package tile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

import main.GamePanel;

/**
 * Manages all tile images, the ground layer, the independent collision layer,
 * and draws the visible portion of the world each frame.
 *
 * Tile array layout (IDs 0–19):
 *   0 grass       1 wall        2 water       3 dirt_path   4 tree
 *   5 sand        6 dark_grass  7 swamp_water 8 stone_floor 9 cobblestone
 *  10 lava_rock  11 cliff_edge 12 crystal_fl 13 shal_water 14 bridge_plank
 *  15 gravel     16 dead_grass 17 snow        18 mud         19 flower_grass
 *
 * If a tile image file is missing, a solid-colour placeholder is used so the
 * game continues to run while artwork is being sourced.
 */
public class TileManager {

    private static final int MAX_TILES = 20;

    GamePanel gp;
    public Tile[] tile;

    /** Ground layer — column-major: mapTilenum[col][row]. */
    public int[][] mapTilenum;

    /**
     * Independent collision layer — column-major: collisionLayer[col][row].
     * 0 = walkable, 1 = blocked.
     * A cell is impassable if tile[id].collision OR collisionLayer[col][row]==1.
     */
    public int[][] collisionLayer;

    /** The full parsed data for the currently loaded level. */
    public MapData currentMap;

    // =========================================================================
    // Construction & initialisation
    // =========================================================================

    public TileManager(GamePanel gp) {
        this.gp = gp;
        tile           = new Tile[MAX_TILES];
        mapTilenum     = new int[gp.maxWorldCol][gp.maxWorldRow];
        collisionLayer = new int[gp.maxWorldCol][gp.maxWorldRow];

        getTileImage();
        // Default: load Level 1 on startup
        try {
            loadLevel(1);
        } catch (IOException e) {
            System.err.println("ERROR loading Level 1 map: " + e.getMessage());
            // Fall back to the legacy map01.txt so the game still launches
            loadLegacyMap();
        }
    }

    // =========================================================================
    // Level loading
    // =========================================================================

    /**
     * Loads ground layer, collision layer and metadata for the given level.
     * Safe to call at runtime (e.g. on level transition).
     * Resizes tile arrays if the new map has different dimensions.
     */
    public void loadLevel(int levelId) throws IOException {
        MapData data = MapLoader.loadLevel(levelId);
        currentMap = data;

        // Resize arrays if map dimensions changed
        if (mapTilenum.length != data.mapWidth || mapTilenum[0].length != data.mapHeight) {
            mapTilenum     = new int[data.mapWidth][data.mapHeight];
            collisionLayer = new int[data.mapWidth][data.mapHeight];
        }

        // Copy ground layer into our column-major arrays
        for (int col = 0; col < data.mapWidth; col++) {
            for (int row = 0; row < data.mapHeight; row++) {
                int id = data.groundLayer[col][row];
                // Clamp to valid tile range
                mapTilenum[col][row]     = (id >= 0 && id < MAX_TILES) ? id : 0;
                collisionLayer[col][row] = data.collisionLayer[col][row];
            }
        }

        // Notify GamePanel of new dimensions (updates fog, minimap, etc.)
        gp.applyMapDimensions(data.mapWidth, data.mapHeight);
    }

    /** Legacy fallback: reads the original map01.txt into ground layer only. */
    private void loadLegacyMap() {
        try (InputStream is = openResource("map/map01.txt");
             java.io.BufferedReader br =
                     new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
            int col = 0, row = 0;
            String line;
            while ((line = br.readLine()) != null && row < gp.maxWorldRow) {
                String[] nums = line.trim().split(" ");
                col = 0;
                while (col < gp.maxWorldCol && col < nums.length) {
                    try {
                        int id = Integer.parseInt(nums[col]);
                        mapTilenum[col][row] = (id >= 0 && id < MAX_TILES) ? id : 0;
                    } catch (NumberFormatException ignored) {}
                    col++;
                }
                row++;
            }
            System.out.println("TileManager: loaded legacy map01.txt as fallback.");
        } catch (Exception e) {
            System.err.println("TileManager: legacy map also failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // Tile image loading
    // =========================================================================

    public void getTileImage() {
        // --- Existing tiles (artwork present) ---
        setup(0,  "grass",          false, new Color(0x5D, 0xA8, 0x32));
        setup(1,  "wall",           true,  new Color(0x88, 0x88, 0x88));
        setup(2,  "water",          true,  new Color(0x1A, 0x6F, 0xB0));
        setup(3,  "earth",          false, new Color(0x8B, 0x69, 0x14));
        setup(4,  "tree",           true,  new Color(0x2B, 0x6B, 0x2B));
        setup(5,  "sand",           false, new Color(0xD4, 0xB9, 0x6B));
        // --- New tiles (artwork optional — placeholder colour used if missing) ---
        setup(6,  "dark_grass",     false, new Color(0x2D, 0x3B, 0x1A));
        setup(7,  "swamp_water",    true,  new Color(0x3A, 0x50, 0x22));
        setup(8,  "stone_floor",    false, new Color(0x70, 0x70, 0x70));
        setup(9,  "cobblestone",    false, new Color(0x99, 0x88, 0x77));
        setup(10, "lava_rock",      true,  new Color(0x8B, 0x25, 0x00));
        setup(11, "cliff_edge",     true,  new Color(0x55, 0x55, 0x66));
        setup(12, "crystal_floor",  false, new Color(0x66, 0xCC, 0xFF));
        setup(13, "shallow_water",  false, new Color(0x55, 0x99, 0xDD));
        setup(14, "bridge_plank",   false, new Color(0xA0, 0x78, 0x4A));
        setup(15, "gravel",         false, new Color(0x88, 0x77, 0x66));
        setup(16, "dead_grass",     false, new Color(0x6B, 0x6B, 0x3A));
        setup(17, "snow",           false, new Color(0xDD, 0xDD, 0xEE));
        setup(18, "mud",            false, new Color(0x4A, 0x38, 0x28));
        setup(19, "flower_grass",   false, new Color(0x6D, 0xB8, 0x40));
    }

    /**
     * Loads one tile. If the image file is missing, creates a solid-colour
     * placeholder so the game never crashes due to missing artwork.
     */
    private void setup(int id, String name, boolean collision, Color fallbackColor) {
        tile[id] = new Tile();
        tile[id].name          = name;
        tile[id].collision     = collision;
        tile[id].minimapColor  = fallbackColor;

        String path = "tile/" + name + ".png";
        try {
            tile[id].Image = readImage(path);
        } catch (IOException e) {
            // Artwork not yet available — generate solid-colour placeholder
            tile[id].Image = makePlaceholder(fallbackColor);
            System.out.println("TileManager: using placeholder for tile " + id
                    + " (" + name + ")");
        }
    }

    /** Creates a solid-colour 48×48 BufferedImage as a placeholder tile. */
    private BufferedImage makePlaceholder(Color color) {
        int size = gp.tileSize;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(color);
        g2.fillRect(0, 0, size, size);
        // Add a subtle 1-px darker border so tiles are visually distinct
        g2.setColor(color.darker());
        g2.drawRect(0, 0, size - 1, size - 1);
        g2.dispose();
        return img;
    }

    // =========================================================================
    // Drawing
    // =========================================================================

    public void draw(Graphics2D g2) {
        int worldCol = 0;
        int worldRow = 0;

        while (worldCol < gp.maxWorldCol && worldRow < gp.maxWorldRow) {
            int tileNum = mapTilenum[worldCol][worldRow];

            int worldX  = worldCol * gp.tileSize;
            int worldY  = worldRow * gp.tileSize;
            int screenX = worldX - gp.player.worldX + gp.player.screenX;
            int screenY = worldY - gp.player.worldY + gp.player.screenY;

            // Camera culling — only draw tiles that overlap the viewport
            if (worldX + gp.tileSize > gp.player.worldX - gp.player.screenX
                    && worldX - gp.tileSize < gp.player.worldX + gp.player.screenX
                    && worldY + gp.tileSize > gp.player.worldY - gp.player.screenY
                    && worldY - gp.tileSize < gp.player.worldY + gp.player.screenY) {

                g2.drawImage(tile[tileNum].Image,
                        screenX, screenY, gp.tileSize, gp.tileSize, null);

                // Fog-of-war overlay (drawn on top of the tile)
                if (gp.fogOfWar != null) {
                    gp.fogOfWar.drawOverlay(g2, screenX, screenY, gp.tileSize,
                            worldCol, worldRow);
                }
            }

            worldCol++;
            if (worldCol == gp.maxWorldCol) {
                worldCol = 0;
                worldRow++;
            }
        }
    }

    // =========================================================================
    // Resource helpers (same two-step fallback pattern as the rest of the engine)
    // =========================================================================

    private BufferedImage readImage(String resourcePath) throws IOException {
        try (InputStream stream = openResource(resourcePath)) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null)
                throw new IOException("Not a readable image: " + resourcePath);
            return image;
        }
    }

    private InputStream openResource(String resourcePath) throws IOException {
        InputStream stream = getClass().getResourceAsStream("/" + resourcePath);
        if (stream != null) return stream;

        Path resource = Paths.get("res", resourcePath);
        if (!Files.exists(resource)) resource = Paths.get("bin", resourcePath);
        if (!Files.exists(resource))
            throw new IOException("Resource not found: " + resourcePath);
        return Files.newInputStream(resource);
    }
}
