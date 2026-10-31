package deadend.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

/**
 * Radial fog-of-war system.
 *
 * Three visibility states per tile:
 *   UNEXPLORED — never seen; heavy dark overlay (alpha ~0.75)
 *   EXPLORED   — seen before; medium overlay (alpha ~0.55)
 *   VISIBLE    — currently in range; clear + smooth radial fade at edges
 *
 * drawFogOverlay() is called AFTER rendering tiles, BEFORE entities.
 * Uses a reusable Pixmap updated each frame when the player moves.
 */
public class FogOfWar {

    public static final int UNEXPLORED = 0;
    public static final int EXPLORED   = 1;
    public static final int VISIBLE    = 2;

    private static final int CLEAR_RADIUS  = 9;   // tiles fully visible
    private static final int FADE_RADIUS   = 14;  // tiles at max Zone-B fog

    private int cols, rows;
    private int[][] state;

    private int playerCol, playerRow;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public FogOfWar(int cols, int rows) {
        resize(cols, rows);
    }

    public void resize(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        this.state = new int[cols][rows];
        this.playerCol = 0;
        this.playerRow = 0;
    }

    public void reset() {
        for (int c = 0; c < cols; c++)
            for (int r = 0; r < rows; r++)
                state[c][r] = UNEXPLORED;
        playerCol = 0;
        playerRow = 0;
    }

    // -------------------------------------------------------------------------
    // Per-frame update
    // -------------------------------------------------------------------------

    public void update(int pCol, int pRow) {
        playerCol = pCol;
        playerRow = pRow;

        int rSq = CLEAR_RADIUS * CLEAR_RADIUS;
        // Demote VISIBLE → EXPLORED in the old ring
        int sweep = CLEAR_RADIUS + 1;
        for (int c = pCol - sweep; c <= pCol + sweep; c++)
            for (int r = pRow - sweep; r <= pRow + sweep; r++)
                if (inBounds(c, r) && state[c][r] == VISIBLE) state[c][r] = EXPLORED;

        // Mark circle as VISIBLE
        for (int c = pCol - CLEAR_RADIUS; c <= pCol + CLEAR_RADIUS; c++) {
            for (int r = pRow - CLEAR_RADIUS; r <= pRow + CLEAR_RADIUS; r++) {
                if (!inBounds(c, r)) continue;
                int dc = c - pCol, dr = r - pRow;
                if (dc * dc + dr * dr <= rSq) state[c][r] = VISIBLE;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    public int getState(int col, int row) {
        if (!inBounds(col, row)) return UNEXPLORED;
        return state[col][row];
    }

    // -------------------------------------------------------------------------
    // Rendering — per-tile fog overlay
    // -------------------------------------------------------------------------

    /**
     * Draws the fog overlay for a single tile using the ShapeRenderer.
     * Call between shapeRenderer.begin(Filled) and shapeRenderer.end().
     * Render in world coordinates.
     */
    public void drawTileFog(ShapeRenderer sr, int col, int row, int tilePx) {
        int dc = col - playerCol;
        int dr = row - playerRow;
        double dist = Math.sqrt(dc * dc + dr * dr);

        float alpha;
        int s = getState(col, row);
        if (dist <= CLEAR_RADIUS) {
            alpha = 0f;                     // fully clear
        } else if (dist <= FADE_RADIUS) {
            float t = (float)((dist - CLEAR_RADIUS) / (FADE_RADIUS - CLEAR_RADIUS));
            float maxAlpha = (s == EXPLORED) ? 0.55f : 0.75f;
            alpha = t * maxAlpha;
        } else {
            alpha = (s == EXPLORED) ? 0.55f : 0.75f;
        }

        if (alpha <= 0.01f) return;

        sr.setColor(0f, 0f, 0f, alpha);
        sr.rect(col * tilePx, row * tilePx, tilePx, tilePx);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean inBounds(int c, int r) {
        return c >= 0 && c < cols && r >= 0 && r < rows;
    }

    public int getClearRadius()  { return CLEAR_RADIUS; }
    public int getFadeRadius()   { return FADE_RADIUS; }
    public int getPlayerCol()    { return playerCol; }
    public int getPlayerRow()    { return playerRow; }
}
