package entities;

import main.GamePanel;
import main.KeyHandler;
import inventory.Weapon;
import inventory.Item;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;

public class Player extends Entity {

    GamePanel  gp;
    KeyHandler keyH;

    public final int screenX;
    public final int screenY;

    // ── Attack system ─────────────────────────────────────────────────────────
    private final WeaponRenderer weaponRenderer;

    /** True for exactly one frame when the player triggers an attack. */
    private boolean attackRequested = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public Player(GamePanel gp, KeyHandler keyH) {

        this.gp   = gp;
        this.keyH = keyH;

        screenX = gp.screenWidth  / 2 - (gp.tileSize / 2);
        screenY = gp.screenHeight / 2 - (gp.tileSize / 2);

        // Hitbox definition
        solidArea = new Rectangle();
        solidArea.x      = 8;
        solidArea.y      = 16;
        solidArea.width  = 32;
        solidArea.height = 32;

        weaponRenderer = new WeaponRenderer(gp);

        setDefaultValues();
        getPlayerImage();
    }

    // ── Defaults ──────────────────────────────────────────────────────────────

    public void setDefaultValues() {
        worldX    = gp.tileSize * 49 + (gp.tileSize / 2);
        worldY    = gp.tileSize * 90;
        speed     = 4;
        direction = "up";
    }

    // ── Sprite loading (unchanged) ────────────────────────────────────────────

    public void getPlayerImage() {
        try {
            up1    = ImageIO.read(getClass().getResourceAsStream("/player/boy_up_1.png"));
            up2    = ImageIO.read(getClass().getResourceAsStream("/player/boy_up_2.png"));
            down1  = ImageIO.read(getClass().getResourceAsStream("/player/boy_down_1.png"));
            down2  = ImageIO.read(getClass().getResourceAsStream("/player/boy_down_2.png"));
            left1  = ImageIO.read(getClass().getResourceAsStream("/player/boy_left_1.png"));
            left2  = ImageIO.read(getClass().getResourceAsStream("/player/boy_left_2.png"));
            right1 = ImageIO.read(getClass().getResourceAsStream("/player/boy_right_1.png"));
            right2 = ImageIO.read(getClass().getResourceAsStream("/player/boy_right_2.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Called by InventoryUI / GamePanel mouse listener ─────────────────────
    /** Signal that the player should attack on the next update tick. */
    public void requestAttack() { attackRequested = true; }

    // ── Update ────────────────────────────────────────────────────────────────

    public void update() {

        // ── Advance weapon animation every tick ───────────────────────────────
        weaponRenderer.update();

        // ── Resolve attack intent ─────────────────────────────────────────────
        boolean spaceAttack = keyH.attackPressed;
        if (spaceAttack) keyH.attackPressed = false; // consume SPACE flag

        if ((spaceAttack || attackRequested) && !weaponRenderer.isAttacking()) {
            Weapon equipped = getEquippedWeapon();
            weaponRenderer.startAttack(equipped); // no-op if equipped == null
        }
        attackRequested = false;

        // ── Movement (unchanged; also plays while ATTACKING for fluidity) ─────
        if (keyH.upPressed || keyH.downPressed ||
                keyH.leftPressed || keyH.rightPressed) {

            if      (keyH.upPressed)    direction = "up";
            else if (keyH.downPressed)  direction = "down";
            else if (keyH.leftPressed)  direction = "left";
            else                        direction = "right";

            collisionOn = false;
            gp.cChecker.checkTile(this);

            if (!collisionOn) {
                switch (direction) {
                    case "up"    -> worldY -= speed;
                    case "down"  -> worldY += speed;
                    case "left"  -> worldX -= speed;
                    case "right" -> worldX += speed;
                }
            }

            spriteCounter++;
            if (spriteCounter > 10) {
                spriteNum     = (spriteNum == 1) ? 2 : 1;
                spriteCounter = 0;
            }
        }
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    public void draw(Graphics2D g2) {

        // ── 1. Player sprite ─────────────────────────────────────────────────
        BufferedImage image = switch (direction) {
            case "up"    -> (spriteNum == 1) ? up1    : up2;
            case "down"  -> (spriteNum == 1) ? down1  : down2;
            case "left"  -> (spriteNum == 1) ? left1  : left2;
            default      -> (spriteNum == 1) ? right1 : right2; // "right"
        };

        // Slight body lean during attack swing for visual feedback
        if (weaponRenderer.isAttacking()) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
        }
        g2.drawImage(image, screenX, screenY, gp.tileSize, gp.tileSize, null);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

        // ── 2. Weapon in hand (drawn on top of the sprite) ───────────────────
        weaponRenderer.draw(g2, screenX, screenY, direction, getEquippedWeapon());
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /** Returns the equipped Weapon from the hotbar, or null if empty / not a weapon. */
    public Weapon getEquippedWeapon() {
        Item selected = gp.hotbar.getSelectedItem();
        return (selected instanceof Weapon w) ? w : null;
    }
}
