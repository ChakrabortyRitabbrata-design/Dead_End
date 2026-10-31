package deadend.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.MathUtils;

import deadend.effects.Gate;
import deadend.enemies.Enemy;
import deadend.entities.Player;
import deadend.map.MapData;
import deadend.world.FogOfWar;
import deadend.world.TileManager;
import deadend.world.TileType;

import java.util.List;

/**
 * HUD minimap — 180×180 px, top-right corner.
 *
 * Shows a 25×25 tile window centred on the player.
 * Fog-of-war states affect tile brightness:
 *   VISIBLE    → full colour
 *   EXPLORED   → 60% brightness
 *   UNEXPLORED → 25% brightness (faint but visible)
 * Enemy markers appear only when their tile is VISIBLE.
 * Gate appears as a blue star when VISIBLE.
 */
public class Minimap {

    private static final int MINIMAP_PX  = 180;
    private static final int PREF_TILES  = 25;
    private static final int INSET       = 10;

    private final ShapeRenderer sr;
    private final BitmapFont    font;

    public Minimap(ShapeRenderer sr, BitmapFont font) {
        this.sr   = sr;
        this.font = font;
    }

    public void draw(SpriteBatch batch, int screenW, int screenH,
                     MapData map, TileManager tileM, FogOfWar fog,
                     Player player, List<Enemy> enemies, Gate gate) {

        if (map == null) return;

        int playerCol = (int)(player.getCenterX() / TileManager.TILE_PX);
        int playerRow = (int)(player.getCenterY() / TileManager.TILE_PX);

        // Compute window
        int effCols = Math.min(PREF_TILES, map.mapWidth);
        int effRows = Math.min(PREF_TILES, map.mapHeight);
        float scaleX = (float) MINIMAP_PX / effCols;
        float scaleY = (float) MINIMAP_PX / effRows;

        int startCol = MathUtils.clamp(playerCol - effCols / 2, 0, map.mapWidth  - effCols);
        int startRow = MathUtils.clamp(playerRow - effRows / 2, 0, map.mapHeight - effRows);
        int endCol   = startCol + effCols;
        int endRow   = startRow + effRows;

        int originX = screenW - MINIMAP_PX - INSET;
        int originY = INSET;

        // --- Draw tile grid via ShapeRenderer ---
        batch.end();

        Matrix4 screenProj = new Matrix4().setToOrtho2D(0, 0, screenW, screenH);
        sr.setProjectionMatrix(screenProj);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        // Background
        sr.setColor(0.05f, 0.05f, 0.05f, 0.85f);
        sr.rect(originX - 2, originY - 2, MINIMAP_PX + 4, MINIMAP_PX + 4);

        // Tiles
        for (int c = startCol; c < endCol; c++) {
            for (int r = startRow; r < endRow; r++) {
                int tileId = map.groundLayer[c][r];
                TileType type = TileType.byId(tileId);
                int fogState = fog.getState(c, r);

                float dim = fogState == FogOfWar.VISIBLE ? 1.0f :
                            fogState == FogOfWar.EXPLORED ? 0.60f : 0.25f;

                sr.setColor(type.r * dim, type.g * dim, type.b * dim, 1f);

                float px = originX + (c - startCol) * scaleX;
                float py = originY + (r - startRow) * scaleY;
                sr.rect(px, py, scaleX + 0.5f, scaleY + 0.5f);
            }
        }

        // Enemy markers
        sr.setColor(Color.RED);
        for (Enemy e : enemies) {
            if (e.isDead()) continue;
            int ec = (int)(e.getCenterX() / TileManager.TILE_PX);
            int er = (int)(e.getCenterY() / TileManager.TILE_PX);
            if (fog.getState(ec, er) == FogOfWar.VISIBLE) {
                float mx = originX + (ec - startCol) * scaleX;
                float my = originY + (er - startRow) * scaleY;
                if (mx >= originX && mx < originX + MINIMAP_PX &&
                    my >= originY && my < originY + MINIMAP_PX)
                    sr.circle(mx, my, 3f);
            }
        }

        // Gate marker (blue diamond)
        if (gate != null) {
            int gc = (int)(gate.getLabelX() / TileManager.TILE_PX);
            int gr = (int)(gate.getLabelY() / TileManager.TILE_PX);
            if (fog.getState(gc, gr) != FogOfWar.UNEXPLORED) {
                float gx = originX + (gc - startCol) * scaleX;
                float gy = originY + (gr - startRow) * scaleY;
                if (gx >= originX && gx < originX + MINIMAP_PX &&
                    gy >= originY && gy < originY + MINIMAP_PX) {
                    sr.setColor(0.3f, 0.7f, 1.0f, 1f);
                    sr.circle(gx, gy, 4f);
                }
            }
        }

        // Player marker
        float markerX = originX + (playerCol - startCol) * scaleX;
        float markerY = originY + (playerRow - startRow) * scaleY;
        sr.setColor(Color.WHITE);
        sr.circle(markerX, markerY, 4f);

        sr.end();

        // Border
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0.6f, 0.6f, 0.6f, 1f);
        sr.rect(originX - 1, originY - 1, MINIMAP_PX + 2, MINIMAP_PX + 2);
        sr.end();

        batch.begin();
    }
}
