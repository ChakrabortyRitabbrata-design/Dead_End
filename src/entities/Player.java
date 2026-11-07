package entities;

import main.GamePanel;
import main.KeyHandler;
import inventory.EquipmentSlot;
import inventory.Item;
import inventory.Weapon;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

public class Player extends Entity {

    private static final String IDLE = "IDLE";
    private static final float PLAYER_RENDER_SCALE = 1.6f;
    private static final float IDLE_RENDER_SCALE = 2.4f;
    private static final int DIRECTION_COUNT = 4;
    private static final int ACTION_COUNT = 4;

    // Direction order: up, right, down, left.
    private static final int UP = 0;
    private static final int RIGHT = 1;
    private static final int DOWN = 2;
    private static final int LEFT = 3;

    // Combat action order: punch, kick, block, dodge.
    private static final int PUNCH = 0;
    private static final int KICK = 1;
    private static final int BLOCK = 2;
    private static final int DODGE = 3;
    private static final int[] COMBAT_FRAME_COUNTS = {3, 3, 3, 4};
    private static final int ROLL_DISTANCE_TILES = 3;
    private static final int ROLL_STEP = 6;

    /*
     * The source sheet contains a label column followed by four direction
     * columns. The row and column boundaries are the visible grid lines in
     * Pixel Art RPG Combat Sprite Sheet.png.
     */
    private static final int[] SHEET_COLUMN_STARTS = {216, 567, 906, 1220};
    private static final int[] SHEET_COLUMN_ENDS = {565, 904, 1218, 1527};
    private static final int[] SHEET_ROW_STARTS = {15, 261, 413, 581, 745};
    private static final int[] SHEET_ROW_ENDS = {259, 411, 579, 743, 967};

    GamePanel gp;
    KeyHandler keyH;

    public final int screenX;
    public final int screenY;

    public String actionState = IDLE;
    public int combatCounter = 0;
    public int combatFrameIndex = 0;
    public int attackSequence = 0;
    private int health = 100;
    private int idleCounter = 0;
    private int idleFrameIndex = 0;
    private int rollDistanceRemaining = 0;
    private String rollDirection = "down";

    // ── Weapon Renderer & Equipment ───────────────────────────────────────────
    private final WeaponRenderer weaponRenderer;

    private final BufferedImage[][][] combatFrames =
            new BufferedImage[DIRECTION_COUNT][ACTION_COUNT][];

    public Player(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;

        screenX = gp.screenWidth / 2 - gp.tileSize / 2;
        screenY = gp.screenHeight / 2 - gp.tileSize / 2;

        solidArea = new Rectangle(8, 16, 32, 32);

        weaponRenderer = new WeaponRenderer(gp);

        setDefaultValues();
        getPlayerImages();
        getCombatImages();
    }

    public void setDefaultValues() {
        worldX = gp.tileSize * 49 + gp.tileSize / 2;
        worldY = gp.tileSize * 90;
        speed = 4;
        direction = "up";
    }

    public void getPlayerImages() {
        try {
            BufferedImage sheet = readImage("player/player_walk_strip.png");
            int frameWidth = sheet.getWidth() / 4;
            int frameHeight = sheet.getHeight() / 4;

            down1 = sheet.getSubimage(0, 0, frameWidth, frameHeight);
            down2 = sheet.getSubimage(frameWidth, 0, frameWidth, frameHeight);
            up1 = sheet.getSubimage(0, frameHeight, frameWidth, frameHeight);
            up2 = sheet.getSubimage(frameWidth, frameHeight, frameWidth, frameHeight);
            left1 = sheet.getSubimage(0, frameHeight * 2, frameWidth, frameHeight);
            left2 = sheet.getSubimage(frameWidth, frameHeight * 2, frameWidth, frameHeight);
            right1 = sheet.getSubimage(0, frameHeight * 3, frameWidth, frameHeight);
            right2 = sheet.getSubimage(frameWidth, frameHeight * 3, frameWidth, frameHeight);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load player walk sprites.", e);
        }
    }

    public void getPlayerImage() {
        getPlayerImages();
    }

