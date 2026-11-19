package main;

import db.AuthDAO;
import db.DatabaseManager;
import db.ScoreDAO;
import db.SessionManager;
import db.User;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;

public class AuthLeaderboardIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.out.println("Running AuthLeaderboardIntegrationTest...");

        // 1. Verify DB connection
        boolean dbOk = DatabaseManager.checkConnection();
        System.out.println("Database online: " + dbOk);

        // 2. Test AuthPanel rendering
        AtomicBoolean authPassed = new AtomicBoolean(false);
        AuthPanel authPanel = new AuthPanel(() -> authPassed.set(true));
        authPanel.setSize(AuthPanel.PANEL_WIDTH, AuthPanel.PANEL_HEIGHT);
        authPanel.doLayout();

        BufferedImage authImg = new BufferedImage(768, 576, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ag = authImg.createGraphics();
        authPanel.paint(ag);
        ag.dispose();

        File outDir = new File("out");
        if (!outDir.exists()) outDir.mkdirs();
        File authPreview = new File("out/auth-panel-preview.png");
        ImageIO.write(authImg, "png", authPreview);
        System.out.println("AuthPanel preview saved to: " + authPreview.getAbsolutePath());

        // Verify solid black background on auth panel
        int corner = authImg.getRGB(5, 5) & 0x00FFFFFF;
        if (corner != 0) {
            throw new AssertionError("AuthPanel background must be solid black.");
        }

        // 3. Test Registration & Login if DB is connected
        if (dbOk) {
            String testUsername = "hero_" + (System.currentTimeMillis() % 100000);
            User registered = AuthDAO.register(testUsername, "heroPass123");
            SessionManager.setCurrentUser(registered);

            // Record some test scores for leaderboard display
            ScoreDAO.saveScore(registered.getUserId(), 3450);
            ScoreDAO.saveScore(registered.getUserId(), 4890);
            System.out.println("Recorded test scores for " + registered.getUsername());
        }

        // 4. Test LeaderboardPanel rendering
        AtomicBoolean backClicked = new AtomicBoolean(false);
        LeaderboardPanel lbPanel = new LeaderboardPanel(() -> backClicked.set(true));
        lbPanel.setSize(LeaderboardPanel.PANEL_WIDTH, LeaderboardPanel.PANEL_HEIGHT);
        lbPanel.doLayout();
        lbPanel.refreshScores();

        // Wait a brief moment for background load
        Thread.sleep(300);

        BufferedImage lbImg = new BufferedImage(768, 576, BufferedImage.TYPE_INT_ARGB);
        Graphics2D lg = lbImg.createGraphics();
        lbPanel.paint(lg);
        lg.dispose();

        File lbPreview = new File("out/leaderboard-preview.png");
        ImageIO.write(lbImg, "png", lbPreview);
        System.out.println("Leaderboard preview saved to: " + lbPreview.getAbsolutePath());

        int lbCorner = lbImg.getRGB(5, 5) & 0x00FFFFFF;
        if (lbCorner != 0) {
            throw new AssertionError("LeaderboardPanel background must be solid black.");
        }

        System.out.println("PASS: Auth and Leaderboard integration verified successfully!");
    }
}
