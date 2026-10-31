package main;

import java.io.IOException;
import java.util.Iterator;

import entities.Dragon;
import entities.Monster;
import tile.MapData;

/**
 * Orchestrates level transitions and monitors progression conditions.
 *
 * Progression rules:
 *   Level 1 → 2  : score >= scoreThresholds[0]  (if > 0)
 *   Level 2 → 3  : score >= scoreThresholds[1]  (if > 0)
 *   Level 3 → END: dragon defeated ONLY — score alone cannot trigger GAME_COMPLETE
 *
 * Score thresholds are configurable at runtime; leave at 0 to disable score-driven
 * progression (useful during testing).
 */
public class LevelManager {

    private final GamePanel gp;

    public int currentLevel = 1;

    /**
     * Score required to leave each level.
     * Index 0 = threshold to leave Level 1, index 1 = Level 2.
     * Level 3 completion is never score-driven.
     * 0 = progression disabled for that level.
     */
    public int[] scoreThresholds = {0, 0};

    // Fade animation state
    private boolean inFade       = false;
    private int     fadeTicks    = 0;
    private static final int FADE_TICKS_HALF = 60; // ticks per half (out / in)
    private int targetLevel;
    public  int fadeAlpha        = 0;               // 0-255; read by GamePanel.paintComponent

    // =========================================================================
    // Construction
    // =========================================================================

    public LevelManager(GamePanel gp) {
        this.gp = gp;
    }

    // =========================================================================
    // Per-tick check
    // =========================================================================

    /** Called every game tick. Checks score-based progression. */
    public void checkProgression() {
        if (gp.gameState != GamePanel.GameState.PLAYING) return;
        if (currentLevel >= 3) return;   // Level 3 exits via onDragonDefeated() only

        int threshold = scoreThresholds[currentLevel - 1];
        if (threshold > 0 && gp.score >= threshold) {
            beginTransition(currentLevel + 1);
        }
    }

    /**
     * Called by Dragon when its death animation completes.
     * The only path to GAME_COMPLETE — score is irrelevant here.
     */
    public void onDragonDefeated() {
        if (currentLevel == 3) {
            gp.gameState = GamePanel.GameState.GAME_COMPLETE;
        }
    }

    // =========================================================================
    // Transition
    // =========================================================================

    public void beginTransition(int target) {
        if (inFade) return;
        inFade       = true;
        fadeTicks    = 0;
        targetLevel  = target;
        gp.gameState = GamePanel.GameState.LEVEL_TRANSITION;
    }

    /**
     * Advances the fade animation by one tick.
     * Call from GamePanel.update() when gameState == LEVEL_TRANSITION.
     */
    public void updateTransition() {
        fadeTicks++;

        if (fadeTicks <= FADE_TICKS_HALF) {
            // Fade out
            fadeAlpha = (int)((float) fadeTicks / FADE_TICKS_HALF * 255);
        } else if (fadeTicks == FADE_TICKS_HALF + 1) {
            // Midpoint — swap the level
            fadeAlpha = 255;
            completeTransition(targetLevel);
        } else {
            // Fade in
            int in = fadeTicks - FADE_TICKS_HALF - 1;
            fadeAlpha = 255 - (int)((float) in / FADE_TICKS_HALF * 255);
            if (fadeTicks >= FADE_TICKS_HALF * 2 + 1) {
                fadeAlpha    = 0;
                inFade       = false;
                gp.gameState = GamePanel.GameState.PLAYING;
            }
        }
    }

    private void completeTransition(int levelId) {
        // 1. Clear all monsters
        gp.monsters.clear();

        // 2. Deactivate dragon
        gp.dragonActive = false;

        // 3. Load new level
        try {
            gp.tileM.loadLevel(levelId);
        } catch (IOException e) {
            System.err.println("ERROR: Failed to load Level " + levelId + ": " + e.getMessage());
            gp.gameState = GamePanel.GameState.PLAYING;
            inFade = false;
            return;
        }
        currentLevel = levelId;

        // 4. Apply score threshold from map metadata (if configured there)
        MapData map = gp.tileM.currentMap;
        if (map.scoreThreshold > 0 && levelId - 1 < scoreThresholds.length) {
            scoreThresholds[levelId - 1] = map.scoreThreshold;
        }

        // 5. Reset fog
        gp.fogOfWar.reset();

        // 6. Reposition player to new spawn
        int[] spawn = map.playerSpawn;
        gp.player.worldX = spawn[0] * gp.tileSize;
        gp.player.worldY = spawn[1] * gp.tileSize;

        // 7. Reset player combat state
        gp.player.actionState = "IDLE";
        gp.player.combatCounter = 0;
        gp.player.combatFrameIndex = 0;

        // 8. Place objects
        gp.aSetter.setObject();

        // 9. Spawn enemies
        gp.spawnMonstersForLevel();

        // 10. Activate boss dragon if this level has one
        if (map.dragonSpawn != null) {
            if (gp.dragon == null) gp.dragon = new Dragon(gp);
            gp.dragon.activate(map.dragonSpawn[0], map.dragonSpawn[1]);
            gp.dragonActive = true;
        }

        System.out.println("LevelManager: transitioned to Level " + levelId
                + " (" + map.levelName + ")");
    }
}
