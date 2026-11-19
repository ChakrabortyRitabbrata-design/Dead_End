package main;

import db.SessionManager;
import java.awt.CardLayout;
import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class Window {
    private static GamePanel currentGamePanel;
    private static PauseMenuPanel pauseMenuPanel;
    private static LeaderboardPanel leaderboardPanel;
    private static AuthPanel authPanel;
    private static MainMenuPanel menuPanel;
    private static String returnScreen = "MENU";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Window::open);
    }

    private static void open() {
        JFrame frame = new JFrame();

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setTitle("Dead End!!");
        frame.setResizable(false);

        CardLayout cardLayout = new CardLayout();
        JPanel rootPanel = new JPanel(cardLayout);
        rootPanel.setPreferredSize(new Dimension(MainMenuPanel.PANEL_WIDTH, MainMenuPanel.PANEL_HEIGHT));

        // 1. Auth Panel (Login / Register / Guest)
        authPanel = new AuthPanel(() -> {
            cardLayout.show(rootPanel, "MENU");
            menuPanel.repaint();
            SwingUtilities.invokeLater(menuPanel::focusStartButton);
        });

        // 2. Leaderboard Panel
        leaderboardPanel = new LeaderboardPanel(() -> {
            if ("PAUSE".equals(returnScreen)) {
                cardLayout.show(rootPanel, "PAUSE");
                SwingUtilities.invokeLater(pauseMenuPanel::focusResumeButton);
            } else if ("GAME".equals(returnScreen)) {
                cardLayout.show(rootPanel, "GAME");
                if (currentGamePanel != null) {
                    SwingUtilities.invokeLater(currentGamePanel::requestFocusInWindow);
                }
            } else {
                cardLayout.show(rootPanel, "MENU");
                menuPanel.repaint();
                SwingUtilities.invokeLater(menuPanel::focusStartButton);
            }
        });

        // 3. Main Menu Panel (with Leaderboard & Logout)
        menuPanel = new MainMenuPanel(
            // Start Game
            () -> startNewGame(frame, rootPanel, cardLayout),
            // Open Leaderboard
            () -> {
                returnScreen = "MENU";
                leaderboardPanel.refreshScores();
                cardLayout.show(rootPanel, "LEADERBOARD");
                SwingUtilities.invokeLater(leaderboardPanel::focusBackButton);
            },
            // Logout / Switch User
            () -> {
                SessionManager.logout();
                authPanel.resetFields();
                cardLayout.show(rootPanel, "AUTH");
                SwingUtilities.invokeLater(authPanel::focusUsernameField);
            },
            // Exit
            () -> {
                frame.dispose();
                System.exit(0);
            }
        );

        // 4. Pause Menu Panel
        pauseMenuPanel = new PauseMenuPanel(
            // RESUME: return to active game
            () -> {
                if (currentGamePanel != null) {
                    cardLayout.show(rootPanel, "GAME");
                    currentGamePanel.resumeGame();
                }
            },
            // LEADERBOARD: view leaderboard during pause
            () -> {
                returnScreen = "PAUSE";
                leaderboardPanel.refreshScores();
                cardLayout.show(rootPanel, "LEADERBOARD");
                SwingUtilities.invokeLater(leaderboardPanel::focusBackButton);
            },
            // RESTART: start fresh session
            () -> startNewGame(frame, rootPanel, cardLayout),
            // EXIT: close application
            () -> {
                if (currentGamePanel != null) {
                    currentGamePanel.stopGameThread();
                }
                frame.dispose();
                System.exit(0);
            }
        );

        rootPanel.add(authPanel, "AUTH");
        rootPanel.add(menuPanel, "MENU");
        rootPanel.add(pauseMenuPanel, "PAUSE");
        rootPanel.add(leaderboardPanel, "LEADERBOARD");
        frame.add(rootPanel);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // Launch into authentication screen
        cardLayout.show(rootPanel, "AUTH");
        SwingUtilities.invokeLater(authPanel::focusUsernameField);
    }

    private static void startNewGame(JFrame frame, JPanel rootPanel, CardLayout cardLayout) {
        if (currentGamePanel != null) {
            currentGamePanel.stopGameThread();
            rootPanel.remove(currentGamePanel);
        }

        GamePanel gamePanel = new GamePanel(
            () -> startNewGame(frame, rootPanel, cardLayout), // RESTART action
            () -> { // EXIT action
                if (currentGamePanel != null) {
                    currentGamePanel.stopGameThread();
                }
                frame.dispose();
                System.exit(0);
            }
        );

        // When game pauses, transition to the PAUSE card
        gamePanel.onPause = () -> {
            cardLayout.show(rootPanel, "PAUSE");
            SwingUtilities.invokeLater(() -> {
                pauseMenuPanel.requestFocusInWindow();
                pauseMenuPanel.focusResumeButton();
            });
        };

        // When game resumes, transition back to the GAME card
        gamePanel.onResume = () -> {
            cardLayout.show(rootPanel, "GAME");
            SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
        };

        // When leaderboard requested in-game (e.g. game over / complete / L key)
        gamePanel.onOpenLeaderboard = () -> {
            returnScreen = "GAME";
            leaderboardPanel.refreshScores();
            cardLayout.show(rootPanel, "LEADERBOARD");
            SwingUtilities.invokeLater(leaderboardPanel::focusBackButton);
        };

        currentGamePanel = gamePanel;

        rootPanel.add(gamePanel, "GAME");
        cardLayout.show(rootPanel, "GAME");
        rootPanel.revalidate();
        rootPanel.repaint();

        // 1. Force keyboard focus onto the GamePanel
        SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);

        // 2. Setup game objects and monsters
        gamePanel.setupGame();

        // 3. Start the game loop
        gamePanel.startGameThread();
    }
}
