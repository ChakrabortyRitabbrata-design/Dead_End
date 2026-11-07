package entities;

import animation.AnimationState;
import animation.Direction;
import inventory.EquipmentSlot;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Manages loading and caching of all production sprite sheets:
 * - Base character animation sheet
 * - Equipment overlay sheets (Helmet, Chestplate, Leggings, Boots, Shield)
 * - Weapon attack animation sheets (Wooden Sword, Iron Sword, Axe)
 * - Visual effect sheets (Slash, Impact)
 */
public class CharacterSpriteManager {

    private static final int FRAME_SIZE = 64;

    private SpriteLayer baseCharacterLayer;
    private final Map<EquipmentSlot, SpriteLayer> equipmentLayers = new EnumMap<>(EquipmentSlot.class);
    private final Map<String, SpriteLayer> weaponAttackLayers = new HashMap<>();
    private BufferedImage slashEffectSheet;
    private BufferedImage impactEffectSheet;

    public CharacterSpriteManager() {
        loadAllLayers();
    }

    public void loadAllLayers() {
        try {
            // 1. Base character
            BufferedImage baseImg = loadImage("player/character_base.png");
            if (baseImg != null) {
                baseCharacterLayer = new SpriteLayer(baseImg, FRAME_SIZE, FRAME_SIZE);
            }

            // 2. Equipment layers
            loadEquipLayer(EquipmentSlot.HEAD, "player/equipment_helmet.png");
            loadEquipLayer(EquipmentSlot.CHEST, "player/equipment_chestplate.png");
            loadEquipLayer(EquipmentSlot.LEGS, "player/equipment_leggings.png");
            loadEquipLayer(EquipmentSlot.FEET, "player/equipment_boots.png");
            loadEquipLayer(EquipmentSlot.OFF_HAND, "player/equipment_shield.png");

            // 3. Weapon attack sheets
            loadWeaponLayer("wooden_sword", "player/weapon_wooden_sword.png");
            loadWeaponLayer("iron_sword", "player/weapon_iron_sword.png");
            loadWeaponLayer("axe", "player/weapon_axe.png");

            // 4. Effects
            slashEffectSheet = loadImage("player/effect_slash.png");
            impactEffectSheet = loadImage("player/effect_impact.png");

        } catch (Exception e) {
            System.err.println("Warning: CharacterSpriteManager failed to load some sheets: " + e.getMessage());
        }
    }

    private void loadEquipLayer(EquipmentSlot slot, String path) {
        BufferedImage img = loadImage(path);
        if (img != null) {
            equipmentLayers.put(slot, new SpriteLayer(img, FRAME_SIZE, FRAME_SIZE));
        }
    }

    private void loadWeaponLayer(String weaponKey, String path) {
        BufferedImage img = loadImage(path);
        if (img != null) {
            weaponAttackLayers.put(weaponKey.toLowerCase(), new SpriteLayer(img, FRAME_SIZE, FRAME_SIZE));
        }
    }

    public BufferedImage getBaseFrame(AnimationState state, Direction dir, int frameIdx) {
        if (baseCharacterLayer == null) return null;
        int animIdx = AttachmentPoint.animIndex(state);
        int maxFrames = AttachmentPoint.frameCount(animIdx);
        int safeFrame = maxFrames > 0 ? (frameIdx % maxFrames) : 0;
        int dirRow = AttachmentPoint.dirRow(dir);
        return baseCharacterLayer.getFrame(dirRow, animIdx, safeFrame);
    }

    public BufferedImage getEquipmentFrame(EquipmentSlot slot, AnimationState state, Direction dir, int frameIdx) {
        SpriteLayer layer = equipmentLayers.get(slot);
        if (layer == null) return null;
        int animIdx = AttachmentPoint.animIndex(state);
        int maxFrames = AttachmentPoint.frameCount(animIdx);
        int safeFrame = maxFrames > 0 ? (frameIdx % maxFrames) : 0;
        int dirRow = AttachmentPoint.dirRow(dir);
        return layer.getFrame(dirRow, animIdx, safeFrame);
    }

    public BufferedImage getWeaponAttackFrame(String weaponKey, Direction dir, int attackFrame) {
        SpriteLayer layer = weaponAttackLayers.get(weaponKey.toLowerCase());
        if (layer == null) {
            // Default fallback to iron sword or wooden sword
            layer = weaponAttackLayers.get("iron_sword");
            if (layer == null) layer = weaponAttackLayers.get("wooden_sword");
        }
        if (layer == null) return null;

        int dirRow = AttachmentPoint.dirRow(dir);
        int safeFrame = Math.max(0, Math.min(3, attackFrame));
        return layer.getFrame(dirRow, safeFrame);
    }

    public BufferedImage getSlashEffectFrame(int frame) {
        if (slashEffectSheet == null) return null;
        int safeFrame = Math.max(0, Math.min(3, frame));
        int x = safeFrame * FRAME_SIZE;
        if (x + FRAME_SIZE <= slashEffectSheet.getWidth()) {
            return slashEffectSheet.getSubimage(x, 0, FRAME_SIZE, FRAME_SIZE);
        }
        return null;
    }

    public BufferedImage getImpactEffectFrame(int frame) {
        if (impactEffectSheet == null) return null;
        int safeFrame = Math.max(0, Math.min(2, frame));
        int x = safeFrame * FRAME_SIZE;
        if (x + FRAME_SIZE <= impactEffectSheet.getWidth()) {
            return impactEffectSheet.getSubimage(x, 0, FRAME_SIZE, FRAME_SIZE);
        }
        return null;
    }

    public boolean hasWeaponAttackSheet(String weaponKey) {
        return weaponAttackLayers.containsKey(weaponKey.toLowerCase());
    }

    private BufferedImage loadImage(String resourcePath) {
        try (InputStream stream = openResource(resourcePath)) {
            if (stream == null) return null;
            return ImageIO.read(stream);
        } catch (IOException e) {
            return null;
        }
    }

    private InputStream openResource(String resourcePath) throws IOException {
        InputStream stream = getClass().getResourceAsStream("/" + resourcePath);
        if (stream != null) return stream;
        Path resource = Paths.get("res", resourcePath);
        if (!Files.exists(resource)) resource = Paths.get("bin", resourcePath);
        if (!Files.exists(resource)) resource = Paths.get("Dead_End", "res", resourcePath);
        if (!Files.exists(resource)) resource = Paths.get("Dead_End", "bin", resourcePath);
        if (Files.exists(resource)) return Files.newInputStream(resource);
        return null;
    }
}
