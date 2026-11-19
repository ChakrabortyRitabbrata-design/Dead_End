package main;

import db.AuthDAO;
import db.DatabaseManager;
import db.SessionManager;
import db.User;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Authentication screen allowing players to Login, Register, or Play as Guest.
 * Adheres to the minimalist modern RPG dark aesthetic of Dead End.
 */
public class AuthPanel extends JPanel {

    public static final int PANEL_WIDTH = 768;
    public static final int PANEL_HEIGHT = 576;

    private final Runnable onAuthSuccess;

    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final RpgButton loginButton;
    private final RpgButton registerButton;
    private final RpgButton guestButton;

    private String statusMessage = "";
    private Color statusColor = Color.GRAY;

    public AuthPanel(Runnable onAuthSuccess) {
        this.onAuthSuccess = onAuthSuccess;

        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setBackground(Color.BLACK);
        setLayout(null);
        setFocusable(true);

        int centerX = PANEL_WIDTH / 2;
        int fieldWidth = 260;
        int fieldHeight = 36;
        int fieldX = centerX - fieldWidth / 2;

        // Username Field
        usernameField = createStyledTextField();
        usernameField.setBounds(fieldX, 230, fieldWidth, fieldHeight);

        // Password Field
        passwordField = createStyledPasswordField();
        passwordField.setBounds(fieldX, 296, fieldWidth, fieldHeight);

        int buttonWidth = 260;
        int buttonHeight = 40;

        // Login Button
        loginButton = new RpgButton("LOGIN");
        loginButton.setBounds(fieldX, 360, buttonWidth, buttonHeight);
        loginButton.addActionListener(e -> performLogin());

        // Register Button
        registerButton = new RpgButton("REGISTER NEW ACCOUNT");
        registerButton.setBounds(fieldX, 408, buttonWidth, buttonHeight);
        registerButton.addActionListener(e -> performRegister());

        // Guest Button
        guestButton = new RpgButton("PLAY AS GUEST");
        guestButton.setBounds(fieldX, 456, buttonWidth, buttonHeight);
        guestButton.addActionListener(e -> playAsGuest());

        // Enter key listeners on fields
        KeyAdapter enterSubmit = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performLogin();
                }
            }
        };
        usernameField.addKeyListener(enterSubmit);
        passwordField.addKeyListener(enterSubmit);

        add(usernameField);
        add(passwordField);
        add(loginButton);
        add(registerButton);
        add(guestButton);

        // Check DB on background thread and update status
        new Thread(() -> {
            boolean ok = DatabaseManager.checkConnection();
            SwingUtilities.invokeLater(() -> {
                if (ok) {
                    statusMessage = "Connected to database. Log in or create an account.";
                    statusColor = new Color(130, 210, 140);
                } else {
                    statusMessage = "Database offline. You can still Play as Guest.";
                    statusColor = new Color(220, 160, 80);
                }
                repaint();
            });
        }).start();
    }

    private JTextField createStyledTextField() {
        JTextField tf = new JTextField();
        tf.setFont(new Font("Georgia", Font.PLAIN, 14));
        tf.setBackground(new Color(14, 14, 18));
        tf.setForeground(new Color(235, 235, 240));
        tf.setCaretColor(Color.WHITE);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(65, 65, 75)),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        return tf;
    }

    private JPasswordField createStyledPasswordField() {
        JPasswordField pf = new JPasswordField();
        pf.setFont(new Font("Georgia", Font.PLAIN, 14));
        pf.setBackground(new Color(14, 14, 18));
        pf.setForeground(new Color(235, 235, 240));
        pf.setCaretColor(Color.WHITE);
        pf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(65, 65, 75)),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        return pf;
    }

    public void focusUsernameField() {
        usernameField.requestFocusInWindow();
    }

    public void resetFields() {
        usernameField.setText("");
        passwordField.setText("");
        statusMessage = "";
        repaint();
    }

    private void performLogin() {
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());

        if (user.isEmpty() || pass.isEmpty()) {
            setStatus("Please enter both username and password.", new Color(240, 90, 90));
            return;
        }

        setStatus("Authenticating...", new Color(200, 200, 210));

        new Thread(() -> {
            try {
                User loggedIn = AuthDAO.login(user, pass);
                SessionManager.setCurrentUser(loggedIn);
                SwingUtilities.invokeLater(() -> {
                    setStatus("Welcome back, " + loggedIn.getUsername() + "!", new Color(110, 225, 120));
                    if (onAuthSuccess != null) onAuthSuccess.run();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setStatus(ex.getMessage(), new Color(240, 90, 90));
                });
            }
        }).start();
    }

    private void performRegister() {
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());

        if (user.isEmpty() || pass.isEmpty()) {
            setStatus("Please choose a username and password.", new Color(240, 90, 90));
            return;
        }

        setStatus("Creating account...", new Color(200, 200, 210));

        new Thread(() -> {
            try {
                User registered = AuthDAO.register(user, pass);
                SessionManager.setCurrentUser(registered);
                SwingUtilities.invokeLater(() -> {
                    setStatus("Account created! Welcome, " + registered.getUsername() + "!", new Color(110, 225, 120));
                    if (onAuthSuccess != null) onAuthSuccess.run();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setStatus(ex.getMessage(), new Color(240, 90, 90));
                });
            }
        }).start();
    }

    private void playAsGuest() {
        SessionManager.logout();
        setStatus("Playing in Guest Mode.", new Color(200, 200, 210));
        if (onAuthSuccess != null) onAuthSuccess.run();
    }

    private void setStatus(String msg, Color color) {
        this.statusMessage = msg;
        this.statusColor = color;
        repaint();
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

        // Heading: DEAD END
        String title = "DEAD END";
        Font titleFont = new Font("Georgia", Font.BOLD, 46);
        g2.setFont(titleFont);
        FontMetrics tfm = g2.getFontMetrics();
        int titleX = centerX - tfm.stringWidth(title) / 2;
        int titleY = 115;

        g2.setColor(new Color(30, 30, 35));
        g2.drawString(title, titleX + 2, titleY + 2);
        g2.setColor(new Color(245, 245, 250));
        g2.drawString(title, titleX, titleY);

        // Divider
        int lineWidth = 260;
        int lineY = 138;
        g2.setColor(new Color(75, 75, 82));
        g2.drawLine(centerX - lineWidth / 2, lineY, centerX + lineWidth / 2, lineY);

        g2.setColor(new Color(220, 220, 230));
        int[] dx = {centerX, centerX + 4, centerX, centerX - 4};
        int[] dy = {lineY - 4, lineY, lineY + 4, lineY};
        g2.fillPolygon(dx, dy, 4);

        // Subtitle
        String sub = "PLAYER AUTHENTICATION";
        g2.setFont(new Font("Georgia", Font.PLAIN, 12));
        g2.setColor(new Color(130, 130, 140));
        FontMetrics sfm = g2.getFontMetrics();
        g2.drawString(sub, centerX - sfm.stringWidth(sub) / 2, 158);

        // Field Labels
        g2.setFont(new Font("Georgia", Font.BOLD, 12));
        g2.setColor(new Color(195, 195, 205));
        g2.drawString("USERNAME", centerX - 130, 222);
        g2.drawString("PASSWORD", centerX - 130, 288);

        // Status Message
        if (statusMessage != null && !statusMessage.isEmpty()) {
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(statusColor);
            FontMetrics mfm = g2.getFontMetrics();
            g2.drawString(statusMessage, centerX - mfm.stringWidth(statusMessage) / 2, 342);
        }

        // Footer hint
        String footerHint = "USE TAB TO SWITCH FIELDS  |  ENTER TO SUBMIT";
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g2.setColor(new Color(70, 70, 75));
        FontMetrics ffm = g2.getFontMetrics();
        g2.drawString(footerHint, centerX - ffm.stringWidth(footerHint) / 2, PANEL_HEIGHT - 24);
    }
}
