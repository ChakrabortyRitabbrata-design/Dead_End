package entities;

import main.DamagePopup;
import main.GamePanel;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

public class Monster {

    private static final int DIRECTION_COUNT = 4;
    private static final int FRAME_COUNT = 4;
    private static final int IDLE_ROW = 0;
    private static final int WALK_ROW = 1;
    private static final int ATTACK_ROW = 2;
    private static final int DEATH_ROW = 3;
    private static final int UP = 0;
    private static final int RIGHT = 1;
    private static final int DOWN = 2;
    private static final int LEFT = 3;

    private final GamePanel gp;
    private final BufferedImage[][][] frames =
            new BufferedImage[4][DIRECTION_COUNT][FRAME_COUNT];
    private final int maxHealth = 100;
    private final int detectionRange;
    private final int attackRange;

    public int worldX;
    public int worldY;
    public int health = maxHealth;

    private int animationCounter;
    private int animationFrame;
    private int attackCounter;
    private int attackCooldown;
    private int lastPlayerAttackSequence = -1;
    private String direction = "down";
    private boolean attacking;
    private boolean moving;
    private boolean dying;
    private boolean dead;
    private boolean attackDamageApplied;
    private int hurtFlashCounter;   // frames to flash red after being hit
    private static final int HURT_FLASH_FRAMES = 8;

    public Monster(GamePanel gp, int worldX, int worldY) {
        this.gp = gp;
        this.worldX = worldX;
        this.worldY = worldY;
        this.detectionRange = gp.tileSize * 12;
        this.attackRange = gp.tileSize + gp.tileSize / 2;
        loadImages();
    }

    private void loadImages() {
        try (InputStream stream = openResource(
                "player/Pixel-Art Undead Monster Spritesheet.png")) {
            BufferedImage sheet = makeTransparent(ImageIO.read(stream));
            if (sheet == null) {
                throw new IOException("Monster sheet is not a readable image.");
            }

            int frameWidth = 96;
            int[][] bands = {
                {145, 260},
                {338, 460},
                {530, 652},
                {738, 850}
            };

            for (int row = 0; row < 4; row++) {
                int y0 = bands[row][0];
                int frameHeight = bands[row][1] - y0;
                for (int directionIndex = 0; directionIndex < DIRECTION_COUNT;
                        directionIndex++) {
                    for (int frame = 0; frame < FRAME_COUNT; frame++) {
                        int column = directionIndex * FRAME_COUNT + frame;
                        BufferedImage cell = sheet.getSubimage(
                                column * frameWidth, y0,
                                frameWidth, frameHeight);
                        frames[row][directionIndex][frame] = normalizeFrame(cell, 64);
                    }
                }
            }
        } catch (IOException e) {
            loadFallbackImages();
        }
    }

