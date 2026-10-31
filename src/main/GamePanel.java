package main;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.swing.JPanel;

import entities.Dragon;
import entities.Monster;
import entities.Player;
import tile.MapData;
import tile.TileManager;
import Object.SuperObject;

public class GamePanel extends JPanel implements Runnable {

    // =========================================================================
    // Screen / world constants (unchanged)
    // =========================================================================

    final int originalTileSize = 16;
    final int scale            = 3;

    public final int tileSize      = originalTileSize * scale;  // 48 px

    public final int maxScreenCol  = 16;
    public final int maxScreenRow  = 12;
    public final int screenWidth   = tileSize * maxScreenCol;   // 768 px
    public final int screenHeight  = tileSize * maxScreenRow;   // 576 px

    // World dimensions — updated dynamically when a level is loaded.
    // Initialised to 100×100 so TileManager can allocate arrays before the first
    // map file is parsed; overwritten by applyMapDimensions() during level loading.
    public int maxWorldCol = 100;
    public int maxWorldRow = 100;
    public int worldWidth  = maxWorldCol * tileSize;
    public int worldHeight = maxWorldRow * tileSize;

    // =========================================================================
    // Game state
    // =========================================================================

    public enum GameState { PLAYING, LEVEL_TRANSITION, FULL_MAP, GAME_OVER, GAME_COMPLETE }
    public GameState gameState = GameState.PLAYING;

    public int score = 0;

    // =========================================================================
    // Core subsystems (order matters — some reference each other)
    // =========================================================================

    public TileManager    tileM   = new TileManager(this);
    public KeyHandler     keyH    = new KeyHandler();
    public CollisionChecker cChecker = new CollisionChecker(this);
    public Player         player  = new Player(this, keyH);
    public AssetSetter    aSetter = new AssetSetter(this);
    public FogOfWar       fogOfWar;
    public Minimap        minimap;
    public LevelManager   levelM;

    // =========================================================================
    // World objects
    // =========================================================================

    /** Replaces the old SuperObject[10] array — no hard slot limit. */
    public List<SuperObject> objects = new ArrayList<>();

    // =========================================================================
    // Entities
    // =========================================================================

    public Dragon   dragon       = null;
    public boolean  dragonActive = false;

    public final List<Monster> monsters = new ArrayList<>();
    static final int MONSTER_RESPAWN_DELAY = 90;
    private int monsterRespawnCounter = MONSTER_RESPAWN_DELAY;

    private final Random random = new Random();

    // =========================================================================
    // Game loop
    // =========================================================================

    int FPS = 60;
    Thread gameThread;

    // =========================================================================
    // Construction
    // =========================================================================

    public GamePanel() {
        this.setPreferredSize(new Dimension(screenWidth, screenHeight));
        this.setBackground(Color.BLACK);
        this.setDoubleBuffered(true);
        this.setFocusable(true);

        this.addKeyListener(keyH);
        this.addFocusListener(keyH);
        this.addMouseListener(keyH);

        // Initialise fog with current dimensions (updated after each loadLevel)
        fogOfWar = new FogOfWar(maxWorldCol, maxWorldRow);
        minimap  = new Minimap(this);
        levelM   = new LevelManager(this);

        // Wire minimap reference into keyH so click detection works
        keyH.minimap = minimap;

        // Apply player spawn from Level 1 map data
        applyPlayerSpawn();

        // Activate dragon if Level 1 meta contains BOSS_DRAGON (currently disabled)
        activateDragonFromMap();
    }

    // =========================================================================
    // Dynamic map dimensions — called by TileManager after loading a level
    // =========================================================================

    /**
     * Updates world-dimension fields to match the newly loaded map.
     * Also resizes the fog-of-war array so it covers the new dimensions.
     * Must be called after TileManager has finished loading the tile arrays.
     */
    public void applyMapDimensions(int cols, int rows) {
        maxWorldCol = cols;
        maxWorldRow = rows;
        worldWidth  = cols * tileSize;
        worldHeight = rows * tileSize;
        if (fogOfWar != null) fogOfWar.resize(cols, rows);
        if (minimap  != null) minimap.buildColorCache();
    }

