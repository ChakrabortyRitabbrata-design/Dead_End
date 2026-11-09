package entities;

import animation.Direction;
import inventory.EquipmentSlot;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Loads authentic pixel-art equipment sprites directly extracted from the user's
 * reference equipment sheet (media_1794207624865_517ede49.jpg).
 *
 * Provides directional sprites (DOWN, LEFT, RIGHT, UP) for:
 * - Helmet (HEAD)
 * - Chestplate (CHEST)
 * - Leggings (LEGS)
 * - Boots (FEET)
 * - Shield (OFF_HAND)
 */
public class EquipmentSpriteSheet {

    private static final int FRAME_SIZE = 26;
    private static final Map<EquipmentSlot, BufferedImage[]> SPRITES = new EnumMap<>(EquipmentSlot.class);
    private static boolean loaded = false;

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        loadSlot(EquipmentSlot.HEAD,     "player/equipment_helmet_clean.png");
        loadSlot(EquipmentSlot.CHEST,    "player/equipment_chestplate_clean.png");
        loadSlot(EquipmentSlot.LEGS,     "player/equipment_leggings_clean.png");
        loadSlot(EquipmentSlot.FEET,     "player/equipment_boots_clean.png");
        loadSlot(EquipmentSlot.OFF_HAND, "player/equipment_shield_clean.png");
    }

    private static void loadSlot(EquipmentSlot slot, String resourcePath) {
        try (InputStream is = openResource(resourcePath)) {
            BufferedImage sheet = ImageIO.read(is);
            if (sheet == null) return;

            int frameW = sheet.getWidth() / 4;
            int frameH = sheet.getHeight();
            BufferedImage[] dirs = new BufferedImage[4];
            for (int d = 0; d < 4; d++) {
                dirs[d] = sheet.getSubimage(d * frameW, 0, frameW, frameH);
            }
            SPRITES.put(slot, dirs);
        } catch (Exception e) {
            System.err.println("Failed to load equipment sprite: " + resourcePath + " (" + e.getMessage() + ")");
        }
    }

    public static BufferedImage getSprite(EquipmentSlot slot, Direction dir) {
        ensureLoaded();
        BufferedImage[] dirs = SPRITES.get(slot);
        if (dirs == null) return null;

        int idx = switch (dir) {
            case DOWN  -> 0;
            case LEFT  -> 1;
            case RIGHT -> 2;
            case UP    -> 3;
        };
        return dirs[idx];
    }

    private static InputStream openResource(String resourcePath) throws IOException {
        InputStream stream = EquipmentSpriteSheet.class.getResourceAsStream("/" + resourcePath);
        if (stream != null) return stream;

        Path resource = Paths.get("res", resourcePath);
        if (!Files.exists(resource)) resource = Paths.get("bin", resourcePath);
        if (!Files.exists(resource)) resource = Paths.get("Dead_End", "res", resourcePath);
        if (!Files.exists(resource)) resource = Paths.get("Dead_End", "bin", resourcePath);

        if (!Files.exists(resource)) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        return Files.newInputStream(resource);
    }
}
