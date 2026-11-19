package main;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * In-game Pause Menu screen.
 * Displays a completely black background with heading "GAME PAUSED"
 * and interactive RPG buttons with full keyboard and mouse support.
 */
public class PauseMenuPanel extends JPanel {

    public static final int PANEL_WIDTH = 768;
    public static final int PANEL_HEIGHT = 576;

    private final Runnable onResume;
    private final Runnable onOpenLeaderboard;
    private final Runnable onRestart;
    private final Runnable onExit;

    private final RpgButton resumeButton;
    private final RpgButton leaderboardButton;
    private final RpgButton restartButton;
    private final RpgButton exitButton;

    public PauseMenuPanel(Runnable onResume, Runnable onRestart, Runnable onExit) {
        this(onResume, null, onRestart, onExit);
    }

    public PauseMenuPanel(Runnable onResume, Runnable onOpenLeaderboard, Runnable onRestart, Runnable onExit) {
        this.onResume = onResume;
        this.onOpenLeaderboard = onOpenLeaderboard;
        this.onRestart = onRestart;
        this.onExit = onExit;

        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setBackground(Color.BLACK);
        setLayout(null);
        setFocusable(true);

        int buttonWidth = 240;
        int buttonX = (PANEL_WIDTH - buttonWidth) / 2;

        boolean hasLeaderboard = (onOpenLeaderboard != null);
        int buttonHeight = hasLeaderboard ? 40 : 46;

        if (hasLeaderboard) {
            resumeButton = new RpgButton("RESUME");
            resumeButton.setBounds(buttonX, 260, buttonWidth, buttonHeight);
            resumeButton.addActionListener(e -> resume());

            leaderboardButton = new RpgButton("LEADERBOARD");
            leaderboardButton.setBounds(buttonX, 308, buttonWidth, buttonHeight);
            leaderboardButton.addActionListener(e -> openLeaderboard());

            restartButton = new RpgButton("RESTART");
            restartButton.setBounds(buttonX, 356, buttonWidth, buttonHeight);
            restartButton.addActionListener(e -> restart());

            exitButton = new RpgButton("EXIT");
            exitButton.setBounds(buttonX, 404, buttonWidth, buttonHeight);
            exitButton.addActionListener(e -> exit());

            KeyAdapter navKeyAdapter = new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int code = e.getKeyCode();
                    if (code == KeyEvent.VK_ESCAPE) {
                        resume();
                    } else if (code == KeyEvent.VK_UP || code == KeyEvent.VK_W) {
                        if (exitButton.isFocusOwner()) restartButton.requestFocusInWindow();
                        else if (restartButton.isFocusOwner()) leaderboardButton.requestFocusInWindow();
                        else if (leaderboardButton.isFocusOwner()) resumeButton.requestFocusInWindow();
                        else exitButton.requestFocusInWindow();
                    } else if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) {
                        if (resumeButton.isFocusOwner()) leaderboardButton.requestFocusInWindow();
                        else if (leaderboardButton.isFocusOwner()) restartButton.requestFocusInWindow();
                        else if (restartButton.isFocusOwner()) exitButton.requestFocusInWindow();
                        else resumeButton.requestFocusInWindow();
                    }
                }
            };

            resumeButton.addKeyListener(navKeyAdapter);
            leaderboardButton.addKeyListener(navKeyAdapter);
            restartButton.addKeyListener(navKeyAdapter);
            exitButton.addKeyListener(navKeyAdapter);
            this.addKeyListener(navKeyAdapter);

            add(resumeButton);
            add(leaderboardButton);
            add(restartButton);
            add(exitButton);
        } else {
            leaderboardButton = null;

            resumeButton = new RpgButton("RESUME");
            resumeButton.setBounds(buttonX, 275, buttonWidth, buttonHeight);
            resumeButton.addActionListener(e -> resume());

            restartButton = new RpgButton("RESTART");
            restartButton.setBounds(buttonX, 340, buttonWidth, buttonHeight);
            restartButton.addActionListener(e -> restart());

            exitButton = new RpgButton("EXIT");
            exitButton.setBounds(buttonX, 405, buttonWidth, buttonHeight);
            exitButton.addActionListener(e -> exit());

            KeyAdapter navKeyAdapter = new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int code = e.getKeyCode();
                    if (code == KeyEvent.VK_ESCAPE) {
                        resume();
                    } else if (code == KeyEvent.VK_UP || code == KeyEvent.VK_W) {
                        if (exitButton.isFocusOwner()) restartButton.requestFocusInWindow();
                        else if (restartButton.isFocusOwner()) resumeButton.requestFocusInWindow();
                        else exitButton.requestFocusInWindow();
                    } else if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) {
                        if (resumeButton.isFocusOwner()) restartButton.requestFocusInWindow();
                        else if (restartButton.isFocusOwner()) exitButton.requestFocusInWindow();
                        else resumeButton.requestFocusInWindow();
                    }
                }
            };

            resumeButton.addKeyListener(navKeyAdapter);
            restartButton.addKeyListener(navKeyAdapter);
            exitButton.addKeyListener(navKeyAdapter);
            this.addKeyListener(navKeyAdapter);

            add(resumeButton);
            add(restartButton);
            add(exitButton);
        }
    }

    public void focusResumeButton() {
        resumeButton.requestFocusInWindow();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(this::focusResumeButton);
    }

    private void resume() {
        if (onResume != null) onResume.run();
    }

    private void openLeaderboard() {
        if (onOpenLeaderboard != null) onOpenLeaderboard.run();
    }

    private void restart() {
        if (onRestart != null) onRestart.run();
    }

    private void exit() {
        if (onExit != null) onExit.run();
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

        // --- Heading: GAME PAUSED ---
        String heading = "GAME PAUSED";
        Font headingFont = new Font("Georgia", Font.BOLD, 46);
        g2.setFont(headingFont);
        FontMetrics hfm = g2.getFontMetrics();
        int headingWidth = hfm.stringWidth(heading);
        int headingX = centerX - headingWidth / 2;
        int headingY = 165;

        // Soft drop shadow
        g2.setColor(new Color(30, 30, 35));
        g2.drawString(heading, headingX + 2, headingY + 2);

        // Crisp white text
        g2.setColor(new Color(245, 245, 250));
        g2.drawString(heading, headingX, headingY);

        // --- Divider Line with Diamond Accent ---
        int lineWidth = 280;
        int lineY = 196;
        g2.setColor(new Color(75, 75, 82));
        g2.drawLine(centerX - lineWidth / 2, lineY, centerX + lineWidth / 2, lineY);

        g2.setColor(new Color(220, 220, 230));
        int[] dx = {centerX, centerX + 4, centerX, centerX - 4};
        int[] dy = {lineY - 4, lineY, lineY + 4, lineY};
        g2.fillPolygon(dx, dy, 4);

        // --- Subtitle ---
        String subtitle = "CURRENT PROGRESS PRESERVED";
        Font subFont = new Font("Georgia", Font.PLAIN, 12);
        g2.setFont(subFont);
        FontMetrics sfm = g2.getFontMetrics();
        int subWidth = sfm.stringWidth(subtitle);
        g2.setColor(new Color(130, 130, 140));
        g2.drawString(subtitle, centerX - subWidth / 2, 220);

        // --- Subtle Bottom Hints ---
        String footerHint = "PRESS ESC TO RESUME  |  ARROW KEYS TO SELECT  |  ENTER TO CONFIRM";
        Font footFont = new Font("SansSerif", Font.PLAIN, 10);
        g2.setFont(footFont);
        FontMetrics ffm = g2.getFontMetrics();
        int footWidth = ffm.stringWidth(footerHint);
        g2.setColor(new Color(70, 70, 75));
        g2.drawString(footerHint, centerX - footWidth / 2, PANEL_HEIGHT - 32);
    }
}
