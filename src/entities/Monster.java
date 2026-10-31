package entities;

import main.GamePanel;

import java.awt.Color;
import java.awt.Graphics2D;
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

            int frameWidth = sheet.getWidth() / 16;
            int frameHeight = sheet.getHeight() / 4;
            for (int row = 0; row < 4; row++) {
                for (int directionIndex = 0; directionIndex < DIRECTION_COUNT;
                        directionIndex++) {
                    for (int frame = 0; frame < FRAME_COUNT; frame++) {
                        int column = directionIndex * FRAME_COUNT + frame;
                        frames[row][directionIndex][frame] = sheet.getSubimage(
                                column * frameWidth, row * frameHeight,
                                frameWidth, frameHeight);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load monster sprites.", e);
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
        if (!gp.player.isAttacking()
                || distanceToPlayer() > gp.tileSize * 2
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
        int directionIndex = getDirectionIndex();
        int row = dying ? DEATH_ROW : attacking ? ATTACK_ROW : moving ? WALK_ROW : IDLE_ROW;
        BufferedImage image = frames[row][directionIndex][animationFrame];
        int screenX = worldX - gp.player.worldX + gp.player.screenX;
        int screenY = worldY - gp.player.worldY + gp.player.screenY;
        int size = gp.tileSize * 2;
        g2.drawImage(image, screenX - gp.tileSize / 2, screenY - gp.tileSize / 2,
                size, size, null);

        int barWidth = gp.tileSize;
        int barHeight = 6;
        int barX = screenX + (gp.tileSize - barWidth) / 2;
        int barY = screenY - gp.tileSize / 2 - 8;
        g2.setColor(Color.DARK_GRAY);
        g2.fillRect(barX, barY, barWidth, barHeight);
        g2.setColor(Color.RED);
        g2.fillRect(barX, barY, barWidth * health / maxHealth, barHeight);
        g2.setColor(Color.BLACK);
        g2.drawRect(barX, barY, barWidth, barHeight);
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
