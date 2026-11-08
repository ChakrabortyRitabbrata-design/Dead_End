package entities;

import animation.Animation;
import animation.AnimationController;
import animation.AnimationFrame;
import animation.AnimationState;
import animation.Direction;
import animation.Hitbox;
import animation.SpriteSheetMapping;
import main.GamePanel;
import main.KeyHandler;
import inventory.EquipmentSlot;
import inventory.Item;
import inventory.Weapon;

import java.awt.BasicStroke;
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

/**
 * Player Entity
 *
 * Preserves the original purple-haired RPG character in the red outfit and cape.
 * Implements frame-specific hand attachment for weapons and equipment.
 */
public class Player extends Entity {
    public static final long IDLE_FRAME = 150_000_000L;
    public static final long WALK_FRAME = 110_000_000L;
    public static final long RUN_FRAME = 75_000_000L;
    public static final long PUNCH_FRAME = 85_000_000L;
    public static final long KICK_FRAME = 100_000_000L;
    public static final long DODGE_FRAME = 75_000_000L;
    public static final long ROLL_FRAME = 90_000_000L;
    public static final long HURT_FRAME = 100_000_000L;
    public static final long DIE_FRAME = 150_000_000L;
    public static final long DODGE_COOLDOWN_NANOS = 450_000_000L;
    private static final long DAMAGE_INVULNERABILITY_NANOS = 500_000_000L;
    private static final long HURT_LOCK_NANOS = 140_000_000L;
    public static final int DODGE_DISTANCE_TILES = 2;
    public static final int DODGE_SPEED = 8;
    private static final int ROLL_DISTANCE = 3;
    private static final int ROLL_SPEED = 6;

    private final GamePanel gp;
    private final KeyHandler keyH;
    private final AnimationController animationController = new AnimationController();
    private final Animation[][] animations =
            new Animation[Direction.values().length][AnimationState.values().length];

    public final int screenX;
    public final int screenY;
    public String actionState = AnimationState.IDLE.name();
    public int combatFrameIndex;
    public int attackSequence;

    private AnimationState state = AnimationState.IDLE;
    private Direction facing = Direction.UP;
    private Direction actionDirection = Direction.UP;
    private int health = 100;
    private long attackCooldownNanos;
    private long dodgeCooldownNanos;
    private long damageInvulnerabilityNanos;
    private long hurtRemainingNanos;
    private int dodgeDistanceRemaining;
    private int rollDistanceRemaining;
    private boolean attackHitApplied;

    // Precision weapon renderer
    private final WeaponRenderer weaponRenderer;

    public Player(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;
        screenX = gp.screenWidth / 2 - gp.tileSize / 2;
        screenY = gp.screenHeight / 2 - gp.tileSize / 2;
        solidArea = new Rectangle(8, 16, 32, 32);

        weaponRenderer = new WeaponRenderer(gp);

        setDefaultValues();
        loadAnimations();
    }

    public void setDefaultValues() {
        worldX = gp.tileSize * 49 + gp.tileSize / 2;
        worldY = gp.tileSize * 90;
        speed = 4;
        direction = "up";
        facing = Direction.UP;
        actionDirection = Direction.UP;
    }

    /**
     * Loads the original Dead End!! character animations from reference sprite sheets.
     */
    private void loadAnimations() {
        try {
            BufferedImage idleSheet = readImage(
                    "player/Four-Direction Pixel RPG Sprite Sheet.png");
            BufferedImage walkingSheet = readImage(
                    "player/Pixel Art Walking Hero Sprite Sheet.png");
            BufferedImage runningSheet = readImage(
                    "player/Pixel Runner Sprite Sheet.png");
            BufferedImage dodgeSheet = readImage(
                    "player/Purple-Haired Scarf Dodge Sprite Sheet.png");
            loadIdleAnimations(idleSheet);
            loadWalkingAnimations(walkingSheet);
            loadRunningAnimations(runningSheet);
            loadDodgeAnimations(dodgeSheet);
            loadPunchAndKickAnimations(readImage(
                    "player/Purple-Haired RPG Hero Punch and Kick Sprite Sheet.png"));
            loadDeathAnimations(readImage(
                    "player/Pixel Hero Four-Direction Death Animation.png"));
            loadFallbackAnimationsFromWalking();
            selectAnimation();
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Unable to load original player animations.", e);
        }
    }

