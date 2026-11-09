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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
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
import main.DamagePopup;

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
    private long blockCooldownNanos = 0;
    private long blockDurationNanos = 0;
    private static final long MAX_BLOCK_HOLD_NANOS = 2_000_000_000L;
    private static final long BLOCK_COOLDOWN_NANOS = 450_000_000L;
    private int blockEffectTimer = 0;
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

    // â”€â”€ Weapon Equipment Helper â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    // â”€â”€ Update â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public void update() {
        update(16_666_667L);
    }

    public void update(long deltaNanos) {
        long delta = Math.min(deltaNanos, 100_000_000L);
        attackCooldownNanos = Math.max(0, attackCooldownNanos - delta);
        dodgeCooldownNanos = Math.max(0, dodgeCooldownNanos - delta);
        damageInvulnerabilityNanos = Math.max(0, damageInvulnerabilityNanos - delta);
        hurtRemainingNanos = Math.max(0, hurtRemainingNanos - delta);
        blockCooldownNanos = Math.max(0, blockCooldownNanos - delta);
        if (blockEffectTimer > 0) blockEffectTimer--;

        // Block duration limit and cooldown logic
        if (keyH.blockPressed && hasShieldEquipped()) {
            blockDurationNanos += delta;
            if (blockDurationNanos >= MAX_BLOCK_HOLD_NANOS) {
                // Drop block due to fatigue, trigger cooldown
                blockDurationNanos = 0;
                blockCooldownNanos = BLOCK_COOLDOWN_NANOS;
            }
        } else {
            if (blockDurationNanos > 0) {
                blockDurationNanos = 0;
                blockCooldownNanos = BLOCK_COOLDOWN_NANOS / 2;
            }
        }
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

    public boolean hasShieldEquipped() {
        return gp != null && gp.equipmentManager != null
                && gp.equipmentManager.getEquipped(EquipmentSlot.OFF_HAND) != null;
    }

    public boolean isBlocking() {
        return keyH.blockPressed && hasShieldEquipped()
                && blockCooldownNanos == 0 && state == AnimationState.IDLE;
    }

    public boolean isFacingTowards(int sourceWorldX, int sourceWorldY) {
        int dx = sourceWorldX - worldX;
        int dy = sourceWorldY - worldY;
        if (Math.abs(dx) > Math.abs(dy)) {
            if (dx > 0 && facing == Direction.RIGHT) return true;
            if (dx < 0 && facing == Direction.LEFT) return true;
        } else {
            if (dy > 0 && facing == Direction.DOWN) return true;
            if (dy < 0 && facing == Direction.UP) return true;
        }
        return false;
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
        takeDamage(damage, -1, -1);
    }

    public void takeDamage(int damage, int sourceWorldX, int sourceWorldY) {
        if (health <= 0 || isDodging() || damageInvulnerabilityNanos > 0) {
            return;
        }

        boolean blocked = false;
        if (isBlocking()) {
            if (sourceWorldX >= 0 && sourceWorldY >= 0) {
                blocked = isFacingTowards(sourceWorldX, sourceWorldY);
            } else {
                blocked = true;
            }
        }

        if (blocked) {
            // Shield blocks 75% of incoming damage
            damage = Math.max(1, damage / 4);
            blockEffectTimer = 14;
            blockCooldownNanos = BLOCK_COOLDOWN_NANOS;
        }

        int defense = (gp != null && gp.equipmentManager != null)
                ? gp.equipmentManager.getTotalDefense() : 0;
        int effectiveDamage = Math.max(1, damage - defense / 2);
        damageInvulnerabilityNanos = DAMAGE_INVULNERABILITY_NANOS;
        hurtRemainingNanos = HURT_LOCK_NANOS;
        health = Math.max(0, health - effectiveDamage);

        // Spawn a red damage popup at player screen center
        DamagePopup.spawn(screenX + gp.tileSize / 2, screenY - 10, effectiveDamage, true);

        if (health == 0) {
            actionDirection = facing;
            setState(AnimationState.DIE);
        } else {
            actionDirection = facing;
            setState(AnimationState.HURT);
        }
    }

    // â”€â”€ Render â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

        // Invulnerability blink: skip drawing every other frame while flashing
        boolean skipDraw = (damageInvulnerabilityNanos > 0)
                && ((damageInvulnerabilityNanos / 60_000_000L) % 2 == 0);
        if (!skipDraw) {
            g2.drawImage(image, drawX, drawY, drawSize, drawSize, null);
        }
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

    public void drawArmorOverlays(Graphics2D g2, int drawX, int drawY, int drawSize, Direction dir) {
        if (gp == null || gp.equipmentManager == null) return;

        double sc = (double) drawSize / 64.0;
        int cx = drawX + (int)(32 * sc);
        if (dir == Direction.LEFT)  cx -= (int)(1 * sc);
        if (dir == Direction.RIGHT) cx += (int)(1 * sc);

        boolean isSide = (dir == Direction.LEFT || dir == Direction.RIGHT);
        boolean isLeft = (dir == Direction.LEFT);
        boolean isBack = (dir == Direction.UP);

        // ── 1. Helmet ────────────────────────────────────────────────────────
        Item head = gp.equipmentManager.getEquipped(EquipmentSlot.HEAD);
        if (head != null) {
            int topY  = drawY + (int)(13 * sc);
            int hw    = isSide ? (int)(19 * sc) : (int)(23 * sc);
            int hh    = (int)(14 * sc);
            int helmX = cx - hw / 2;

            // Main dome
            g2.setColor(new Color(175, 192, 215, 235));
            g2.fillArc(helmX, topY, hw, hh + 2, 0, 180);

            // Metallic light reflection on top
            g2.setColor(new Color(225, 235, 250, 200));
            g2.fillArc(helmX + (int)(3*sc), topY + (int)(1*sc), hw - (int)(6*sc), (int)(5*sc), 20, 140);

            // Brow band / brim
            g2.setColor(new Color(85, 100, 125, 240));
            g2.fillRect(helmX - 1, topY + hh / 2 + (int)(1*sc), hw + 2, (int)(3*sc));

            // Cheek / side plates
            if (!isSide && !isBack) {
                g2.setColor(new Color(150, 170, 195, 220));
                int cgW = (int)(4 * sc);
                int cgH = (int)(6 * sc);
                g2.fillRoundRect(helmX, topY + hh / 2 + (int)(3*sc), cgW, cgH, 2, 2);
                g2.fillRoundRect(helmX + hw - cgW, topY + hh / 2 + (int)(3*sc), cgW, cgH, 2, 2);
            }

            // Visor slit
            if (!isBack) {
                g2.setColor(new Color(12, 16, 26, 245));
                int sY = topY + hh / 2 + (int)(1*sc);
                int sW = isSide ? (int)(7 * sc) : (int)(13 * sc);
                g2.fillRect(cx - sW / 2, sY, sW, (int)(2*sc));
            }

            // Outline
            g2.setColor(new Color(60, 75, 95, 230));
            g2.setStroke(new BasicStroke(Math.max(1f, (float)(1.2*sc))));
            g2.drawArc(helmX, topY, hw, hh + 2, 0, 180);
            g2.setStroke(new BasicStroke(1f));
        }

        // ── 2. Chestplate ────────────────────────────────────────────────────
        Item chest = gp.equipmentManager.getEquipped(EquipmentSlot.CHEST);
        if (chest != null) {
            int cy2 = drawY + (int)(29 * sc);
            int cw  = isSide ? (int)(13 * sc) : (int)(19 * sc);
            int ch  = (int)(11 * sc);
            int chestX = cx - cw / 2;

            // Plate body (fitted cuirass over red tunic)
            g2.setColor(new Color(170, 188, 212, 235));
            g2.fillRoundRect(chestX, cy2, cw, ch, (int)(3*sc), (int)(3*sc));

            // Highlights
            g2.setColor(new Color(215, 230, 248, 170));
            g2.fillRoundRect(chestX + (int)(2*sc), cy2 + (int)(1*sc), cw / 2 - (int)(1*sc), ch - (int)(3*sc), 2, 2);

            // Center ridge
            if (!isSide) {
                g2.setColor(new Color(95, 115, 140, 220));
                g2.fillRect(cx - (int)(0.5*sc), cy2 + (int)(1*sc), (int)(1.5*sc), ch - (int)(2*sc));

                // Shoulder pauldrons
                int pw = (int)(6 * sc);
                int ph = (int)(5 * sc);
                g2.setColor(new Color(155, 175, 200, 230));
                g2.fillRoundRect(chestX - pw + (int)(2*sc), cy2 - (int)(1*sc), pw, ph, 2, 2);
                g2.fillRoundRect(chestX + cw - (int)(2*sc), cy2 - (int)(1*sc), pw, ph, 2, 2);
            }

            // Outline
            g2.setColor(new Color(65, 80, 105, 220));
            g2.setStroke(new BasicStroke(Math.max(1f, (float)(1.1*sc))));
            g2.drawRoundRect(chestX, cy2, cw, ch, (int)(3*sc), (int)(3*sc));
            g2.setStroke(new BasicStroke(1f));
        }

        // ── 3. Leggings ──────────────────────────────────────────────────────
        Item legs = gp.equipmentManager.getEquipped(EquipmentSlot.LEGS);
        if (legs != null) {
            int ly  = drawY + (int)(42 * sc);
            int lw  = (int)(7 * sc);
            int lh  = (int)(9 * sc);
            int gap = (int)(4 * sc);

            if (isSide) {
                int lx = cx - lw / 2;
                g2.setColor(new Color(145, 165, 190, 230));
                g2.fillRoundRect(lx, ly, lw, lh, 2, 2);
                g2.setColor(new Color(195, 212, 232, 160));
                g2.fillRect(lx + (int)(1*sc), ly + (int)(1*sc), (int)(2*sc), lh - (int)(2*sc));
            } else {
                int leftLegX  = cx - lw - gap / 2;
                int rightLegX = cx + gap / 2;
                g2.setColor(new Color(145, 165, 190, 230));
                g2.fillRoundRect(leftLegX, ly, lw, lh, 2, 2);
                g2.fillRoundRect(rightLegX, ly, lw, lh, 2, 2);

                g2.setColor(new Color(195, 212, 232, 160));
                g2.fillRect(leftLegX + (int)(1*sc), ly + (int)(1*sc), (int)(2*sc), lh - (int)(2*sc));
                g2.fillRect(rightLegX + (int)(1*sc), ly + (int)(1*sc), (int)(2*sc), lh - (int)(2*sc));
            }
        }

        // ── 4. Boots ─────────────────────────────────────────────────────────
        Item feet = gp.equipmentManager.getEquipped(EquipmentSlot.FEET);
        if (feet != null) {
            int by   = drawY + (int)(52 * sc);
            int bw   = (int)(8 * sc);
            int bh   = (int)(8 * sc);
            int bgap = (int)(4 * sc);

            if (isSide) {
                int bx = cx - bw / 2;
                g2.setColor(new Color(80, 95, 118, 240));
                g2.fillRoundRect(bx, by, bw, bh, 2, 2);
                // Metal toe plate
                g2.setColor(new Color(185, 202, 222, 230));
                g2.fillRect(bx + (int)(1*sc), by + bh - (int)(3*sc), bw - (int)(2*sc), (int)(2*sc));
            } else {
                int leftBootX  = cx - bw - bgap / 2;
                int rightBootX = cx + bgap / 2;
                g2.setColor(new Color(80, 95, 118, 240));
                g2.fillRoundRect(leftBootX, by, bw, bh, 2, 2);
                g2.fillRoundRect(rightBootX, by, bw, bh, 2, 2);

                // Metal toe plates
                g2.setColor(new Color(185, 202, 222, 230));
                g2.fillRect(leftBootX + (int)(1*sc), by + bh - (int)(3*sc), bw - (int)(2*sc), (int)(2*sc));
                g2.fillRect(rightBootX + (int)(1*sc), by + bh - (int)(3*sc), bw - (int)(2*sc), (int)(2*sc));
            }
        }
    }

    public void drawOffHand(Graphics2D g2, int drawX, int drawY, int drawSize, Direction dir) {
        if (gp == null || gp.equipmentManager == null) return;
        Item offHand = gp.equipmentManager.getEquipped(EquipmentSlot.OFF_HAND);
        if (offHand == null) return;

        double sc = (double) drawSize / 64.0;
        int sw = (int)(16 * sc);
        int sh = (int)(20 * sc);
        int sx, sy;

        if (isBlocking()) {
            // Shield thrust forward in front of body
            sx = drawX + drawSize / 2 - sw / 2;
            sy = drawY + (int)(28 * sc);
        } else {
            // Resting position: anchor to off-hand side
            switch (dir) {
                case UP    -> { sx = drawX + (int)(14 * sc); sy = drawY + (int)(30 * sc); }
                case DOWN  -> { sx = drawX + (int)(13 * sc); sy = drawY + (int)(32 * sc); }
                case LEFT  -> { sx = drawX + (int)(26 * sc); sy = drawY + (int)(32 * sc); }
                default    -> { sx = drawX + (int)(15 * sc); sy = drawY + (int)(32 * sc); }
            }
        }

        // ── Draw shield: round wooden body + metal rim + gold boss ────────────
        int rx = sw / 2;  // radius x
        int ry = sh / 2;  // radius y

        // Wooden body (brown)
        g2.setColor(new Color(130, 90, 50, 230));
        g2.fillOval(sx, sy, sw, sh);

        // Wood grain lines
        g2.setColor(new Color(100, 68, 35, 180));
        g2.setStroke(new BasicStroke(Math.max(1f, (float)(0.8*sc))));
        for (int grain = 1; grain < 3; grain++) {
            int gx = sx + sw/4 * grain;
            g2.drawLine(gx, sy + (int)(2*sc), gx, sy + sh - (int)(2*sc));
        }

        // Metal rim
        g2.setColor(new Color(160, 175, 195, 240));
        g2.setStroke(new BasicStroke(Math.max(1.5f, (float)(2*sc))));
        g2.drawOval(sx, sy, sw, sh);

        // Metal cross straps
        g2.setColor(new Color(140, 160, 185, 200));
        g2.setStroke(new BasicStroke(Math.max(1f, (float)(1.5*sc))));
        g2.drawLine(sx + sw/2, sy + (int)(2*sc), sx + sw/2, sy + sh - (int)(2*sc));
        g2.drawLine(sx + (int)(2*sc), sy + sh/2, sx + sw - (int)(2*sc), sy + sh/2);

        // Gold center boss
        int bossSize = (int)(6 * sc);
        g2.setColor(new Color(220, 185, 50, 255));
        g2.fillOval(sx + sw/2 - bossSize/2, sy + sh/2 - bossSize/2, bossSize, bossSize);
        // Boss highlight
        g2.setColor(new Color(255, 230, 120, 200));
        g2.fillOval(sx + sw/2 - bossSize/2 + (int)(1*sc),
                    sy + sh/2 - bossSize/2 + (int)(1*sc),
                    (int)(2*sc), (int)(2*sc));
        // Boss rim
        g2.setColor(new Color(160, 125, 20, 240));
        g2.setStroke(new BasicStroke(Math.max(1f, (float)(0.8*sc))));
        g2.drawOval(sx + sw/2 - bossSize/2, sy + sh/2 - bossSize/2, bossSize, bossSize);

        // Blocking flash: bright blue-white rim when blocking
        if (isBlocking()) {
            g2.setColor(new Color(180, 220, 255, 200));
            g2.setStroke(new BasicStroke(Math.max(2f, (float)(3*sc))));
            g2.drawOval(sx - (int)(2*sc), sy - (int)(2*sc), sw + (int)(4*sc), sh + (int)(4*sc));
        }
        g2.setStroke(new BasicStroke(1f));
    }

    public BufferedImage getPlayerPreviewImage() {
        return getDirectionalIdleFrame(Direction.DOWN);
    }

    public BufferedImage getDirectionalIdleFrame(Direction dir) {
        try {
            if (animations != null && animations[dir.ordinal()][AnimationState.IDLE.ordinal()] != null) {
                return animations[dir.ordinal()][AnimationState.IDLE.ordinal()].getFrame(0).getImage();
            }
        } catch (Exception ignored) {
        }
        return animationController.getCurrentFrame().getImage();
    }

    private String directionName(Direction value) {
        return value.name().toLowerCase();
    }
}