    public void getCombatImages() {
        try {
            BufferedImage sheet = readImage(
                    "player/Pixel Art RPG Combat Sprite Sheet.png");

            for (int directionIndex = 0; directionIndex < DIRECTION_COUNT; directionIndex++) {
                for (int actionIndex = 0; actionIndex < ACTION_COUNT; actionIndex++) {
                    int row = actionIndex + 1;
                    int frameCount = COMBAT_FRAME_COUNTS[actionIndex];
                    combatFrames[directionIndex][actionIndex] =
                            new BufferedImage[frameCount];

                    for (int frame = 0; frame < frameCount; frame++) {
                        combatFrames[directionIndex][actionIndex][frame] =
                                sliceCombatFrame(sheet, directionIndex, row, frame, frameCount);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load combat sprite sheet.", e);
        }
    }

    private BufferedImage sliceCombatFrame(BufferedImage sheet, int directionIndex,
            int row, int frame, int frameCount) {
        int columnStart = SHEET_COLUMN_STARTS[directionIndex];
        int columnEnd = SHEET_COLUMN_ENDS[directionIndex];
        int rowStart = SHEET_ROW_STARTS[row];
        int rowEnd = SHEET_ROW_ENDS[row];

        int cellWidth = (columnEnd - columnStart) / frameCount;
        int x = columnStart + frame * cellWidth + 2;
        int y = rowStart + 2;
        int width = (frame == frameCount - 1
                ? columnEnd - columnStart - frame * cellWidth
                : cellWidth) - 4;
        int height = rowEnd - rowStart - 4;

        return sheet.getSubimage(x, y, width, height);
    }

    public BufferedImage makeTransparent(BufferedImage loadedImage) {
        if (loadedImage == null) {
            return null;
        }

        BufferedImage transparentImage = new BufferedImage(
                loadedImage.getWidth(), loadedImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < loadedImage.getHeight(); y++) {
            for (int x = 0; x < loadedImage.getWidth(); x++) {
                int argb = loadedImage.getRGB(x, y);
                int rgb = argb & 0x00FFFFFF;
                transparentImage.setRGB(x, y,
                        rgb < 0x101010 ? rgb : argb | 0xFF000000);
            }
        }
        return transparentImage;
    }

    private BufferedImage readImage(String resourcePath) throws IOException {
        try (InputStream stream = openResource(resourcePath)) {
            BufferedImage image = makeTransparent(ImageIO.read(stream));
            if (image == null) {
                throw new IOException("Resource is not a readable image: " + resourcePath);
            }
            return image;
        }
    }

    private InputStream openResource(String resourcePath) throws IOException {
        InputStream stream = getClass().getResourceAsStream("/" + resourcePath);
        if (stream != null) {
            return stream;
        }

        Path resource = Paths.get("res", resourcePath);
        if (!Files.exists(resource)) {
            resource = Paths.get("bin", resourcePath);
        }
        if (!Files.exists(resource)) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        return Files.newInputStream(resource);
    }

    // ── Weapon Equipment Helper ───────────────────────────────────────────────

    /**
     * Retrieves the currently equipped weapon.
     * The Main Hand equipment slot is the primary source of truth.
     */
    public Weapon getEquippedWeapon() {
        if (gp != null && gp.equipmentManager != null) {
            Item item = gp.equipmentManager.getEquipped(EquipmentSlot.MAIN_HAND);
            if (item instanceof Weapon w) {
                return w;
            }
        }
        return null;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public void update() {
        // Advance weapon animation ticks
        weaponRenderer.update();

        // Check SPACE bar attack
        if (keyH.attackPressed) {
            keyH.attackPressed = false;
            triggerAttackAction();
        }

        updateCombatAction();
        if (isLockedAction()) {
            return;
        }
        updateMovement();
        if (!isMoving()) {
            updateIdleAnimation();
        }
    }

    private void triggerAttackAction() {
        Weapon equipped = getEquippedWeapon();
        if (equipped != null) {
            if (!weaponRenderer.isAttacking()) {
                weaponRenderer.startAttack(equipped);
                attackSequence++;
            }
        } else {
            startCombatAction("PUNCH", PUNCH);
        }
    }

    private void updateCombatAction() {
        if ("BLOCK".equals(actionState)) {
            if (keyH.blockPressed) {
                animateCombat(BLOCK);
            } else {
                actionState = IDLE;
                combatCounter = 0;
                combatFrameIndex = 0;
            }
            return;
        }

        if (!IDLE.equals(actionState)) {
            animateCurrentCombatAction();
            return;
        }

        if (keyH.consumePunch()) {
            triggerAttackAction();
        } else if (keyH.consumeKick()) {
            startCombatAction("KICK", KICK);
        } else if (keyH.blockPressed) {
            startCombatAction("BLOCK", BLOCK);
        } else if (keyH.consumeDodge()) {
            startCombatAction("DODGE", DODGE);
        }
    }

    private void startCombatAction(String state, int actionIndex) {
        actionState = state;
        combatCounter = 0;
        combatFrameIndex = 0;
        if (PUNCH == actionIndex || KICK == actionIndex) {
            attackSequence++;
        }
        if (DODGE == actionIndex) {
            rollDirection = getInputDirection();
            rollDistanceRemaining = gp.tileSize * ROLL_DISTANCE_TILES;
        }
    }

    private void animateCurrentCombatAction() {
        int actionIndex = getActionIndex();
        if (DODGE == actionIndex) {
            updateRollMovement();
        }
        combatCounter++;
        if (combatCounter >= 8) {
            combatCounter = 0;
            if (combatFrameIndex >= COMBAT_FRAME_COUNTS[actionIndex] - 1) {
                actionState = IDLE;
                combatFrameIndex = 0;
                rollDistanceRemaining = 0;
            } else {
                combatFrameIndex++;
            }
        }
    }

    private void animateCombat(int actionIndex) {
        combatCounter++;
        if (combatCounter >= 8) {
            combatCounter = 0;
            combatFrameIndex++;
            if (combatFrameIndex >= COMBAT_FRAME_COUNTS[actionIndex]) {
                combatFrameIndex = 0;
            }
        }
    }

    private int getActionIndex() {
        if ("KICK".equals(actionState)) {
            return KICK;
        } else if ("BLOCK".equals(actionState)) {
            return BLOCK;
        } else if ("DODGE".equals(actionState)) {
            return DODGE;
        }
        return PUNCH;
    }

    private void updateMovement() {
        if (!isMoving()) {
            return;
        }

        if (keyH.upPressed) {
            direction = "up";
        } else if (keyH.downPressed) {
            direction = "down";
        } else if (keyH.leftPressed) {
            direction = "left";
        } else if (keyH.rightPressed) {
            direction = "right";
        }

        collisionOn = false;
        gp.cChecker.checkTile(this);
        if (!collisionOn) {
            if ("up".equals(direction)) {
                worldY -= speed;
            } else if ("down".equals(direction)) {
                worldY += speed;
            } else if ("left".equals(direction)) {
                worldX -= speed;
            } else if ("right".equals(direction)) {
                worldX += speed;
            }
        }

        spriteCounter++;
        if (spriteCounter > 10) {
            spriteNum = spriteNum == 1 ? 2 : 1;
            spriteCounter = 0;
        }
    }

    private void updateRollMovement() {
        if (rollDistanceRemaining <= 0) {
            return;
        }

        int step = Math.min(ROLL_STEP, rollDistanceRemaining);
        direction = rollDirection;
        collisionOn = false;

        int originalSpeed = speed;
        speed = step;
        gp.cChecker.checkTile(this);
        speed = originalSpeed;

        if (collisionOn) {
            rollDistanceRemaining = 0;
            return;
        }

        if ("up".equals(rollDirection)) {
            worldY -= step;
        } else if ("down".equals(rollDirection)) {
            worldY += step;
        } else if ("left".equals(rollDirection)) {
            worldX -= step;
        } else if ("right".equals(rollDirection)) {
            worldX += step;
        }
        rollDistanceRemaining -= step;
    }

    private String getInputDirection() {
        if (keyH.upPressed) {
            return "up";
        } else if (keyH.downPressed) {
            return "down";
        } else if (keyH.leftPressed) {
            return "left";
        } else if (keyH.rightPressed) {
            return "right";
        }
        return direction;
    }

    private boolean isMoving() {
        return keyH.upPressed || keyH.downPressed
                || keyH.leftPressed || keyH.rightPressed;
    }

    private void updateIdleAnimation() {
        idleCounter++;
        if (idleCounter >= 12) {
            idleCounter = 0;
            idleFrameIndex = (idleFrameIndex + 1) % 4;
        }
    }

    private boolean isLockedAction() {
        return !"IDLE".equals(actionState);
    }

    public boolean isAttacking() {
        return weaponRenderer.isAttacking() || "PUNCH".equals(actionState) || "KICK".equals(actionState);
    }

    public int getAttackDamage() {
        Weapon equipped = getEquippedWeapon();
        if (equipped != null && weaponRenderer.isAttacking()) {
            return equipped.damage;
        }
        return "KICK".equals(actionState) ? 40 : 25;
    }

    public boolean isBlocking() {
        return "BLOCK".equals(actionState);
    }

    public boolean isDodging() {
        return "DODGE".equals(actionState);
    }

    public int getHealth() {
        return health;
    }

    public void takeDamage(int damage) {
        int defense = (gp != null && gp.equipmentManager != null)
                ? gp.equipmentManager.getTotalDefense() : 0;
        int effectiveDamage = Math.max(1, damage - defense / 2);
        health = Math.max(0, health - effectiveDamage);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    public void draw(Graphics2D g2) {
        // 1. Draw player sprite
        BufferedImage image = getCurrentPlayerImage();
        int playerRenderSize = getPlayerRenderSize();
        drawCentered(g2, image, playerRenderSize);

        // 2. Draw off-hand item (e.g. shield)
        drawOffHand(g2, screenX, screenY, direction);

        // 3. Draw armor overlays (helmet, chestplate, leggings, boots)
        drawArmorOverlays(g2, screenX, screenY, playerRenderSize, direction);

        // 4. Draw equipped weapon in hand (follows player and animates swings)
        weaponRenderer.draw(g2, screenX, screenY, direction, getEquippedWeapon());
    }

    private void drawArmorOverlays(Graphics2D g2, int sx, int sy, int drawSize, String dir) {
        if (gp == null || gp.equipmentManager == null) return;

        int drawX = sx - (drawSize - gp.tileSize) / 2;
        int drawY = sy - (drawSize - gp.tileSize) / 2;

        int cx = drawX + drawSize / 2;
        if ("left".equals(dir))  cx -= 2;
        if ("right".equals(dir)) cx += 2;

        // ── 1. Head (Helmet) ──────────────────────────────────────────────────
        Item head = gp.equipmentManager.getEquipped(EquipmentSlot.HEAD);
        if (head != null) {
            int topY = drawY + (int) (drawSize * 0.16);
            int hw   = (int) (drawSize * 0.28);
            int hh   = (int) (drawSize * 0.22);

            g2.setColor(new Color(170, 190, 215, 230));
            g2.fillArc(cx - hw / 2, topY, hw, hh, 0, 180);
            g2.setColor(new Color(80, 95, 120, 240));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawArc(cx - hw / 2, topY, hw, hh, 0, 180);
            // Visor brim
            g2.fillRect(cx - hw / 2 - 1, topY + hh / 2 - 2, hw + 2, 4);
            if (!"up".equals(dir)) {
                // Eye slit
                g2.setColor(new Color(15, 20, 30, 250));
                g2.fillRect(cx - hw / 4, topY + hh / 2 - 1, hw / 2, 2);
            }
            g2.setStroke(new BasicStroke(1f));
        }

        // ── 2. Chest (Chestplate) ─────────────────────────────────────────────
        Item chest = gp.equipmentManager.getEquipped(EquipmentSlot.CHEST);
        if (chest != null) {
            int cy = drawY + (int) (drawSize * 0.38);
            int cw = (int) (drawSize * 0.32);
            int ch = (int) (drawSize * 0.24);

            g2.setColor(new Color(155, 175, 200, 220));
            g2.fillRoundRect(cx - cw / 2, cy, cw, ch, 6, 6);
            g2.setColor(new Color(75, 90, 115, 230));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(cx - cw / 2, cy, cw, ch, 6, 6);
            // Pauldrons (shoulders)
            g2.setColor(new Color(130, 150, 180, 230));
            g2.fillOval(cx - cw / 2 - 4, cy, 8, 8);
            g2.fillOval(cx + cw / 2 - 4, cy, 8, 8);
            g2.setStroke(new BasicStroke(1f));
        }

        // ── 3. Legs (Leggings) ────────────────────────────────────────────────
        Item legs = gp.equipmentManager.getEquipped(EquipmentSlot.LEGS);
        if (legs != null) {
            int ly  = drawY + (int) (drawSize * 0.62);
            int lw  = (int) (drawSize * 0.12);
            int lh  = (int) (drawSize * 0.18);
            int gap = (int) (drawSize * 0.04);

            g2.setColor(new Color(140, 160, 185, 220));
            g2.fillRoundRect(cx - lw - gap / 2, ly, lw, lh, 4, 4);
            g2.fillRoundRect(cx + gap / 2, ly, lw, lh, 4, 4);
            // Waist guard
            g2.setColor(new Color(80, 95, 120, 230));
            g2.fillRect(cx - lw - gap / 2, ly, lw * 2 + gap, 3);
        }

        // ── 4. Feet (Boots) ───────────────────────────────────────────────────
        Item feet = gp.equipmentManager.getEquipped(EquipmentSlot.FEET);
        if (feet != null) {
            int by   = drawY + (int) (drawSize * 0.80);
            int bw   = (int) (drawSize * 0.13);
            int bh   = (int) (drawSize * 0.11);
            int bgap = (int) (drawSize * 0.04);

            g2.setColor(new Color(70, 80, 95, 240));
            g2.fillRoundRect(cx - bw - bgap / 2, by, bw, bh, 4, 4);
            g2.fillRoundRect(cx + bgap / 2, by, bw, bh, 4, 4);
            // Steel toe caps
            g2.setColor(new Color(175, 195, 220, 240));
            g2.fillRect(cx - bw - bgap / 2, by + bh - 3, bw, 3);
            g2.fillRect(cx + bgap / 2, by + bh - 3, bw, 3);
        }
    }

    private void drawOffHand(Graphics2D g2, int px, int py, String dir) {
        if (gp == null || gp.equipmentManager == null) return;
        Item offHand = gp.equipmentManager.getEquipped(EquipmentSlot.OFF_HAND);
        if (offHand == null) return;

        int ts = gp.tileSize;
        int sx, sy;
        int sw = 18, sh = 24;

        if (isBlocking()) {
            // Shield held up in front while blocking
            sx = px + ts / 2 - sw / 2;
            sy = py + ts / 2 - sh / 2;
        } else {
            // Resting on off-hand side
            switch (dir) {
                case "up"    -> { sx = px + 6;           sy = py + ts / 2 - 4; }
                case "down"  -> { sx = px + 4;           sy = py + ts / 2; }
                case "left"  -> { sx = px + ts - sw - 4; sy = py + ts / 2; }
                default      -> { sx = px + 4;           sy = py + ts / 2; } // right
            }
        }

        // Shield body
        g2.setColor(new Color(160, 180, 205));
        int[] xs = { sx + sw / 2, sx, sx, sx + sw, sx + sw };
        int[] ys = { sy + sh, sy + 4, sy, sy, sy + 4 };
        g2.fillPolygon(xs, ys, 5);

        // Center boss
        g2.setColor(new Color(220, 180, 60));
        g2.fillOval(sx + sw / 2 - 3, sy + sh / 2 - 4, 6, 6);

        // Rim
        g2.setColor(new Color(70, 90, 120));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawPolygon(xs, ys, 5);
        g2.setStroke(new BasicStroke(1f));
    }

    public BufferedImage getPlayerPreviewImage() {
        return down1;
    }

    private BufferedImage getCurrentPlayerImage() {
        if (!IDLE.equals(actionState)) {
            int directionIndex = getDirectionIndex();
            int actionIndex = getActionIndex();
            return combatFrames[directionIndex][actionIndex][combatFrameIndex];
        }

        if ("up".equals(direction)) {
            return spriteNum == 1 ? up1 : up2;
        } else if ("left".equals(direction)) {
            return spriteNum == 1 ? left1 : left2;
        } else if ("right".equals(direction)) {
            return spriteNum == 1 ? right1 : right2;
        }
        return spriteNum == 1 ? down1 : down2;
    }

    private int getDirectionIndex() {
        if ("up".equals(direction)) {
            return UP;
        } else if ("right".equals(direction)) {
            return RIGHT;
        } else if ("left".equals(direction)) {
            return LEFT;
        }
        return DOWN;
    }

    private int getPlayerRenderSize() {
        float renderScale = IDLE.equals(actionState)
                ? IDLE_RENDER_SCALE : PLAYER_RENDER_SCALE;
        return Math.round(gp.tileSize * renderScale);
    }

    private void drawCentered(Graphics2D g2, BufferedImage image, int drawSize) {
        if (image == null) {
            return;
        }
        int drawX = screenX - (drawSize - gp.tileSize) / 2;
        int drawY = screenY - (drawSize - gp.tileSize) / 2;
        g2.drawImage(image, drawX, drawY, drawSize, drawSize, null);
    }
}