    private void loadFallbackAnimationsFromWalking() {
        for (Direction dir : Direction.values()) {
            AnimationFrame[] frames = new AnimationFrame[2];
            for (int frame = 0; frame < frames.length; frame++) {
                frames[frame] = animations[dir.ordinal()][AnimationState.WALK.ordinal()]
                        .getFrame(frame);
            }
            for (AnimationState animState : AnimationState.values()) {
                if (animState == AnimationState.WALK || animState == AnimationState.IDLE
                        || animState == AnimationState.RUN || animState == AnimationState.DODGE
                        || animState == AnimationState.PUNCH || animState == AnimationState.KICK
                        || animState == AnimationState.DIE) {
                    continue;
                }
                boolean looping = false;
                animations[dir.ordinal()][animState.ordinal()] =
                        new Animation(frames, looping);
            }
        }
    }

    private void loadDodgeAnimations(BufferedImage dodgeSheet) {
        for (Direction dir : Direction.values()) {
            AnimationFrame[] frames = new AnimationFrame[4];
            for (int frame = 0; frame < 4; frame++) {
                Rectangle bounds = SpriteSheetMapping.dodgeFrameBounds(dir, frame);
                frames[frame] = new AnimationFrame(
                        normalizeFrame(dodgeSheet.getSubimage(
                                bounds.x, bounds.y, bounds.width, bounds.height)),
                        DODGE_FRAME);
            }

            animations[dir.ordinal()][AnimationState.DODGE.ordinal()] =
                    new Animation(frames, false);
        }
    }

    private void loadPunchAndKickAnimations(BufferedImage combatSheet) {
        for (Direction dir : Direction.values()) {
            AnimationFrame[] punches = new AnimationFrame[4];
            AnimationFrame[] kicks = new AnimationFrame[4];
            for (int frame = 0; frame < 4; frame++) {
                Rectangle punchBounds = SpriteSheetMapping.punchFrameBounds(dir, frame);
                Rectangle kickBounds = SpriteSheetMapping.kickFrameBounds(dir, frame);
                punches[frame] = new AnimationFrame(
                        normalizeFrame(combatSheet.getSubimage(
                                punchBounds.x, punchBounds.y,
                                punchBounds.width, punchBounds.height)),
                        PUNCH_FRAME);
                kicks[frame] = new AnimationFrame(
                        normalizeFrame(combatSheet.getSubimage(
                                kickBounds.x, kickBounds.y,
                                kickBounds.width, kickBounds.height)),
                        KICK_FRAME);
            }

            animations[dir.ordinal()][AnimationState.PUNCH.ordinal()] =
                    new Animation(punches, false);
            animations[dir.ordinal()][AnimationState.KICK.ordinal()] =
                    new Animation(kicks, false);
        }
    }

    private void loadDeathAnimations(BufferedImage deathSheet) {
        for (Direction dir : Direction.values()) {
            AnimationFrame[] frames = new AnimationFrame[4];
            for (int frame = 0; frame < frames.length; frame++) {
                Rectangle bounds = SpriteSheetMapping.deathFrameBounds(dir, frame);
                frames[frame] = new AnimationFrame(
                        normalizeFrame(deathSheet.getSubimage(
                                bounds.x, bounds.y, bounds.width, bounds.height)),
                        DIE_FRAME);
            }
            animations[dir.ordinal()][AnimationState.DIE.ordinal()] =
                    new Animation(frames, false);
        }
    }

    private void loadIdleAnimations(BufferedImage idleSheet) {
        for (Direction dir : Direction.values()) {
            Rectangle bounds = SpriteSheetMapping.idleFrameBounds(dir);
            AnimationFrame[] frames = new AnimationFrame[] {
                new AnimationFrame(
                        normalizeFrame(idleSheet.getSubimage(
                                bounds.x, bounds.y, bounds.width, bounds.height)),
                        IDLE_FRAME)
            };
            animations[dir.ordinal()][AnimationState.IDLE.ordinal()] =
                    new Animation(frames, true);
        }
    }

