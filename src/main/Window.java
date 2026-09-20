package main;

import javax.swing.JFrame;

public class Window {
    public static void main(String[] args) {

        JFrame frame = new JFrame();

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setTitle("Dead End!!");
        frame.setResizable(false);

        GamePanel gamePanel = new GamePanel();
        frame.add(gamePanel);

        frame.pack();

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // 1. Force the keyboard focus onto the GamePanel (Fixes WASD not working)
        gamePanel.requestFocusInWindow();

        // 2. Fixed capitalization (setupGame with capital 'G')
        gamePanel.setupGame();

        // 3. Start the game loop
        gamePanel.startGameThread();
    }
}