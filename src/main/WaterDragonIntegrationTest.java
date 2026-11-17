package main;

import entities.Dragon;
import tile.MapData;
import tile.MapLoader;

import java.awt.image.BufferedImage;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Headless integration checks for water boundaries and the water dragon boss. */
public final class WaterDragonIntegrationTest {
    private WaterDragonIntegrationTest() {}

    public static void main(String[] args) throws Exception {
        GamePanel game = new GamePanel();
        game.setSize(game.screenWidth, game.screenHeight);
        game.setupGame();

        assertWaterBlocked(game, 1);
        assertWaterBlocked(game, 2);
        assertMovementKeyAliases(game);

        MapData region3 = MapLoader.loadLevel(3);
        game.levelM.enter(3, region3.playerSpawn[0], region3.playerSpawn[1]);
        check(game.dragonActive && game.dragon != null, "Dragon did not activate in region 3.");
        check(game.dragon2 != null, "The additional dragon did not activate in region 3.");
        check(game.dragon.getAnimationFrameCount() == 3,
                "Dragon sprite sheet should load all three animation frames.");
        check(game.dragon.getAnimationDirectionRow() == 0,
                "Dragon should start with its down-facing animation row.");
        int initialDragonFrame = game.dragon.getCurrentAnimationFrame();
        game.dragon.update(100_000_000L);
        game.dragon.update(100_000_000L);
        check(game.dragon.getCurrentAnimationFrame() != initialDragonFrame,
                "Dragon sprite-sheet animation did not advance.");
        check(game.dragon.getWaterBodySize() > 10, "Boss marker did not resolve to a water body.");
        check(game.dragon.isEntirelyInWater(), "Dragon spawned outside deep water.");
        check(game.dragon2.isEntirelyInWater(), "Second dragon spawned outside deep water.");
        check(game.dragon.getWaterRegionId() != game.dragon2.getWaterRegionId(),
                "The two dragons should start in different lakes.");

        int[] entry = findWaterEntry(region3);
        assertWaterBlocked(game, 3);
        game.player.direction = "right";
        game.player.speed = 4;
        game.player.collisionOn = false;
        game.player.worldX = entry[0] * game.tileSize + 6;
        game.player.worldY = entry[1] * game.tileSize;
        int shorelineX = game.player.worldX;
        long movementStart = System.currentTimeMillis();
        game.keyH.keyPressed(new KeyEvent(game, KeyEvent.KEY_PRESSED, movementStart,
                0, KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED));
        for (int tick = 0; tick < 6; tick++) {
            game.player.update(16_666_667L);
        }
        game.keyH.keyReleased(new KeyEvent(game, KeyEvent.KEY_RELEASED, movementStart + 1,
                0, KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED));
        check(game.player.worldX == shorelineX,
                "Player walked into deep water in region 3.");
        check(!game.player.isSwimming(), "Player entered a swim state in region 3.");

        int[] target = findWaterTarget(region3, game.dragon.worldX, game.dragon.worldY);
        game.player.worldX = target[0] * game.tileSize;
        game.player.worldY = target[1] * game.tileSize;
        check(!game.player.isSwimming(), "Player entered a swim state in region 3.");
        Set<Dragon.State> seen = new HashSet<>();
        for (int tick = 0; tick < 360; tick++) {
            game.dragon.update(100_000_000L);
            seen.add(game.dragon.getState());
            check(game.dragon.isOnValidTerrain(), "Dragon crossed into blocked terrain.");
        }
        check(seen.contains(Dragon.State.PREPARING_FIRE)
                        || seen.contains(Dragon.State.FIREBALL),
                "Dragon did not enter the fireball attack sequence.");
        check(seen.contains(Dragon.State.CHARGING_PULSE)
                        || seen.contains(Dragon.State.ENERGY_PULSE),
                "Dragon did not enter the energy-pulse attack sequence.");
        check(game.player.getHealth() < game.player.getMaxHealth(),
                "Dragon attacks did not damage the player.");

        game.player.worldX = 8 * game.tileSize;
        game.player.worldY = 48 * game.tileSize;
        check(!game.player.isSwimming(), "Player left the water but remained in swim state.");
        for (int tick = 0; tick < 10; tick++) {
            game.dragon.update(100_000_000L);
            check(game.dragon.isOnValidTerrain(),
                    "Dragon crossed into blocked terrain after the player left.");
        }
        check(game.dragon.getState() == Dragon.State.FOLLOWING
                        || game.dragon.getState() == Dragon.State.PREPARING_FIRE
                        || game.dragon.getState() == Dragon.State.FIREBALL,
                "Dragon did not pursue the player onto passable land.");

        for (int targetRegion = 0; targetRegion < 3; targetRegion++) {
            if (targetRegion == game.dragon.getWaterRegionId()) {
                continue;
            }
            int[] otherLake = findWaterCellInRegion(region3, targetRegion);
            game.player.worldX = otherLake[0] * game.tileSize;
            game.player.worldY = otherLake[1] * game.tileSize;
            for (int tick = 0; tick < 8; tick++) {
                game.dragon.update(100_000_000L);
                check(game.dragon.isOnValidTerrain(),
                        "Dragon crossed into blocked terrain while transferring between lakes.");
            }
            check(game.dragon.getWaterRegionId() == targetRegion
                            && game.dragon.isPlayerInWaterBody(),
                    "Dragon did not follow the player into water body " + targetRegion + ".");
        }

        int[] landTarget = findLandTarget(game, region3, game.dragon);
        game.player.worldX = landTarget[0] * game.tileSize + 4;
        game.player.worldY = landTarget[1] * game.tileSize + 4;
        check(!game.player.isSwimming(), "Player unexpectedly entered the water.");
        int healthBeforeLandAttack = game.player.getHealth();
        boolean dragonReachedLand = false;
        boolean secondDragonReachedLand = false;
        for (int tick = 0; tick < 600; tick++) {
            game.player.update(100_000_000L);
            game.dragon.update(100_000_000L);
            game.dragon2.update(100_000_000L);
            check(game.dragon.isOnValidTerrain() && game.dragon2.isOnValidTerrain(),
                    "A dragon crossed into blocked terrain.");
            dragonReachedLand |= !game.dragon.isEntirelyInWater();
            secondDragonReachedLand |= !game.dragon2.isEntirelyInWater();
        }
        check(dragonReachedLand && secondDragonReachedLand,
                "Both dragons should leave the water and pursue the player on land.");
        check(game.player.getHealth() < healthBeforeLandAttack,
                "The dragons did not attack the player on land: health="
                        + game.player.getHealth() + "/" + healthBeforeLandAttack
                        + ", states=" + game.dragon.getState() + "/" + game.dragon2.getState()
                        + ", positions=" + game.dragon.worldX + "," + game.dragon.worldY
                        + "/" + game.dragon2.worldX + "," + game.dragon2.worldY
                        + ", target=" + landTarget[0] + "," + landTarget[1]
                        + ", recognized=" + game.dragon.isPlayerInWaterBody()
                        + "/" + game.dragon2.isPlayerInWaterBody());

        game.player.worldX = game.dragon.worldX - game.player.solidArea.x;
        game.player.worldY = game.dragon.worldY - game.player.solidArea.y;
        game.updateCamera();
        BufferedImage screenshot = new BufferedImage(game.screenWidth, game.screenHeight,
                BufferedImage.TYPE_INT_ARGB);
        var graphics = screenshot.createGraphics();
        game.paint(graphics);
        graphics.dispose();
        Path screenshotPath = Path.of("out", "world-checks", "water-dragon.png");
        Files.createDirectories(screenshotPath.getParent());
        javax.imageio.ImageIO.write(screenshot, "png", screenshotPath.toFile());

        Path save = Path.of("out", "world-checks", "water-dragon-save.properties");
        game.levelM.save(save);
        GamePanel restored = new GamePanel();
        restored.setupGame();
        restored.levelM.load(save);
        check(restored.levelM.currentLevel == 3 && restored.dragon != null,
                "Save/load did not restore the region 3 boss.");
        check(restored.dragon2 != null, "Save/load did not restore the second dragon.");
        check(restored.dragon.health == game.dragon.health,
                "Save/load did not preserve boss health.");
        check(restored.dragon2.health == game.dragon2.health,
                "Save/load did not preserve the second dragon's health.");
        check(restored.dragon.getWaterRegionId() == game.dragon.getWaterRegionId(),
                "Save/load did not preserve the active water body.");
        check(restored.dragon.isOnValidTerrain(),
                "Save/load restored the dragon to blocked terrain.");
        check(restored.dragon2.isOnValidTerrain(),
                "Save/load restored the second dragon to blocked terrain.");
        restored.player.restoreHealth(restored.player.getMaxHealth());
        restored.player.resetActionState();
        restored.player.worldX = restored.dragon.worldX - restored.tileSize;
        restored.player.worldY = restored.dragon.worldY - restored.tileSize / 2;
        restored.keyH.rightPressed = true;
        restored.player.update(16_666_667L);
        restored.keyH.rightPressed = false;
        restored.player.worldX = restored.dragon.worldX - restored.tileSize;
        restored.player.worldY = restored.dragon.worldY - restored.tileSize / 2;
        int healthBeforePunch = restored.dragon.health;
        long keyTime = System.currentTimeMillis();
        restored.keyH.keyPressed(new KeyEvent(restored, KeyEvent.KEY_PRESSED, keyTime,
                0, KeyEvent.VK_J, 'j'));
        for (int tick = 0; tick < 16; tick++) {
            restored.player.worldX = restored.dragon.worldX - restored.tileSize;
            restored.player.worldY = restored.dragon.worldY - restored.tileSize / 2;
            restored.player.update(16_666_667L);
            restored.dragon.update(16_666_667L);
        }
        restored.keyH.keyReleased(new KeyEvent(restored, KeyEvent.KEY_RELEASED,
                keyTime + 1, 0, KeyEvent.VK_J, 'j'));
        check(restored.dragon.health < healthBeforePunch,
                "Player melee hitbox did not damage the dragon.");
        restored.dragon.takeDamage(restored.dragon.health);
        for (int tick = 0; tick < 20; tick++) {
            restored.dragon.update(100_000_000L);
        }
        check(restored.gameState != GamePanel.GameState.GAME_COMPLETE,
                "Defeating one dragon incorrectly completed the encounter.");
        Path partialSave = Path.of("out", "world-checks", "one-dragon-defeated.properties");
        restored.levelM.save(partialSave);
        GamePanel partialRestore = new GamePanel();
        partialRestore.setupGame();
        partialRestore.levelM.load(partialSave);
        check(partialRestore.dragon.isDead && !partialRestore.dragon2.isDead,
                "Save/load did not preserve the first dragon's defeat.");
        check(partialRestore.gameState != GamePanel.GameState.GAME_COMPLETE,
                "Save/load completed the encounter after only one dragon was defeated.");
        partialRestore.dragon2.takeDamage(partialRestore.dragon2.health);
        for (int tick = 0; tick < 20; tick++) {
            partialRestore.dragon2.update(100_000_000L);
        }
        check(partialRestore.gameState == GamePanel.GameState.GAME_COMPLETE,
                "The remaining dragon did not complete the encounter after save/load.");
        restored.dragon2.takeDamage(restored.dragon2.health);
        for (int tick = 0; tick < 20; tick++) {
            restored.dragon2.update(100_000_000L);
        }
        check(restored.gameState == GamePanel.GameState.GAME_COMPLETE
                        && restored.levelM.progression.rewarded("3-dragon"),
                "Defeating both dragons did not award progression.");

        restored.levelM.enter(1, 10, 40);
        check(!restored.dragonActive, "Dragon remained active outside region 3.");
        System.out.println("PASS: player water boundaries, two water dragons, land pursuit/attacks, both attack types, and save/load");
        System.out.println("Dragon render capture: " + screenshotPath.toAbsolutePath());
    }

