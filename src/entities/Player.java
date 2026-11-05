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

public class Player extends Entity {
    public static final long IDLE_FRAME = 150_000_000L;
    public static final long WALK_FRAME = 110_000_000L;
    public static final long RUN_FRAME = 75_000_000L;
    public static final long PUNCH_FRAME = 90_000_000L;
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

    /** Screen-space camera anchor, updated by GamePanel's clamped camera. */
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
    /** Level-1 passive regen: accumulates idle nanos, regens 8 HP every 1 s. */
    private long idleRegenAccumNanos = 0;
    private static final long REGEN_INTERVAL_NANOS = 1_000_000_000L;
    private static final int REGEN_AMOUNT = 8;

    public Player(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;
        screenX = gp.screenWidth / 2 - gp.tileSize / 2;
        screenY = gp.screenHeight / 2 - gp.tileSize / 2;
        solidArea = new Rectangle(8, 16, 32, 32);
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
            throw new IllegalStateException("Unable to load player animations.", e);
        }
    }

    private void loadFallbackAnimationsFromWalking() {
        for (Direction direction : Direction.values()) {
            AnimationFrame[] frames = new AnimationFrame[2];
            for (int frame = 0; frame < frames.length; frame++) {
                frames[frame] = animations[direction.ordinal()][AnimationState.WALK.ordinal()]
                        .getFrame(frame);
            }
            for (AnimationState state : AnimationState.values()) {
                if (state == AnimationState.WALK || state == AnimationState.IDLE
                        || state == AnimationState.RUN || state == AnimationState.DODGE
                        || state == AnimationState.PUNCH || state == AnimationState.KICK
                        || state == AnimationState.DIE) {
                    continue;
                }
                boolean looping = false;
                animations[direction.ordinal()][state.ordinal()] =
                        new Animation(frames, looping);
            }
        }
    }

    private void loadDodgeAnimations(BufferedImage dodgeSheet) {
        for (Direction direction : Direction.values()) {
            AnimationFrame[] frames = new AnimationFrame[4];
            for (int frame = 0; frame < 4; frame++) {
                Rectangle bounds = SpriteSheetMapping.dodgeFrameBounds(direction, frame);
                frames[frame] = new AnimationFrame(
                        normalizeFrame(dodgeSheet.getSubimage(
                                bounds.x, bounds.y, bounds.width, bounds.height)),
                        DODGE_FRAME);
            }

            animations[direction.ordinal()][AnimationState.DODGE.ordinal()] =
                    new Animation(frames, false);
        }
    }

    private void loadPunchAndKickAnimations(BufferedImage combatSheet) {
        for (Direction direction : Direction.values()) {
            AnimationFrame[] punches = new AnimationFrame[4];
            AnimationFrame[] kicks = new AnimationFrame[4];
            for (int frame = 0; frame < 4; frame++) {
                Rectangle punchBounds = SpriteSheetMapping.punchFrameBounds(direction, frame);
                Rectangle kickBounds = SpriteSheetMapping.kickFrameBounds(direction, frame);
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

            animations[direction.ordinal()][AnimationState.PUNCH.ordinal()] =
                    new Animation(punches, false);
            animations[direction.ordinal()][AnimationState.KICK.ordinal()] =
                    new Animation(kicks, false);
        }
    }

    private void loadDeathAnimations(BufferedImage deathSheet) {
        for (Direction direction : Direction.values()) {
            AnimationFrame[] frames = new AnimationFrame[4];
            for (int frame = 0; frame < frames.length; frame++) {
                Rectangle bounds = SpriteSheetMapping.deathFrameBounds(direction, frame);
                frames[frame] = new AnimationFrame(
                        normalizeFrame(deathSheet.getSubimage(
                                bounds.x, bounds.y, bounds.width, bounds.height)),
                        DIE_FRAME);
            }
            animations[direction.ordinal()][AnimationState.DIE.ordinal()] =
                    new Animation(frames, false);
        }
    }

    private void loadIdleAnimations(BufferedImage idleSheet) {
        for (Direction direction : Direction.values()) {
            Rectangle bounds = SpriteSheetMapping.idleFrameBounds(direction);
            AnimationFrame[] frames = new AnimationFrame[] {
                new AnimationFrame(
                        normalizeFrame(idleSheet.getSubimage(
                                bounds.x, bounds.y, bounds.width, bounds.height)),
                        IDLE_FRAME)
            };
            animations[direction.ordinal()][AnimationState.IDLE.ordinal()] =
                    new Animation(frames, true);
        }
    }

    private void loadWalkingAnimations(BufferedImage walkingSheet) {
        for (Direction direction : Direction.values()) {
            AnimationFrame[] frames = new AnimationFrame[2];
            for (int frame = 0; frame < frames.length; frame++) {
                Rectangle bounds = SpriteSheetMapping.walkingFrameBounds(direction, frame);
                frames[frame] = new AnimationFrame(
                        normalizeFrame(walkingSheet.getSubimage(
                                bounds.x, bounds.y, bounds.width, bounds.height)),
                        WALK_FRAME);
            }
            animations[direction.ordinal()][AnimationState.WALK.ordinal()] =
                    new Animation(frames, true);
        }
    }

    private void loadRunningAnimations(BufferedImage runningSheet) {
        for (Direction direction : Direction.values()) {
            AnimationFrame[] frames = new AnimationFrame[2];
            for (int frame = 0; frame < frames.length; frame++) {
                Rectangle bounds = SpriteSheetMapping.runningFrameBounds(direction, frame);
                frames[frame] = new AnimationFrame(
                        normalizeFrame(runningSheet.getSubimage(
                                bounds.x, bounds.y, bounds.width, bounds.height)),
                        RUN_FRAME);
            }
            animations[direction.ordinal()][AnimationState.RUN.ordinal()] =
                    new Animation(frames, true);
        }
    }

    private long frameDuration(AnimationState animationState) {
        switch (animationState) {
        case IDLE: return IDLE_FRAME;
        case WALK: return WALK_FRAME;
        case RUN: return RUN_FRAME;
        case PUNCH: return PUNCH_FRAME;
        case KICK: return KICK_FRAME;
        case DODGE: return DODGE_FRAME;
        case ROLL: return ROLL_FRAME;
        case HURT: return HURT_FRAME;
        default: return DIE_FRAME;
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

    public void update() {
        update(16_666_667L);
    }

    public void update(long deltaNanos) {
        long delta = Math.min(deltaNanos, 100_000_000L);
        attackCooldownNanos = Math.max(0, attackCooldownNanos - delta);
        dodgeCooldownNanos = Math.max(0, dodgeCooldownNanos - delta);
        damageInvulnerabilityNanos = Math.max(0, damageInvulnerabilityNanos - delta);
        hurtRemainingNanos = Math.max(0, hurtRemainingNanos - delta);

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

        if (keyH.consumePunch() && attackCooldownNanos == 0) {
            startAction(AnimationState.PUNCH);
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
            speed = keyH.runPressed ? 9 : 6;
            moveWithCollision(speed);
            setState(keyH.runPressed ? AnimationState.RUN : AnimationState.WALK);
            idleRegenAccumNanos = 0; // reset regen on movement
        } else {
            speed = 6;
            setState(AnimationState.IDLE);
            tickIdleRegen(delta); // passive regen while idle in level 1
        }
        updateAnimation(delta);
    }

    /** +8 HP every 1 s while idle in Level 1 and not at max health. */
    private void tickIdleRegen(long delta) {
        if (gp.levelM == null || gp.levelM.currentLevel != 1) return;
        if (health <= 0 || health >= getMaxHealth()) {
            idleRegenAccumNanos = 0;
            return;
        }
        idleRegenAccumNanos += delta;
        while (idleRegenAccumNanos >= REGEN_INTERVAL_NANOS) {
            idleRegenAccumNanos -= REGEN_INTERVAL_NANOS;
            health = Math.min(getMaxHealth(), health + REGEN_AMOUNT);
        }
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
        int reach = state == AnimationState.KICK ? gp.tileSize + 12 : gp.tileSize;
        return Hitbox.inFront(worldX + gp.tileSize / 2, worldY + gp.tileSize / 2,
                actionDirection, reach, gp.tileSize - 4, gp.tileSize - 4);
    }

    public int getAttackDamage() {
        return state == AnimationState.KICK ? 35 : 25;
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

    /** Restores a validated saved health value and clears any transient action. */
    public void restoreHealth(int value) {
        health = Math.max(0, Math.min(getMaxHealth(), value));
        if (health == 0) {
            actionDirection = facing;
            setState(AnimationState.DIE);
        } else {
            resetActionState();
        }
    }

    /** Used after a region change or load so stale combat frames cannot persist. */
    public void resetActionState() {
        attackCooldownNanos = 0;
        dodgeCooldownNanos = 0;
        hurtRemainingNanos = 0;
        dodgeDistanceRemaining = 0;
        rollDistanceRemaining = 0;
        attackHitApplied = false;
        actionDirection = facing;
        state = health <= 0 ? AnimationState.DIE : AnimationState.IDLE;
        actionState = state.name();
        selectAnimation();
    }

    public boolean isDead() {
        return health == 0;
    }

    /** Allows the final death frame to play before GamePanel opens game over. */
    public boolean isDeathAnimationFinished() {
        return state == AnimationState.DIE && animationController.isFinished();
    }

    public void takeDamage(int damage) {
        if (health <= 0 || isDodging() || damageInvulnerabilityNanos > 0) {
            return;
        }
        damageInvulnerabilityNanos = DAMAGE_INVULNERABILITY_NANOS;
        hurtRemainingNanos = HURT_LOCK_NANOS;
        idleRegenAccumNanos = 0; // combat resets idle regen
        health = Math.max(0, health - damage);
        if (health == 0) {
            actionDirection = facing;
            setState(AnimationState.DIE);
        } else {
            actionDirection = facing;
            setState(AnimationState.HURT);
        }
    }

    public void draw(Graphics2D g2) {
        BufferedImage image = animationController.getCurrentFrame().getImage();
        int drawSize = gp.tileSize * 2;
        int drawX = screenX + (gp.tileSize - drawSize) / 2;
        int drawY = screenY + gp.tileSize - drawSize;
        Object oldInterpolation = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(image, drawX, drawY, drawSize, drawSize, null);
        if (oldInterpolation != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }
    }

    private String directionName(Direction value) {
        return value.name().toLowerCase();
    }
}
