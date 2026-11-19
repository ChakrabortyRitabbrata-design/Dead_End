package main;

import inventory.ExecutionerSword;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Non-blocking acquisition popup for the Legendary Executioner Sword.
 * Renders in the top-right corner of GamePanel with smooth fade-in and fade-out.
 */
public class ExecutionerSwordNotification {

    private static final int FADE_IN_TICKS  = 30;  // 0.5 sec
    private static final int LINGER_TICKS   = 180; // 3.0 sec
    private static final int FADE_OUT_TICKS = 30;  // 0.5 sec
    public static final int TOTAL_TICKS    = FADE_IN_TICKS + LINGER_TICKS + FADE_OUT_TICKS; // 240 ticks

    private final GamePanel gp;
    private int ticks = 0;
    private boolean active = false;
    private BufferedImage cachedIcon = null;

    public ExecutionerSwordNotification(GamePanel gp) {
        this.gp = gp;
    }

    public void trigger() {
        this.ticks = 0;
        this.active = true;
        if (cachedIcon == null) {
            cachedIcon = ExecutionerSword.getRawIcon();
        }
    }

    public boolean isActive() {
        return active;
    }

    public void update() {
        if (!active) return;
        ticks++;
        if (ticks >= TOTAL_TICKS) {
            active = false;
        }
    }

    public void draw(Graphics2D g2, int screenW, int screenH) {
        if (!active) return;

        float alpha;
        if (ticks < FADE_IN_TICKS) {
            alpha = (float) ticks / FADE_IN_TICKS;
        } else if (ticks < FADE_IN_TICKS + LINGER_TICKS) {
            alpha = 1.0f;
        } else {
            int remaining = TOTAL_TICKS - ticks;
            alpha = Math.max(0.0f, (float) remaining / FADE_OUT_TICKS);
        }

        if (alpha <= 0.01f) return;

        int popupW = 270;
        int popupH = 68;
        int x = screenW - popupW - 12;
        // Positioned in top-right corner; just below the minimap (y=10..190) to avoid obscuring it
        int y = (gp.minimap != null) ? 198 : 12;

        Composite oldComp = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        Object oldAntialias = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dark translucent background with gothic crimson tint
        g2.setColor(new Color(14, 8, 12, 235));
        g2.fillRoundRect(x, y, popupW, popupH, 10, 10);

        // Subtle crimson glow / outer accent
        g2.setColor(new Color(210, 25, 45, 180));
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawRoundRect(x, y, popupW, popupH, 10, 10);

        // Inner soft highlight border
        g2.setColor(new Color(255, 90, 110, 70));
        g2.setStroke(new BasicStroke(1.0f));
        g2.drawRoundRect(x + 2, y + 2, popupW - 4, popupH - 4, 8, 8);

        // Draw icon
        int iconSize = 48;
        int iconX = x + 10;
        int iconY = y + (popupH - iconSize) / 2;

        // Icon backing slot
        g2.setColor(new Color(28, 12, 18, 220));
        g2.fillRoundRect(iconX - 2, iconY - 2, iconSize + 4, iconSize + 4, 6, 6);
        g2.setColor(new Color(160, 25, 40, 190));
        g2.drawRoundRect(iconX - 2, iconY - 2, iconSize + 4, iconSize + 4, 6, 6);

        BufferedImage icon = (cachedIcon != null) ? cachedIcon : ExecutionerSword.getRawIcon();
        if (icon != null) {
            g2.drawImage(icon, iconX, iconY, iconSize, iconSize, null);
        }

        // Text typography
        int textX = iconX + iconSize + 12;

        // Subtitle / weapon tier
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        g2.setColor(new Color(255, 75, 85));
        g2.drawString("LEGENDARY WEAPON UNLOCKED", textX, y + 18);

        // Weapon Name
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g2.setColor(new Color(255, 235, 235));
        g2.drawString("EXECUTIONER SWORD", textX, y + 36);

        // Tagline
        g2.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        g2.setColor(new Color(240, 185, 75));
        g2.drawString("THE FINAL JUDGMENT", textX, y + 52);

        // Restore graphics state
        if (oldAntialias != null) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialias);
        }
        g2.setComposite(oldComp);
    }
}
