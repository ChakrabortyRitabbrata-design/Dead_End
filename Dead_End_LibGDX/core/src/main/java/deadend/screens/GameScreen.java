package deadend.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;

import deadend.DeadEndGame;
import deadend.camera.GameCamera;
import deadend.effects.Gate;
import deadend.enemies.Enemy;
import deadend.entities.Player;
import deadend.map.MapData;
import deadend.map.MapLoader;
import deadend.rendering.DecorSystem;
import deadend.ui.FullMap;
import deadend.ui.HUD;
import deadend.ui.Minimap;
import deadend.world.FogOfWar;
import deadend.world.TileManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Main gameplay screen for Level 1.
 *
 * Rendering order (back to front):
 *   1. Ground tiles (multiple variants, water animated)
 *   2. Terrain transition overlays (grass↔dirt, grass↔sand edges)
 *   3. Ground-layer decor (tufts, ferns, fallen logs)
 *   4. Gate (ShapeRenderer, world-space)
 *   5. Enemies
 *   6. Player
 *   7. Elevated decor (trees, bushes, rocks — above entities)
 *   8. Fog-of-war overlay
 *   9. HUD, minimap, full-map (screen-space)
 *   10. Gate label
 *   11. Game-over overlay
 */
public class GameScreen implements Screen {

    // =========================================================================
    // State
    // =========================================================================
    private enum GameState { PLAYING, FULL_MAP, GAME_OVER }
    private GameState state = GameState.PLAYING;

    // =========================================================================
    // Systems
    // =========================================================================
    private final DeadEndGame game;
    private TileManager  tileM;
    private FogOfWar     fog;
    private GameCamera   camera;
    private Player       player;
    private Gate         gate;
    private Minimap      minimapUI;
    private FullMap      fullMapUI;
    private HUD          hud;
    private DecorSystem  decor;

    private MapData      mapData;
    private final List<Enemy> enemies = new ArrayList<>();
    private final Random rng = new Random(42L);

    private int   score         = 0;
    private float gameOverTimer = 0f;
    private float renderDelta   = 0f;

    // =========================================================================
    // Construction
    // =========================================================================

    public GameScreen(DeadEndGame game) { this.game = game; }

    // =========================================================================
    // Screen lifecycle
    // =========================================================================

    @Override
    public void show() {
        // Systems
        tileM    = new TileManager();
        fog      = new FogOfWar(100, 100);
        camera   = new GameCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        hud      = new HUD(game.shapes, game.fontSmall);
        minimapUI= new Minimap(game.shapes, game.fontSmall);
        fullMapUI= new FullMap(game.shapes, game.font, game.fontSmall);
        decor    = new DecorSystem();
        decor.load();

        // Load level 1
        try {
            mapData = MapLoader.loadLevel(1);
        } catch (Exception e) {
            Gdx.app.error("GameScreen", "Failed to load level 1: " + e.getMessage());
            mapData = makeFallbackMap();
        }

        tileM.setMap(mapData);
        fog.resize(mapData.mapWidth, mapData.mapHeight);
        camera.setWorldBounds(tileM.worldWidth(), tileM.worldHeight());

        // Populate decor AFTER map is set
        decor.populate(mapData, tileM);

        // Player
        player = new Player(tileM);
        int[] spawn = mapData.playerSpawn;
        player.setPosition(spawn[0] * TileManager.TILE_PX, spawn[1] * TileManager.TILE_PX);

        // Gate
        gate = createGate();

        // Enemies
        spawnEnemies();

        // Input
        setupInput();

        Gdx.app.log("GameScreen", "Level loaded: " + mapData.levelName +
            " (" + mapData.mapWidth + "x" + mapData.mapHeight + ")" +
            " — " + decor.getEntries().size() + " decor objects placed");
    }

    private Gate createGate() {
        for (MapData.LocationRecord loc : mapData.locations) {
            if (loc.name().contains("Gate") || loc.name().contains("gate")) {
                float gx = (loc.col() + loc.width() / 2f - 2f) * TileManager.TILE_PX;
                float gy = (loc.row() + 1f) * TileManager.TILE_PX;
                return new Gate(gx, gy);
            }
        }
        return new Gate((mapData.mapWidth - 8) * TileManager.TILE_PX, 3 * TileManager.TILE_PX);
    }

    private void spawnEnemies() {
        enemies.clear();
        for (MapData.EnemyRegion region : mapData.enemyRegions) {
            for (int i = 0; i < region.maxCount(); i++) {
                trySpawnInRegion(region);
            }
        }
    }

