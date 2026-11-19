package main;

import db.DatabaseManager;
import db.ScoreDAO;
import db.ScoreEntry;
import db.SessionManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Global Leaderboard screen displaying top scores fetched from MySQL.
 * Clean, high-legibility modern RPG layout matching Dead End aesthetic.
 */
public class LeaderboardPanel extends JPanel {

    public static final int PANEL_WIDTH = 768;
    public static final int PANEL_HEIGHT = 576;

    private final Runnable onBack;
    private final RpgButton backButton;
    private final RpgButton refreshButton;

    private List<ScoreEntry> scores = new ArrayList<>();
    private boolean loading = false;
    private String errorMessage = null;

    public LeaderboardPanel(Runnable onBack) {
        this.onBack = onBack;

        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setBackground(Color.BLACK);
        setLayout(null);
        setFocusable(true);

        int buttonWidth = 160;
        int buttonHeight = 40;
        int startX = (PANEL_WIDTH - (buttonWidth * 2 + 20)) / 2;
        int buttonY = PANEL_HEIGHT - 65;

        backButton = new RpgButton("BACK");
        backButton.setBounds(startX, buttonY, buttonWidth, buttonHeight);
        backButton.addActionListener(e -> goBack());

        refreshButton = new RpgButton("REFRESH");
        refreshButton.setBounds(startX + buttonWidth + 20, buttonY, buttonWidth, buttonHeight);
        refreshButton.addActionListener(e -> refreshScores());

        KeyAdapter escAdapter = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    goBack();
                }
            }
        };
        this.addKeyListener(escAdapter);
        backButton.addKeyListener(escAdapter);
        refreshButton.addKeyListener(escAdapter);

        add(backButton);
        add(refreshButton);
    }

    public void focusBackButton() {
        backButton.requestFocusInWindow();
    }

    private void goBack() {
        if (onBack != null) onBack.run();
    }

    public void refreshScores() {
        loading = true;
        errorMessage = null;
        repaint();

        new Thread(() -> {
            try {
                List<ScoreEntry> list = ScoreDAO.getTopScores(10);
                SwingUtilities.invokeLater(() -> {
                    this.scores = list;
                    this.loading = false;
                    this.errorMessage = null;
                    repaint();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    this.scores = new ArrayList<>();
                    this.loading = false;
                    this.errorMessage = "Unable to load scores: " + e.getMessage();
                    repaint();
                });
            }
        }).start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Solid black background
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, getWidth(), getHeight());

        int centerX = getWidth() / 2;

        // Title: LEADERBOARD
        String title = "LEADERBOARD";
        Font titleFont = new Font("Georgia", Font.BOLD, 42);
        g2.setFont(titleFont);
        FontMetrics tfm = g2.getFontMetrics();
        int titleX = centerX - tfm.stringWidth(title) / 2;
        int titleY = 72;

        g2.setColor(new Color(30, 30, 35));
        g2.drawString(title, titleX + 2, titleY + 2);
        g2.setColor(new Color(245, 245, 250));
        g2.drawString(title, titleX, titleY);

        // Divider
        int lineWidth = 320;
        int lineY = 88;
        g2.setColor(new Color(75, 75, 82));
        g2.drawLine(centerX - lineWidth / 2, lineY, centerX + lineWidth / 2, lineY);

        g2.setColor(new Color(220, 220, 230));
        int[] dx = {centerX, centerX + 4, centerX, centerX - 4};
        int[] dy = {lineY - 4, lineY, lineY + 4, lineY};
        g2.fillPolygon(dx, dy, 4);

        // Subtitle
        String sub = "HALL OF FAME — TOP EXPLORERS";
        g2.setFont(new Font("Georgia", Font.PLAIN, 12));
        g2.setColor(new Color(130, 130, 140));
        FontMetrics sfm = g2.getFontMetrics();
        g2.drawString(sub, centerX - sfm.stringWidth(sub) / 2, 106);

        // Current user status
        String userBanner;
        if (SessionManager.isLoggedIn()) {
            int high = ScoreDAO.getUserHighScore(SessionManager.getUserId());
            userBanner = "LOGGED IN AS: " + SessionManager.getUsername().toUpperCase() +
                         "  |  PERSONAL BEST: " + String.format("%,d", high) + " PTS";
        } else {
            userBanner = "PLAYING AS GUEST  |  LOG IN TO SUBMIT SCORES TO LEADERBOARD";
        }
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.setColor(new Color(195, 175, 120));
        FontMetrics ufm = g2.getFontMetrics();
        g2.drawString(userBanner, centerX - ufm.stringWidth(userBanner) / 2, 128);

        // Table Box
        int tableX = 84;
        int tableY = 142;
        int tableW = PANEL_WIDTH - (tableX * 2);
        int tableH = 345;

        g2.setColor(new Color(12, 12, 16));
        g2.fillRect(tableX, tableY, tableW, tableH);
        g2.setColor(new Color(55, 55, 62));
        g2.drawRect(tableX, tableY, tableW, tableH);

        // Table Header
        int headerH = 28;
        g2.setColor(new Color(22, 22, 28));
        g2.fillRect(tableX + 1, tableY + 1, tableW - 2, headerH);
        g2.setColor(new Color(65, 65, 75));
        g2.drawLine(tableX, tableY + headerH, tableX + tableW, tableY + headerH);

        g2.setFont(new Font("Georgia", Font.BOLD, 12));
        g2.setColor(new Color(210, 210, 220));

        int colRankX = tableX + 24;
        int colUserX = tableX + 90;
        int colScoreX = tableX + 340;
        int colDateX = tableX + 460;

        g2.drawString("RANK", colRankX, tableY + 19);
        g2.drawString("PLAYER", colUserX, tableY + 19);
        g2.drawString("SCORE", colScoreX, tableY + 19);
        g2.drawString("DATE", colDateX, tableY + 19);

        // Table Rows
        if (loading) {
            g2.setFont(new Font("Georgia", Font.ITALIC, 14));
            g2.setColor(new Color(180, 180, 190));
            String loadText = "Loading leaderboard data from MySQL...";
            FontMetrics lfm = g2.getFontMetrics();
            g2.drawString(loadText, centerX - lfm.stringWidth(loadText) / 2, tableY + 160);
        } else if (errorMessage != null) {
            g2.setFont(new Font("Georgia", Font.PLAIN, 13));
            g2.setColor(new Color(240, 100, 100));
            FontMetrics efm = g2.getFontMetrics();
            g2.drawString(errorMessage, centerX - efm.stringWidth(errorMessage) / 2, tableY + 160);
        } else if (scores.isEmpty()) {
            g2.setFont(new Font("Georgia", Font.ITALIC, 14));
            g2.setColor(new Color(150, 150, 160));
            String emptyText = "No scores recorded yet. Be the first to defeat monsters and set a record!";
            FontMetrics efm = g2.getFontMetrics();
            g2.drawString(emptyText, centerX - efm.stringWidth(emptyText) / 2, tableY + 160);
        } else {
            int rowY = tableY + headerH + 24;
            int rowHeight = 29;

            for (int i = 0; i < scores.size(); i++) {
                ScoreEntry entry = scores.get(i);
                int currentY = rowY + (i * rowHeight);

                // Alternating row background
                if (i % 2 == 1) {
                    g2.setColor(new Color(18, 18, 22));
                    g2.fillRect(tableX + 1, currentY - 18, tableW - 2, rowHeight);
                }

                // Highlight if current player
                boolean isCurrentPlayer = SessionManager.isLoggedIn() &&
                        SessionManager.getUserId() == entry.getUserId();
                if (isCurrentPlayer) {
                    g2.setColor(new Color(255, 215, 0, 28));
                    g2.fillRect(tableX + 1, currentY - 18, tableW - 2, rowHeight);
                }

                // Rank Color
                Color rankColor;
                if (entry.getRank() == 1) rankColor = new Color(255, 215, 0);     // Gold
                else if (entry.getRank() == 2) rankColor = new Color(220, 225, 235); // Silver
                else if (entry.getRank() == 3) rankColor = new Color(215, 140, 65);  // Bronze
                else rankColor = new Color(160, 160, 170);

                g2.setFont(new Font("Georgia", Font.BOLD, 13));
                g2.setColor(rankColor);
                g2.drawString("#" + entry.getRank(), colRankX, currentY);

                // Player name
                g2.setFont(new Font("Georgia", isCurrentPlayer ? Font.BOLD : Font.PLAIN, 13));
                g2.setColor(isCurrentPlayer ? new Color(255, 235, 140) : new Color(230, 230, 235));
                String nameDisplay = entry.getUsername() + (isCurrentPlayer ? " (YOU)" : "");
                g2.drawString(nameDisplay, colUserX, currentY);

                // Score
                g2.setFont(new Font("Georgia", Font.BOLD, 13));
                g2.setColor(new Color(245, 245, 250));
                g2.drawString(String.format("%,d", entry.getScore()), colScoreX, currentY);

                // Date
                g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
                g2.setColor(new Color(140, 140, 150));
                g2.drawString(entry.getFormattedDate(), colDateX, currentY);
            }
        }
    }
}
