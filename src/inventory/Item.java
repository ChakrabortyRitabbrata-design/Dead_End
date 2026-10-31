package inventory;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Base class for all items in Dead End!!
 * Designed to be extended for weapons, consumables, etc. in future phases.
 */
public class Item {

    // ── Identity ────────────────────────────────────────────────────────────
    public String name;
    public String description;
    public ItemType type;
    public BufferedImage icon; // null = use placeholder

    // ── Stack info ──────────────────────────────────────────────────────────
    public int stackSize;     // current stack count
    public int maxStackSize;  // max allowed in one slot

    // ── Item types (extend as needed in future phases) ───────────────────────
    public enum ItemType {
        MISC,
        WEAPON,
        CONSUMABLE,
        MATERIAL
    }

    // ── Constructors ────────────────────────────────────────────────────────

    public Item(String name, String description, ItemType type, int maxStackSize) {
        this.name        = name;
        this.description = description;
        this.type        = type;
        this.maxStackSize = maxStackSize;
        this.stackSize   = 1;
        this.icon        = null; // use placeholder unless set
    }

    public Item(String name, String description, ItemType type) {
        this(name, description, type, 64);
    }

    // ── Icon generation ─────────────────────────────────────────────────────

    /**
     * Returns the item icon. If no sprite was loaded, generates a coloured
     * placeholder using Java 2D so the inventory still looks good.
     *
     * @param size pixel size (width == height)
     */
    public BufferedImage getIcon(int size) {
        if (icon != null) return icon;
        return generatePlaceholder(size);
    }

    private BufferedImage generatePlaceholder(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background colour driven by item type
        Color bg = switch (type) {
            case WEAPON     -> new Color(180, 60,  60);
            case CONSUMABLE -> new Color(60,  160, 80);
            case MATERIAL   -> new Color(60,  100, 180);
            default         -> new Color(120, 120, 120);
        };

        // Rounded rectangle fill
        g2.setColor(bg);
        g2.fillRoundRect(2, 2, size - 4, size - 4, 8, 8);

        // Border
        g2.setColor(bg.darker());
        g2.drawRoundRect(2, 2, size - 4, size - 4, 8, 8);

        // First letter of item name centred
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, size / 2));
        String label = name != null && !name.isEmpty()
                       ? String.valueOf(name.charAt(0)).toUpperCase()
                       : "?";
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int tx = (size - fm.stringWidth(label)) / 2;
        int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(label, tx, ty);

        g2.dispose();
        return img;
    }

    // ── Copy ────────────────────────────────────────────────────────────────

    /** Returns a shallow copy of this item (same icon reference). */
    public Item copy() {
        Item copy = new Item(name, description, type, maxStackSize);
        copy.stackSize = this.stackSize;
        copy.icon      = this.icon;
        return copy;
    }

    @Override
    public String toString() {
        return name + " x" + stackSize;
    }
}