    private void trySpawnInRegion(MapData.EnemyRegion region) {
        for (int attempt = 0; attempt < 15; attempt++) {
            double angle = rng.nextDouble() * 2 * Math.PI;
            double dist  = Math.sqrt(rng.nextDouble()) * region.radius();
            int col = region.centerCol() + (int)(Math.cos(angle) * dist);
            int row = region.centerRow() + (int)(Math.sin(angle) * dist);

            if (col < 1 || col >= mapData.mapWidth - 1 || row < 1 || row >= mapData.mapHeight - 1) continue;
            int dc = col - region.centerCol(), dr = row - region.centerRow();
            if (dc * dc + dr * dr > region.radius() * region.radius()) continue;
            if (tileM.isSolid(col * TileManager.TILE_PX, row * TileManager.TILE_PX, 32f, 32f)) continue;

            float ex = col * TileManager.TILE_PX, ey = row * TileManager.TILE_PX;
            float pdx = ex - player.worldX, pdy = ey - player.worldY;
            if (pdx * pdx + pdy * pdy < 200f * 200f) continue;

            enemies.add(new Enemy(ex, ey, tileM));
            return;
        }
    }

    private void setupInput() {
        InputAdapter adapter = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.M || keycode == Input.Keys.TAB) {
                    toggleFullMap(); return true;
                }
                if (keycode == Input.Keys.ESCAPE && state == GameState.FULL_MAP) {
                    toggleFullMap(); return true;
                }
                return false;
            }
            @Override
            public boolean scrolled(float amountX, float amountY) {
                if (state == GameState.FULL_MAP) { fullMapUI.handleScroll(amountY); return true; }
                return false;
            }
        };
        Gdx.input.setInputProcessor(adapter);
    }

    private void toggleFullMap() {
        if (state == GameState.PLAYING) {
            state = GameState.FULL_MAP;
            fullMapUI.reset();
        } else if (state == GameState.FULL_MAP) {
            state = GameState.PLAYING;
        }
    }

    // =========================================================================
    // Game loop
    // =========================================================================

    @Override
    public void render(float delta) {
        delta = Math.min(delta, 0.05f);
        renderDelta = delta;

        ScreenUtils.clear(0.05f, 0.07f, 0.04f, 1f); // very dark green-black

        if (state == GameState.PLAYING) {
            update(delta);
        } else if (state == GameState.FULL_MAP) {
            fullMapUI.update(delta);
            gate.update(delta);
        }

        drawWorld(delta);
        drawScreenSpace();
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    private void update(float delta) {
        player.update(delta);

        int pCol = (int)(player.getCenterX() / TileManager.TILE_PX);
        int pRow = (int)(player.getCenterY() / TileManager.TILE_PX);
        fog.update(pCol, pRow);

        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy e = it.next();
            e.update(delta, player);
            if (e.isDead()) { it.remove(); score += 10; }
        }

        gate.update(delta);
        camera.update(player.getCenterX(), player.getCenterY(), delta);

        if (player.health <= 0) { state = GameState.GAME_OVER; gameOverTimer = 0f; }
    }

    // -------------------------------------------------------------------------
    // Rendering — world space
    // -------------------------------------------------------------------------

    private void drawWorld(float delta) {
        game.batch.setProjectionMatrix(camera.getCamera().combined);
        game.shapes.setProjectionMatrix(camera.getCamera().combined);

        float camX = camera.getWorldOrigin().x;
        float camY = camera.getWorldOrigin().y;
        float vpW  = Gdx.graphics.getWidth();
        float vpH  = Gdx.graphics.getHeight();

        // --- 1. Ground tiles (multiple variants, water animated) ---
        game.batch.begin();
        tileM.draw(delta, game.batch, camX, camY, vpW, vpH);
        game.batch.end();

        // --- 2. Terrain transition overlays (alpha-blended edges) ---
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        game.batch.begin();
        decor.drawTransitionOverlays(game.batch, mapData, camX, camY, vpW, vpH);
        game.batch.end();

        // --- 3. Ground-layer decor (tufts, ferns, logs — below entities) ---
        game.batch.begin();
        decor.drawGroundDecor(game.batch, camX, camY, vpW, vpH);
        game.batch.end();

        // --- 4. Gate (ShapeRenderer, world space) ---
        Gdx.gl.glEnable(GL20.GL_BLEND);
        game.shapes.begin(ShapeRenderer.ShapeType.Filled);
        gate.draw(game.shapes);
        game.shapes.end();

        // --- 5. Enemies + Player ---
        game.batch.begin();
        for (Enemy e : enemies) e.draw(game.batch);
        player.draw(game.batch);
        game.batch.end();

        // --- 6. Elevated decor (trees, bushes, rocks — renders above entities) ---
        Gdx.gl.glEnable(GL20.GL_BLEND);
        game.batch.begin();
        decor.drawElevatedDecor(game.batch, camX, camY, vpW, vpH);
        game.batch.end();

        // --- 7. Fog-of-war overlay ---
        Gdx.gl.glEnable(GL20.GL_BLEND);
        game.shapes.begin(ShapeRenderer.ShapeType.Filled);

        int pCol = (int)(player.getCenterX() / TileManager.TILE_PX);
        int pRow = (int)(player.getCenterY() / TileManager.TILE_PX);

        int colStart = Math.max(0, (int)(camX / TileManager.TILE_PX) - 1);
        int rowStart = Math.max(0, (int)(camY / TileManager.TILE_PX) - 1);
        int colEnd   = Math.min(mapData.mapWidth,  (int)((camX + vpW) / TileManager.TILE_PX) + 2);
        int rowEnd   = Math.min(mapData.mapHeight, (int)((camY + vpH) / TileManager.TILE_PX) + 2);

        for (int c = colStart; c < colEnd; c++) {
            for (int r = rowStart; r < rowEnd; r++) {
                fog.drawTileFog(game.shapes, c, r, TileManager.TILE_PX);
            }
        }
        game.shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    // -------------------------------------------------------------------------
    // Rendering — screen space
    // -------------------------------------------------------------------------

    private void drawScreenSpace() {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        // HUD — manages its own batch.end()/begin() for ShapeRenderer interleave
        game.batch.begin();
        hud.draw(game.batch, sw, sh, player, score);

        // Minimap — also calls batch.end() then batch.begin() internally
        minimapUI.draw(game.batch, sw, sh, mapData, tileM, fog, player, enemies, gate);

        // Full map overlay
        if (state == GameState.FULL_MAP) {
            fullMapUI.draw(game.batch, sw, sh, mapData, tileM, fog, player, gate);
        }

        game.batch.end();

        // Gate locked label
        drawGateLabel(sw, sh);

        // Game over screen
        if (state == GameState.GAME_OVER) {
            drawGameOver(sw, sh);
        }
    }

    private void drawGateLabel(int sw, int sh) {
        float gWorldX = gate.getLabelX();
        float gWorldY = gate.getLabelY();
        float camX = camera.getWorldOrigin().x;
        float camY = camera.getWorldOrigin().y;
        float gScreenX = gWorldX - camX;
        float gScreenY = gWorldY - camY;

        if (gScreenX < 0 || gScreenX > sw || gScreenY < 0 || gScreenY > sh) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Matrix4 screenProj = new Matrix4().setToOrtho2D(0, 0, sw, sh);
        game.batch.setProjectionMatrix(screenProj);
        game.batch.begin();
        game.fontSmall.setColor(gate.locked ? new Color(0.4f, 0.8f, 1f, 1f) : Color.GREEN);
        game.fontSmall.draw(game.batch,
                gate.locked ? "[ LOCKED ]" : "[ ENTER ]",
                gScreenX - 36, gScreenY + 18);
        game.fontSmall.setColor(Color.WHITE);
        game.batch.end();
    }

    private void drawGameOver(int sw, int sh) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Matrix4 proj = new Matrix4().setToOrtho2D(0, 0, sw, sh);
        game.shapes.setProjectionMatrix(proj);
        game.shapes.begin(ShapeRenderer.ShapeType.Filled);
        game.shapes.setColor(0f, 0f, 0f, 0.72f);
        game.shapes.rect(0, 0, sw, sh);
        game.shapes.end();

        game.batch.setProjectionMatrix(proj);
        game.batch.begin();
        game.font.setColor(0.8f, 0.1f, 0.1f, 1f);
        game.font.draw(game.batch, "DEAD END", sw / 2f - 80, sh / 2f + 40);
        game.fontSmall.setColor(Color.WHITE);
        game.fontSmall.draw(game.batch, "Score: " + score, sw / 2f - 40, sh / 2f);
        game.fontSmall.draw(game.batch, "Press R to restart", sw / 2f - 70, sh / 2f - 30);
        game.batch.end();

        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) { hide(); show(); }
    }

    // =========================================================================
    // Fallback map
    // =========================================================================

    private MapData makeFallbackMap() {
        Gdx.app.log("GameScreen", "Using built-in fallback map (50x50)");
        MapData m = new MapData(1, "The Forgotten Kingdom", 50, 50, 48);
        for (int c = 0; c < 50; c++)
            for (int r = 0; r < 50; r++) m.groundLayer[c][r] = 0;
        for (int c = 0; c < 50; c++) { m.groundLayer[c][0] = 4; m.groundLayer[c][49] = 4; }
        for (int r = 0; r < 50; r++) { m.groundLayer[0][r] = 4; m.groundLayer[49][r] = 4; }
        for (int c = 5; c < 45; c++) m.groundLayer[c][25] = 3;
        for (int r = 5; r < 45; r++) m.groundLayer[25][r] = 3;
        for (int c = 1; c < 49; c++) m.groundLayer[c][15] = 2;
        for (int c = 22; c < 28; c++) m.groundLayer[c][15] = 3;
        m.playerSpawn = new int[]{25, 40};
        m.enemyRegions.add(new MapData.EnemyRegion(10, 10, 5, 3, 0));
        m.enemyRegions.add(new MapData.EnemyRegion(40, 10, 5, 3, 0));
        m.locations.add(new MapData.LocationRecord("Level 2 Gate", 43, 3, 5, 6));
        return m;
    }

    // =========================================================================
    // Screen callbacks
    // =========================================================================

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
        camera.setWorldBounds(tileM.worldWidth(), tileM.worldHeight());
    }

    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}

    @Override
    public void dispose() {
        tileM.dispose();
        player.dispose();
        decor.dispose();
        Enemy.disposeSharedSheet();
    }
}