    private void loadWalkingAnimations(BufferedImage walkingSheet) {
        for (Direction dir : Direction.values()) {
            AnimationFrame[] frames = new AnimationFrame[2];
            for (int frame = 0; frame < frames.length; frame++) {
                Rectangle bounds = SpriteSheetMapping.walkingFrameBounds(dir, frame);
                frames[frame] = new AnimationFrame(
                        normalizeFrame(walkingSheet.getSubimage(
                                bounds.x, bounds.y, bounds.width, bounds.height)),
                        WALK_FRAME);
            }
            animations[dir.ordinal()][AnimationState.WALK.ordinal()] =
                    new Animation(frames, true);
        }
    }

    private void loadRunningAnimations(BufferedImage runningSheet) {
        for (Direction dir : Direction.values()) {
            AnimationFrame[] frames = new AnimationFrame[2];
            for (int frame = 0; frame < frames.length; frame++) {
                Rectangle bounds = SpriteSheetMapping.runningFrameBounds(dir, frame);
                frames[frame] = new AnimationFrame(
                        normalizeFrame(runningSheet.getSubimage(
                                bounds.x, bounds.y, bounds.width, bounds.height)),
                        RUN_FRAME);
            }
            animations[dir.ordinal()][AnimationState.RUN.ordinal()] =
                    new Animation(frames, true);
        }
    }