    private BufferedImage normalizeFrame(BufferedImage source, int targetSize) {
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getRGB(x, y) >>> 24) != 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        }

        BufferedImage normalized = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        int contentWidth = maxX - minX + 1;
        int contentHeight = maxY - minY + 1;
        double scale = Math.min((double) targetSize / 96.0, (double) targetSize / 120.0);
        int drawWidth = Math.max(1, (int) Math.round(contentWidth * scale));
        int drawHeight = Math.max(1, (int) Math.round(contentHeight * scale));
        int drawX = (targetSize - drawWidth) / 2;
        int drawY = targetSize - drawHeight;

        Graphics2D graphics = normalized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(source.getSubimage(minX, minY, contentWidth, contentHeight),
                drawX, drawY, drawWidth, drawHeight, null);
        graphics.dispose();
        return normalized;
    }

    private void loadFallbackImages() {
        try (InputStream stream = openResource("player/Pixel Runner Sprite Sheet.png")) {
            BufferedImage sheet = makeTransparent(ImageIO.read(stream));
            int[] centersX = {785, 990};
            for (int row = 0; row < 4; row++) {
                for (int directionIndex = 0; directionIndex < DIRECTION_COUNT;
                        directionIndex++) {
                    for (int frame = 0; frame < FRAME_COUNT; frame++) {
                        int centerX = centersX[frame % centersX.length];
                        int centerY = 120 + row * 210;
                        BufferedImage sub = sheet.getSubimage(
                                centerX - 100, centerY - 100, 200, 200);
                        frames[row][directionIndex][frame] = normalizeFrame(sub, 64);
                    }
                }
            }
        } catch (IOException | RuntimeException fallbackError) {
            throw new IllegalStateException("Unable to load monster sprites or fallback asset.",
                    fallbackError);
        }
    }

    private BufferedImage makeTransparent(BufferedImage image) {
        if (image == null) {
            return null;
        }
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int rgb = argb & 0x00FFFFFF;
                result.setRGB(x, y, rgb < 0x101010 ? rgb : argb | 0xFF000000);
            }
        }
        return result;
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
            resource = Paths.get("Dead_End", "res", resourcePath);
        }
        if (!Files.exists(resource)) {
            resource = Paths.get("Dead_End", "bin", resourcePath);
        }
        if (!Files.exists(resource)) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        return Files.newInputStream(resource);
    }

    public void update() {
        if (dead) {
            return;
        }
        if (dying) {
            updateDeathAnimation();
            return;
        }

        if (attackCooldown > 0) {
            attackCooldown--;
        }
        applyPlayerAttack();

        double distance = distanceToPlayer();
        if (attacking) {
            updateAttack();
        } else if (distance <= detectionRange) {
            if (distance <= attackRange) {
                moving = false;
                startAttack();
            } else {
                followPlayer();
            }
        } else {
            moving = false;
            animate(IDLE_ROW);
        }
    }

    private void applyPlayerAttack() {
        Rectangle attackHitbox = gp.player.getAttackHitbox();
        Rectangle monsterBounds = new Rectangle(worldX, worldY, gp.tileSize, gp.tileSize);
        if (attackHitbox == null
                || !attackHitbox.intersects(monsterBounds)
                || lastPlayerAttackSequence == gp.player.attackSequence) {
            return;
        }
        lastPlayerAttackSequence = gp.player.attackSequence;
        takeDamage(gp.player.getAttackDamage());
    }

    private void followPlayer() {
        moving = true;
        int deltaX = gp.player.worldX - worldX;
        int deltaY = gp.player.worldY - worldY;
        if (Math.abs(deltaX) > Math.abs(deltaY)) {
            direction = deltaX < 0 ? "left" : "right";
        } else {
            direction = deltaY < 0 ? "up" : "down";
        }

        int step = 2;
        if ("up".equals(direction)) {
            worldY -= step;
        } else if ("down".equals(direction)) {
            worldY += step;
        } else if ("left".equals(direction)) {
            worldX -= step;
        } else {
            worldX += step;
        }
        animate(WALK_ROW);
    }

    private void startAttack() {
        if (attackCooldown > 0) {
            animate(IDLE_ROW);
            return;
        }
        attacking = true;
        attackDamageApplied = false;
        attackCounter = 0;
        animationFrame = 0;
    }

    private void updateAttack() {
        attackCounter++;
        if (attackCounter == 12 && !attackDamageApplied
                && distanceToPlayer() <= attackRange) {
            attackDamageApplied = true;
            if (!gp.player.isBlocking() && !gp.player.isDodging()) {
                gp.player.takeDamage(10);
            }
        }
        if (attackCounter >= 20) {
            attacking = false;
            attackCooldown = 30;
            animationFrame = 0;
        } else if (attackCounter % 5 == 0) {
            animationFrame = Math.min(FRAME_COUNT - 1, animationFrame + 1);
        }
    }

    public void takeDamage(int damage) {
        if (dying || dead) {
            return;
        }
        health = Math.max(0, health - damage);
        hurtFlashCounter = HURT_FLASH_FRAMES;
        int screenX = worldX - gp.player.worldX + gp.player.screenX + gp.tileSize / 2;
        int screenY = worldY - gp.player.worldY + gp.player.screenY - 10;
        DamagePopup.spawn(screenX, screenY, damage, false);
        if (health == 0) {
            dying = true;
            attacking = false;
            animationFrame = 0;
            animationCounter = 0;
        }
    }

    private void updateDeathAnimation() {
        animationCounter++;
        if (animationCounter >= 8) {
            animationCounter = 0;
            animationFrame++;
            if (animationFrame >= FRAME_COUNT) {
                animationFrame = FRAME_COUNT - 1;
                dead = true;
            }
        }
    }

    private void animate(int row) {
        animationCounter++;
        if (animationCounter >= 8) {
            animationCounter = 0;
            animationFrame = (animationFrame + 1) % FRAME_COUNT;
        }
    }

    private double distanceToPlayer() {
        return Math.hypot(gp.player.worldX - worldX, gp.player.worldY - worldY);
    }

    public boolean isDead() {
        return dead;
    }

    public void draw(Graphics2D g2) {
        if (dead) {
            return;
        }
        // Advance hurt flash counter
        if (hurtFlashCounter > 0) {
            hurtFlashCounter--;
        }

        int directionIndex = getDirectionIndex();
        int row = dying ? DEATH_ROW : attacking ? ATTACK_ROW : moving ? WALK_ROW : IDLE_ROW;
        BufferedImage image = frames[row][directionIndex][animationFrame];
        int screenX = worldX - gp.player.worldX + gp.player.screenX;
        int screenY = worldY - gp.player.worldY + gp.player.screenY;
        int size = gp.tileSize * 2;
        int drawX = screenX + (gp.tileSize - size) / 2;
        int drawY = screenY + gp.tileSize - size;

        Object oldInterpolation = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // Hurt flash: draw semi-transparent red overlay every other frame
        if (hurtFlashCounter > 0 && (hurtFlashCounter % 2 == 0)) {
            // Draw tinted version: draw image, then red overlay
            g2.drawImage(image, drawX, drawY, size, size, null);
            java.awt.Composite old2 = g2.getComposite();
            g2.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, 0.55f));
            g2.setColor(new Color(255, 60, 60));
            g2.fillRect(drawX, drawY, size, size);
            g2.setComposite(old2);
        } else {
            g2.drawImage(image, drawX, drawY, size, size, null);
        }

        if (oldInterpolation != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }

        // ── Health bar ───────────────────────────────────────────────────────
        if (!dying) {
            int barWidth  = gp.tileSize + 10;
            int barHeight = 7;
            int barX = screenX + (gp.tileSize - barWidth) / 2;
            int barY = drawY - 12;
            float ratio = (float) health / maxHealth;

            // Background
            g2.setColor(new Color(30, 30, 30, 200));
            g2.fillRoundRect(barX - 1, barY - 1, barWidth + 2, barHeight + 2, 4, 4);

            // Fill: green → yellow → red based on HP ratio
            Color fillColor;
            if (ratio > 0.6f) {
                fillColor = new Color(50, 200, 50);
            } else if (ratio > 0.3f) {
                fillColor = new Color(230, 200, 20);
            } else {
                fillColor = new Color(210, 40, 40);
            }
            g2.setColor(fillColor);
            g2.fillRoundRect(barX, barY, (int)(barWidth * ratio), barHeight, 3, 3);

            // Border
            g2.setColor(new Color(10, 10, 10, 220));
            g2.drawRoundRect(barX - 1, barY - 1, barWidth + 2, barHeight + 2, 4, 4);
        }
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
}
