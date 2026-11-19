package main;

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
import javax.swing.JPanel;

/**
 * Clean, modern RPG Main Menu screen displayed upon launching the game.
 * Features a pure black background, stylized title typography, and
 * interactive RPG-styled buttons.
 */
public class MainMenuPanel extends JPanel {

    public static final int PANEL_WIDTH = 768;
    public static final int PANEL_HEIGHT = 576;

    private final Runnable onStartGame;
    private final Runnable onOpenLeaderboard;
    private final Runnable onLogout;
    private final Runnable onExit;

    private final RpgButton startButton;
    private final RpgButton leaderboardButton;
    private final RpgButton logoutButton;
    private final RpgButton exitButton;

    public MainMenuPanel(Runnable onStartGame, Runnable onExit) {
        this(onStartGame, null, null, onExit);
    }

    public MainMenuPanel(Runnable onStartGame, Runnable onOpenLeaderboard, Runnable onLogout, Runnable onExit) {
        this.onStartGame = onStartGame;
        this.onOpenLeaderboard = onOpenLeaderboard;
        this.onLogout = onLogout;
        this.onExit = onExit;

        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setBackground(Color.BLACK);
        setLayout(null);
        setFocusable(true);

        int buttonWidth = 240;
        int buttonX = (PANEL_WIDTH - buttonWidth) / 2;

        boolean fullMenu = (onOpenLeaderboard != null);
        int buttonHeight = fullMenu ? 40 : 46;

        if (fullMenu) {
            startButton = new RpgButton("START GAME");
            startButton.setBounds(buttonX, 290, buttonWidth, buttonHeight);
            startButton.addActionListener(e -> startGame());

            leaderboardButton = new RpgButton("LEADERBOARD");
            leaderboardButton.setBounds(buttonX, 338, buttonWidth, buttonHeight);
            leaderboardButton.addActionListener(e -> openLeaderboard());

            logoutButton = new RpgButton("SWITCH USER");
            logoutButton.setBounds(buttonX, 386, buttonWidth, buttonHeight);
            logoutButton.addActionListener(e -> logout());

            exitButton = new RpgButton("EXIT");
            exitButton.setBounds(buttonX, 434, buttonWidth, buttonHeight);
            exitButton.addActionListener(e -> exitGame());

            // 4-button keyboard navigation
            KeyAdapter navKeyAdapter = new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int code = e.getKeyCode();
                    if (code == KeyEvent.VK_UP || code == KeyEvent.VK_W) {
                        if (exitButton.isFocusOwner()) logoutButton.requestFocusInWindow();
                        else if (logoutButton.isFocusOwner()) leaderboardButton.requestFocusInWindow();
                        else if (leaderboardButton.isFocusOwner()) startButton.requestFocusInWindow();
                        else exitButton.requestFocusInWindow();
                    } else if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) {
                        if (startButton.isFocusOwner()) leaderboardButton.requestFocusInWindow();
                        else if (leaderboardButton.isFocusOwner()) logoutButton.requestFocusInWindow();
                        else if (logoutButton.isFocusOwner()) exitButton.requestFocusInWindow();
                        else startButton.requestFocusInWindow();
                    } else if (code == KeyEvent.VK_ESCAPE) {
                        exitGame();
                    }
                }
            };

            startButton.addKeyListener(navKeyAdapter);
            leaderboardButton.addKeyListener(navKeyAdapter);
            logoutButton.addKeyListener(navKeyAdapter);
            exitButton.addKeyListener(navKeyAdapter);
            this.addKeyListener(navKeyAdapter);

            add(startButton);
            add(leaderboardButton);
            add(logoutButton);
            add(exitButton);
        } else {
            leaderboardButton = null;
            logoutButton = null;

            startButton = new RpgButton("START GAME");
            startButton.setBounds(buttonX, 310, buttonWidth, buttonHeight);
            startButton.addActionListener(e -> startGame());

            exitButton = new RpgButton("EXIT");
            exitButton.setBounds(buttonX, 376, buttonWidth, buttonHeight);
            exitButton.addActionListener(e -> exitGame());

            KeyAdapter navKeyAdapter = new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int code = e.getKeyCode();
                    if (code == KeyEvent.VK_UP || code == KeyEvent.VK_W) {
                        startButton.requestFocusInWindow();
                    } else if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) {
                        exitButton.requestFocusInWindow();
                    } else if (code == KeyEvent.VK_ESCAPE) {
                        exitGame();
                    }
                }
            };

            startButton.addKeyListener(navKeyAdapter);
            exitButton.addKeyListener(navKeyAdapter);
            this.addKeyListener(navKeyAdapter);

            add(startButton);
            add(exitButton);
        }
    }

    public void focusStartButton() {
        startButton.requestFocusInWindow();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        javax.swing.SwingUtilities.invokeLater(startButton::requestFocusInWindow);
    }

    private void startGame() {
        if (onStartGame != null) onStartGame.run();
    }

    private void openLeaderboard() {
        if (onOpenLeaderboard != null) onOpenLeaderboard.run();
    }

    private void logout() {
        if (onLogout != null) onLogout.run();
    }

    private void exitGame() {
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

        // --- Game Title ---
        String title = "DEAD END";
        Font titleFont = new Font("Georgia", Font.BOLD, 52);
        g2.setFont(titleFont);
        FontMetrics tfm = g2.getFontMetrics();
        int titleWidth = tfm.stringWidth(title);
        int titleX = centerX - titleWidth / 2;
        int titleY = 175;

        // Subtle soft drop shadow
        g2.setColor(new Color(30, 30, 35));
        g2.drawString(title, titleX + 2, titleY + 2);

        // Crisp white title text
        g2.setColor(new Color(245, 245, 250));
        g2.drawString(title, titleX, titleY);

        // --- Divider Line & RPG Accents ---
        int lineWidth = 280;
        int lineY = 205;
        g2.setColor(new Color(75, 75, 82));
        g2.drawLine(centerX - lineWidth / 2, lineY, centerX + lineWidth / 2, lineY);

        // Center diamond accent on divider
        g2.setColor(new Color(220, 220, 230));
        int[] dx = {centerX, centerX + 4, centerX, centerX - 4};
        int[] dy = {lineY - 4, lineY, lineY + 4, lineY};
        g2.fillPolygon(dx, dy, 4);

        // --- Subtitle ---
        String subtitle = "A 2D ACTION RPG";
        Font subFont = new Font("Georgia", Font.PLAIN, 12);
        g2.setFont(subFont);
        FontMetrics sfm = g2.getFontMetrics();
        int subWidth = sfm.stringWidth(subtitle);
        g2.setColor(new Color(130, 130, 140));
        g2.drawString(subtitle, centerX - subWidth / 2, 228);

        // --- User status indicator ---
        String userBanner = SessionManager.isLoggedIn()
                ? "PLAYER: " + SessionManager.getUsername().toUpperCase()
                : "PLAYING AS GUEST";
        g2.setFont(new Font("Georgia", Font.BOLD, 11));
        g2.setColor(SessionManager.isLoggedIn() ? new Color(205, 185, 130) : new Color(135, 140, 150));
        FontMetrics ufm = g2.getFontMetrics();
        g2.drawString(userBanner, centerX - ufm.stringWidth(userBanner) / 2, 254);

        // --- Subtle Bottom Hints ---
        String footerHint = "SELECT WITH MOUSE OR ARROW KEYS  |  PRESS ENTER";
        Font footFont = new Font("SansSerif", Font.PLAIN, 10);
        g2.setFont(footFont);
        FontMetrics ffm = g2.getFontMetrics();
        int footWidth = ffm.stringWidth(footerHint);
        g2.setColor(new Color(70, 70, 75));
        g2.drawString(footerHint, centerX - footWidth / 2, PANEL_HEIGHT - 32);
    }
}
