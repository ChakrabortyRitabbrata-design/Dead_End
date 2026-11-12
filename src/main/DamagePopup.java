package main;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Floating damage number popups.
 *
 * Usage:
 *   DamagePopup.spawn(screenX, screenY, amount, isPlayerDamage);
 *   // In GamePanel.update():  DamagePopup.updateAll(deltaNanos);
 *   // In GamePanel.paintComponent():  DamagePopup.drawAll(g2);
 */
public final class DamagePopup {

    // ── Static pool ───────────────────────────────────────────────────────────
    private static final List<DamagePopup> ACTIVE = new ArrayList<>();
    private static final long LIFETIME_NANOS = 1_200_000_000L; // 1.2 seconds
    private static final Font  FONT_BIG   = new Font("Arial", Font.BOLD, 18);
    private static final Font  FONT_SMALL = new Font("Arial", Font.BOLD, 14);

    // ── Instance fields ───────────────────────────────────────────────────────
    private float x;
    private float y;
    private final int   damage;
    private final boolean isPlayerDamage;  // true = red (player took hit), false = white/yellow (enemy took hit)
    private long elapsed;

    // ── Factory ───────────────────────────────────────────────────────────────

    private DamagePopup(float x, float y, int damage, boolean isPlayerDamage) {
        this.x             = x;
        this.y             = y;
        this.damage        = damage;
        this.isPlayerDamage = isPlayerDamage;
    }

    /** Spawn a new damage popup at the given screen coordinates. */
    public static void spawn(int screenX, int screenY, int damage, boolean isPlayerDamage) {
        // Slight random horizontal spread so overlapping numbers separate
        float jitter = (float)(Math.random() * 16 - 8);
        ACTIVE.add(new DamagePopup(screenX + jitter, screenY, damage, isPlayerDamage));
    }

    /** Advance all active popups by the given delta. Call once per frame. */
    public static void updateAll(long deltaNanos) {
        Iterator<DamagePopup> it = ACTIVE.iterator();
        while (it.hasNext()) {
            DamagePopup p = it.next();
            p.elapsed += deltaNanos;
            // Float upward
            float dt = deltaNanos / 1_000_000_000f;
            p.y -= 48 * dt;
            if (p.elapsed >= LIFETIME_NANOS) {
                it.remove();
            }
        }
    }

    /** Draw all active popups. Call inside paintComponent(). */
    public static void drawAll(Graphics2D g2) {
        for (DamagePopup p : ACTIVE) {
            p.draw(g2);
        }
    }

    // ── Instance draw ─────────────────────────────────────────────────────────

    private void draw(Graphics2D g2) {
        float progress = (float) elapsed / LIFETIME_NANOS;
        float alpha = progress < 0.6f ? 1.0f : 1.0f - (progress - 0.6f) / 0.4f;
        alpha = Math.max(0f, Math.min(1f, alpha));

        // Choose color
        Color fill = isPlayerDamage
                ? new Color(240, 40, 40)   // Red for player damage
                : new Color(255, 220, 50);  // Yellow/gold for enemy damage

        // Scale effect: larger near spawn, normal after a bit
        Font font = progress < 0.15f ? FONT_BIG : FONT_SMALL;
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        String text = "-" + damage;
        int textW = fm.stringWidth(text);
        int textH = fm.getAscent();

        int px = (int)(this.x - textW / 2);
        int py = (int)(this.y);

        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Dark outline for readability
        g2.setColor(new Color(0, 0, 0, 200));
        g2.drawString(text, px - 1, py - 1);
        g2.drawString(text, px + 1, py - 1);
        g2.drawString(text, px - 1, py + 1);
        g2.drawString(text, px + 1, py + 1);

        // Fill text
        g2.setColor(fill);
        g2.drawString(text, px, py);

        g2.setComposite(old);
    }

    // avoid unintentional instantiation
    private DamagePopup() { this(0, 0, 0, false); }
}
