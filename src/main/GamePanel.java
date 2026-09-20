package main;

import java.awt.Color;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
//import java.awt.Image;
//import java.awt.Toolkit;

import javax.swing.JPanel;

import entities.Player;
import entities.Dragon;
import tile.TileManager;
import Object.SuperObject;

public class GamePanel extends JPanel implements Runnable {

    // Screen Settings
    final int originalTileSize = 16;
    final int scale = 3;

    public final int tileSize = originalTileSize * scale;

    public final int maxScreenCol = 16;
    public final int maxScreenRow = 12;

    public final int screenWidth = tileSize * maxScreenCol;
    public final int screenHeight = tileSize * maxScreenRow;
    
    public final int maxWorldCol = 100;
    public final int maxWorldRow = 100;
    
    public final int worldWidth = maxWorldCol * tileSize;
    public final int worldHeight = maxWorldRow * tileSize;
    
    public TileManager tileM = new TileManager(this);
    

    int FPS = 60;

    KeyHandler keyH = new KeyHandler();

    Thread gameThread;
    
    public CollisionChecker cChecker = new CollisionChecker(this);
    public Player player = new Player(this, keyH);
    public AssetSetter aSetter = new AssetSetter(this);
    public SuperObject obj[] = new SuperObject[10];
    public Dragon dragon = new Dragon(this);
    
    

    public GamePanel() {

        this.setPreferredSize(
            new Dimension(screenWidth, screenHeight)
        );

        this.setBackground(Color.BLACK);

        this.setDoubleBuffered(true);

        this.addKeyListener(keyH);

        this.setFocusable(true);
    }

    public void startGameThread() {

        gameThread = new Thread(this);

        gameThread.start();
    }

    @Override
    public void run() {

        double drawInterval = 1000000000 / FPS;

        long nextDrawTime =
                System.nanoTime() + (long) drawInterval;

        while (gameThread != null) {

            update();

            repaint();

            try {

                double remainingTime =
                        nextDrawTime - System.nanoTime();

                remainingTime = remainingTime / 1000000;

                if (remainingTime < 0) {
                    remainingTime = 0;
                }

                Thread.sleep((long) remainingTime);

                nextDrawTime += (long) drawInterval;

            } catch (InterruptedException e) {

                e.printStackTrace();
            }
        }
    }
    
    public void setupGame() {
    	  aSetter.setObject();
    }

    public void update() {

        player.update();
        dragon.update();
    }

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        
        tileM.draw(g2);
        
        for (int i = 0; i < obj.length; i++) {
        	if (obj[i] != null) {
        		obj[i].draw(g2, this);
        	}
     }
        dragon.draw(g2);

        player.draw(g2);

        g2.dispose();
    }
}