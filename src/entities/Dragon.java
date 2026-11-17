package entities;

import animation.Direction;
import main.DamagePopup;
import main.GamePanel;
import tile.MapData;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

/** Animated water dragon boss for the third region. World coordinates are its center. */
public class Dragon {
    public enum State {
        IDLE, SWIMMING, FOLLOWING, PREPARING_FIRE, FIREBALL,
        CHARGING_PULSE, ENERGY_PULSE, HURT, STUNNED, SUBMERGING, DEATH
    }

    private static final int MAX_HEALTH = 300;
    private static final int FIREBALL_DAMAGE = 14;
    private static final int PULSE_DAMAGE = 22;
    private static final int FIREBALL_SPEED = 260;
    private static final double DETECTION_RANGE_TILES = 64.0;
    private static final double KEEP_DISTANCE_TILES = 2.2;
    private static final double PULSE_TRIGGER_TILES = 3.8;
    private static final double FIREBALL_TRIGGER_TILES = 9.0;
    private static final double MAX_PULSE_RADIUS_TILES = 3.2;
    private static final long FIREBALL_COOLDOWN_NANOS = 1_900_000_000L;
    private static final long PULSE_COOLDOWN_NANOS = 5_200_000_000L;
    private static final long FIRE_PREPARATION_NANOS = 760_000_000L;
    private static final long PULSE_CHARGE_NANOS = 1_050_000_000L;
    private static final long SUBMERGE_NANOS = 420_000_000L;
    private static final long DEATH_ANIMATION_NANOS = 1_900_000_000L;
    private static final long MAX_UPDATE_NANOS = 100_000_000L;
    private static final int SPRITE_SIZE_TILES = 3;
    private static final int SPRITE_SHEET_ROWS = 4;
    private static final int SPRITE_SHEET_COLUMNS = 3;
    private static final int SPRITE_FRAME_SIZE = 192;
    private static final int MAX_PARTICLES = 48;

    private final GamePanel gp;
    private final MapData levelMap;
    private final Random random = new Random(0xD12A60L);
    private final BufferedImage[][] frames =
            new BufferedImage[SPRITE_SHEET_ROWS][SPRITE_SHEET_COLUMNS];
    private long[] frameDurationsNanos;
    private final boolean[][] waterBody;
    private final List<int[]> waterCells = new ArrayList<>();
    private final List<Fireball> fireballs = new ArrayList<>();
    private final List<Explosion> explosions = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final int spriteSize;
    private List<List<int[]>> waterRegions = new ArrayList<>();
    private int[][] waterRegionIds;
    private int activeWaterRegion = -1;
    private int pendingWaterRegion = -1;

    public int worldX;
    public int worldY;
    public boolean isDead;
    public int health = MAX_HEALTH;

    private State state = State.IDLE;
    private double velocityX;
    private double velocityY;
    private double heading = Math.PI / 2.0;
    private double hoverPhase;
    private double animationNanos;
    private double stateNanos;
    private double fireballCooldownNanos;
    private double pulseCooldownNanos;
    private double wanderNanos;
    private int currentFrame;
    private int lastPlayerAttackSequence = -1;
    private boolean active;
    private boolean lastAttackWasPulse;
    private double pulseRadius;
    private long idleRippleNanos;
    private int hurtFlash;
    private double wanderTargetX;
    private double wanderTargetY;
    private boolean hasWanderTarget;
    private long navigationCooldownNanos;
    private int navigationTargetCol = -1;
    private int navigationTargetRow = -1;
    private double navigationWaypointX;
    private double navigationWaypointY;

    public Dragon(GamePanel gp) {
        this(gp, gp.tileM.currentMap);
    }

    public Dragon(GamePanel gp, MapData levelMap) {
        this.gp = gp;
        this.levelMap = levelMap;
        if (levelMap == null || levelMap.levelId != 3) {
            throw new IllegalArgumentException("The water dragon requires region 3 map data.");
        }
        this.spriteSize = gp.tileSize * SPRITE_SIZE_TILES;
        this.waterBody = new boolean[levelMap.mapWidth][levelMap.mapHeight];
        loadDragonFrames();
    }

    /**
     * Activates on the nearest connected deep-water body to the map's authored boss marker.
     * All later movement, projectiles, and area effects are clipped to that same body.
     */
    public void activate(int spawnCol, int spawnRow) {
        activate(spawnCol, spawnRow, -1);
    }

    public void activate(int spawnCol, int spawnRow, int excludedWaterRegion) {
        if (levelMap.levelId != 3) {
            throw new IllegalStateException("The water dragon can only be activated in region 3.");
        }
        buildWaterRegions();
        int[] seed = nearestDeepWaterCell(spawnCol, spawnRow, excludedWaterRegion);
        if (seed == null) {
            throw new IllegalStateException("Region 3 boss marker is not near a deep-water body.");
        }
        activeWaterRegion = waterRegionIds[seed[0]][seed[1]];
        selectWaterRegion(activeWaterRegion);

        int[] spawn = findSafeSpawn(spawnCol, spawnRow);
        if (spawn == null) {
            throw new IllegalStateException("Region 3 water body has no safe dragon spawn area.");
        }
        worldX = spawn[0];
        worldY = spawn[1];
        health = MAX_HEALTH;
        isDead = false;
        active = true;
        state = State.IDLE;
        stateNanos = 0;
        animationNanos = 0;
        fireballCooldownNanos = 0;
        pulseCooldownNanos = 0;
        velocityX = 0;
        velocityY = 0;
        hoverPhase = 0;
        lastPlayerAttackSequence = -1;
        fireballs.clear();
        explosions.clear();
        particles.clear();
        hasWanderTarget = false;
        navigationCooldownNanos = 0;
        navigationTargetCol = -1;
        navigationTargetRow = -1;
        lastAttackWasPulse = false;
        pendingWaterRegion = -1;
    }

    public void update() {
        update(16_666_667L);
    }

