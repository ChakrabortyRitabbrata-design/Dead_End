package inventory;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * The legendary Executioner Sword.
 * Unlocked only in Region 3 with cumulative progression score >= 250.
 *
 * Special combat attributes:
 * - 1-hit kill against regular zombies
 * - Exactly 50% max health damage against Water Dragons (2 hits to defeat)
 * - Sprinting disabled while equipped due to immense weight
 */
public class ExecutionerSword extends Weapon {

    public static final String ID = "executioner_sword";
    public static final String NAME = "EXECUTIONER SWORD";
    public static final String RARITY = "LEGENDARY";
    public static final String SUBTITLE = "THE FINAL JUDGMENT";
    public static final int BASE_DAMAGE = 150;
    public static final int ATTACK_SPEED = 35;
    public static final int RANGE = 64;

    private static BufferedImage rawIcon = null;
    private static final Map<Integer, BufferedImage> scaledIconCache = new HashMap<>();

    public ExecutionerSword() {
        super(NAME, "THE FINAL JUDGMENT — A colossal crimson-bladed executioner sword of immense weight.",
              BASE_DAMAGE, ATTACK_SPEED, RANGE, "Sword");
        this.id = ID;
        this.rarity = RARITY;
        this.compatibleSlot = EquipmentSlot.MAIN_HAND;
        this.icon = getIcon(42);
    }

    public static BufferedImage getRawIcon() {
        if (rawIcon == null) {
            rawIcon = loadIconAsset();
        }
        return rawIcon;
    }

    private static BufferedImage loadIconAsset() {
        String[] possiblePaths = {
            "res/ExecutionerSword/Inventory-icon.png",
            "res/ExecutionerSword/inventory-icon.png",
            "bin/ExecutionerSword/Inventory-icon.png",
            "bin/ExecutionerSword/inventory-icon.png",
            "ExecutionerSword/Inventory-icon.png",
            "ExecutionerSword/inventory-icon.png"
        };

        for (String p : possiblePaths) {
            File f = new File(p);
            if (f.exists() && f.isFile()) {
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) return img;
                } catch (IOException ignored) {}
            }
        }

        // Try classpath / classloader
        try (InputStream is = ExecutionerSword.class.getResourceAsStream("/ExecutionerSword/Inventory-icon.png")) {
            if (is != null) {
                BufferedImage img = ImageIO.read(is);
                if (img != null) return img;
            }
        } catch (IOException ignored) {}

        return null;
    }

    @Override
    public BufferedImage getIcon(int size) {
        if (size <= 0) size = 42;
        BufferedImage cached = scaledIconCache.get(size);
        if (cached != null) return cached;

        BufferedImage raw = getRawIcon();
        if (raw == null) {
            return super.getIcon(size);
        }

        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(raw, 0, 0, size, size, null);
        g2.dispose();

        scaledIconCache.put(size, scaled);
        return scaled;
    }

    @Override
    public Item copy() {
        ExecutionerSword copy = new ExecutionerSword();
        copy.id = this.id;
        copy.rarity = this.rarity;
        copy.stackSize = this.stackSize;
        copy.icon = this.icon;
        copy.compatibleSlot = this.compatibleSlot;
        return copy;
    }
}
