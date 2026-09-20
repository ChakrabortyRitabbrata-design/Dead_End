package entities;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import main.GamePanel;

public class Dragon {

    GamePanel gp;
    public int worldX, worldY;
    public int targetY;       // Fully risen hover level
    public int submergedY;    // Underwater depth

    public BufferedImage frame1, frame2, frame3, frame4;
    public int spriteCounter = 0;
    public int spriteNum = 1;

    // States: 0 = UNDERWATER, 1 = RISING, 2 = HOVERING, 3 = DIVING
    private int state = 0;
    private final int STATE_UNDERWATER = 0;
    private final int STATE_RISING = 1;
    private final int STATE_HOVERING = 2;
    private final int STATE_DIVING = 3;

    private float alpha = 0.0f;
    private double hoverAngle = 0;

    // Size: 4x4 tiles (192x192 px)
    public final int dragonWidth;
    public final int dragonHeight;

    // Trigger Radius: approx 7 tiles away (350 px)
    private final int triggerDistance;

    public Dragon(GamePanel gp) {
        this.gp = gp;
        this.dragonWidth = gp.tileSize * 4;
        this.dragonHeight = gp.tileSize * 4;
        this.triggerDistance = gp.tileSize * 7;

        // Centered horizontally over the shore
        this.worldX = 48 * gp.tileSize;
        
        // Fully risen hover position
        this.targetY = 29 * gp.tileSize;
        
        // Start completely submerged underwater
        this.submergedY = targetY + (gp.tileSize * 4);
        this.worldY = submergedY;
        this.state = STATE_UNDERWATER;
        this.alpha = 0.0f;

        getDragonImages();
    }

    public void getDragonImages() {
        try {
            InputStream is = getClass().getResourceAsStream("/dragon/Dragon_Sprite_Sheet.png");
            if (is == null) {
                System.out.println("❌ ERROR: Could not find /dragon/dragon_strip.png!");
                return;
            }

            BufferedImage sheet = ImageIO.read(is);
            int sheetWidth = sheet.getWidth();
            int sheetHeight = sheet.getHeight();
            int frameWidth = sheetWidth / 4;

            frame1 = sheet.getSubimage(0 * frameWidth, 0, frameWidth, sheetHeight);
            frame2 = sheet.getSubimage(1 * frameWidth, 0, frameWidth, sheetHeight);
            frame3 = sheet.getSubimage(2 * frameWidth, 0, frameWidth, sheetHeight);
            frame4 = sheet.getSubimage(3 * frameWidth, 0, frameWidth, sheetHeight);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void update() {
        // Calculate exact center distance between player and dragon
        int dragonCenterX = worldX + (dragonWidth / 2);
        int dragonCenterY = targetY + (dragonHeight / 2);
        int playerCenterX = gp.player.worldX + (gp.tileSize / 2);
        int playerCenterY = gp.player.worldY + (gp.tileSize / 2);

        double distance = Math.hypot(dragonCenterX - playerCenterX, dragonCenterY - playerCenterY);
        boolean playerIsNearShore = (distance <= triggerDistance);

        // State Transitions based on player presence
        if (playerIsNearShore) {
            // Player is close: rise up and hover
            if (state == STATE_UNDERWATER || state == STATE_DIVING) {
                state = STATE_RISING;
            }
        } else {
            // Player left: dive down into the water
            if (state == STATE_HOVERING || state == STATE_RISING) {
                state = STATE_DIVING;
            }
        }

        // State Machine Actions
        switch (state) {

            case STATE_UNDERWATER:
                worldY = submergedY;
                alpha = 0.0f;
                break;

            case STATE_RISING:
                // Smooth rise up from the water
                if (worldY > targetY) {
                    worldY -= 2;
                }
                // Fade in
                if (alpha < 0.95f) {
                    alpha += 0.04f;
                    if (alpha > 1.0f) alpha = 1.0f;
                }

                // Reached target hover altitude -> stay hovering
                if (worldY <= targetY) {
                    worldY = targetY;
                    alpha = 1.0f;
                    state = STATE_HOVERING;
                }
                break;

            case STATE_HOVERING:
                // Stays hovering and animated indefinitely as long as player is near
                hoverAngle += 0.08;
                worldY = targetY + (int) (Math.sin(hoverAngle) * 8);
                alpha = 1.0f;
                break;

            case STATE_DIVING:
                // Smooth dive down
                if (worldY < submergedY) {
                    worldY += 3;
                }
                // Fade out
                if (alpha > 0.05f) {
                    alpha -= 0.05f;
                } else {
                    alpha = 0.0f;
                }

                // Reached bottom depth -> dormant underwater
                if (worldY >= submergedY && alpha <= 0.0f) {
                    worldY = submergedY;
                    alpha = 0.0f;
                    state = STATE_UNDERWATER;
                }
                break;
        }

        // Flapping Animation (runs whenever visible)
        if (state != STATE_UNDERWATER || alpha > 0.0f) {
            spriteCounter++;
            if (spriteCounter > 8) {
                if (spriteNum == 1) spriteNum = 2;
                else if (spriteNum == 2) spriteNum = 3;
                else if (spriteNum == 3) spriteNum = 4;
                else if (spriteNum == 4) spriteNum = 1;
                spriteCounter = 0;
            }
        }
    }

    public void draw(Graphics2D g2) {
        if (alpha <= 0.01f || state == STATE_UNDERWATER) {
            return;
        }

        int screenX = worldX - gp.player.worldX + gp.player.screenX;
        int screenY = worldY - gp.player.worldY + gp.player.screenY;

        // Camera culling
        if (worldX + dragonWidth + gp.tileSize * 2 > gp.player.worldX - gp.player.screenX &&
            worldX - dragonWidth - gp.tileSize * 2 < gp.player.worldX + gp.player.screenX &&
            worldY + dragonHeight + gp.tileSize * 2 > gp.player.worldY - gp.player.screenY &&
            worldY - dragonHeight - gp.tileSize * 2 < gp.player.worldY + gp.player.screenY) {

            BufferedImage current = frame1;
            if (spriteNum == 2) current = frame2;
            if (spriteNum == 3) current = frame3;
            if (spriteNum == 4) current = frame4;

            if (current != null) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.drawImage(current, screenX, screenY, dragonWidth, dragonHeight, null);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            }
        }
    }
}