    public void update(long deltaNanos) {
        if (!active || isDead) {
            return;
        }
        long delta = Math.max(0, Math.min(deltaNanos, MAX_UPDATE_NANOS));
        double seconds = delta / 1_000_000_000.0;
        animationNanos += delta;
        stateNanos += delta;
        hoverPhase += seconds * 2.4;
        fireballCooldownNanos = Math.max(0, fireballCooldownNanos - delta);
        pulseCooldownNanos = Math.max(0, pulseCooldownNanos - delta);
        wanderNanos -= delta;
        navigationCooldownNanos = Math.max(0, navigationCooldownNanos - delta);
        idleRippleNanos += delta;
        if (hurtFlash > 0) {
            hurtFlash--;
        }

        updateAnimationFrame();
        updateParticles(seconds);
        updateProjectiles(seconds);
        updateExplosions(delta);
        applyPlayerAttack();

        if (health <= 0) {
            updateDeath();
            return;
        }
        if (state == State.HURT || state == State.STUNNED) {
            if (stateNanos >= 420_000_000L) {
                state = State.IDLE;
                stateNanos = 0;
            } else {
                dampVelocity(seconds);
                moveWithinWater(seconds);
                return;
            }
        }
        if (state == State.PREPARING_FIRE) {
            if (!isPlayerTargetable()) {
                state = State.IDLE;
                stateNanos = 0;
                return;
            }
            turnTowardPlayer(seconds);
            if (stateNanos >= FIRE_PREPARATION_NANOS) {
                launchFireball();
                state = State.FIREBALL;
                stateNanos = 0;
            }
            return;
        }
        if (state == State.CHARGING_PULSE) {
            if (!isPlayerTargetable()) {
                state = State.IDLE;
                stateNanos = 0;
                return;
            }
            if (stateNanos >= PULSE_CHARGE_NANOS) {
                releasePulse();
            }
            return;
        }
        if (state == State.ENERGY_PULSE || state == State.FIREBALL) {
            if (stateNanos >= 500_000_000L) {
                state = State.IDLE;
                stateNanos = 0;
            }
            if (state == State.ENERGY_PULSE) {
                return;
            }
        }

        if (state == State.SUBMERGING) {
            if (stateNanos >= SUBMERGE_NANOS) {
                finishWaterTravel();
            }
            return;
        }
        int playerWaterRegion = getPlayerWaterRegion();
        if (playerWaterRegion >= 0 && playerWaterRegion != activeWaterRegion) {
            pendingWaterRegion = playerWaterRegion;
            state = State.SUBMERGING;
            stateNanos = 0;
            velocityX = 0;
            velocityY = 0;
            fireballs.clear();
            return;
        }

        boolean playerInBody = isPlayerTargetable();
        double distance = playerInBody ? distanceToPlayer() : Double.POSITIVE_INFINITY;
        if (playerInBody && distance <= gp.tileSize * DETECTION_RANGE_TILES) {
            turnTowardPlayer(seconds);
            if (tryStartAttack(distance)) {
                return;
            }
            if (distance > gp.tileSize * KEEP_DISTANCE_TILES) {
                double playerX = gp.player.worldX + gp.tileSize / 2.0;
                double playerY = gp.player.worldY + gp.tileSize / 2.0;
                double[] waypoint = findChaseWaypoint(playerX, playerY);
                steerToward(waypoint[0], waypoint[1], seconds, 110);
                state = State.FOLLOWING;
            } else {
                orbitPlayer(seconds);
                state = State.IDLE;
            }
        } else {
            swimPattern(seconds);
            state = Math.hypot(velocityX, velocityY) > 8 ? State.SWIMMING : State.IDLE;
        }
        moveWithinWater(seconds);
    }

    public void takeDamage(int damage) {
        if (!active || isDead || state == State.SUBMERGING || health <= 0 || damage <= 0) {
            return;
        }
        health = Math.max(0, health - damage);
        hurtFlash = 9;
        int screenX = worldX - gp.player.worldX + gp.player.screenX;
        int screenY = worldY - gp.player.worldY + gp.player.screenY - spriteSize / 2;
        DamagePopup.spawn(screenX, screenY, damage, false);
        if (health > 0) {
            state = damage >= 20 ? State.STUNNED : State.HURT;
            stateNanos = 0;
            velocityX *= -0.22;
            velocityY *= -0.22;
            fireballs.clear();
            emitParticles(worldX, worldY, new Color(255, 91, 110), 9, 55);
        } else {
            state = State.DEATH;
            stateNanos = 0;
            velocityX = 0;
            velocityY = 0;
            fireballs.clear();
            explosions.clear();
            particles.clear();
        }
    }

    public int getHealth() {
        return health;
    }

    public State getState() {
        return state;
    }

    public int getWaterBodySize() {
        return waterCells.size();
    }

    public int getWaterRegionId() {
        return activeWaterRegion;
    }

    public int getAnimationFrameCount() {
        return SPRITE_SHEET_COLUMNS;
    }

    public int getCurrentAnimationFrame() {
        return currentFrame;
    }

    public int getAnimationDirectionRow() {
        return animationDirectionRow();
    }

    public boolean isPlayerInWaterBody() {
        return getPlayerWaterRegion() == activeWaterRegion;
    }

    private boolean isPlayerTargetable() {
        return isPlayerInWaterBody() || isPlayerOnLand();
    }

    public boolean restoreWaterRegion(int regionId) {
        if (regionId < 0 || regionId >= waterRegions.size()) {
            return false;
        }
        int previousRegion = activeWaterRegion;
        selectWaterRegion(regionId);
        if (findSafeSpawn(worldX / gp.tileSize, worldY / gp.tileSize, true) == null) {
            selectWaterRegion(previousRegion);
            return false;
        }
        return true;
    }

    public boolean isEntirelyInWater() {
        return active && isWaterOnlyPosition(worldX, worldY);
    }

    public boolean isOnValidTerrain() {
        return active && isSafeDragonPosition(worldX, worldY);
    }

    public void restoreDefeated() {
        health = 0;
        isDead = true;
        fireballs.clear();
        explosions.clear();
        particles.clear();
    }

    public void draw(Graphics2D g2) {
        if (!active || isDead || state == State.SUBMERGING) {
            return;
        }
        drawWaterEffects(g2);
        drawExplosions(g2);
        drawFireballs(g2);
        drawDragon(g2);
    }

