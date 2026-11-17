package main;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class PauseMenuTest {
    private static GamePanel activeGamePanel;

    public static void main(String[] args) throws Exception {
        System.out.println("Running PauseMenuTest...");

        // 1. Direct PauseMenuPanel verification
        AtomicBoolean resumed = new AtomicBoolean(false);
        AtomicBoolean restarted = new AtomicBoolean(false);
        AtomicBoolean exited = new AtomicBoolean(false);

        PauseMenuPanel pauseMenu = new PauseMenuPanel(
            () -> resumed.set(true),
            () -> restarted.set(true),
            () -> exited.set(true)
        );
        pauseMenu.setSize(PauseMenuPanel.PANEL_WIDTH, PauseMenuPanel.PANEL_HEIGHT);
        pauseMenu.doLayout();

        int buttonCount = 0;
        JButton resumeBtn = null, restartBtn = null, exitBtn = null;
        for (Component comp : pauseMenu.getComponents()) {
            if (comp instanceof JButton btn) {
                buttonCount++;
                if ("RESUME".equals(btn.getText())) resumeBtn = btn;
                else if ("RESTART".equals(btn.getText())) restartBtn = btn;
                else if ("EXIT".equals(btn.getText())) exitBtn = btn;
            }
        }

        if (buttonCount != 3 || resumeBtn == null || restartBtn == null || exitBtn == null) {
            throw new AssertionError("PauseMenuPanel must contain exactly RESUME, RESTART, and EXIT buttons.");
        }

        resumeBtn.doClick();
        restartBtn.doClick();
        exitBtn.doClick();
        if (!resumed.get() || !restarted.get() || !exited.get()) {
            throw new AssertionError("PauseMenuPanel button clicks failed to trigger callbacks.");
        }

        // 2. Full Window flow test (Menu -> Start Game -> ESC -> Pause Menu -> Resume)
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        CardLayout cardLayout = new CardLayout();
        JPanel rootPanel = new JPanel(cardLayout);
        rootPanel.setPreferredSize(new Dimension(768, 576));

        AtomicBoolean pauseCardShown = new AtomicBoolean(false);
        AtomicBoolean gameCardShown = new AtomicBoolean(false);

        PauseMenuPanel windowPausePanel = new PauseMenuPanel(
            () -> {
                cardLayout.show(rootPanel, "GAME");
                if (activeGamePanel != null) activeGamePanel.resumeGame();
            },
            () -> {},
            () -> {}
        );

        GamePanel gamePanel = new GamePanel();
        gamePanel.onPause = () -> {
            cardLayout.show(rootPanel, "PAUSE");
            pauseCardShown.set(true);
        };
        gamePanel.onResume = () -> {
            cardLayout.show(rootPanel, "GAME");
            gameCardShown.set(true);
        };
        activeGamePanel = gamePanel;

        rootPanel.add(gamePanel, "GAME");
        rootPanel.add(windowPausePanel, "PAUSE");
        frame.add(rootPanel);
        frame.pack();
        frame.setVisible(true);

        cardLayout.show(rootPanel, "GAME");
        gamePanel.setupGame();

        // Initial state
        if (gamePanel.gameState != GamePanel.GameState.PLAYING) {
            throw new AssertionError("Initial state should be PLAYING");
        }

        // Simulate ESC in game
        long now = System.currentTimeMillis();
        gamePanel.keyH.keyPressed(new KeyEvent(gamePanel, KeyEvent.KEY_PRESSED, now, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
        gamePanel.update(16_666_667L);

        if (gamePanel.gameState != GamePanel.GameState.PAUSED) {
            throw new AssertionError("State should be PAUSED after pressing ESC");
        }
        if (!pauseCardShown.get()) {
            throw new AssertionError("Pause card should be displayed when game is paused");
        }

        // Capture rootPanel while paused and verify Pause Menu (Heading and Buttons) are fully rendered
        BufferedImage pauseCapture = new BufferedImage(768, 576, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = pauseCapture.createGraphics();
        rootPanel.paint(g);
        g.dispose();

        File previewFile = new File("out/pause-menu-preview.png");
        ImageIO.write(pauseCapture, "png", previewFile);

        // Verify that the pause menu is NOT just solid black (text, heading, buttons must be visible)
        int nonBlackPixels = 0;
        for (int y = 0; y < 576; y++) {
            for (int x = 0; x < 768; x++) {
                if ((pauseCapture.getRGB(x, y) & 0x00FFFFFF) != 0) nonBlackPixels++;
            }
        }
        if (nonBlackPixels < 1000) {
            throw new AssertionError("Pause menu was rendered solid black! Non-black pixel count: " + nonBlackPixels);
        }
        System.out.println("Pause menu capture successfully verified (non-black pixels: " + nonBlackPixels + ")");

        // Test ESC while paused resumes the game
        windowPausePanel.dispatchEvent(new KeyEvent(windowPausePanel, KeyEvent.KEY_PRESSED, now + 50, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
        if (gamePanel.gameState != GamePanel.GameState.PLAYING) {
            throw new AssertionError("State should be PLAYING after pressing ESC in pause menu");
        }

        frame.dispose();
        System.out.println("PASS: All Pause Menu tests passed successfully!");
    }
}
