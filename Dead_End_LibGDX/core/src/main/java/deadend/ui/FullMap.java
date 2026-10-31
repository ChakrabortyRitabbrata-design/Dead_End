package deadend.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.MathUtils;

import deadend.effects.Gate;
import deadend.entities.Player;
import deadend.map.MapData;
import deadend.world.FogOfWar;
import deadend.world.TileManager;
import deadend.world.TileType;

/**
 * Full-screen map overlay (opened with M key).
 * Shows complete level, zoom/pan, fog-of-war, locations, and the gate icon.
 */
public class FullMap {

    private static final float MIN_ZOOM = 0.4f;
    private static final float MAX_ZOOM = 4.0f;
    private static final float ZOOM_SPEED = 0.002f;

    private float zoom   = 1.0f;
    private float panX   = 0f;
    private float panY   = 0f;
    private int   dragStartX = -1;
    private int   dragStartY = -1;
    private float dragPanX, dragPanY;

    private final ShapeRenderer sr;
    private final BitmapFont    font;
    private final BitmapFont    fontSmall;
    private final GlyphLayout   layout = new GlyphLayout();

    private float time = 0f;

    public FullMap(ShapeRenderer sr, BitmapFont font, BitmapFont fontSmall) {
        this.sr        = sr;
        this.font      = font;
        this.fontSmall = fontSmall;
    }

    public void reset() {
        zoom = 1.0f;
        panX = 0f;
        panY = 0f;
    }

    public void update(float delta) {
        time += delta;

        // Mouse drag for panning
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            dragStartX = Gdx.input.getX();
            dragStartY = Gdx.input.getY();
            dragPanX   = panX;
            dragPanY   = panY;
        } else if (Gdx.input.isButtonPressed(Input.Buttons.LEFT) && dragStartX >= 0) {
            int dx = Gdx.input.getX() - dragStartX;
            int dy = Gdx.input.getY() - dragStartY;
            panX = dragPanX + dx;
            panY = dragPanY - dy;
        } else {
            dragStartX = -1;
        }

