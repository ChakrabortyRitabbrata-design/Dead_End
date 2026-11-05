package tile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

import main.GamePanel;

/**
 * TileManager — loads all pixel-art tile images and renders the world.
 *
 * Tile ID map (IDs 0–19):
 *   0  grass          — base grass (4 variants applied per-position)
 *   1  dirt_path      — walkable dirt/sand path
 *   2  water_full     — deep water (blocking)
 *   3  water_shore_n  — north shoreline (walkable)
 *   4  water_shore_s  — south shoreline
 *   5  water_shore_e  — east shoreline
 *   6  water_shore_w  — west shoreline
 *   7  water_corner_ne— water corner
 *   8  grass_flowers  — flower grass variant
 *   9  trans_dirt_n   — grass-dirt transition north
 *  10  trans_dirt_ne  — transition NE
 *  11  trans_dirt_e   — transition E
 *  12  trans_dirt_se  — transition SE
 *  13  trans_dirt_s   — transition S
 *  14  trans_dirt_sw  — transition SW
 *  15  trans_dirt_w   — transition W
 *  16  trans_dirt_nw  — transition NW
 *  17  bridge_plank   — wooden bridge (walkable)
 *  18  lake_center    — deep lake water (blocking)
 *  19  lake_shore     — lake shoreline (walkable)
 *
 * Sprites are loaded from res/tile/*.png — the verified pixel-art sprites
 * extracted from the actual Forest RPG tileset (media_1793520886268_0e8f9f36.jpg).
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
     */
    public int[][] collisionLayer;

    /** The full parsed data for the currently loaded level. */
    public MapData currentMap;

    // Grass variants for visual richness (all walkable, visually different)
    private BufferedImage[] grassVariants;

    // =========================================================================
    // Construction
    // =========================================================================

    public TileManager(GamePanel gp) {
        this.gp        = gp;
        tile           = new Tile[MAX_TILES];
        mapTilenum     = new int[gp.maxWorldCol][gp.maxWorldRow];
        collisionLayer = new int[gp.maxWorldCol][gp.maxWorldRow];

        getTileImage();
        loadGrassVariants();

        try {
            loadLevel(1);
        } catch (IOException e) {
            System.err.println("ERROR loading Level 1: " + e.getMessage());
            loadLegacyMap();
        }
    }

    // =========================================================================
    // Level loading
    // =========================================================================

    public void loadLevel(int levelId) throws IOException {
        MapData data = MapLoader.loadLevel(levelId);
        currentMap   = data;

        if (mapTilenum.length != data.mapWidth || mapTilenum[0].length != data.mapHeight) {
            mapTilenum     = new int[data.mapWidth][data.mapHeight];
            collisionLayer = new int[data.mapWidth][data.mapHeight];
        }

        for (int col = 0; col < data.mapWidth; col++) {
            for (int row = 0; row < data.mapHeight; row++) {
                int id = data.groundLayer[col][row];
                mapTilenum[col][row]     = (id >= 0 && id < MAX_TILES) ? id : 0;
                collisionLayer[col][row] = data.collisionLayer[col][row];
            }
        }

        gp.applyMapDimensions(data.mapWidth, data.mapHeight);
    }

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
        } catch (Exception e) {
            System.err.println("TileManager: legacy map failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // Tile setup — maps each ID to the correct verified sprite file
    // =========================================================================

    private void getTileImage() {
        // ID  sprite-file-name              collision  minimap-colour
        setup( 0, "grass_plain",      false, new Color(0x5B, 0x8E, 0x3E));  // grass base
        setup( 1, "dirt_t_south",     false, new Color(0x9B, 0x74, 0x42));  // dirt path (main centre tile)
        setup( 2, "water_full",       true,  new Color(0x26, 0x5A, 0xA0));  // deep water
        setup( 3, "water_shore_n",    false, new Color(0x4A, 0x8A, 0xAA));  // shore N
        setup( 4, "water_shore_s",    false, new Color(0x4A, 0x8A, 0xAA));  // shore S
        setup( 5, "water_shore_e",    false, new Color(0x4A, 0x8A, 0xAA));  // shore E
        setup( 6, "water_shore_w",    false, new Color(0x4A, 0x8A, 0xAA));  // shore W
        setup( 7, "water_corner_ne",  false, new Color(0x5A, 0x8E, 0xB0));  // corner
        setup( 8, "grass_flowers",    false, new Color(0x70, 0xA8, 0x48));  // flower grass
        setup( 9, "trans_dirt_n",     false, new Color(0x70, 0x98, 0x48));  // transition N
        setup(10, "trans_dirt_ne",    false, new Color(0x70, 0x98, 0x48));  // transition NE
        setup(11, "trans_dirt_e",     false, new Color(0x70, 0x98, 0x48));  // transition E
        setup(12, "trans_dirt_se",    false, new Color(0x70, 0x98, 0x48));  // transition SE
        setup(13, "trans_dirt_s",     false, new Color(0x70, 0x98, 0x48));  // transition S
        setup(14, "trans_dirt_sw",    false, new Color(0x70, 0x98, 0x48));  // transition SW
        setup(15, "trans_dirt_w",     false, new Color(0x70, 0x98, 0x48));  // transition W
        setup(16, "trans_dirt_nw",    false, new Color(0x70, 0x98, 0x48));  // transition NW
        setup(17, "bridge_horizontal",false, new Color(0x8A, 0x62, 0x2E));  // bridge
        setup(18, "lake_center",      true,  new Color(0x26, 0x5A, 0xA0));  // lake deep
        setup(19, "lake_north",       false, new Color(0x46, 0x82, 0xA8));  // lake shore
    }

    // =========================================================================
    // Grass variant system — 4 variants for visual richness
    // =========================================================================

    private void loadGrassVariants() {
        String[] files = {"grass_plain", "grass_detail1", "grass_detail2", "grass_extra_a"};
        grassVariants  = new BufferedImage[files.length];
        for (int i = 0; i < files.length; i++) {
            try {
                BufferedImage raw = readImage("tile/" + files[i] + ".png");
                grassVariants[i] = scaleNearest(raw, gp.tileSize, gp.tileSize);
            } catch (IOException e) {
                grassVariants[i] = makePlaceholder(new Color(0x5B, 0x8E, 0x3E));
            }
        }
    }

    private BufferedImage getGrassVariant(int col, int row) {
        // Deterministic hash — never a visible checkerboard pattern
        int h = (col * 73856093) ^ (row * 19349663) ^ (col * 83492791);
        int n = Math.abs(h) % 100;
        if (n < 55) return grassVariants[0];   // 55% plain grass
        if (n < 78) return grassVariants[1];   // 23% detail1
        if (n < 92) return grassVariants[2];   // 14% detail2
        return grassVariants[3];               //  8% extra variant
    }

    // =========================================================================
    // Tile loading helpers
    // =========================================================================

    private void setup(int id, String imgName, boolean collision, Color miniColor) {
        tile[id] = new Tile();
        tile[id].name         = imgName;
        tile[id].collision    = collision;
        tile[id].minimapColor = miniColor;

        try {
            BufferedImage raw = readImage("tile/" + imgName + ".png");
            tile[id].Image = scaleNearest(raw, gp.tileSize, gp.tileSize);
        } catch (IOException e) {
            tile[id].Image = makePlaceholder(miniColor);
            System.out.println("TileManager: placeholder for " + id + " (" + imgName + ")");
        }
    }

    /** NEAREST_NEIGHBOR scaling — mandatory for crisp pixel art. */
    public static BufferedImage scaleNearest(BufferedImage src, int w, int h) {
        if (src.getWidth() == w && src.getHeight() == h) return src;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private BufferedImage makePlaceholder(Color color) {
        int sz = gp.tileSize;
        BufferedImage img = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, sz, sz);
        g.setColor(color.darker());
        g.drawRect(0, 0, sz - 1, sz - 1);
        g.dispose();
        return img;
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    public void draw(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                            RenderingHints.VALUE_RENDER_QUALITY);

        int worldCol = 0, worldRow = 0;

        while (worldCol < gp.maxWorldCol && worldRow < gp.maxWorldRow) {
            int tileNum = mapTilenum[worldCol][worldRow];
            int worldX  = worldCol * gp.tileSize;
            int worldY  = worldRow * gp.tileSize;
            int screenX = worldX - gp.player.worldX + gp.player.screenX;
            int screenY = worldY - gp.player.worldY + gp.player.screenY;

            // Camera culling
            if (worldX + gp.tileSize > gp.player.worldX - gp.player.screenX
                    && worldX - gp.tileSize < gp.player.worldX + gp.player.screenX
                    && worldY + gp.tileSize > gp.player.worldY - gp.player.screenY
                    && worldY - gp.tileSize < gp.player.worldY + gp.player.screenY) {

                BufferedImage img = (tileNum == 0 && grassVariants != null)
                        ? getGrassVariant(worldCol, worldRow)
                        : tile[tileNum].Image;

                g2.drawImage(img, screenX, screenY, gp.tileSize, gp.tileSize, null);

                if (gp.fogOfWar != null) {
                    gp.fogOfWar.drawOverlay(g2, screenX, screenY,
                            gp.tileSize, worldCol, worldRow);
                }
            }

            if (++worldCol == gp.maxWorldCol) { worldCol = 0; worldRow++; }
        }
    }

    // =========================================================================
    // Resource loading — two-step fallback (classpath → file system)
    // =========================================================================

    private BufferedImage readImage(String path) throws IOException {
        try (InputStream s = openResource(path)) {
            BufferedImage img = ImageIO.read(s);
            if (img == null) throw new IOException("Not a valid image: " + path);
            return img;
        }
    }

    private InputStream openResource(String resourcePath) throws IOException {
        InputStream s = getClass().getResourceAsStream("/" + resourcePath);
        if (s != null) return s;
        Path p = Paths.get("res", resourcePath);
        if (!Files.exists(p)) p = Paths.get("bin", resourcePath);
        if (!Files.exists(p)) p = Paths.get("Dead_End", "res", resourcePath);
        if (!Files.exists(p)) p = Paths.get("Dead_End", "bin", resourcePath);
        if (!Files.exists(p)) throw new IOException("Resource not found: " + resourcePath);
        return Files.newInputStream(p);
    }
}
