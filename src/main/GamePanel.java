package main;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
//import java.awt.Image;
//import java.awt.Toolkit;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;

import entities.Player;
import entities.Dragon;
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

    KeyHandler keyH = new KeyHandler();

    Thread gameThread;

    public CollisionChecker cChecker = new CollisionChecker(this);
    public Player            player   = new Player(this, keyH);
    public AssetSetter       aSetter  = new AssetSetter(this);
    public SuperObject       obj[]    = new SuperObject[10];
    public Dragon            dragon   = new Dragon(this);

    // ── Inventory System ──────────────────────────────────────────────────────
    public Inventory    inventory;
    public Hotbar       hotbar;
    public InventoryUI  inventoryUI;

    // ── Game State ────────────────────────────────────────────────────────────
    /**
     * Simple two-state flag.
     *   "PLAY"      – normal gameplay, player can move.
     *   "INVENTORY" – inventory open, gameplay paused.
     */
    public String gameState = "PLAY";

    // ── Constructor ───────────────────────────────────────────────────────────

    public GamePanel() {

        this.setPreferredSize(new Dimension(screenWidth, screenHeight));
        this.setBackground(Color.BLACK);
        this.setDoubleBuffered(true);
        this.addKeyListener(keyH);
        this.setFocusable(true);

        // ── FIX: Disable Swing's built-in focus traversal so TAB key is
        //         delivered to our KeyListener instead of cycling focus.
        this.setFocusTraversalKeysEnabled(false);

        // Build inventory objects (InventoryUI registers its own mouse listeners
        // on this panel, so 'this' must be passed after the panel is constructed)
        inventory   = new Inventory();
        hotbar      = new Hotbar(inventory);
        inventoryUI = new InventoryUI(inventory, hotbar, this);

        // ── Left-click → attack (when gameplay is active) ─────────────────────
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();   // keep keyboard focus
                if (e.getButton() == MouseEvent.BUTTON1
                        && gameState.equals("PLAY")) {
                    player.requestAttack();
                }
            }
        });
    }

    // ── Game Thread ───────────────────────────────────────────────────────────

    public void startGameThread() {
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override
    public void run() {

        double drawInterval = 1_000_000_000.0 / FPS;
        long nextDrawTime   = System.nanoTime() + (long) drawInterval;

        while (gameThread != null) {

            update();
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

        // ── TAB: toggle inventory ────────────────────────────────────────────
        if (keyH.inventoryTogglePressed) {
            keyH.inventoryTogglePressed = false;   // consume
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
            keyH.inventoryClosePressed = false;    // consume
            if (gameState.equals("INVENTORY")) {
                gameState = "PLAY";
                inventoryUI.hide();
            }
        }

        // ── Hotbar number keys (work in both states) ──────────────────────────
        if (keyH.hotbarKeyPressed > 0) {
            hotbar.selectByKeyNumber(keyH.hotbarKeyPressed);
            keyH.hotbarKeyPressed = -1;            // consume
        }

        // ── Gameplay updates only when not in inventory ───────────────────────
        if (gameState.equals("PLAY")) {
            player.update();
            dragon.update();
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        // ── World ─────────────────────────────────────────────────────────────
        tileM.draw(g2);

        for (int i = 0; i < obj.length; i++) {
            if (obj[i] != null) obj[i].draw(g2, this);
        }

        dragon.draw(g2);
        player.draw(g2);

        // ── HUD: hotbar always visible ────────────────────────────────────────
        inventoryUI.drawHotbar(g2, screenWidth, screenHeight);

        // ── Inventory overlay (only when open) ────────────────────────────────
        if (gameState.equals("INVENTORY")) {
            inventoryUI.draw(g2, screenWidth, screenHeight);
        }

        g2.dispose();
    }
}
