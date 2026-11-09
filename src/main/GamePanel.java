package main;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.swing.JPanel;

import entities.Player;
import entities.Dragon;
import entities.Monster;
import inventory.EquipmentManager;
import inventory.Hotbar;
import inventory.Inventory;
import inventory.InventoryUI;
import tile.TileManager;
import Object.SuperObject;

public class GamePanel extends JPanel implements Runnable {

    // ── Screen Settings ───────────────────────────────────────────────────────
    final int originalTileSize = 16;
    final int scale = 3;

    public final int tileSize = originalTileSize * scale;

    public final int maxScreenCol = 16;
    public final int maxScreenRow = 12;

    public final int screenWidth  = tileSize * maxScreenCol;
    public final int screenHeight = tileSize * maxScreenRow;

    public final int maxWorldCol = 100;
    public final int maxWorldRow = 100;

    public final int worldWidth  = maxWorldCol * tileSize;
    public final int worldHeight = maxWorldRow * tileSize;

    // ── Game Systems ──────────────────────────────────────────────────────────
    public TileManager tileM = new TileManager(this);

    int FPS = 60;

    public KeyHandler keyH = new KeyHandler();

    Thread gameThread;
    private long lastUpdateNanos;

    public CollisionChecker cChecker = new CollisionChecker(this);
    public Player            player   = new Player(this, keyH);
    public AssetSetter       aSetter  = new AssetSetter(this);
    public SuperObject       obj[]    = new SuperObject[10];
    public Dragon            dragon   = new Dragon(this);

    // ── Monsters ──────────────────────────────────────────────────────────────
    public final List<Monster> monsters = new ArrayList<>();
    private static final int MAX_MONSTERS = 10;
    private static final int MONSTER_RESPAWN_DELAY = 90;
    private int monsterRespawnCounter = MONSTER_RESPAWN_DELAY;
    private int nextSpawnIndex;
    private final Random random = new Random();

    // ── Inventory and Equipment Systems ───────────────────────────────────────
    public Inventory        inventory        = new Inventory();
    public Hotbar           hotbar           = new Hotbar(inventory);
    public EquipmentManager equipmentManager = new EquipmentManager();
    public InventoryUI      inventoryUI;

    // ── Heart UI assets (generated procedurally) ──────────────────────────────
    private BufferedImage heartFull;
    private BufferedImage heartHalf;
    private BufferedImage heartEmpty;

    // ── Game State ────────────────────────────────────────────────────────────
    /**
     * "PLAY"      – normal gameplay, player and monsters update.
     * "INVENTORY" – inventory open, gameplay paused.
     */
    public String gameState = "PLAY";

    // ── Constructor ───────────────────────────────────────────────────────────

    public GamePanel() {

        this.setPreferredSize(new Dimension(screenWidth, screenHeight));
        this.setBackground(Color.BLACK);
        this.setDoubleBuffered(true);

        this.addKeyListener(keyH);
        this.addFocusListener(keyH);
        this.addMouseListener(keyH);

        this.setFocusable(true);

        // ── Disable Swing's built-in focus traversal so TAB reaches KeyListener
        this.setFocusTraversalKeysEnabled(false);

        // ── UI Initialization ─────────────────────────────────────────────────
        inventoryUI = new InventoryUI(inventory, hotbar, equipmentManager, this);

        // ── Generate heart sprites ────────────────────────────────────────────
        heartFull  = createHeartImage(true,  false);
        heartHalf  = createHeartImage(true,  true);
        heartEmpty = createHeartImage(false, false);

        spawnMonsters();
    }

    // ── Heart image generation ────────────────────────────────────────────────

