package main;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.JButton;

/**
 * Headless test to verify MainMenuPanel UI, component hierarchy,
 * styling, and actions.
 */
public class MainMenuTest {
    public static void main(String[] args) throws Exception {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean exited = new AtomicBoolean(false);

        MainMenuPanel menu = new MainMenuPanel(
            () -> started.set(true),
            () -> exited.set(true)
        );
        menu.setSize(MainMenuPanel.PANEL_WIDTH, MainMenuPanel.PANEL_HEIGHT);
        menu.doLayout();

        // 1. Verify dimensions
        if (menu.getPreferredSize().width != 768 || menu.getPreferredSize().height != 576) {
            throw new AssertionError("Menu dimensions should be 768x576");
        }

        // 2. Verify button count & labels
        int buttonCount = 0;
        JButton startBtn = null;
        JButton exitBtn = null;

        for (Component comp : menu.getComponents()) {
            if (comp instanceof JButton btn) {
                buttonCount++;
                if ("START GAME".equals(btn.getText())) {
                    startBtn = btn;
                } else if ("EXIT".equals(btn.getText())) {
                    exitBtn = btn;
                }
            }
        }

        if (buttonCount != 2) {
            throw new AssertionError("Expected exactly 2 buttons, found: " + buttonCount);
        }
        if (startBtn == null) {
            throw new AssertionError("START GAME button not found");
        }
        if (exitBtn == null) {
            throw new AssertionError("EXIT button not found");
        }

        // 3. Verify actions
        startBtn.doClick();
        if (!started.get()) {
            throw new AssertionError("START GAME action was not triggered");
        }

        exitBtn.doClick();
        if (!exited.get()) {
            throw new AssertionError("EXIT action was not triggered");
        }

        // 4. Verify rendering to image and save preview
        BufferedImage image = new BufferedImage(768, 576, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        menu.paint(g2);
        g2.dispose();

        // Check corner pixels are solid black
        int topLeft = image.getRGB(5, 5) & 0x00FFFFFF;
        int topRight = image.getRGB(760, 5) & 0x00FFFFFF;
        if (topLeft != 0 || topRight != 0) {
            throw new AssertionError("Menu background must be completely black. Found: " + Integer.toHexString(topLeft));
        }

        File outDir = new File("out");
        if (!outDir.exists()) outDir.mkdirs();
        File previewFile = new File("out/main-menu-preview.png");
        ImageIO.write(image, "png", previewFile);
        System.out.println("Preview saved to: " + previewFile.getAbsolutePath());

        System.out.println("PASS: MainMenuPanel verification passed successfully!");
    }
}
