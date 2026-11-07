package entities;

import animation.AnimationState;
import animation.Direction;
import inventory.EquipmentManager;
import inventory.EquipmentSlot;
import inventory.Item;
import inventory.Weapon;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Synchronized sprite layer renderer for the character and equipped gear.
 * Renders equipment overlays in exact directional and animation-frame synchronization
 * with the base character body.
 *
 * Directional Rendering Order:
 * - Facing DOWN:
 *   1. Off-Hand (Shield) / Cape
 *   2. Base Body
 *   3. Legs (Leggings)
 *   4. Boots
 *   5. Chestplate
 *   6. Helmet
 *   7. Main Hand (Weapon idle/held)
 *
 * - Facing UP:
 *   1. Main Hand (Weapon)
 *   2. Helmet
 *   3. Chestplate
 *   4. Legs (Leggings)
 *   5. Boots
 *   6. Base Body / Cape (front of back)
 *   7. Off-Hand (Shield)
 *
 * - Facing LEFT / RIGHT:
 *   1. Off-Hand (if on back side)
 *   2. Base Body
 *   3. Legs
 *   4. Boots
 *   5. Chestplate
 *   6. Helmet
 *   7. Main Hand (Weapon front side)
 */
public class EquipmentRenderer {

    private final CharacterSpriteManager spriteManager;

    public EquipmentRenderer(CharacterSpriteManager spriteManager) {
        this.spriteManager = spriteManager;
    }

    public CharacterSpriteManager getSpriteManager() {
        return spriteManager;
    }

    /**
     * Renders character base body and all equipped armor/shield layers.
     */
    public void render(Graphics2D g2, EquipmentManager equipMgr,
                       AnimationState state, Direction dir, int frameIdx,
                       int drawX, int drawY, int drawSize) {

        Object oldInterp = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        BufferedImage base = spriteManager.getBaseFrame(state, dir, frameIdx);
        BufferedImage helmet = hasEquipped(equipMgr, EquipmentSlot.HEAD)
                ? spriteManager.getEquipmentFrame(EquipmentSlot.HEAD, state, dir, frameIdx) : null;
        BufferedImage chest = hasEquipped(equipMgr, EquipmentSlot.CHEST)
                ? spriteManager.getEquipmentFrame(EquipmentSlot.CHEST, state, dir, frameIdx) : null;
        BufferedImage legs = hasEquipped(equipMgr, EquipmentSlot.LEGS)
                ? spriteManager.getEquipmentFrame(EquipmentSlot.LEGS, state, dir, frameIdx) : null;
        BufferedImage boots = hasEquipped(equipMgr, EquipmentSlot.FEET)
                ? spriteManager.getEquipmentFrame(EquipmentSlot.FEET, state, dir, frameIdx) : null;
        BufferedImage shield = hasEquipped(equipMgr, EquipmentSlot.OFF_HAND)
                ? spriteManager.getEquipmentFrame(EquipmentSlot.OFF_HAND, state, dir, frameIdx) : null;

        // Draw in direction-aware depth order
        if (dir == Direction.UP) {
            // Facing UP: shield & helmet in back/front properly
            if (shield != null) g2.drawImage(shield, drawX, drawY, drawSize, drawSize, null);
            if (base != null) g2.drawImage(base, drawX, drawY, drawSize, drawSize, null);
            if (boots != null) g2.drawImage(boots, drawX, drawY, drawSize, drawSize, null);
            if (legs != null) g2.drawImage(legs, drawX, drawY, drawSize, drawSize, null);
            if (chest != null) g2.drawImage(chest, drawX, drawY, drawSize, drawSize, null);
            if (helmet != null) g2.drawImage(helmet, drawX, drawY, drawSize, drawSize, null);
        } else {
            // Facing DOWN, LEFT, RIGHT:
            if (shield != null && dir == Direction.RIGHT) {
                // Shield on left hand (behind character when facing right)
                g2.drawImage(shield, drawX, drawY, drawSize, drawSize, null);
            }
            if (base != null) g2.drawImage(base, drawX, drawY, drawSize, drawSize, null);
            if (boots != null) g2.drawImage(boots, drawX, drawY, drawSize, drawSize, null);
            if (legs != null) g2.drawImage(legs, drawX, drawY, drawSize, drawSize, null);
            if (chest != null) g2.drawImage(chest, drawX, drawY, drawSize, drawSize, null);
            if (helmet != null) g2.drawImage(helmet, drawX, drawY, drawSize, drawSize, null);
            if (shield != null && dir != Direction.RIGHT) {
                // Shield in front when facing down or left
                g2.drawImage(shield, drawX, drawY, drawSize, drawSize, null);
            }
        }

        if (oldInterp != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
        }
    }

    /**
     * Renders a static preview composite (facing down, neutral idle frame)
     * for UI panels (like InventoryUI character preview).
     */
    public void renderPreview(Graphics2D g2, EquipmentManager equipMgr,
                              int px, int py, int size) {
        render(g2, equipMgr, AnimationState.IDLE, Direction.DOWN, 0, px, py, size);

        // Also render weapon icon in hand position on preview
        if (equipMgr != null) {
            Item weapon = equipMgr.getEquipped(EquipmentSlot.MAIN_HAND);
            if (weapon instanceof Weapon w) {
                BufferedImage icon = w.getIcon(size / 3);
                int wx = px + size - size / 3;
                int wy = py + size / 2 - 4;
                g2.drawImage(icon, wx, wy, size / 3, size / 3, null);
            }
        }
    }

    private boolean hasEquipped(EquipmentManager equipMgr, EquipmentSlot slot) {
        return equipMgr != null && equipMgr.isEquipped(slot);
    }
}