    /**
     * Draws a pixel-art heart icon (20×20 px) into a BufferedImage.
     * @param filled  whether the heart has any fill
     * @param half    whether only the left half is filled (half heart)
     */
    private BufferedImage createHeartImage(boolean filled, boolean half) {
        int size = 20;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // Heart pixel pattern (14x12 heart centered in 20x20)
        // Each cell is 1px; pattern rows from top
        int[] row0 = {3,4,5,6,  8,9,10,11};
        int[] row1 = {2,3,4,5,6,7, 7,8,9,10,11,12};
        int[] row2 = {1,2,3,4,5,6,7,8,9,10,11,12,13};
        int[] row3 = {1,2,3,4,5,6,7,8,9,10,11,12,13};
        int[] row4 = {2,3,4,5,6,7,8,9,10,11,12};
        int[] row5 = {3,4,5,6,7,8,9,10,11};
        int[] row6 = {4,5,6,7,8,9,10};
        int[] row7 = {5,6,7,8,9};
        int[] row8 = {6,7,8};
        int[] row9 = {7};
        int[][] rows = {row0,row1,row2,row3,row4,row5,row6,row7,row8,row9};
        int startY = 4;

        Color fillColor  = new Color(220, 40, 40);
        Color halfColor  = new Color(180, 30, 30);
        Color darkColor  = new Color(60,  60, 60);
        Color emptyColor = new Color(80, 80, 80, 180);
        Color outlineCol = new Color(20, 20, 20, 240);

        for (int r = 0; r < rows.length; r++) {
            for (int px : rows[r]) {
                int drawX = px + 2;
                int drawY = startY + r;
                Color c;
                if (!filled) {
                    c = emptyColor;
                } else if (half && drawX >= size / 2) {
                    c = emptyColor;
                } else {
                    c = (r <= 1 && drawX < size / 2 + 2) ? new Color(255, 100, 100) : fillColor;
                }
                img.setRGB(drawX, drawY, c.getRGB() | 0xFF000000);
            }
        }

        // Simple 1px outline
        for (int r = 0; r < rows.length; r++) {
            for (int px : rows[r]) {
                int drawX = px + 2;
                int drawY = startY + r;
                // Check neighbors
                boolean edge = false;
                if (r == 0 || drawX == rows[r][0] + 2 || drawX == rows[r][rows[r].length-1] + 2) edge = true;
                if (edge) {
                    // Will just draw outline dots around non-transparent pixels
                }
            }
        }

        g.dispose();
        return img;
    }

    // ── Game Thread ───────────────────────────────────────────────────────────

    public void startGameThread() {
        gameThread = new Thread(this);
        lastUpdateNanos = System.nanoTime();
        gameThread.start();
    }

