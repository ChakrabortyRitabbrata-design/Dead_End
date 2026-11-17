package main;

import java.awt.CardLayout;
import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class Window {
    private static GamePanel currentGamePanel;
    private static PauseMenuPanel pauseMenuPanel;

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

        MainMenuPanel menuPanel = new MainMenuPanel(
            () -> startNewGame(frame, rootPanel, cardLayout),
            () -> {
                frame.dispose();
                System.exit(0);
            }
        );

        pauseMenuPanel = new PauseMenuPanel(
            // RESUME: return to active game
            () -> {
                if (currentGamePanel != null) {
                    cardLayout.show(rootPanel, "GAME");
                    currentGamePanel.resumeGame();
                }
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

        rootPanel.add(menuPanel, "MENU");
        rootPanel.add(pauseMenuPanel, "PAUSE");
        frame.add(rootPanel);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        cardLayout.show(rootPanel, "MENU");
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
