package main;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
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

        spawnMonsters();
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
        drawPlayerHealthBar(g2);

        // ── HUD: hotbar always visible at bottom ──────────────────────────────
        inventoryUI.drawHotbar(g2, screenWidth, screenHeight);

        // ── Inventory overlay (only when open) ────────────────────────────────
        if (gameState.equals("INVENTORY")) {
            inventoryUI.draw(g2, screenWidth, screenHeight);
        }

        g2.dispose();
    }

    private void drawPlayerHealthBar(Graphics2D g2) {
        int barWidth = 180;
        int barHeight = 18;
        int x = 16;
        int y = screenHeight - barHeight - 16;
        int health = Math.max(0, Math.min(player.getMaxHealth(), player.getHealth()));
        int fillWidth = barWidth * health / player.getMaxHealth();

        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRect(x - 6, y - 22, barWidth + 12, barHeight + 28);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g2.drawString("HP " + health + "/" + player.getMaxHealth(), x, y - 6);
        g2.setColor(Color.DARK_GRAY);
        g2.fillRect(x, y, barWidth, barHeight);
        g2.setColor(new Color(210, 35, 35));
        g2.fillRect(x, y, fillWidth, barHeight);
        g2.setColor(Color.WHITE);
        g2.drawRect(x, y, barWidth, barHeight);
    }
}
