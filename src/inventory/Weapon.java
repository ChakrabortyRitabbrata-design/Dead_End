package inventory;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Weapon is a specialised Item with combat statistics.
 *
 * Compatible with EquipmentSlot.MAIN_HAND.
 */
public class Weapon extends Item {

    // ── Weapon stats ──────────────────────────────────────────────────────────
    public int    damage;       // base damage per hit
    public int    attackSpeed;  // lower = faster (ticks between swings)
    public int    range;        // attack range in pixels (future use)
    public String weaponClass;  // "Sword", "Axe", "Bow", etc.

    // ── Constructor ───────────────────────────────────────────────────────────

    public Weapon(String name, String description,
                  int damage, int attackSpeed, int range, String weaponClass) {
        super(name, description, ItemType.WEAPON, 1); // weapons don't stack
        this.damage         = damage;
        this.attackSpeed    = attackSpeed;
        this.range          = range;
        this.weaponClass    = weaponClass;
        this.compatibleSlot = EquipmentSlot.MAIN_HAND;
    }

    // ── Icon override: draw a weapon silhouette using Java 2D ────────────────

    @Override
    public BufferedImage getIcon(int size) {
        if (icon != null) return icon;
        return generateWeaponIcon(size);
    }

    private BufferedImage generateWeaponIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        // Background: dark red for weapons
        g2.setColor(new Color(60, 20, 20));
        g2.fillRoundRect(1, 1, size - 2, size - 2, 8, 8);
        g2.setColor(new Color(120, 40, 40));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(1, 1, size - 2, size - 2, 8, 8);

        int pad = size / 7;

        switch (weaponClass) {
            case "Sword" -> drawSword(g2, size, pad);
            case "Axe"   -> drawAxe(g2, size, pad);
            case "Bow"   -> drawBow(g2, size, pad);
            default      -> drawSword(g2, size, pad);
        }

        // Weapon name abbreviated at bottom
        g2.setFont(new Font("Arial", Font.BOLD, Math.max(7, size / 6)));
        g2.setColor(new Color(220, 180, 180));
        String abbr = weaponClass.substring(0, Math.min(3, weaponClass.length()));
        java.awt.FontMetrics fm = g2.getFontMetrics();
        g2.drawString(abbr,
                (size - fm.stringWidth(abbr)) / 2,
                size - 3);

        g2.dispose();
        return img;
    }

    private void drawSword(Graphics2D g2, int size, int pad) {
        // Blade: diagonal line from top-right to centre
        g2.setColor(new Color(200, 210, 220));
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawLine(size - pad, pad, pad + 2, size - pad - 6);

        // Guard: small crossbar
        g2.setColor(new Color(180, 140, 40));
        g2.setStroke(new BasicStroke(3f));
        int gx = pad + (size - 2 * pad) / 3;
        int gy = pad + 2 * (size - 2 * pad) / 3;
        g2.drawLine(gx - 5, gy - 5, gx + 5, gy + 5);

        // Handle
        g2.setColor(new Color(130, 80, 30));
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawLine(pad + 2, size - pad - 6, pad, size - pad);
    }

    private void drawAxe(Graphics2D g2, int size, int pad) {
        // Shaft
        g2.setColor(new Color(130, 80, 30));
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawLine(pad + 2, size - pad, size - pad - 2, pad + 4);

        // Axe head (triangle-ish polygon)
        int[] xs = { size - pad - 2, size - pad - 8, size - pad + 2 };
        int[] ys = { pad + 4, pad + 14, pad + 14 };
        g2.setColor(new Color(190, 200, 210));
        g2.fillPolygon(xs, ys, 3);
        g2.setColor(new Color(140, 150, 160));
        g2.drawPolygon(xs, ys, 3);
    }

    private void drawBow(Graphics2D g2, int size, int pad) {
        // Arc (bow body)
        g2.setColor(new Color(130, 80, 30));
        g2.setStroke(new BasicStroke(2f));
        g2.drawArc(pad, pad, size - 2 * pad, size - 2 * pad, 60, 240);

        // String
        g2.setColor(new Color(220, 210, 180));
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(size / 2, pad + 3, size / 2, size - pad - 3);
    }

    // ── Copy ──────────────────────────────────────────────────────────────────

    @Override
    public Item copy() {
        Weapon w = new Weapon(name, description, damage, attackSpeed, range, weaponClass);
        w.icon = this.icon;
        w.compatibleSlot = this.compatibleSlot;
        return w;
    }

    @Override
    public String toString() {
        return name + " [" + weaponClass + " | DMG:" + damage + "]";
    }
}