    private static void assertWaterBlocked(GamePanel game, int region)
            throws Exception {
        MapData map = MapLoader.loadLevel(region);
        game.levelM.enter(region, map.playerSpawn[0], map.playerSpawn[1]);
        check(region == 3 || !game.dragonActive, "Dragon activated outside region 3.");
        int[] entry = findWaterEntry(map);
        game.player.worldX = entry[0] * game.tileSize + 6;
        game.player.worldY = entry[1] * game.tileSize;
        game.player.direction = "right";
        game.player.speed = 4;
        game.player.collisionOn = false;
        game.cChecker.checkTile(game.player);
        check(game.player.collisionOn, "Deep water became traversable in region " + region + ".");
        check(!game.player.isSwimming(), "Player entered swim state in region " + region + ".");
    }

    private static void assertMovementKeyAliases(GamePanel game) {
        long now = System.currentTimeMillis();
        game.keyH.keyPressed(new KeyEvent(game, KeyEvent.KEY_PRESSED, now, 0,
                KeyEvent.VK_W, 'w'));
        game.keyH.keyPressed(new KeyEvent(game, KeyEvent.KEY_PRESSED, now + 1, 0,
                KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED));
        game.keyH.keyReleased(new KeyEvent(game, KeyEvent.KEY_RELEASED, now + 2, 0,
                KeyEvent.VK_W, 'w'));
        check(game.keyH.upPressed && "up".equals(game.keyH.getMovementDirection()),
                "Releasing W stopped movement while Up was still held.");
        game.keyH.keyReleased(new KeyEvent(game, KeyEvent.KEY_RELEASED, now + 3, 0,
                KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED));
        check(!game.keyH.upPressed, "Up arrow remained pressed after key release.");

        game.keyH.keyPressed(new KeyEvent(game, KeyEvent.KEY_PRESSED, now + 4, 0,
                KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED));
        check(game.keyH.leftPressed && "left".equals(game.keyH.getMovementDirection()),
                "Left arrow did not activate movement.");
        game.keyH.keyReleased(new KeyEvent(game, KeyEvent.KEY_RELEASED, now + 5, 0,
                KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED));
        check(!game.keyH.leftPressed, "Left arrow remained pressed after key release.");
    }

