package main;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;

/**
 * Custom styled button adhering to a modern minimalist RPG aesthetic.
 * Features dark framing, clean white typography, and subtle hover highlights.
 */
public class RpgButton extends JButton {
    private boolean isHovered = false;
    private boolean isPressed = false;

    public RpgButton(String text) {
        super(text);
        setFocusPainted(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                isPressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isPressed = false;
                repaint();
            }
        });

        addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        boolean active = isHovered || isFocusOwner();

        // Background
        if (isPressed) {
            g2.setColor(new Color(255, 255, 255, 35));
            g2.fillRect(0, 0, w, h);
        } else if (active) {
            g2.setColor(new Color(255, 255, 255, 18));
            g2.fillRect(0, 0, w, h);
        } else {
            g2.setColor(new Color(10, 10, 12));
            g2.fillRect(0, 0, w, h);
        }

        // Border
        if (active) {
            g2.setColor(new Color(225, 225, 235));
            g2.drawRect(0, 0, w - 1, h - 1);

            // Subtle corner highlights
            g2.setColor(Color.WHITE);
            int cornerLen = 5;
            // Top-left
            g2.drawLine(0, 0, cornerLen, 0);
            g2.drawLine(0, 0, 0, cornerLen);
            // Top-right
            g2.drawLine(w - 1, 0, w - 1 - cornerLen, 0);
            g2.drawLine(w - 1, 0, w - 1, cornerLen);
            // Bottom-left
            g2.drawLine(0, h - 1, cornerLen, h - 1);
            g2.drawLine(0, h - 1, 0, h - 1 - cornerLen);
            // Bottom-right
            g2.drawLine(w - 1, h - 1, w - 1 - cornerLen, h - 1);
            g2.drawLine(w - 1, h - 1, w - 1, h - 1 - cornerLen);
        } else {
            g2.setColor(new Color(60, 60, 68));
            g2.drawRect(0, 0, w - 1, h - 1);
        }

        // Text
        Font font = new Font("Georgia", Font.BOLD, 15);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();

        String label = getText();
        if (active) {
            label = ">  " + label + "  <";
        }

        int textX = (w - fm.stringWidth(label)) / 2;
        int textY = (h - fm.getHeight()) / 2 + fm.getAscent();

        // Shadow
        g2.setColor(new Color(0, 0, 0, 160));
        g2.drawString(label, textX + 1, textY + 1);

        // Foreground
        g2.setColor(active ? Color.WHITE : new Color(215, 215, 222));
        g2.drawString(label, textX, textY);

        g2.dispose();
    }
}