    // =========================================================================
    // Setup (called from Window after the frame is visible)
    // =========================================================================

    public void setupGame() {
        aSetter.setObject();
        spawnMonstersForLevel();
    }

    public void startGameThread() {
        gameThread = new Thread(this);
        gameThread.start();
    }

    // =========================================================================
    // Game loop
    // =========================================================================

    @Override
    public void run() {
        double drawInterval = 1_000_000_000.0 / FPS;
        long nextDrawTime   = System.nanoTime() + (long) drawInterval;

        while (gameThread != null) {
            update();
            repaint();

            try {
                double remaining = nextDrawTime - System.nanoTime();
                if (remaining < 0) remaining = 0;
                Thread.sleep((long)(remaining / 1_000_000));
                nextDrawTime += (long) drawInterval;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // =========================================================================
    // Update
    // =========================================================================

    public void update() {
        // --- Input routing (works in ALL states) ---
        if (keyH.consumeMapToggle())                          toggleFullMap();
        if (keyH.consumeEscape() && gameState == GameState.FULL_MAP) toggleFullMap();

        // --- Level transition animation tick ---
        if (gameState == GameState.LEVEL_TRANSITION) {
            levelM.updateTransition();
            return;
        }

        // --- Early return for non-PLAYING states ---
        if (gameState != GameState.PLAYING) return;

        // --- Fog of war ---
        fogOfWar.update(player.worldX / tileSize, player.worldY / tileSize);

        // --- Entities ---
        player.update();

        if (dragonActive && dragon != null) dragon.update();

        Iterator<Monster> it = monsters.iterator();
        while (it.hasNext()) {
            Monster m = it.next();
            m.update();
            if (m.isDead()) it.remove();
        }

        replenishMonsters();
        levelM.checkProgression();
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        // --- World ---
        tileM.draw(g2);

        // --- Objects ---
        for (SuperObject obj : objects) {
            if (obj != null) obj.draw(g2, this);
        }

        // --- Entities ---
        if (dragonActive && dragon != null) dragon.draw(g2);
        for (Monster m : monsters) m.draw(g2);
        player.draw(g2);

        // --- HUD minimap (always drawn in PLAYING / FULL_MAP states) ---
        if (gameState == GameState.PLAYING || gameState == GameState.FULL_MAP) {
            minimap.drawHUD(g2);
        }

        // --- Full-screen map overlay ---
        if (gameState == GameState.FULL_MAP) {
            minimap.drawFullMap(g2);
        }

        // --- Level transition fade ---
        if (gameState == GameState.LEVEL_TRANSITION && levelM.fadeAlpha > 0) {
            g2.setColor(new Color(0, 0, 0, Math.min(255, levelM.fadeAlpha)));
            g2.fillRect(0, 0, screenWidth, screenHeight);
        }

        // --- Game complete screen ---
        if (gameState == GameState.GAME_COMPLETE) {
            drawEndScreen(g2, "YOU SURVIVED", new Color(255, 215, 0));
        }

        // --- Game over screen ---
        if (gameState == GameState.GAME_OVER) {
            drawEndScreen(g2, "DEAD END", new Color(180, 0, 0));
        }

        g2.dispose();
    }

    private void drawEndScreen(Graphics2D g2, String text, Color color) {
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRect(0, 0, screenWidth, screenHeight);
        g2.setFont(new Font("Arial", Font.BOLD, 48));
        g2.setColor(color);
        var fm = g2.getFontMetrics();
        g2.drawString(text, (screenWidth - fm.stringWidth(text)) / 2,
                screenHeight / 2 - fm.getHeight() / 2 + fm.getAscent());
        g2.setFont(new Font("Arial", Font.PLAIN, 16));
        g2.setColor(Color.WHITE);
        String sub = "Score: " + score;
        fm = g2.getFontMetrics();
        g2.drawString(sub, (screenWidth - fm.stringWidth(sub)) / 2,
                screenHeight / 2 + 40);
    }

    // =========================================================================
    // Map toggle
    // =========================================================================

    public void toggleFullMap() {
        if (gameState == GameState.PLAYING) {
            keyH.clearAllInputs();
            gameState = GameState.FULL_MAP;
            minimap.resetFullMapView();
        } else if (gameState == GameState.FULL_MAP) {
            minimap.resetDrag();
            gameState = GameState.PLAYING;
        }
    }

    // =========================================================================
    // Monster management
    // =========================================================================

    public void spawnMonstersForLevel() {
        monsters.clear();
        monsterRespawnCounter = MONSTER_RESPAWN_DELAY;

        if (tileM.currentMap == null) return;
        for (MapData.EnemyRegion region : tileM.currentMap.enemyRegions) {
            for (int i = 0; i < region.maxCount(); i++) {
                spawnMonsterInRegion(region);
            }
        }
    }

    private void spawnMonsterInRegion(MapData.EnemyRegion region) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist  = Math.sqrt(random.nextDouble()) * region.radius();
            int col = region.centerCol() + (int)(Math.cos(angle) * dist);
            int row = region.centerRow() + (int)(Math.sin(angle) * dist);

            if (isValidSpawnTile(col, row, region)) {
                monsters.add(new Monster(this, col * tileSize, row * tileSize));
                return;
            }
        }
        // All 10 attempts failed — skip; respawn logic will retry later
    }

    private boolean isValidSpawnTile(int col, int row, MapData.EnemyRegion region) {
        // 1. World bounds
        if (col < 0 || col >= maxWorldCol || row < 0 || row >= maxWorldRow) return false;
        // 2. Within the circular region
        int dc = col - region.centerCol(), dr = row - region.centerRow();
        if (dc * dc + dr * dr > region.radius() * region.radius()) return false;
        // 3. Tile type collision
        int tileId = tileM.mapTilenum[col][row];
        if (tileM.tile[tileId].collision) return false;
        // 4. Collision layer
        if (tileM.collisionLayer[col][row] == 1) return false;
        // 5. Not occupied by another monster (within 1 tile)
        int wx = col * tileSize, wy = row * tileSize;
        for (Monster m : monsters) {
            if (Math.abs(m.worldX - wx) < tileSize && Math.abs(m.worldY - wy) < tileSize)
                return false;
        }
        // 6. Minimum 5 tiles from player
        int pcol = player.worldX / tileSize, prow = player.worldY / tileSize;
        int pdc = col - pcol, pdr = row - prow;
        if (pdc * pdc + pdr * pdr < 25) return false;
        return true;
    }

    private void replenishMonsters() {
        if (tileM.currentMap == null || tileM.currentMap.enemyRegions.isEmpty()) return;

        int totalMax = tileM.currentMap.enemyRegions.stream()
                .mapToInt(MapData.EnemyRegion::maxCount).sum();

        if (monsters.size() >= totalMax) {
            monsterRespawnCounter = MONSTER_RESPAWN_DELAY;
            return;
        }
        monsterRespawnCounter--;
        if (monsterRespawnCounter <= 0) {
            // Pick a random region weighted by its maxCount
            List<MapData.EnemyRegion> regions = tileM.currentMap.enemyRegions;
            MapData.EnemyRegion region = regions.get(random.nextInt(regions.size()));
            spawnMonsterInRegion(region);
            monsterRespawnCounter = MONSTER_RESPAWN_DELAY;
        }
    }

    // =========================================================================
    // Spawn helpers
    // =========================================================================

    private void applyPlayerSpawn() {
        if (tileM.currentMap != null) {
            int[] spawn = tileM.currentMap.playerSpawn;
            player.worldX = spawn[0] * tileSize;
            player.worldY = spawn[1] * tileSize;
        }
    }

    private void activateDragonFromMap() {
        if (tileM.currentMap != null && tileM.currentMap.dragonSpawn != null) {
            dragon = new Dragon(this);
            int[] ds = tileM.currentMap.dragonSpawn;
            dragon.activate(ds[0], ds[1]);
            dragonActive = true;
        }
    }
}