    private void loadDragonFrames() {
        try {
            Path spritePath = findResource("Dragon sprice game.jpeg");
            BufferedImage sheet = ImageIO.read(spritePath.toFile());
            if (sheet == null || sheet.getWidth() < SPRITE_FRAME_SIZE * SPRITE_SHEET_COLUMNS
                    || sheet.getHeight() != SPRITE_FRAME_SIZE * SPRITE_SHEET_ROWS) {
                throw new IOException("Dragon sprite sheet must contain four 192-pixel rows.");
            }
            BufferedImage transparentSheet = removeWhiteBackground(sheet);
            int frameStartX = (sheet.getWidth() - SPRITE_FRAME_SIZE * SPRITE_SHEET_COLUMNS) / 2;
            for (int row = 0; row < SPRITE_SHEET_ROWS; row++) {
                for (int column = 0; column < SPRITE_SHEET_COLUMNS; column++) {
                    frames[row][column] = normalizeFrame(transparentSheet.getSubimage(
                            frameStartX + column * SPRITE_FRAME_SIZE,
                            row * SPRITE_FRAME_SIZE,
                            SPRITE_FRAME_SIZE, SPRITE_FRAME_SIZE));
                }
            }
            frameDurationsNanos = new long[SPRITE_SHEET_COLUMNS];
            java.util.Arrays.fill(frameDurationsNanos, 180_000_000L);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Unable to load the four-direction dragon sprite sheet.", e);
        }
    }

