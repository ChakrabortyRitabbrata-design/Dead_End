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

    // ── Weapon Renderer & Equipment ───────────────────────────────────────────
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

        // Update weapon animation ticks
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
        Object oldInterpolation = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(image, drawX, drawY, drawSize, drawSize, null);
        if (oldInterpolation != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }

        String dir = directionName(facing);

        // 2. Draw off-hand item (shield)
        drawOffHand(g2, screenX, screenY, dir);

        // 3. Draw armor overlays (helmet, chestplate, leggings, boots)
        drawArmorOverlays(g2, drawX, drawY, drawSize, dir);

        // 4. Draw equipped weapon in hand
        weaponRenderer.draw(g2, screenX, screenY, dir, getEquippedWeapon());
    }

    private void drawArmorOverlays(Graphics2D g2, int drawX, int drawY, int drawSize, String dir) {
        if (gp == null || gp.equipmentManager == null) return;

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