        // Zoom is handled by handleScroll() via the InputProcessor in GameScreen
    }

    public void handleScroll(float amount) {
        zoom = MathUtils.clamp(zoom - amount * 0.15f, MIN_ZOOM, MAX_ZOOM);
    }

    public void draw(SpriteBatch batch, int screenW, int screenH,
                     MapData map, TileManager tileM, FogOfWar fog,
                     Player player, Gate gate) {
        if (map == null) return;

        float baseScale = Math.min(
            (float)(screenW - 80) / map.mapWidth,
            (float)(screenH - 80) / map.mapHeight);
        float scale  = baseScale * zoom;
        float mapPxW = map.mapWidth  * scale;
        float mapPxH = map.mapHeight * scale;
        float originX = (screenW - mapPxW) / 2f + panX;
        float originY = (screenH - mapPxH) / 2f + panY;

        batch.end();

        Matrix4 screenProj = new Matrix4().setToOrtho2D(0, 0, screenW, screenH);
        sr.setProjectionMatrix(screenProj);

        // --- Dark semi-transparent background ---
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0f, 0f, 0f, 0.82f);
        sr.rect(0, 0, screenW, screenH);

        // --- Tile grid ---
        for (int c = 0; c < map.mapWidth; c++) {
            for (int r = 0; r < map.mapHeight; r++) {
                float dx = originX + c * scale;
                float dy = originY + r * scale;
                if (dx + scale < 0 || dx > screenW || dy + scale < 0 || dy > screenH) continue;

                int tileId   = map.groundLayer[c][r];
                TileType type = TileType.byId(tileId);
                int fogState = fog.getState(c, r);
                float dim = fogState == FogOfWar.VISIBLE ? 1.0f :
                            fogState == FogOfWar.EXPLORED ? 0.65f : 0.22f;

                sr.setColor(type.r * dim, type.g * dim, type.b * dim, 1f);
                sr.rect(dx, dy, scale + 0.5f, scale + 0.5f);
            }
        }

        // --- Named locations ---
        for (MapData.LocationRecord loc : map.locations) {
            if (loc.name().startsWith("Level 2")) continue; // gate drawn separately
            float lx = originX + loc.col()   * scale;
            float ly = originY + loc.row()   * scale;
            float lw = loc.width()  * scale;
            float lh = loc.height() * scale;
            sr.setColor(1f, 0.87f, 0.39f, 0.18f);
            sr.rect(lx, ly, lw, lh);
            sr.setColor(1f, 0.87f, 0.39f, 0.55f);
            sr.rect(lx, ly, lw, 1.5f);
            sr.rect(lx, ly + lh - 1.5f, lw, 1.5f);
            sr.rect(lx, ly, 1.5f, lh);
            sr.rect(lx + lw - 1.5f, ly, 1.5f, lh);
        }

        // --- Level 2 gate icon (distinctive blue portal icon) ---
        if (gate != null) {
            float gx = originX + (gate.worldX / TileManager.TILE_PX) * scale;
            float gy = originY + (gate.worldY / TileManager.TILE_PX) * scale;
            float gs = Math.max(8f, scale * 4);  // gate icon size
            float pulse = 0.5f + 0.5f * MathUtils.sin(time * 3f);
            // Outer glow ring
            sr.setColor(0.1f, 0.4f + 0.3f * pulse, 1.0f, 0.5f + 0.3f * pulse);
            sr.circle(gx + gs / 2, gy + gs / 2, gs * 0.7f);
            // Inner bright core
            sr.setColor(0.6f, 0.85f, 1.0f, 1f);
            sr.circle(gx + gs / 2, gy + gs / 2, gs * 0.35f);
        }

        // --- Player marker (pulsing white circle) ---
        float px = originX + (player.getCenterX() / TileManager.TILE_PX) * scale;
        float py = originY + (player.getCenterY() / TileManager.TILE_PX) * scale;
        float pulse2 = 0.5f + 0.5f * MathUtils.sin(time * 4f);
        sr.setColor(1f, 1f, 1f, 0.9f);
        sr.circle(px, py, 5f + 2f * pulse2);
        sr.setColor(0.2f, 0.8f, 0.4f, 1f);
        sr.circle(px, py, 3.5f);

        sr.end();

        // --- Text labels (via SpriteBatch) ---
        batch.begin();
        batch.setProjectionMatrix(screenProj);

        for (MapData.LocationRecord loc : map.locations) {
            if (loc.name().startsWith("Level 2")) continue;
            float lx = originX + loc.col() * scale;
            float ly = originY + loc.row() * scale;
            if (loc.width() * scale < 15f) continue;

            // Shadow
            fontSmall.setColor(0f, 0f, 0f, 0.9f);
            fontSmall.draw(batch, loc.name(), lx + 2, ly + loc.height() * scale - 2);
            // Label
            fontSmall.setColor(1f, 0.92f, 0.55f, 1f);
            fontSmall.draw(batch, loc.name(), lx + 1, ly + loc.height() * scale - 1);
        }

        // Gate label
        if (gate != null) {
            float gx = originX + (gate.worldX / TileManager.TILE_PX) * scale;
            float gy = originY + (gate.worldY / TileManager.TILE_PX) * scale + scale * 6;
            fontSmall.setColor(0f, 0f, 0f, 0.9f);
            fontSmall.draw(batch, "LEVEL 2 GATE", gx - 1, gy);
            fontSmall.setColor(0.4f, 0.85f, 1.0f, 1f);
            fontSmall.draw(batch, "LEVEL 2 GATE", gx, gy + 1);
        }

        // Controls hint
        fontSmall.setColor(0.7f, 0.7f, 0.7f, 0.9f);
        String hint = "M / ESC — close  |  Scroll — zoom  |  Drag — pan";
        layout.setText(fontSmall, hint);
        fontSmall.draw(batch, hint, (screenW - layout.width) / 2f, 20);

        batch.setProjectionMatrix(sr.getProjectionMatrix()); // restore
    }
}