    private BufferedImage readImage(String resourcePath) throws IOException {
        try (InputStream stream = openResource(resourcePath)) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                throw new IOException("Resource is not a readable image: " + resourcePath);
            }
            return makeTransparent(image);
        }
    }

    private BufferedImage makeTransparent(BufferedImage image) {
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

    private BufferedImage normalizeFrame(BufferedImage source) {
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
            return source;
        }

        BufferedImage normalized = new BufferedImage(
                SpriteSheetMapping.SOURCE_FRAME_SIZE,
                SpriteSheetMapping.SOURCE_FRAME_SIZE,
                BufferedImage.TYPE_INT_ARGB);
        int contentWidth = maxX - minX + 1;
        int contentHeight = maxY - minY + 1;
        double scale = Math.min(
                (double) normalized.getWidth() / contentWidth,
                (double) normalized.getHeight() / contentHeight);
        int drawWidth = Math.max(1, (int) Math.round(contentWidth * scale));
        int drawHeight = Math.max(1, (int) Math.round(contentHeight * scale));
        int drawX = (normalized.getWidth() - drawWidth) / 2;
        int drawY = normalized.getHeight() - drawHeight;
        Graphics2D graphics = normalized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(source.getSubimage(minX, minY, contentWidth, contentHeight),
                drawX, drawY, drawWidth, drawHeight, null);
        graphics.dispose();
        return normalized;
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

    // ── Weapon Equipment Helper ───────────────────────────────────────────────

    public Weapon getEquippedWeapon() {
        if (gp != null && gp.equipmentManager != null) {
            Item item = gp.equipmentManager.getEquipped(EquipmentSlot.MAIN_HAND);
            if (item instanceof Weapon w) {
                return w;
            }
        }
        return null;
    }

    public WeaponRenderer getWeaponRenderer() {
        return weaponRenderer;
    }

    public AnimationController getAnimationController() {
        return animationController;
    }

    public AnimationState getState() {
        return state;
    }

    public Direction getFacing() {
        return facing;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public void update() {
        update(16_666_667L);
    }

    public void update(long deltaNanos) {
        long delta = Math.min(deltaNanos, 100_000_000L);
        attackCooldownNanos = Math.max(0, attackCooldownNanos - delta);
        dodgeCooldownNanos = Math.max(0, dodgeCooldownNanos - delta);
        damageInvulnerabilityNanos = Math.max(0, damageInvulnerabilityNanos - delta);
        hurtRemainingNanos = Math.max(0, hurtRemainingNanos - delta);

        if (weaponRenderer != null) {
            weaponRenderer.update();
        }

        if (state == AnimationState.DIE) {
            updateAnimation(delta);
            return;
        }
        if (state == AnimationState.HURT) {
            updateHurt(delta);
            return;
        }
        if (isActionLocked()) {
            updateLockedAction(delta);
            return;
        }

        // Weapon Attack or Punch/Kick trigger
        Weapon equipped = getEquippedWeapon();
        boolean attackRequested = keyH.attackPressed || keyH.consumePunch();
        if (attackRequested && attackCooldownNanos == 0) {
            keyH.attackPressed = false;
            if (equipped != null) {
                if (weaponRenderer != null && !weaponRenderer.isAttacking()) {
                    weaponRenderer.startAttack(equipped);
                    startAction(AnimationState.PUNCH); // Plays attack motion with character's arm
                    attackSequence++;
                    attackCooldownNanos = 250_000_000L;
                }
            } else {
                startAction(AnimationState.PUNCH);
            }
        } else if (keyH.consumeKick() && attackCooldownNanos == 0) {
            startAction(AnimationState.KICK);
        } else if (keyH.consumeDodge() && dodgeCooldownNanos == 0) {
            startAction(AnimationState.DODGE);
        } else if (keyH.consumeRoll() && dodgeCooldownNanos == 0) {
            startAction(AnimationState.ROLL);
        } else {
            updateMovement(delta);
        }
    }

    private void updateHurt(long delta) {
        updateAnimation(delta);
        if (hurtRemainingNanos == 0 || animationController.isFinished()) {
            resumeFromHit();
        }
    }

    private void resumeFromHit() {
        hurtRemainingNanos = 0;
        if (hasMovementInput()) {
            facing = inputDirection();
            direction = directionName(facing);
            setState(keyH.runPressed ? AnimationState.RUN : AnimationState.WALK);
        } else {
            setState(AnimationState.IDLE);
        }
    }

    private void updateLockedAction(long delta) {
        if (state == AnimationState.DODGE) {
            updateDodgeMovement();
        } else if (state == AnimationState.ROLL) {
            updateEvasionMovement();
        }
        updateAnimation(delta);
        if (animationController.isFinished()) {
            if (state == AnimationState.HURT && health > 0) {
                setState(AnimationState.IDLE);
            } else if (state != AnimationState.DIE) {
                dodgeDistanceRemaining = 0;
                rollDistanceRemaining = 0;
                if (hasMovementInput()) {
                    facing = inputDirection();
                    direction = directionName(facing);
                    setState(keyH.runPressed ? AnimationState.RUN : AnimationState.WALK);
                } else {
                    setState(AnimationState.IDLE);
                }
            }
        }
    }

    private void updateDodgeMovement() {
        if (dodgeDistanceRemaining <= 0) {
            return;
        }
        int step = Math.min(DODGE_SPEED, dodgeDistanceRemaining);
        direction = directionName(actionDirection);
        moveWithCollision(step);
        dodgeDistanceRemaining -= step;
    }

    private void updateAnimation(long delta) {
        animationController.update(delta);
        combatFrameIndex = animationController.getFrameIndex();
        if (isAttackActive() && !attackHitApplied) {
            attackHitApplied = true;
        }
    }

    private void startAction(AnimationState nextState) {
        actionDirection = nextState == AnimationState.DODGE || nextState == AnimationState.ROLL
                ? inputDirection() : facing;
        facing = actionDirection;
        direction = directionName(facing);
        attackHitApplied = false;
        if (nextState == AnimationState.PUNCH || nextState == AnimationState.KICK) {
            attackSequence++;
            attackCooldownNanos = nextState == AnimationState.PUNCH
                    ? 260_000_000L : 450_000_000L;
        }
        if (nextState == AnimationState.DODGE) {
            dodgeCooldownNanos = DODGE_COOLDOWN_NANOS;
            dodgeDistanceRemaining = gp.tileSize * DODGE_DISTANCE_TILES;
        } else if (nextState == AnimationState.ROLL) {
            dodgeCooldownNanos = 650_000_000L;
            rollDistanceRemaining = gp.tileSize * ROLL_DISTANCE;
        }
        setState(nextState);
    }

    private void updateMovement(long delta) {
        Direction input = inputDirection();
        boolean moving = hasMovementInput();
        if (moving) {
            facing = input;
            direction = directionName(facing);
            speed = keyH.runPressed ? 6 : 4;
            moveWithCollision(speed);
            setState(keyH.runPressed ? AnimationState.RUN : AnimationState.WALK);
        } else {
            speed = 4;
            setState(AnimationState.IDLE);
        }
        updateAnimation(delta);
    }

    private void updateEvasionMovement() {
        if (rollDistanceRemaining <= 0) {
            return;
        }
        int step = state == AnimationState.ROLL ? ROLL_SPEED : 5;
        step = Math.min(step, rollDistanceRemaining);
        speed = step;
        direction = directionName(actionDirection);
        moveWithCollision(step);
        rollDistanceRemaining -= step;
        speed = 4;
    }

    private void moveWithCollision(int amount) {
        collisionOn = false;
        int originalSpeed = speed;
        speed = amount;
        gp.cChecker.checkTile(this);
        speed = originalSpeed;
        if (collisionOn) {
            return;
        }
        switch (facingForMovement()) {
        case UP: worldY -= amount; break;
        case RIGHT: worldX += amount; break;
        case LEFT: worldX -= amount; break;
        default: worldY += amount; break;
        }
    }

    private Direction facingForMovement() {
        return state == AnimationState.DODGE || state == AnimationState.ROLL
                ? actionDirection : facing;
    }

    private Direction inputDirection() {
        String movementDirection = keyH.getMovementDirection();
        if (movementDirection != null) {
            return Direction.valueOf(movementDirection.toUpperCase());
        }
        return facing;
    }

    private boolean hasMovementInput() {
        return keyH.upPressed || keyH.downPressed || keyH.leftPressed || keyH.rightPressed;
    }

    private void setState(AnimationState nextState) {
        Direction nextDirection = isActionState(nextState) ? actionDirection : facing;
        if (state == nextState
                && animationController.getDirection() == nextDirection) {
            return;
        }
        state = nextState;
        actionState = nextState.name();
        selectAnimation();
    }

    private void selectAnimation() {
        Direction animationDirection = isActionLocked() ? actionDirection : facing;
        animationController.setAnimation(state, animationDirection,
                animations[animationDirection.ordinal()][state.ordinal()]);
    }

    private boolean isActionState(AnimationState animationState) {
        return animationState == AnimationState.PUNCH
                || animationState == AnimationState.KICK
                || animationState == AnimationState.DODGE
                || animationState == AnimationState.ROLL
                || animationState == AnimationState.HURT
                || animationState == AnimationState.DIE;
    }

    private boolean isActionLocked() {
        return state == AnimationState.PUNCH || state == AnimationState.KICK
                || state == AnimationState.DODGE || state == AnimationState.ROLL;
    }

    public boolean isAttacking() {
        if (weaponRenderer != null && weaponRenderer.isAttacking()) {
            return true;
        }
        return (state == AnimationState.PUNCH || state == AnimationState.KICK)
                && isAttackActive();
    }

    private boolean isAttackActive() {
        return state == AnimationState.PUNCH && combatFrameIndex == 1
                || state == AnimationState.KICK && combatFrameIndex == 2;
    }

    public Rectangle getAttackHitbox() {
        if (!isAttacking()) {
            return null;
        }
        Weapon equipped = getEquippedWeapon();
        int reach = (equipped != null) ? Math.max(gp.tileSize + 16, equipped.range)
                : (state == AnimationState.KICK ? gp.tileSize + 12 : gp.tileSize);
        return Hitbox.inFront(worldX + gp.tileSize / 2, worldY + gp.tileSize / 2,
                actionDirection, reach, gp.tileSize - 4, gp.tileSize - 4);
    }

    public int getAttackDamage() {
        Weapon equipped = getEquippedWeapon();
        if (equipped != null && weaponRenderer != null && weaponRenderer.isAttacking()) {
            return equipped.damage;
        }
        return state == AnimationState.KICK ? 20 : 10;
    }

    public boolean isBlocking() {
        return keyH.blockPressed && state == AnimationState.IDLE;
    }

    public boolean isDodging() {
        return state == AnimationState.DODGE || state == AnimationState.ROLL;
    }

    public int getHealth() {
        return health;
    }

    public int getMaxHealth() {
        return 100;
    }

    public void takeDamage(int damage) {
        if (health <= 0 || isDodging() || damageInvulnerabilityNanos > 0) {
            return;
        }
        int defense = (gp != null && gp.equipmentManager != null)
                ? gp.equipmentManager.getTotalDefense() : 0;
        int effectiveDamage = Math.max(1, damage - defense / 2);
        damageInvulnerabilityNanos = DAMAGE_INVULNERABILITY_NANOS;
        hurtRemainingNanos = HURT_LOCK_NANOS;
        health = Math.max(0, health - effectiveDamage);
        if (health == 0) {
            actionDirection = facing;
            setState(AnimationState.DIE);
        } else {
            actionDirection = facing;
            setState(AnimationState.HURT);
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    public void draw(Graphics2D g2) {
        BufferedImage image = animationController.getCurrentFrame().getImage();
        int drawSize = gp.tileSize * 2;
        int drawX = screenX + (gp.tileSize - drawSize) / 2;
        int drawY = screenY + gp.tileSize - drawSize;

        Weapon equipped = getEquippedWeapon();
        int frameIdx = animationController.getFrameIndex();

        // 1. Draw rear-layer weapon if facing UP
        if (equipped != null && weaponRenderer != null) {
            weaponRenderer.drawHandAnchored(g2, drawX, drawY, drawSize, state, facing, frameIdx, equipped, "BEHIND");
        }

        // 2. Draw off-hand item behind if facing RIGHT
        if (facing == Direction.RIGHT) {
            drawOffHand(g2, drawX, drawY, drawSize, facing);
        }

        // 3. Draw the original player character sprite
        Object oldInterpolation = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(image, drawX, drawY, drawSize, drawSize, null);
        if (oldInterpolation != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }

        // 4. Draw armor overlays
        drawArmorOverlays(g2, drawX, drawY, drawSize, facing);

        // 5. Draw off-hand item in front if facing DOWN, LEFT, UP
        if (facing != Direction.RIGHT) {
            drawOffHand(g2, drawX, drawY, drawSize, facing);
        }

        // 6. Draw front-layer weapon in hand
        if (equipped != null && weaponRenderer != null) {
            weaponRenderer.drawHandAnchored(g2, drawX, drawY, drawSize, state, facing, frameIdx, equipped, "FRONT");
        }
    }

    private void drawArmorOverlays(Graphics2D g2, int drawX, int drawY, int drawSize, Direction dir) {
        if (gp == null || gp.equipmentManager == null) return;

        double scale = (double) drawSize / 64.0;
        int cx = drawX + drawSize / 2;
        if (dir == Direction.LEFT)  cx -= 3;
        if (dir == Direction.RIGHT) cx += 3;

        // ── 1. Head (Helmet) ──────────────────────────────────────────────────
        Item head = gp.equipmentManager.getEquipped(EquipmentSlot.HEAD);
        if (head != null) {
            int topY = drawY + (int) (12 * scale);
            int hw   = (int) (26 * scale);
            int hh   = (int) (18 * scale);

            g2.setColor(new Color(175, 195, 220, 235));
            g2.fillArc(cx - hw / 2, topY, hw, hh, 0, 180);
            g2.setColor(new Color(80, 95, 120, 240));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawArc(cx - hw / 2, topY, hw, hh, 0, 180);
            g2.fillRect(cx - hw / 2 - 1, topY + hh / 2 - 2, hw + 2, 4);
            if (dir != Direction.UP) {
                g2.setColor(new Color(15, 20, 30, 250));
                g2.fillRect(cx - hw / 4, topY + hh / 2 - 1, hw / 2, 2);
            }
            g2.setStroke(new BasicStroke(1f));
        }

        // ── 2. Chest (Chestplate) ─────────────────────────────────────────────
        Item chest = gp.equipmentManager.getEquipped(EquipmentSlot.CHEST);
        if (chest != null) {
            int cy = drawY + (int) (26 * scale);
            int cw = (int) (22 * scale);
            int ch = (int) (16 * scale);

            g2.setColor(new Color(160, 180, 205, 225));
            g2.fillRoundRect(cx - cw / 2, cy, cw, ch, 6, 6);
            g2.setColor(new Color(75, 90, 115, 230));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(cx - cw / 2, cy, cw, ch, 6, 6);
            g2.setColor(new Color(130, 150, 180, 230));
            g2.fillOval(cx - cw / 2 - 3, cy, 7, 7);
            g2.fillOval(cx + cw / 2 - 4, cy, 7, 7);
            g2.setStroke(new BasicStroke(1f));
        }

        // ── 3. Legs (Leggings) ────────────────────────────────────────────────
        Item legs = gp.equipmentManager.getEquipped(EquipmentSlot.LEGS);
        if (legs != null) {
            int ly  = drawY + (int) (42 * scale);
            int lw  = (int) (8 * scale);
            int lh  = (int) (10 * scale);
            int gap = (int) (4 * scale);

            g2.setColor(new Color(145, 165, 190, 220));
            g2.fillRoundRect(cx - lw - gap / 2, ly, lw, lh, 4, 4);
            g2.fillRoundRect(cx + gap / 2, ly, lw, lh, 4, 4);
            g2.setColor(new Color(80, 95, 120, 230));
            g2.fillRect(cx - lw - gap / 2, ly, lw * 2 + gap, 3);
        }

        // ── 4. Feet (Boots) ───────────────────────────────────────────────────
        Item feet = gp.equipmentManager.getEquipped(EquipmentSlot.FEET);
        if (feet != null) {
            int by   = drawY + (int) (52 * scale);
            int bw   = (int) (9 * scale);
            int bh   = (int) (8 * scale);
            int bgap = (int) (4 * scale);

            g2.setColor(new Color(70, 80, 95, 240));
            g2.fillRoundRect(cx - bw - bgap / 2, by, bw, bh, 4, 4);
            g2.fillRoundRect(cx + bgap / 2, by, bw, bh, 4, 4);
            g2.setColor(new Color(175, 195, 220, 240));
            g2.fillRect(cx - bw - bgap / 2, by + bh - 2, bw, 2);
            g2.fillRect(cx + bgap / 2, by + bh - 2, bw, 2);
        }
    }

    private void drawOffHand(Graphics2D g2, int drawX, int drawY, int drawSize, Direction dir) {
        if (gp == null || gp.equipmentManager == null) return;
        Item offHand = gp.equipmentManager.getEquipped(EquipmentSlot.OFF_HAND);
        if (offHand == null) return;

        double scale = (double) drawSize / 64.0;
        int sx, sy;
        int sw = (int)(15 * scale);
        int sh = (int)(20 * scale);

        if (isBlocking()) {
            sx = drawX + drawSize / 2 - sw / 2;
            sy = drawY + (int)(30 * scale);
        } else {
            // Anchor to left hand position
            switch (dir) {
                case UP    -> { sx = drawX + (int)(15 * scale); sy = drawY + (int)(32 * scale); }
                case DOWN  -> { sx = drawX + (int)(14 * scale); sy = drawY + (int)(34 * scale); }
                case LEFT  -> { sx = drawX + (int)(28 * scale); sy = drawY + (int)(34 * scale); }
                default    -> { sx = drawX + (int)(16 * scale); sy = drawY + (int)(34 * scale); } // RIGHT
            }
        }

        g2.setColor(new Color(160, 180, 205));
        int[] xs = { sx + sw / 2, sx, sx, sx + sw, sx + sw };
        int[] ys = { sy + sh, sy + 3, sy, sy, sy + 3 };
        g2.fillPolygon(xs, ys, 5);

        g2.setColor(new Color(220, 180, 60));
        g2.fillOval(sx + sw / 2 - 3, sy + sh / 2 - 3, 6, 6);

        g2.setColor(new Color(70, 90, 120));
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawPolygon(xs, ys, 5);
        g2.setStroke(new BasicStroke(1f));
    }

    public BufferedImage getPlayerPreviewImage() {
        try {
            if (animations != null && animations[Direction.DOWN.ordinal()][AnimationState.IDLE.ordinal()] != null) {
                return animations[Direction.DOWN.ordinal()][AnimationState.IDLE.ordinal()].getFrame(0).getImage();
            }
        } catch (Exception ignored) {
        }
        return animationController.getCurrentFrame().getImage();
    }

    private String directionName(Direction value) {
        return value.name().toLowerCase();
    }
}
