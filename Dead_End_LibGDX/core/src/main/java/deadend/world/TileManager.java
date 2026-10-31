package deadend.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import deadend.map.MapData;
import deadend.rendering.PixmapArtist;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages tile textures and renders the visible portion of the world.
 *
 * Visual improvements:
 *   - Multiple grass variants (6) chosen per-tile by position hash
 *   - Multiple dirt variants (4) chosen per-tile by position hash
 *   - Rich procedural textures at 96×96px (rendered at TILE_PX)
 *   - Linear filtering for smooth upscaling
 *   - Water with animated tint cycling
 *   - All textures generated at load time via PixmapArtist
 *
 * Column-major storage: groundLayer[col][row].
 */
public class TileManager {

    public static final int TILE_PX = 48;  // rendered pixels per tile

    // Multiple terrain variants
    private List<Texture> grassVariants = new ArrayList<>();
    private List<Texture> dirtVariants  = new ArrayList<>();
    private Texture sand, water, wall;

    private MapData currentMap;
    private float waterTimer = 0f;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public TileManager() {
        loadTextures();
    }

    private void loadTextures() {
        // Grass: 6 variants — rich procedural hand-painted look
        for (int i = 0; i < 6; i++) {
            grassVariants.add(PixmapArtist.grassVariant(i));
        }
        Gdx.app.log("TileManager", "Generated 6 grass variants");

        // Dirt: 4 variants
        for (int i = 0; i < 4; i++) {
            dirtVariants.add(PixmapArtist.dirtVariant(i));
        }
        Gdx.app.log("TileManager", "Generated 4 dirt variants");

        sand  = PixmapArtist.sandTexture();
        water = PixmapArtist.waterTexture(0f);
        wall  = PixmapArtist.wallTexture();

        Gdx.app.log("TileManager", "All terrain textures generated");
    }

    // -------------------------------------------------------------------------
    // Map loading
    // -------------------------------------------------------------------------

    public void setMap(MapData map) { this.currentMap = map; }
    public MapData getCurrentMap()  { return currentMap; }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    /**
     * Draws all tiles visible in the given camera view rectangle.
     * @param delta      frame time (for water animation)
     * @param batch      active SpriteBatch
     * @param camX       camera left edge in world pixels
     * @param camY       camera bottom edge in world pixels (LibGDX Y-up)
     * @param viewW      viewport width
     * @param viewH      viewport height
     */
    public void draw(float delta, SpriteBatch batch, float camX, float camY, float viewW, float viewH) {
        if (currentMap == null) return;
        waterTimer += delta;

        int colStart = Math.max(0, (int)(camX / TILE_PX) - 1);
        int rowStart = Math.max(0, (int)(camY / TILE_PX) - 1);
        int colEnd   = Math.min(currentMap.mapWidth,  (int)((camX + viewW) / TILE_PX) + 2);
        int rowEnd   = Math.min(currentMap.mapHeight, (int)((camY + viewH) / TILE_PX) + 2);

        for (int col = colStart; col < colEnd; col++) {
            for (int row = rowStart; row < rowEnd; row++) {
                int tileId = currentMap.groundLayer[col][row];
                TileType type = TileType.byId(tileId);

                float wx = col * TILE_PX;
                float wy = row * TILE_PX;

                Texture tex = selectTexture(type, col, row);
                if (tex == null) continue;

                // Water: animated tint
                if (type == TileType.WATER) {
                    float shimmer = 0.92f + 0.08f * (float)Math.sin(waterTimer * 1.8f + col * 0.4f + row * 0.3f);
                    batch.setColor(shimmer, shimmer, 1f, 1f);
                }

                batch.draw(tex, wx, wy, TILE_PX, TILE_PX);

                if (type == TileType.WATER) batch.setColor(Color.WHITE);
            }
        }
    }

    /**
     * Legacy overload (without delta — delta = 0 for water).
     */
    public void draw(SpriteBatch batch, float camX, float camY, float viewW, float viewH) {
        draw(0f, batch, camX, camY, viewW, viewH);
    }

    // -------------------------------------------------------------------------
    // Texture selection — variant by position hash for determinism
    // -------------------------------------------------------------------------

    private Texture selectTexture(TileType type, int col, int row) {
        return switch (type) {
            case GRASS, DARK_GRASS, DEAD_GRASS -> {
                int variant = posHash(col, row, 0) % grassVariants.size();
                yield grassVariants.get(variant);
            }
            case DIRT, COBBLESTONE, BRIDGE_PLANK, STONE_FLOOR -> {
                int variant = posHash(col, row, 1) % dirtVariants.size();
                yield dirtVariants.get(variant);
            }
            case SAND     -> sand;
            case WATER, SWAMP_WATER -> water;
            case WALL     -> wall;
            case TREE     -> {
                // Trees are rendered by DecorSystem as large sprites;
                // tile itself gets a dark ground base
                int variant = posHash(col, row, 2) % grassVariants.size();
                yield grassVariants.get(variant);
            }
        };
    }

    /** Position-based deterministic hash for texture variant selection */
    private int posHash(int col, int row, int salt) {
        int h = col * 73856093 ^ row * 19349663 ^ salt * 83492791;
        return Math.abs(h % 0xFFF);
    }

    // -------------------------------------------------------------------------
    // Collision query
    // -------------------------------------------------------------------------

    public boolean isSolid(float worldX, float worldY, float width, float height) {
        if (currentMap == null) return true;

        int colLeft   = Math.max(0, (int)(worldX / TILE_PX));
        int colRight  = Math.min(currentMap.mapWidth  - 1, (int)((worldX + width  - 1) / TILE_PX));
        int rowBottom = Math.max(0, (int)(worldY / TILE_PX));
        int rowTop    = Math.min(currentMap.mapHeight - 1, (int)((worldY + height - 1) / TILE_PX));

        for (int c = colLeft; c <= colRight; c++) {
            for (int r = rowBottom; r <= rowTop; r++) {
                if (c < 0 || c >= currentMap.mapWidth || r < 0 || r >= currentMap.mapHeight) return true;
                if (currentMap.collisionLayer[c][r] == 1) return true;
                if (TileType.byId(currentMap.groundLayer[c][r]).collision) return true;
            }
        }
        return false;
    }

    /** World width in pixels. */
    public float worldWidth()  { return currentMap != null ? currentMap.mapWidth  * TILE_PX : 0; }
    /** World height in pixels. */
    public float worldHeight() { return currentMap != null ? currentMap.mapHeight * TILE_PX : 0; }

    // -------------------------------------------------------------------------
    // Resource disposal
    // -------------------------------------------------------------------------

    public void dispose() {
        grassVariants.forEach(Texture::dispose);
        dirtVariants.forEach(Texture::dispose);
        grassVariants.clear();
        dirtVariants.clear();
        if (sand  != null) sand.dispose();
        if (water != null) water.dispose();
        if (wall  != null) wall.dispose();
    }
}
