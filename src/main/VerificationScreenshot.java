package main;

import animation.Animation;
import animation.AnimationState;
import animation.Direction;
import entities.Player;
import inventory.Armor;
import inventory.EquipmentSlot;
import inventory.Item;
import inventory.Weapon;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class VerificationScreenshot {

    public static void main(String[] args) {
        System.out.println("Starting Verification Screenshot generation...");

        GamePanel gp = new GamePanel();
        Player player = gp.player;

        Weapon ironSword = new Weapon("Iron Sword", "A forged iron sword", 12, 30, 60, "Sword");
        Armor helmet     = new Armor("Basic Helmet", "Iron helmet", 5, "Helmet", EquipmentSlot.HEAD);
        Armor chestplate = new Armor("Basic Chestplate", "Iron chestplate", 15, "Chestplate", EquipmentSlot.CHEST);
        Armor leggings   = new Armor("Basic Leggings", "Iron leggings", 10, "Leggings", EquipmentSlot.LEGS);
        Armor boots      = new Armor("Basic Boots", "Iron boots", 5, "Boots", EquipmentSlot.FEET);
        Armor shield     = new Armor("Basic Shield", "Shield with gold boss", 12, "Shield", EquipmentSlot.OFF_HAND);

        int cols = 4; // DOWN, LEFT, RIGHT, UP
        int rows = 6;
        int cellW = 185;
        int cellH = 185;
        int headerH = 75;
        int width  = cols * cellW + 40;
        int height = rows * cellH + headerH + 30;

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dark background matching RPG sheet theme
        g2.setColor(new Color(18, 18, 28));
        g2.fillRect(0, 0, width, height);

        // Header Title
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 22));
        g2.drawString("Dead End!! - Character & Synchronized Equipment Verification", 24, 34);

        g2.setColor(new Color(170, 185, 215));
        g2.setFont(new Font("Arial", Font.PLAIN, 13));
        g2.drawString("Full-Coverage Closed Helmet | Snug-Fit Body Armor | Directional Attack & Shield Animation", 24, 56);

        Direction[] directions = { Direction.DOWN, Direction.LEFT, Direction.RIGHT, Direction.UP };
        String[] dirNames = { "DOWN (Front)", "LEFT (Side)", "RIGHT (Side)", "UP (Back)" };

        int renderSize = 105;

        for (int r = 0; r < rows; r++) {
            // Configure equipment for this row
            gp.equipmentManager.unequip(EquipmentSlot.MAIN_HAND);
            gp.equipmentManager.unequip(EquipmentSlot.OFF_HAND);
            gp.equipmentManager.unequip(EquipmentSlot.HEAD);
            gp.equipmentManager.unequip(EquipmentSlot.CHEST);
            gp.equipmentManager.unequip(EquipmentSlot.LEGS);
            gp.equipmentManager.unequip(EquipmentSlot.FEET);

            String rowLabel;
            AnimationState state = AnimationState.IDLE;
            int frameIdx = 0;
            boolean blockStance = false;

            if (r == 0) {
                rowLabel = "1. Bare-Handed (Original Base Character)";
            } else if (r == 1) {
                rowLabel = "2. Equipped: Iron Sword (Hand-Anchored)";
                gp.equipmentManager.equip(EquipmentSlot.MAIN_HAND, ironSword);
            } else if (r == 2) {
                rowLabel = "3. Equipped: Iron Sword + Shield";
                gp.equipmentManager.equip(EquipmentSlot.MAIN_HAND, ironSword);
                gp.equipmentManager.equip(EquipmentSlot.OFF_HAND, shield);
            } else if (r == 3) {
                rowLabel = "4. Full Gear Idle (Whole Helmet Covering Face + Fitted Armor + Sabatons)";
                gp.equipmentManager.equip(EquipmentSlot.MAIN_HAND, ironSword);
                gp.equipmentManager.equip(EquipmentSlot.OFF_HAND, shield);
                gp.equipmentManager.equip(EquipmentSlot.HEAD, helmet);
                gp.equipmentManager.equip(EquipmentSlot.CHEST, chestplate);
                gp.equipmentManager.equip(EquipmentSlot.LEGS, leggings);
                gp.equipmentManager.equip(EquipmentSlot.FEET, boots);
            } else if (r == 4) {
                rowLabel = "5. Full Gear Attack Swing (Dynamic Armor Movement in Fight Directions)";
                gp.equipmentManager.equip(EquipmentSlot.MAIN_HAND, ironSword);
                gp.equipmentManager.equip(EquipmentSlot.OFF_HAND, shield);
                gp.equipmentManager.equip(EquipmentSlot.HEAD, helmet);
                gp.equipmentManager.equip(EquipmentSlot.CHEST, chestplate);
                gp.equipmentManager.equip(EquipmentSlot.LEGS, leggings);
                gp.equipmentManager.equip(EquipmentSlot.FEET, boots);
                state = AnimationState.PUNCH;
                frameIdx = 2; // Full attack swing extension frame
            } else {
                rowLabel = "6. Full Gear Shield Block (Active Defense Stance)";
                gp.equipmentManager.equip(EquipmentSlot.MAIN_HAND, ironSword);
                gp.equipmentManager.equip(EquipmentSlot.OFF_HAND, shield);
                gp.equipmentManager.equip(EquipmentSlot.HEAD, helmet);
                gp.equipmentManager.equip(EquipmentSlot.CHEST, chestplate);
                gp.equipmentManager.equip(EquipmentSlot.LEGS, leggings);
                gp.equipmentManager.equip(EquipmentSlot.FEET, boots);
                blockStance = true;
            }

            int rowY = headerH + r * cellH;

            // Row header label
            g2.setColor(new Color(230, 200, 100));
            g2.setFont(new Font("Arial", Font.BOLD, 13));
            g2.drawString(rowLabel, 20, rowY + 16);

            for (int c = 0; c < cols; c++) {
                Direction dir = directions[c];
                int cellX = 20 + c * cellW;
                int boxY  = rowY + 24;
                int boxW  = cellW - 12;
                int boxH  = cellH - 32;

                // Box background
                g2.setColor(new Color(28, 30, 42));
                g2.fillRoundRect(cellX, boxY, boxW, boxH, 10, 10);
                g2.setColor(new Color(55, 60, 80));
                g2.drawRoundRect(cellX, boxY, boxW, boxH, 10, 10);

                // Direction label at top of cell
                g2.setColor(new Color(150, 170, 200));
                g2.setFont(new Font("Arial", Font.BOLD, 11));
                g2.drawString(dirNames[c], cellX + 12, boxY + 18);

                // Shadow under character
                int charDrawX = cellX + (boxW - renderSize) / 2;
                int charDrawY = boxY + (boxH - renderSize) / 2 + 6;
                g2.setColor(new Color(10, 12, 18, 160));
                g2.fillOval(charDrawX + renderSize / 2 - 28, charDrawY + renderSize - 18, 56, 14);

                // Get character sprite for state & direction
                BufferedImage charSprite = null;
                try {
                    java.lang.reflect.Field f = Player.class.getDeclaredField("animations");
                    f.setAccessible(true);
                    Animation[][] anims = (Animation[][]) f.get(player);
                    Animation anim = anims[dir.ordinal()][state.ordinal()];
                    if (anim != null) {
                        int fCount = anim.getFrameCount();
                        int fIdx = Math.min(frameIdx, fCount - 1);
                        charSprite = anim.getFrame(fIdx).getImage();
                    }
                } catch (Exception ignored) {}
                if (charSprite == null) {
                    charSprite = player.getDirectionalIdleFrame(dir);
                }

                Weapon eqW = (Weapon) gp.equipmentManager.getEquipped(EquipmentSlot.MAIN_HAND);

                // 1. Rear weapon if UP
                if (eqW != null && player.getWeaponRenderer() != null) {
                    player.getWeaponRenderer().drawHandAnchored(g2, charDrawX, charDrawY, renderSize,
                            state, dir, frameIdx, eqW, "BEHIND");
                }

                // 2. Offhand behind if RIGHT
                if (dir == Direction.RIGHT) {
                    player.drawOffHand(g2, charDrawX, charDrawY, renderSize, state, dir, frameIdx);
                }

                // 3. Base character sprite
                if (charSprite != null) {
                    Object oldInterp = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g2.drawImage(charSprite, charDrawX, charDrawY, renderSize, renderSize, null);
                    if (oldInterp != null) g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
                }

                // 4. Armor Overlays
                player.drawArmorOverlays(g2, charDrawX, charDrawY, renderSize, state, dir, frameIdx);

                // 5. Offhand in front if NOT RIGHT
                if (dir != Direction.RIGHT) {
                    player.drawOffHand(g2, charDrawX, charDrawY, renderSize, state, dir, frameIdx);
                }

                // 6. Front weapon
                if (eqW != null && player.getWeaponRenderer() != null) {
                    player.getWeaponRenderer().drawHandAnchored(g2, charDrawX, charDrawY, renderSize,
                            state, dir, frameIdx, eqW, "FRONT");
                }
            }
        }

        g2.dispose();

        // Save output to workspace root and brain directory
        try {
            File outFile1 = new File("character_four_directions_sword.png");
            ImageIO.write(canvas, "png", outFile1);
            System.out.println("Saved: " + outFile1.getAbsolutePath());

            File outFile2 = new File("C:\\Users\\HP\\.gemini\\antigravity\\brain\\d34a3eff-f7a3-43cf-bfee-09957eec74ec\\character_four_directions_sword.png");
            ImageIO.write(canvas, "png", outFile2);
            System.out.println("Saved artifact: " + outFile2.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