    private BufferedImage removeWhiteBackground(BufferedImage source) {
        BufferedImage result = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();

        int width = result.getWidth();
        int height = result.getHeight();
        int[] pixels = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();
        boolean[] visited = new boolean[pixels.length];
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            enqueueBackgroundPixel(x, width, height, pixels, visited, pending);
            enqueueBackgroundPixel((height - 1) * width + x,
                    width, height, pixels, visited, pending);
        }
        for (int y = 0; y < height; y++) {
            enqueueBackgroundPixel(y * width, width, height, pixels, visited, pending);
            enqueueBackgroundPixel(y * width + width - 1,
                    width, height, pixels, visited, pending);
        }
        while (!pending.isEmpty()) {
            int index = pending.removeFirst();
            pixels[index] = 0;
            int x = index % width;
            int y = index / width;
            if (x > 0) enqueueBackgroundPixel(index - 1, width, height, pixels, visited, pending);
            if (x + 1 < width) enqueueBackgroundPixel(index + 1, width, height, pixels, visited, pending);
            if (y > 0) enqueueBackgroundPixel(index - width, width, height, pixels, visited, pending);
            if (y + 1 < height) enqueueBackgroundPixel(index + width, width, height, pixels, visited, pending);
        }
        return result;
    }

    private void enqueueBackgroundPixel(int index, int width, int height, int[] pixels,
            boolean[] visited, ArrayDeque<Integer> pending) {
        if (index < 0 || index >= width * height || visited[index]) {
            return;
        }
        int color = pixels[index];
        int red = (color >>> 16) & 0xff;
        int green = (color >>> 8) & 0xff;
        int blue = color & 0xff;
        int minimum = Math.min(red, Math.min(green, blue));
        int maximum = Math.max(red, Math.max(green, blue));
        if (minimum < 218 || maximum - minimum > 24) {
            return;
        }
        visited[index] = true;
        pending.addLast(index);
    }

    private Path findResource(String resourcePath) throws IOException {
        Path[] candidates = {
            Paths.get("res", resourcePath),
            Paths.get("bin", resourcePath),
            Paths.get("Dead_End", "res", resourcePath),
            Paths.get("Dead_End", "bin", resourcePath),
            Paths.get(resourcePath)
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IOException("Resource not found: " + resourcePath);
    }

    private BufferedImage normalizeFrame(BufferedImage source) {
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getRGB(x, y) >>> 24) > 20) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            throw new IllegalStateException("The water dragon sprite sheet contains an empty frame.");
        }
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        BufferedImage normalized = new BufferedImage(160, 160, BufferedImage.TYPE_INT_ARGB);
        double scale = Math.min(148.0 / width, 148.0 / height);
        int drawWidth = Math.max(1, (int) Math.round(width * scale));
        int drawHeight = Math.max(1, (int) Math.round(height * scale));
        Graphics2D g = normalized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(source.getSubimage(minX, minY, width, height),
                (160 - drawWidth) / 2, (160 - drawHeight) / 2,
                drawWidth, drawHeight, null);
        g.dispose();
        return normalized;
    }

    private int[] nearestDeepWaterCell(int col, int row, int excludedWaterRegion) {
        MapData map = levelMap;
        int[] closest = null;
        int closestDistance = Integer.MAX_VALUE;
        for (int x = 0; x < map.mapWidth; x++) {
            for (int y = 0; y < map.mapHeight; y++) {
                if (!isDeepWaterTile(x, y)
                        || waterRegionIds[x][y] == excludedWaterRegion) {
                    continue;
                }
                int dx = x - col;
                int dy = y - row;
                int distance = dx * dx + dy * dy;
                if (distance < closestDistance) {
                    closest = new int[] {x, y};
                    closestDistance = distance;
                }
            }
        }
        return closest;
    }

    private void buildWaterRegions() {
        waterRegions = new ArrayList<>();
        waterRegionIds = new int[levelMap.mapWidth][levelMap.mapHeight];
        for (int col = 0; col < levelMap.mapWidth; col++) {
            java.util.Arrays.fill(waterRegionIds[col], -1);
        }
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int col = 0; col < levelMap.mapWidth; col++) {
            for (int row = 0; row < levelMap.mapHeight; row++) {
                if (!isDeepWaterTile(col, row) || waterRegionIds[col][row] >= 0) {
                    continue;
                }
                int id = waterRegions.size();
                List<int[]> cells = new ArrayList<>();
                ArrayDeque<int[]> pending = new ArrayDeque<>();
                waterRegionIds[col][row] = id;
                pending.add(new int[] {col, row});
                while (!pending.isEmpty()) {
                    int[] cell = pending.removeFirst();
                    cells.add(cell);
                    for (int[] offset : offsets) {
                        int x = cell[0] + offset[0];
                        int y = cell[1] + offset[1];
                        if (x < 0 || y < 0 || x >= levelMap.mapWidth || y >= levelMap.mapHeight
                                || waterRegionIds[x][y] >= 0 || !isDeepWaterTile(x, y)) {
                            continue;
                        }
                        waterRegionIds[x][y] = id;
                        pending.addLast(new int[] {x, y});
                    }
                }
                waterRegions.add(cells);
            }
        }
    }

    private void selectWaterRegion(int regionId) {
        activeWaterRegion = regionId;
        waterCells.clear();
        for (int col = 0; col < waterBody.length; col++) {
            java.util.Arrays.fill(waterBody[col], false);
        }
        waterCells.addAll(waterRegions.get(regionId));
        for (int[] cell : waterCells) {
            waterBody[cell[0]][cell[1]] = true;
        }
    }

    private void finishWaterTravel() {
        if (getPlayerWaterRegion() != pendingWaterRegion) {
            pendingWaterRegion = -1;
            state = State.IDLE;
            stateNanos = 0;
            return;
        }
        int previousRegion = activeWaterRegion;
        selectWaterRegion(pendingWaterRegion);
        int[] spawn = findSafeSpawn(
                gp.player.worldX / gp.tileSize, gp.player.worldY / gp.tileSize, true);
        pendingWaterRegion = -1;
        stateNanos = 0;
        if (spawn == null) {
            selectWaterRegion(previousRegion);
            state = State.IDLE;
            return;
        }
        worldX = spawn[0];
        worldY = spawn[1];
        hasWanderTarget = false;
        state = State.IDLE;
    }

    private int[] findSafeSpawn(int spawnCol, int spawnRow) {
        return findSafeSpawn(spawnCol, spawnRow, false);
    }

    private int[] findSafeSpawn(int spawnCol, int spawnRow, boolean prioritizeDistance) {
        int[] best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestClearance = -1;
        for (int[] cell : waterCells) {
            int x = cell[0] * gp.tileSize + gp.tileSize / 2;
            int y = cell[1] * gp.tileSize + gp.tileSize / 2;
            if (!isWaterOnlyPosition(x, y)) {
                continue;
            }
            double distance = Math.hypot(cell[0] - spawnCol, cell[1] - spawnRow);
            double clearance = waterBoundaryDistance(x, y);
            if (prioritizeDistance
                    ? distance < bestDistance
                    : clearance > bestClearance + 12
                        || (Math.abs(clearance - bestClearance) <= 12
                            && distance < bestDistance)) {
                best = new int[] {x, y};
                bestDistance = distance;
                bestClearance = clearance;
            }
        }
        if (best == null) {
            return null;
        }
        return best;
    }

    private boolean isDeepWaterTile(int col, int row) {
        if (col < 0 || row < 0 || col >= levelMap.mapWidth || row >= levelMap.mapHeight) {
            return false;
        }
        int tile = levelMap.groundLayer[col][row];
        return tile == 2 || tile == 18;
    }

    private boolean isWaterPoint(double x, double y) {
        int col = (int) Math.floor(x / gp.tileSize);
        int row = (int) Math.floor(y / gp.tileSize);
        return col >= 0 && row >= 0 && col < waterBody.length && row < waterBody[col].length
                && waterBody[col][row];
    }

    private boolean isWaterArea(double x, double y, double radius) {
        for (double offsetY = -radius; offsetY <= radius; offsetY += 6) {
            for (double offsetX = -radius; offsetX <= radius; offsetX += 6) {
                if (offsetX * offsetX + offsetY * offsetY <= radius * radius
                        && !isTraversablePoint(x + offsetX, y + offsetY)) {
                    return false;
                }
            }
        }
        return isTraversablePoint(x, y);
    }

    private boolean isSafeDragonPosition(double x, double y) {
        double half = Math.ceil(spriteSize / Math.sqrt(2.0)) + 4;
        for (double sampleY = y - half; sampleY <= y + half; sampleY += 10) {
            for (double sampleX = x - half; sampleX <= x + half; sampleX += 10) {
                if (!isTraversablePoint(sampleX, sampleY)) {
                    return false;
                }
            }
        }
        return isTraversablePoint(x + half, y + half)
                && isTraversablePoint(x - half, y - half)
                && isTraversablePoint(x + half, y - half)
                && isTraversablePoint(x - half, y + half);
    }

    private boolean isWaterOnlyPosition(double x, double y) {
        double half = Math.ceil(spriteSize / Math.sqrt(2.0)) + 4;
        for (double sampleY = y - half; sampleY <= y + half; sampleY += 10) {
            for (double sampleX = x - half; sampleX <= x + half; sampleX += 10) {
                if (!isWaterPoint(sampleX, sampleY)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isTraversablePoint(double x, double y) {
        if (isWaterPoint(x, y)) {
            return true;
        }
        int col = (int) Math.floor(x / gp.tileSize);
        int row = (int) Math.floor(y / gp.tileSize);
        if (col < 0 || row < 0 || col >= levelMap.mapWidth || row >= levelMap.mapHeight
                || isDeepWaterTile(col, row) || levelMap.collisionLayer[col][row] != 0) {
            return false;
        }
        int tileId = levelMap.groundLayer[col][row];
        return tileId >= 0 && tileId < gp.tileM.tile.length
                && !gp.tileM.tile[tileId].collision;
    }

    private boolean isPlayerOnLand() {
        Rectangle bounds = gp.player.solidArea;
        int left = gp.player.worldX + bounds.x;
        int right = left + bounds.width - 1;
        int top = gp.player.worldY + bounds.y;
        int bottom = top + bounds.height - 1;
        return isLandPoint(left, top) && isLandPoint(right, top)
                && isLandPoint(left, bottom) && isLandPoint(right, bottom)
                && isLandPoint((left + right) / 2.0, (top + bottom) / 2.0);
    }

    private boolean isLandPoint(double x, double y) {
        if (!isTraversablePoint(x, y)) {
            return false;
        }
        int col = (int) Math.floor(x / gp.tileSize);
        int row = (int) Math.floor(y / gp.tileSize);
        return !isDeepWaterTile(col, row);
    }

    private int getPlayerWaterRegion() {
        Rectangle bounds = gp.player.solidArea;
        int left = gp.player.worldX + bounds.x;
        int right = left + bounds.width - 1;
        int top = gp.player.worldY + bounds.y;
        int bottom = top + bounds.height - 1;
        int region = getWaterRegionAt((left + right) / 2, (top + bottom) / 2);
        if (region < 0
                || getWaterRegionAt(left, top) != region
                || getWaterRegionAt(right, top) != region
                || getWaterRegionAt(left, bottom) != region
                || getWaterRegionAt(right, bottom) != region) {
            return -1;
        }
        return region;
    }

    private int getWaterRegionAt(int x, int y) {
        int col = (int) Math.floor((double) x / gp.tileSize);
        int row = (int) Math.floor((double) y / gp.tileSize);
        if (col < 0 || row < 0 || col >= waterRegionIds.length
                || row >= waterRegionIds[col].length) {
            return -1;
        }
        return waterRegionIds[col][row];
    }

    private double distanceToPlayer() {
        double playerX = gp.player.worldX + gp.tileSize / 2.0;
        double playerY = gp.player.worldY + gp.tileSize / 2.0;
        return Math.hypot(playerX - worldX, playerY - worldY);
    }

    private void updateAnimationFrame() {
        long cycleDuration = 0;
        for (long duration : frameDurationsNanos) {
            cycleDuration += duration;
        }
        long elapsed = (long) (animationNanos % cycleDuration);
        for (int frame = 0; frame < frameDurationsNanos.length; frame++) {
            if (elapsed < frameDurationsNanos[frame]) {
                currentFrame = frame;
                return;
            }
            elapsed -= frameDurationsNanos[frame];
        }
        currentFrame = 0;
    }

    private boolean tryStartAttack(double distance) {
        boolean canFireball = fireballCooldownNanos == 0
                && distance <= gp.tileSize * FIREBALL_TRIGGER_TILES;
        boolean canPulse = pulseCooldownNanos == 0
                && distance <= gp.tileSize * PULSE_TRIGGER_TILES;
        if (!canFireball && !canPulse) {
            return false;
        }

        boolean choosePulse = canPulse && (!canFireball
                || random.nextDouble() < (lastAttackWasPulse ? 0.25 : 0.65));
        if (choosePulse) {
            lastAttackWasPulse = true;
            state = State.CHARGING_PULSE;
            stateNanos = 0;
            pulseRadius = gp.tileSize * MAX_PULSE_RADIUS_TILES;
            velocityX = 0;
            velocityY = 0;
        } else {
            lastAttackWasPulse = false;
            state = State.PREPARING_FIRE;
            stateNanos = 0;
            fireballCooldownNanos = FIREBALL_COOLDOWN_NANOS;
            velocityX *= 0.25;
            velocityY *= 0.25;
        }
        return true;
    }

    private void launchFireball() {
        double dx = gp.player.worldX + gp.tileSize / 2.0 - worldX;
        double dy = gp.player.worldY + gp.tileSize / 2.0 - worldY;
        double length = Math.max(1, Math.hypot(dx, dy));
        double directionX = dx / length;
        double directionY = dy / length;
        double muzzleDistance = spriteSize * 0.34;
        double startX = worldX + directionX * muzzleDistance;
        double startY = worldY + directionY * muzzleDistance;
        double fireballRadius = gp.tileSize * 0.4;
        if (!isWaterArea(startX, startY, fireballRadius)) {
            startX = worldX;
            startY = worldY;
        }
        if (!isWaterArea(startX, startY, fireballRadius)) {
            return;
        }
        fireballs.add(new Fireball(startX, startY, directionX, directionY));
        state = State.FIREBALL;
        stateNanos = 0;
        emitParticles(startX, startY, new Color(255, 137, 36), 8, 52);
    }

    private void releasePulse() {
        state = State.ENERGY_PULSE;
        stateNanos = 0;
        pulseCooldownNanos = PULSE_COOLDOWN_NANOS;
        emitParticles(worldX, worldY, new Color(99, 239, 255), 20, 92);
        if (isPlayerTargetable()
                && distanceToPlayer() <= pulseRadius
                && !gp.player.isDodging()) {
            gp.player.takeDamage(PULSE_DAMAGE, worldX, worldY);
        }
    }

    private void applyPlayerAttack() {
        Rectangle hitbox = gp.player.getAttackHitbox();
        if (hitbox == null || lastPlayerAttackSequence == gp.player.attackSequence) {
            return;
        }
        Rectangle bounds = new Rectangle(worldX - spriteSize / 3, worldY - spriteSize / 3,
                spriteSize * 2 / 3, spriteSize * 2 / 3);
        if (!hitbox.intersects(bounds)) {
            return;
        }
        lastPlayerAttackSequence = gp.player.attackSequence;
        Direction direction = gp.player.getFacing();
        int damage = gp.player.getAttackDamage();
        takeDamage(damage);
        if (health > 0 && damage >= 20) {
            state = State.STUNNED;
            stateNanos = 0;
        }
        switch (direction) {
            case UP -> velocityY -= 25;
            case DOWN -> velocityY += 25;
            case LEFT -> velocityX -= 25;
            case RIGHT -> velocityX += 25;
        }
    }

    private void turnTowardPlayer(double seconds) {
        double targetX = gp.player.worldX + gp.tileSize / 2.0;
        double targetY = gp.player.worldY + gp.tileSize / 2.0;
        turnToward(targetX - worldX, targetY - worldY, seconds);
    }

    private void turnToward(double dx, double dy, double seconds) {
        if (Math.hypot(dx, dy) < 1) {
            return;
        }
        double target = Math.atan2(dy, dx);
        double difference = normalizeAngle(target - heading);
        heading += difference * Math.min(1, seconds * 4.5);
    }

    private void steerToward(double x, double y, double seconds, double speed) {
        double dx = x - worldX;
        double dy = y - worldY;
        double distance = Math.hypot(dx, dy);
        if (distance < 1) {
            dampVelocity(seconds);
            return;
        }
        turnToward(dx, dy, seconds);
        double desiredX = dx / distance * speed;
        double desiredY = dy / distance * speed;
        double acceleration = 210 * seconds;
        velocityX = approach(velocityX, desiredX, acceleration);
        velocityY = approach(velocityY, desiredY, acceleration);
    }

    private double[] findChaseWaypoint(double targetX, double targetY) {
        int targetCol = (int) (targetX / gp.tileSize);
        int targetRow = (int) (targetY / gp.tileSize);
        if (navigationCooldownNanos > 0
                && targetCol == navigationTargetCol && targetRow == navigationTargetRow
                && Math.hypot(navigationWaypointX - worldX,
                        navigationWaypointY - worldY) > gp.tileSize * 0.45) {
            return new double[] {navigationWaypointX, navigationWaypointY};
        }

        navigationTargetCol = targetCol;
        navigationTargetRow = targetRow;
        navigationCooldownNanos = 350_000_000L;
        int cols = levelMap.mapWidth;
        int rows = levelMap.mapHeight;
        int startCol = Math.max(0, Math.min(cols - 1, worldX / gp.tileSize));
        int startRow = Math.max(0, Math.min(rows - 1, worldY / gp.tileSize));
        int start = startRow * cols + startCol;
        int[] parents = new int[cols * rows];
        java.util.Arrays.fill(parents, -2);
        parents[start] = -1;
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        pending.add(start);
        int goal = start;
        double bestDistance = Math.hypot(
                startCol * gp.tileSize + gp.tileSize / 2.0 - targetX,
                startRow * gp.tileSize + gp.tileSize / 2.0 - targetY);
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!pending.isEmpty()) {
            int cell = pending.removeFirst();
            int col = cell % cols;
            int row = cell / cols;
            double cellX = col * gp.tileSize + gp.tileSize / 2.0;
            double cellY = row * gp.tileSize + gp.tileSize / 2.0;
            double distance = Math.hypot(cellX - targetX, cellY - targetY);
            if (distance < bestDistance) {
                bestDistance = distance;
                goal = cell;
            }
            if (distance <= gp.tileSize * KEEP_DISTANCE_TILES) {
                goal = cell;
                break;
            }
            for (int[] offset : offsets) {
                int nextCol = col + offset[0];
                int nextRow = row + offset[1];
                if (nextCol < 0 || nextRow < 0 || nextCol >= cols || nextRow >= rows) {
                    continue;
                }
                int next = nextRow * cols + nextCol;
                if (parents[next] != -2
                        || !isSafeDragonPosition(nextCol * gp.tileSize + gp.tileSize / 2.0,
                                nextRow * gp.tileSize + gp.tileSize / 2.0)) {
                    continue;
                }
                parents[next] = cell;
                pending.addLast(next);
            }
        }
        int firstStep = goal;
        while (parents[firstStep] >= 0 && parents[firstStep] != start) {
            firstStep = parents[firstStep];
        }
        if (parents[firstStep] != start) {
            navigationWaypointX = targetX;
            navigationWaypointY = targetY;
        } else {
            navigationWaypointX = firstStep % cols * gp.tileSize + gp.tileSize / 2.0;
            navigationWaypointY = firstStep / cols * gp.tileSize + gp.tileSize / 2.0;
        }
        return new double[] {navigationWaypointX, navigationWaypointY};
    }

    private void orbitPlayer(double seconds) {
        double desiredAngle = Math.atan2(
                worldY - gp.player.worldY - gp.tileSize / 2.0,
                worldX - gp.player.worldX - gp.tileSize / 2.0) + 0.8;
        double radius = gp.tileSize * 1.7;
        double targetX = gp.player.worldX + gp.tileSize / 2.0 + Math.cos(desiredAngle) * radius;
        double targetY = gp.player.worldY + gp.tileSize / 2.0 + Math.sin(desiredAngle) * radius;
        if (isSafeDragonPosition(targetX, targetY)) {
            steerToward(targetX, targetY, seconds, 62);
        } else {
            dampVelocity(seconds);
        }
    }

    private void swimPattern(double seconds) {
        if (wanderNanos <= 0 || !hasWanderTarget
                || Math.hypot(wanderTargetX - worldX, wanderTargetY - worldY) < gp.tileSize * 0.7) {
            chooseWanderTarget();
        }
        if (hasWanderTarget) {
            steerToward(wanderTargetX, wanderTargetY, seconds, 48);
            turnToward(wanderTargetX - worldX, wanderTargetY - worldY, seconds);
        }
    }

    private void chooseWanderTarget() {
        if (waterCells.isEmpty()) {
            hasWanderTarget = false;
            return;
        }
        for (int attempt = 0; attempt < 24; attempt++) {
            int[] cell = waterCells.get(random.nextInt(waterCells.size()));
            int x = cell[0] * gp.tileSize + gp.tileSize / 2;
            int y = cell[1] * gp.tileSize + gp.tileSize / 2;
            if (isSafeDragonPosition(x, y)) {
                wanderTargetX = x;
                wanderTargetY = y;
                wanderNanos = (2.0 + random.nextDouble() * 3.5) * 1_000_000_000.0;
                hasWanderTarget = true;
                return;
            }
        }
        wanderNanos = 1_000_000_000L;
        hasWanderTarget = false;
    }

    private void moveWithinWater(double seconds) {
        double nextX = worldX + velocityX * seconds;
        double nextY = worldY + velocityY * seconds;
        if (isSafeDragonPosition(nextX, nextY)) {
            worldX = (int) Math.round(nextX);
            worldY = (int) Math.round(nextY);
            return;
        }
        boolean moved = false;
        if (isSafeDragonPosition(nextX, worldY)) {
            worldX = (int) Math.round(nextX);
            velocityY *= 0.5;
            moved = true;
        }
        if (isSafeDragonPosition(worldX, nextY)) {
            worldY = (int) Math.round(nextY);
            velocityX *= 0.5;
            moved = true;
        }
        if (!moved) {
            velocityX *= -0.18;
            velocityY *= -0.18;
            hasWanderTarget = false;
        }
        if (!isSafeDragonPosition(worldX, worldY)) {
            int[] safe = findSafeSpawn(worldX / gp.tileSize, worldY / gp.tileSize);
            if (safe == null) {
                throw new IllegalStateException("Water dragon movement left its water body.");
            }
            worldX = safe[0];
            worldY = safe[1];
            velocityX = 0;
            velocityY = 0;
        }
    }

    private void dampVelocity(double seconds) {
        double factor = Math.max(0, 1 - seconds * 3.5);
        velocityX *= factor;
        velocityY *= factor;
    }

    private void updateProjectiles(double seconds) {
        for (int i = fireballs.size() - 1; i >= 0; i--) {
            Fireball fireball = fireballs.get(i);
            fireball.update(seconds);
            if (fireball.hitPlayer) {
                if (!gp.player.isDodging() && isPlayerTargetable()) {
                    gp.player.takeDamage(FIREBALL_DAMAGE,
                            (int) fireball.x, (int) fireball.y);
                }
                explode(fireball.x, fireball.y);
                fireballs.remove(i);
            } else if (fireball.elapsed >= 3_000_000_000L
                    || !isWaterArea(fireball.x, fireball.y, gp.tileSize * 0.4)) {
                if (isTraversablePoint(fireball.x, fireball.y)) {
                    explode(fireball.x, fireball.y);
                }
                fireballs.remove(i);
            }
        }
    }

    private void explode(double x, double y) {
        if (!isTraversablePoint(x, y)) {
            return;
        }
        explosions.add(new Explosion(x, y));
        emitParticles(x, y, new Color(255, 132, 43), 9, 65);
    }

    private void updateExplosions(long delta) {
        for (int i = explosions.size() - 1; i >= 0; i--) {
            Explosion explosion = explosions.get(i);
            explosion.elapsed += delta;
            if (explosion.elapsed >= 420_000_000L) {
                explosions.remove(i);
            }
        }
    }

    private void emitParticles(double x, double y, Color color, int count, double speed) {
        while (particles.size() + count > MAX_PARTICLES && !particles.isEmpty()) {
            particles.remove(0);
        }
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double velocity = speed * (0.25 + random.nextDouble() * 0.75);
            particles.add(new Particle(x, y, Math.cos(angle) * velocity,
                    Math.sin(angle) * velocity, color,
                    320_000_000L + random.nextInt(480_000_000)));
        }
    }

    private void updateParticles(double seconds) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle particle = particles.get(i);
            particle.x += particle.vx * seconds;
            particle.y += particle.vy * seconds;
            particle.vx *= Math.max(0, 1 - seconds * 1.4);
            particle.vy *= Math.max(0, 1 - seconds * 1.4);
            particle.age += (long) (seconds * 1_000_000_000L);
            if (particle.age >= particle.lifetime
                    || !isTraversablePoint(particle.x, particle.y)) {
                particles.remove(i);
            }
        }
        if (idleRippleNanos >= 420_000_000L) {
            idleRippleNanos = 0;
            if (isWaterPoint(worldX, worldY) && random.nextDouble() < 0.7) {
                emitParticles(worldX, worldY + spriteSize / 3.0,
                        new Color(88, 215, 234), 2, 14);
            }
        }
    }

    private void drawDragon(Graphics2D g2) {
        int screenX = worldX - gp.player.worldX + gp.player.screenX;
        int screenY = worldY - gp.player.worldY + gp.player.screenY;
        double bob = Math.sin(hoverPhase) * 3.5;
        float alpha = state == State.DEATH
                ? (float) Math.max(0, 1.0 - stateNanos / DEATH_ANIMATION_NANOS) : 1f;
        Composite oldComposite = g2.getComposite();
        Object oldInterpolation = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        int size = state == State.DEATH
                ? Math.max(1, (int) (spriteSize * (1 - 0.28 * stateNanos / DEATH_ANIMATION_NANOS)))
                : spriteSize;
        int directionRow = animationDirectionRow();
        Graphics2D dragonGraphics = (Graphics2D) g2.create();
        if (state == State.CHARGING_PULSE || state == State.ENERGY_PULSE) {
            float glow = (float) (0.20 + 0.28 * (0.5 + 0.5 * Math.sin(stateNanos / 80_000_000.0)));
            dragonGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    Math.min(0.65f, glow)));
            dragonGraphics.drawImage(frames[directionRow]
                            [(currentFrame + 2) % SPRITE_SHEET_COLUMNS],
                    screenX - size / 2, screenY - size / 2, size, size, null);
            dragonGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        }
        dragonGraphics.drawImage(frames[directionRow][currentFrame], screenX - size / 2,
                screenY - size / 2 + (int) bob, size, size, null);
        drawEyeGlow(dragonGraphics, screenX, screenY, size);
        if (state == State.PREPARING_FIRE) {
            drawMouthCharge(dragonGraphics, screenX, screenY, size);
        }
        dragonGraphics.dispose();
        g2.setComposite(oldComposite);
        if (oldInterpolation != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }
        drawBossHealthBar(g2, screenX, screenY - size / 2 - 12);
    }

    private int animationDirectionRow() {
        double normalized = normalizeAngle(heading);
        if (normalized >= -Math.PI / 4.0 && normalized < Math.PI / 4.0) {
            return 2;
        }
        if (normalized >= Math.PI / 4.0 && normalized < 3.0 * Math.PI / 4.0) {
            return 0;
        }
        if (normalized < -Math.PI / 4.0 && normalized >= -3.0 * Math.PI / 4.0) {
            return 3;
        }
        return 1;
    }

    private void drawEyeGlow(Graphics2D g2, int x, int y, int size) {
        float glow = state == State.HURT ? 0.65f : 1f;
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, glow));
        g2.setColor(hurtFlash > 0
                ? new Color(255, 91, 110, 230) : new Color(114, 248, 255, 200));
        int eyeSize = Math.max(4, size / 14);
        int eyeY = y - size / 8;
        g2.fillOval(x - size / 10 - eyeSize / 2, eyeY, eyeSize, eyeSize);
        g2.fillOval(x + size / 10 - eyeSize / 2, eyeY, eyeSize, eyeSize);
        g2.setComposite(old);
    }

    private void drawMouthCharge(Graphics2D g2, int x, int y, int size) {
        float progress = Math.min(1, (float) stateNanos / FIRE_PREPARATION_NANOS);
        int radius = Math.max(5, (int) (size * (0.04 + progress * 0.11)));
        int flameY = y + size / 3;
        g2.setColor(new Color(255, 94, 29, 90 + (int) (progress * 100)));
        g2.fillOval(x - radius * 2, flameY - radius * 2, radius * 4, radius * 4);
        g2.setColor(new Color(255, 239, 164, 220));
        g2.fillOval(x - radius / 2, flameY - radius / 2, radius, radius);
    }

    private void drawBossHealthBar(Graphics2D g2, int centerX, int y) {
        if (health <= 0) {
            return;
        }
        int width = gp.tileSize * 2;
        int x = centerX - width / 2;
        g2.setColor(new Color(8, 22, 38, 210));
        g2.fillRoundRect(x, y, width, 9, 6, 6);
        g2.setColor(new Color(22, 60, 83));
        g2.fillRoundRect(x + 1, y + 1, width - 2, 7, 5, 5);
        g2.setColor(new Color(40, 221, 239));
        g2.fillRoundRect(x + 1, y + 1,
                Math.max(0, (width - 2) * health / MAX_HEALTH), 7, 5, 5);
        g2.setColor(new Color(203, 252, 255));
        g2.drawRoundRect(x, y, width, 9, 6, 6);
    }

    private void drawWaterEffects(Graphics2D g) {
        int x = worldX - gp.player.worldX + gp.player.screenX;
        int y = worldY - gp.player.worldY + gp.player.screenY;
        int ripple = gp.tileSize + (int) (Math.sin(hoverPhase * 1.3) * 7);
        g.setColor(new Color(47, 207, 238, 36));
        g.fillOval(x - ripple, y + spriteSize / 4 - ripple / 4,
                ripple * 2, ripple / 2);
        g.setColor(new Color(127, 246, 255, 105));
        g.setStroke(new BasicStroke(2f));
        g.drawOval(x - ripple / 2, y + spriteSize / 4 - ripple / 8,
                ripple, ripple / 4);
        drawPulse(g);
        for (Particle particle : particles) {
            float alpha = Math.max(0, 1f - (float) particle.age / particle.lifetime);
            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.setColor(particle.color);
            int px = (int) particle.x - gp.player.worldX + gp.player.screenX;
            int py = (int) particle.y - gp.player.worldY + gp.player.screenY;
            int size = particle.color.getRed() > 200 ? 5 : 3;
            g.fillOval(px - size / 2, py - size / 2, size, size);
            g.setComposite(old);
        }
    }

    private void drawFireballs(Graphics2D g) {
        for (Fireball fireball : fireballs) {
            int screenX = (int) fireball.x - gp.player.worldX + gp.player.screenX;
            int screenY = (int) fireball.y - gp.player.worldY + gp.player.screenY;
            int size = gp.tileSize * 3 / 4;
            BufferedImage image = WaterDragonEffects.fireball(fireball.frame());
            g.drawImage(image, screenX - size / 2, screenY - size / 2, size, size, null);
            g.setColor(new Color(255, 170, 61, 110));
            g.fillOval(screenX - size / 3, screenY - size / 3, size * 2 / 3, size * 2 / 3);
        }
    }

    private void drawExplosions(Graphics2D g) {
        for (Explosion explosion : explosions) {
            float progress = (float) explosion.elapsed / 420_000_000L;
            int frame = Math.min(WaterDragonEffects.FRAME_COUNT - 1,
                    (int) (progress * WaterDragonEffects.FRAME_COUNT));
            int size = (int) (gp.tileSize * 1.7 * (0.7 + progress * 0.3));
            int x = (int) explosion.x - gp.player.worldX + gp.player.screenX;
            int y = (int) explosion.y - gp.player.worldY + gp.player.screenY;
            g.drawImage(WaterDragonEffects.explosion(frame),
                    x - size / 2, y - size / 2, size, size, null);
        }
    }

    private void drawPulse(Graphics2D g) {
        if (state != State.ENERGY_PULSE) {
            if (state == State.CHARGING_PULSE) {
                double intensity = 0.4 + 0.6 * (0.5 + 0.5
                        * Math.sin(stateNanos / 65_000_000.0));
                int size = (int) (gp.tileSize * (0.7 + intensity * 0.4));
                int x = worldX - gp.player.worldX + gp.player.screenX;
                int y = worldY - gp.player.worldY + gp.player.screenY;
                g.setColor(new Color(109, 238, 255, (int) (120 * intensity)));
                g.fillOval(x - size / 2, y - size / 2, size, size);
            }
            return;
        }
        double progress = Math.min(1, stateNanos / 500_000_000.0);
        int diameter = (int) Math.max(1, pulseRadius * 2 * progress);
        int x = worldX - gp.player.worldX + gp.player.screenX;
        int y = worldY - gp.player.worldY + gp.player.screenY;
        int frame = Math.min(WaterDragonEffects.FRAME_COUNT - 1,
                (int) (progress * WaterDragonEffects.FRAME_COUNT));
        g.drawImage(WaterDragonEffects.pulse(frame),
                x - diameter / 2, y - diameter / 2, diameter, diameter, null);
    }

    private Area buildWaterClip() {
        int cameraX = gp.player.worldX - gp.player.screenX;
        int cameraY = gp.player.worldY - gp.player.screenY;
        Area clip = new Area();
        for (int[] cell : waterCells) {
            clip.add(new Area(new Rectangle(cell[0] * gp.tileSize - cameraX,
                    cell[1] * gp.tileSize - cameraY, gp.tileSize, gp.tileSize)));
        }
        return clip;
    }

    private double waterBoundaryDistance(double x, double y) {
        double boundary = gp.tileSize * MAX_PULSE_RADIUS_TILES;
        for (int angleIndex = 0; angleIndex < 48; angleIndex++) {
            double angle = angleIndex * Math.PI / 24.0;
            double distance = 0;
            while (distance < boundary) {
                distance += 6;
                if (!isWaterPoint(x + Math.cos(angle) * distance,
                        y + Math.sin(angle) * distance)) {
                    break;
                }
            }
            boundary = Math.min(boundary, Math.max(gp.tileSize * 0.5, distance - 6));
        }
        return boundary;
    }

    private void updateDeath() {
        state = State.DEATH;
        emitParticles(worldX, worldY, new Color(112, 228, 245), 1, 16);
        if (stateNanos >= DEATH_ANIMATION_NANOS) {
            isDead = true;
            if (gp.levelM != null) {
                gp.levelM.onDragonDefeated(this);
            }
        }
    }

    private static double approach(double current, double target, double amount) {
        if (current < target) {
            return Math.min(target, current + amount);
        }
        return Math.max(target, current - amount);
    }

    private static double normalizeAngle(double angle) {
        while (angle > Math.PI) {
            angle -= Math.PI * 2;
        }
        while (angle < -Math.PI) {
            angle += Math.PI * 2;
        }
        return angle;
    }

    private final class Fireball {
        double x;
        double y;
        final double velocityX;
        final double velocityY;
        long elapsed;
        boolean hitPlayer;
        long trailNanos;

        Fireball(double x, double y, double directionX, double directionY) {
            this.x = x;
            this.y = y;
            velocityX = directionX * FIREBALL_SPEED;
            velocityY = directionY * FIREBALL_SPEED;
        }

        void update(double seconds) {
            x += velocityX * seconds;
            y += velocityY * seconds;
            elapsed += (long) (seconds * 1_000_000_000L);
            trailNanos += (long) (seconds * 1_000_000_000L);
            if (trailNanos >= 80_000_000L && isTraversablePoint(x, y)) {
                trailNanos = 0;
                emitParticles(x, y, new Color(255, 150, 47), 2, 28);
            }
            if (!isWaterArea(x, y, gp.tileSize * 0.4)) {
                return;
            }
            Rectangle playerBounds = new Rectangle(gp.player.worldX + gp.player.solidArea.x,
                    gp.player.worldY + gp.player.solidArea.y,
                    gp.player.solidArea.width, gp.player.solidArea.height);
            Rectangle projectileBounds = new Rectangle((int) x - gp.tileSize / 5,
                    (int) y - gp.tileSize / 5, gp.tileSize * 2 / 5, gp.tileSize * 2 / 5);
            hitPlayer = isPlayerTargetable() && projectileBounds.intersects(playerBounds);
        }

        int frame() {
            return (int) (elapsed / 70_000_000L) % WaterDragonEffects.FRAME_COUNT;
        }
    }

    private static final class Explosion {
        final double x;
        final double y;
        long elapsed;

        Explosion(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class Particle {
        double x;
        double y;
        double vx;
        double vy;
        final Color color;
        final long lifetime;
        long age;

        Particle(double x, double y, double vx, double vy, Color color, long lifetime) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.lifetime = lifetime;
        }
    }
}
