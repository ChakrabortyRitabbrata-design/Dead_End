package inventory;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Armor is a specialised Item with a defence rating and an equipment slot.
 *
 * Armor items can only be placed in the equipment slot matching their
 * {@link #equipSlot} field. They do not stack (maxStackSize = 1).
 */
public class Armor extends Item {

    // ── Armor stats ───────────────────────────────────────────────────────────
    public int    defense;     // damage reduction per hit
    public String armorClass;  // "Helmet", "Chestplate", "Leggings", "Boots", "Shield"

    /** The equipment slot this piece of armor fits into. */
    public EquipmentSlot equipSlot;

    // ── Constructor ──────────────────────────────────────────────────────────

    public Armor(String name, String description,
                 int defense, String armorClass, EquipmentSlot equipSlot) {
        super(name, description, ItemType.ARMOR, 1); // armor never stacks
        this.defense        = defense;
        this.armorClass     = armorClass;
        this.equipSlot      = equipSlot;
        this.compatibleSlot = equipSlot;
    }

    // ── Icon override ────────────────────────────────────────────────────────

    @Override
    public BufferedImage getIcon(int size) {
        if (icon != null) return icon;
        return generateArmorIcon(size);
    }

    private BufferedImage generateArmorIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        // Dark steel background
        g2.setColor(new Color(30, 40, 55));
        g2.fillRoundRect(1, 1, size - 2, size - 2, 8, 8);
        g2.setColor(new Color(70, 90, 120));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(1, 1, size - 2, size - 2, 8, 8);

        int p = size / 8;

        switch (armorClass) {
            case "Helmet"     -> drawHelmet(g2, size, p);
            case "Chestplate" -> drawChestplate(g2, size, p);
            case "Leggings"   -> drawLeggings(g2, size, p);
            case "Boots"      -> drawBoots(g2, size, p);
            case "Shield"     -> drawShield(g2, size, p);
            default           -> drawHelmet(g2, size, p);
        }

        // Abbreviation label
        g2.setFont(new Font("Arial", Font.BOLD, Math.max(7, size / 6)));
        g2.setColor(new Color(170, 200, 230));
        String abbr = armorClass.substring(0, Math.min(3, armorClass.length()));
        java.awt.FontMetrics fm = g2.getFontMetrics();
        g2.drawString(abbr, (size - fm.stringWidth(abbr)) / 2, size - 3);

        g2.dispose();
        return img;
    }

    private void drawHelmet(Graphics2D g2, int size, int p) {
        g2.setColor(new Color(150, 170, 200));
        // dome
        g2.fillArc(p, p, size - 2 * p, (size - 2 * p) / 2 + 4, 0, 180);
        // brim
        g2.fillRect(p, p + (size - 2 * p) / 2, size - 2 * p, 5);
        // visor slit
        g2.setColor(new Color(20, 30, 50));
        g2.fillRect(p + 4, p + (size - 2 * p) / 2 - 4, size - 2 * p - 8, 4);
    }

    private void drawChestplate(Graphics2D g2, int size, int p) {
        g2.setColor(new Color(150, 170, 200));
        // body
        int bx = p + 2, by = p + 4, bw = size - 2 * p - 4, bh = size - 2 * p - 8;
        g2.fillRoundRect(bx, by, bw, bh, 6, 6);
        // centre ridge
        g2.setColor(new Color(100, 120, 150));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(size / 2, by + 2, size / 2, by + bh - 2);
        // shoulder pauldrons
        g2.setColor(new Color(130, 150, 180));
        g2.fillOval(bx - 3, by, 10, 8);
        g2.fillOval(bx + bw - 7, by, 10, 8);
    }

    private void drawLeggings(Graphics2D g2, int size, int p) {
        g2.setColor(new Color(150, 170, 200));
        int lw = (size - 2 * p - 4) / 2;
        int lh = size - 2 * p - 4;
        // left leg
        g2.fillRoundRect(p + 2, p + 2, lw, lh, 4, 4);
        // right leg
        g2.fillRoundRect(p + 2 + lw + 2, p + 2, lw, lh, 4, 4);
        // waistband
        g2.setColor(new Color(100, 120, 150));
        g2.fillRect(p + 2, p + 2, lw * 2 + 2, 6);
    }

    private void drawBoots(Graphics2D g2, int size, int p) {
        g2.setColor(new Color(150, 170, 200));
        int bw = size - 2 * p;
        int bh = size - 2 * p;
        // ankle
        g2.fillRoundRect(p, p + 2, bw, bh / 2, 4, 4);
        // sole / toe
        g2.fillRoundRect(p - 2, p + bh / 2, bw + 4, bh / 2, 6, 6);
        // toe cap darker
        g2.setColor(new Color(100, 120, 150));
        g2.fillRoundRect(p + bw / 2, p + bh / 2, bw / 2, bh / 2, 4, 4);
    }

    private void drawShield(Graphics2D g2, int size, int p) {
        g2.setColor(new Color(150, 170, 200));
        int[] xs = { size / 2, p, p, size - p, size - p };
        int[] ys = { size - p - 2, p + 4, p, p, p + 4 };
        g2.fillPolygon(xs, ys, 5);
        // boss (centre knob)
        g2.setColor(new Color(200, 160, 60));
        g2.fillOval(size / 2 - 4, size / 2 - 6, 8, 8);
        // rim
        g2.setColor(new Color(80, 100, 130));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawPolygon(xs, ys, 5);
    }

    // ── Copy ──────────────────────────────────────────────────────────────────

    @Override
    public Item copy() {
        Armor a = new Armor(name, description, defense, armorClass, equipSlot);
        a.icon = this.icon;
        a.compatibleSlot = this.compatibleSlot;
        return a;
    }

    @Override
    public String toString() {
        return name + " [" + armorClass + " | DEF:" + defense + "]";
    }
}