    private static int[] findWaterEntry(MapData map) {
        for (int col = 1; col < map.mapWidth - 1; col++) {
            for (int row = 1; row < map.mapHeight - 1; row++) {
                if (!isDeepWater(map.groundLayer[col + 1][row])) {
                    continue;
                }

                if (map.groundLayer[col][row] != 2 && map.groundLayer[col][row] != 18
                        && map.collisionLayer[col][row] == 0) {
                    return new int[] {col, row};
                }
            }
        }
        throw new AssertionError("No land-to-water boundary found.");
    }

    private static int[] findLandTarget(GamePanel game, MapData map, Dragon dragon) {
        int[] best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int col = 3; col < map.mapWidth - 3; col++) {
            for (int row = 3; row < map.mapHeight - 3; row++) {
                int tile = map.groundLayer[col][row];
                if (isDeepWater(tile) || map.collisionLayer[col][row] != 0
                        || game.tileM.tile[tile].collision) {
                    continue;
                }
                boolean areaOpen = true;
                for (int dx = -2; dx <= 2 && areaOpen; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        int x = col + dx;
                        int y = row + dy;
                        int neighbor = map.groundLayer[x][y];
                        if (!isDeepWater(neighbor)
                                && (map.collisionLayer[x][y] != 0
                                    || game.tileM.tile[neighbor].collision)) {
                            areaOpen = false;
                            break;
                        }
                    }
                }
                if (!areaOpen) {
                    continue;
                }
                double distance = Math.hypot(
                        col * game.tileSize + game.tileSize / 2.0 - dragon.worldX,
                        row * game.tileSize + game.tileSize / 2.0 - dragon.worldY);
                if (distance >= game.tileSize * 4 && distance <= game.tileSize * 8
                        && distance < bestDistance) {
                    best = new int[] {col, row};
                    bestDistance = distance;
                }
            }
        }
        if (best == null) {
            throw new AssertionError("No nearby passable land target was found.");
        }
        return best;
    }

    private static int[] findWaterTarget(MapData map, int dragonX, int dragonY) {
        int startCol = dragonX / 48;
        int startRow = dragonY / 48;
        boolean[][] seen = new boolean[map.mapWidth][map.mapHeight];
        ArrayDeque<int[]> pending = new ArrayDeque<>();
        pending.add(new int[] {startCol, startRow});
        seen[startCol][startRow] = true;
        int[] best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        while (!pending.isEmpty()) {
            int[] cell = pending.removeFirst();
            double distance = Math.hypot(cell[0] * 48 + 24 - dragonX,
                    cell[1] * 48 + 24 - dragonY);
            if (distance >= 4 * 48 && distance <= 7 * 48
                    && distance < bestDistance) {
                best = cell;
                bestDistance = distance;
            }
            for (int[] offset : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int col = cell[0] + offset[0];
                int row = cell[1] + offset[1];
                if (col < 0 || row < 0 || col >= map.mapWidth || row >= map.mapHeight
                        || seen[col][row] || !isDeepWater(map.groundLayer[col][row])) {
                    continue;
                }
                seen[col][row] = true;
                pending.addLast(new int[] {col, row});
            }
        }
        if (best == null) {
            throw new AssertionError("No same-lake player target was found.");
        }
        return best;
    }

    private static int[] findWaterCellInRegion(MapData map, int targetWaterRegion) {
        boolean[][] seen = new boolean[map.mapWidth][map.mapHeight];
        int regionId = 0;
        for (int col = 0; col < map.mapWidth; col++) {
            for (int row = 0; row < map.mapHeight; row++) {
                if (!isDeepWater(map.groundLayer[col][row]) || seen[col][row]) {
                    continue;
                }
                int[] seed = {col, row};
                ArrayDeque<int[]> pending = new ArrayDeque<>();
                pending.add(seed);
                seen[col][row] = true;
                while (!pending.isEmpty()) {
                    int[] cell = pending.removeFirst();
                    for (int[] offset : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int x = cell[0] + offset[0];
                        int y = cell[1] + offset[1];
                        if (x < 0 || y < 0 || x >= map.mapWidth || y >= map.mapHeight
                                || seen[x][y] || !isDeepWater(map.groundLayer[x][y])) {
                            continue;
                        }
                        seen[x][y] = true;
                        pending.addLast(new int[] {x, y});
                    }
                }
                if (regionId == targetWaterRegion) {
                    return seed;
                }
                regionId++;
            }
        }
        throw new AssertionError("No second water body was found.");
    }

    private static boolean isDeepWater(int tile) {
        return tile == 2 || tile == 18;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