    @Override
    public void run() {

        double drawInterval = 1_000_000_000.0 / FPS;
        long nextDrawTime   = System.nanoTime() + (long) drawInterval;

        while (gameThread != null) {

            long now = System.nanoTime();
            long deltaNanos = now - lastUpdateNanos;
            lastUpdateNanos = now;
            update(deltaNanos);

            repaint();

            try {
                double remainingTime = (nextDrawTime - System.nanoTime()) / 1_000_000.0;
                if (remainingTime < 0) remainingTime = 0;
                Thread.sleep((long) remainingTime);
                nextDrawTime += (long) drawInterval;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    public void setupGame() {
        aSetter.setObject();
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public void update() {
        update(16_666_667L);
    }

    public void update(long deltaNanos) {

        // ── TAB: toggle inventory ────────────────────────────────────────────
        if (keyH.inventoryTogglePressed) {
            keyH.inventoryTogglePressed = false;
            if (gameState.equals("PLAY")) {
                gameState = "INVENTORY";
                inventoryUI.show();
            } else {
                gameState = "PLAY";
                inventoryUI.hide();
            }
        }

        // ── ESC: close inventory ──────────────────────────────────────────────
        if (keyH.inventoryClosePressed) {
            keyH.inventoryClosePressed = false;
            if (gameState.equals("INVENTORY")) {
                gameState = "PLAY";
                inventoryUI.hide();
            }
        }

        // ── Hotbar number keys (1–9) ─────────────────────────────────────────
        if (keyH.hotbarKeyPressed > 0) {
            hotbar.selectByKeyNumber(keyH.hotbarKeyPressed);
            keyH.hotbarKeyPressed = -1;
        }

        // ── Gameplay updates only when playing (not in inventory) ─────────────
        if (gameState.equals("PLAY")) {
            player.update(deltaNanos);
            dragon.update();

            Iterator<Monster> iterator = monsters.iterator();
            while (iterator.hasNext()) {
                Monster monster = iterator.next();
                monster.update();
                if (monster.isDead()) {
                    iterator.remove();
                }
            }
            replenishMonsters();

            // ── Advance damage popups ─────────────────────────────────────────
            DamagePopup.updateAll(deltaNanos);
        }
    }

    // ── Monster Spawning ──────────────────────────────────────────────────────

    private void spawnMonsters() {
        while (monsters.size() < MAX_MONSTERS) {
            addMonsterAtSpawnPoint();
        }
    }

    private void replenishMonsters() {
        if (monsters.size() >= MAX_MONSTERS) {
            monsterRespawnCounter = MONSTER_RESPAWN_DELAY;
            return;
        }

        monsterRespawnCounter--;
        if (monsterRespawnCounter <= 0) {
            addMonsterAtSpawnPoint();
            monsterRespawnCounter = MONSTER_RESPAWN_DELAY;
        }
    }

    private void addMonsterAtSpawnPoint() {
        int[][] spawnTiles = {
                {43, 26}, {52, 26}, {43, 34}, {54, 35},
                {20, 18}, {28, 22}, {35, 40}, {58, 48},
                {66, 62}, {32, 72}
        };
        int[] spawn = spawnTiles[nextSpawnIndex % spawnTiles.length];
        nextSpawnIndex++;

        if (random.nextBoolean()) {
            int offset = random.nextInt(3) - 1;
            spawn = new int[] {spawn[0] + offset, spawn[1]};
        }
        monsters.add(new Monster(this, spawn[0] * tileSize, spawn[1] * tileSize));
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        // ── World tiles ───────────────────────────────────────────────────────
        tileM.draw(g2);

        // ── Objects ───────────────────────────────────────────────────────────
        for (int i = 0; i < obj.length; i++) {
            if (obj[i] != null) obj[i].draw(g2, this);
        }

        // ── Dragon Boss ───────────────────────────────────────────────────────
        dragon.draw(g2);

        // ── Monsters ──────────────────────────────────────────────────────────
        for (Monster monster : monsters) {
            monster.draw(g2);
        }

        // ── Player ────────────────────────────────────────────────────────────
        player.draw(g2);

        // ── Damage popups ─────────────────────────────────────────────────────
        DamagePopup.drawAll(g2);

        // ── HUD: hearts + hotbar always visible ───────────────────────────────
        drawPlayerHearts(g2);
        inventoryUI.drawHotbar(g2, screenWidth, screenHeight);

        // ── Inventory overlay (only when open) ────────────────────────────────
        if (gameState.equals("INVENTORY")) {
            inventoryUI.draw(g2, screenWidth, screenHeight);
        }

        g2.dispose();
    }

    // ── Heart HUD ─────────────────────────────────────────────────────────────

    /**
     * Draws pixel heart icons in the top-left corner.
     * Max health = 100, 10 hearts, each worth 10 HP.
     * Full heart  = 10 HP, half heart = 5 HP, empty = 0 HP.
     */
    private void drawPlayerHearts(Graphics2D g2) {
        final int MAX_HEARTS = 10;
        final int HEART_SIZE = 20;
        final int HEART_GAP  = 3;
        final int START_X    = 14;
        final int START_Y    = 14;

        int currentHP = Math.max(0, Math.min(player.getMaxHealth(), player.getHealth()));
        int maxHP     = player.getMaxHealth();

        // Semi-transparent panel behind hearts
        int panelW = MAX_HEARTS * (HEART_SIZE + HEART_GAP) + 4;
        int panelH = HEART_SIZE + 8;
        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRoundRect(START_X - 6, START_Y - 4, panelW, panelH, 8, 8);

        for (int i = 0; i < MAX_HEARTS; i++) {
            int hpThreshold    = (i + 1) * (maxHP / MAX_HEARTS);
            int halfThreshold  = i * (maxHP / MAX_HEARTS) + (maxHP / MAX_HEARTS / 2);
            int drawX = START_X + i * (HEART_SIZE + HEART_GAP);
            int drawY = START_Y;

            BufferedImage heart;
            if (currentHP >= hpThreshold) {
                heart = heartFull;
            } else if (currentHP > halfThreshold) {
                heart = heartHalf;
            } else {
                heart = heartEmpty;
            }
            g2.drawImage(heart, drawX, drawY, HEART_SIZE, HEART_SIZE, null);
        }

        // HP text below hearts
        g2.setColor(new Color(200, 200, 200));
        g2.setFont(new Font("Arial", Font.PLAIN, 10));
        g2.drawString(currentHP + "/" + maxHP + " HP", START_X, START_Y + HEART_SIZE + 10);
    }
}
