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

    public int screenX;
    public int screenY;
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

    public boolean isDead() {
        return health <= 0;
    }

    public boolean isDeathAnimationFinished() {
        return state == AnimationState.DIE && animationController.isFinished();
    }

    public void restoreHealth(int amount) {
        health = Math.max(0, Math.min(getMaxHealth(), amount));
    }

    public void resetActionState() {
        attackCooldownNanos = 0;
        dodgeCooldownNanos = 0;
        damageInvulnerabilityNanos = 0;
        hurtRemainingNanos = 0;
        dodgeDistanceRemaining = 0;
        rollDistanceRemaining = 0;
        attackHitApplied = false;
        actionDirection = facing;
        setState(health <= 0 ? AnimationState.DIE : AnimationState.IDLE);
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
            drawOffHand(g2, drawX, drawY, drawSize, state, facing, frameIdx);
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

        // 4. Draw armor overlays with dynamic per-frame and attack alignment
        drawArmorOverlays(g2, drawX, drawY, drawSize, state, facing, frameIdx);

        // 5. Draw off-hand item in front if facing DOWN, LEFT, UP
        if (facing != Direction.RIGHT) {
            drawOffHand(g2, drawX, drawY, drawSize, state, facing, frameIdx);
        }

        // 6. Draw front-layer weapon in hand
        if (equipped != null && weaponRenderer != null) {
            weaponRenderer.drawHandAnchored(g2, drawX, drawY, drawSize, state, facing, frameIdx, equipped, "FRONT");
        }
    }

    public void drawArmorOverlays(Graphics2D g2, int drawX, int drawY, int drawSize, Direction dir) {
        drawArmorOverlays(g2, drawX, drawY, drawSize, AnimationState.IDLE, dir, 0);
    }

    public void drawArmorOverlays(Graphics2D g2, int drawX, int drawY, int drawSize, AnimationState state, Direction dir, int frameIdx) {
        if (gp == null || gp.equipmentManager == null) return;

        double sc = (double) drawSize / 64.0;
        ArmorAttachment.ArmorFrameData data = ArmorAttachment.getOffsets(state, dir, frameIdx);

        // 1. Leggings (layered under chestplate and boots)
        Item legs = gp.equipmentManager.getEquipped(EquipmentSlot.LEGS);
        if (legs != null) {
            BufferedImage legSprite = EquipmentSpriteSheet.getSprite(EquipmentSlot.LEGS, dir);
            if (legSprite != null) {
                int lw = (int)(24 * sc);
                int lh = (int)(14 * sc);
                int lx = drawX + (int)(data.legsX * sc) - lw / 2;
                int ly = drawY + (int)(data.legsY * sc);
                g2.drawImage(legSprite, lx, ly, lw, lh, null);
            }
        }

        // 2. Boots — drawn before chestplate, covers original orange boots
        Item feet = gp.equipmentManager.getEquipped(EquipmentSlot.FEET);
        if (feet != null) {
            BufferedImage bootSprite = EquipmentSpriteSheet.getSprite(EquipmentSlot.FEET, dir);
            if (bootSprite != null) {
                int bw = (int)(26 * sc);
                int bh = (int)(16 * sc);
                int bx = drawX + (int)(data.bootsX * sc) - bw / 2;
                int by = drawY + (int)(data.bootsY * sc);
                g2.drawImage(bootSprite, bx, by, bw, bh, null);
            }
        }

        // 3. Chestplate — sits snugly over torso and shoulders under scarf
        Item chest = gp.equipmentManager.getEquipped(EquipmentSlot.CHEST);
        if (chest != null) {
            BufferedImage chestSprite = EquipmentSpriteSheet.getSprite(EquipmentSlot.CHEST, dir);
            if (chestSprite != null) {
                int cw = (int)(28 * sc);
                int ch = (int)(18 * sc);
                int cx = drawX + (int)(data.chestX * sc) - cw / 2;
                int cy = drawY + (int)(data.chestY * sc);
                g2.drawImage(chestSprite, cx, cy, cw, ch, null);
            }
        }

        // 4. Helmet — full coverage closed knight helm covering forehead, eyes, cheeks, and chin
        Item head = gp.equipmentManager.getEquipped(EquipmentSlot.HEAD);
        if (head != null) {
            BufferedImage helmSprite = EquipmentSpriteSheet.getSprite(EquipmentSlot.HEAD, dir);
            if (helmSprite != null) {
                int hw = (int)(34 * sc);
                int hh = (int)(34 * sc);
                int hx = drawX + (int)(data.helmX * sc) - hw / 2;
                int hy = drawY + (int)(data.helmY * sc);
                g2.drawImage(helmSprite, hx, hy, hw, hh, null);
            }
        }
    }

    public void drawOffHand(Graphics2D g2, int drawX, int drawY, int drawSize, Direction dir) {
        drawOffHand(g2, drawX, drawY, drawSize, AnimationState.IDLE, dir, 0);
    }

    public void drawOffHand(Graphics2D g2, int drawX, int drawY, int drawSize, AnimationState state, Direction dir, int frameIdx) {
        if (gp == null || gp.equipmentManager == null) return;
        Item offHand = gp.equipmentManager.getEquipped(EquipmentSlot.OFF_HAND);
        if (offHand == null) return;

        double sc = (double) drawSize / 64.0;
        int sw = (int)(20 * sc);
        int sh = (int)(26 * sc);
        int sx, sy;

        if (isBlocking()) {
            // Thrust forward in blocking stance
            sx = drawX + drawSize / 2 - sw / 2;
            sy = drawY + (int)(25 * sc);
        } else {
            ArmorAttachment.ArmorFrameData data = ArmorAttachment.getOffsets(state, dir, frameIdx);
            sx = drawX + (int)(data.shieldX * sc) - sw / 2;
            sy = drawY + (int)(data.shieldY * sc);
        }

        BufferedImage shieldSprite = EquipmentSpriteSheet.getSprite(EquipmentSlot.OFF_HAND, dir);
        if (shieldSprite != null) {
            g2.drawImage(shieldSprite, sx, sy, sw, sh, null);
        }

        // Blocking flash ring effect
        if (isBlocking() || blockEffectTimer > 0) {
            g2.setColor(new Color(180, 220, 255, 180));
            g2.setStroke(new BasicStroke(Math.max(2f, (float)(2.5 * sc))));
            g2.drawOval(sx - (int)(2*sc), sy - (int)(2*sc), sw + (int)(4*sc), sh + (int)(4*sc));
            g2.setStroke(new BasicStroke(1f));
        }
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
