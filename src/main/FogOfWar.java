package main;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Per-tile exploration state for the fog-of-war system.
 *
 * States:
 *   UNEXPLORED (0) — tile never seen; rendered with a heavy dark overlay.
 *   EXPLORED   (1) — tile previously seen; rendered with a medium dark overlay.
 *   VISIBLE    (2) — tile currently within the player's sight radius; overlay
 *                    is zero inside the clear zone and graduates outward.
 *
 * Radial gradient fog (three zones):
 *   Zone A  — dist ≤ 9 tiles from player: fully clear (alpha = 0).
 *   Zone B  — 9 < dist ≤ 15 tiles: light fog linearly ramping 0 → 100.
 *   Zone C  — dist > 15 tiles: use stored exploration state.
 *              UNEXPLORED → alpha 190 (heavy but not solid black).
 *              EXPLORED   → alpha 140 (medium overlay, terrain clearly visible).
 *
 * The state array is [col][row] (column-major), matching the TileManager convention.
 * Call reset() on each level transition to clear all state.
 *
 * Performance notes:
 *   • DIST_SQ_TO_ALPHA is a static lookup table — no sqrt per frame.
 *   • FOG_COLORS is a pre-allocated Color array in steps of 5 — no new Color() per frame.
 *   • drawOverlay() allocates nothing.
 */
public class FogOfWar {

    public static final int UNEXPLORED = 0;
    public static final int EXPLORED   = 1;
    public static final int VISIBLE    = 2;

    // =========================================================================
    // Fog zone parameters
    // =========================================================================

    /** Clear-vision radius in tiles (Zone A). */
    public static final int DEFAULT_RADIUS = 9;

    /** Light-fog outer boundary in tiles (Zone B ends here). */
    private static final double ZONE_B_OUTER = 15.0;

    /** Alpha applied to UNEXPLORED tiles outside Zone B (0–255). Never 255. */
    private static final int ALPHA_UNEXPLORED = 190;

    /** Alpha applied to EXPLORED tiles outside Zone B (0–255). */
    private static final int ALPHA_EXPLORED   = 140;

    // =========================================================================
    // Static lookup table — built once, never reallocated
    // =========================================================================

    /**
     * Maps distSq (distance-squared in tiles) → overlay alpha [0..200].
     * Index is clamped to MAX_DIST_SQ before lookup.
     * Only covers Zones A and B; Zone C uses the fixed state-based alphas above.
     */
    private static final int MAX_DIST_SQ = 700; // ~26.4 tiles
    private static final int[] DIST_SQ_TO_ALPHA = new int[MAX_DIST_SQ + 1];

    static {
        double zoneAOuter = DEFAULT_RADIUS;           // 9.0
        double zoneBRange = ZONE_B_OUTER - zoneAOuter; // 6.0
        for (int dSq = 0; dSq <= MAX_DIST_SQ; dSq++) {
            double dist = Math.sqrt(dSq);
            if (dist <= zoneAOuter) {
                DIST_SQ_TO_ALPHA[dSq] = 0;
            } else if (dist <= ZONE_B_OUTER) {
                // Linear ramp 0 → 100 across Zone B
                DIST_SQ_TO_ALPHA[dSq] = (int) ((dist - zoneAOuter) / zoneBRange * 100.0);
            } else {
                // Beyond Zone B — flag as 255 so caller knows to use state-based alpha
                DIST_SQ_TO_ALPHA[dSq] = 255;
            }
        }
    }

    // =========================================================================
    // Pre-allocated Color objects — steps of 5, covers alpha 0..200
    // =========================================================================

    /** FOG_COLORS[i] == new Color(0,0,0, i*5).  Covers alpha 0..200 in 41 entries. */
    private static final int COLOR_STEP = 5;
    private static final Color[] FOG_COLORS;

    static {
        int maxAlpha = 200;
        int buckets  = maxAlpha / COLOR_STEP + 1; // 41
        FOG_COLORS = new Color[buckets];
        for (int i = 0; i < buckets; i++) {
            FOG_COLORS[i] = new Color(0, 0, 0, i * COLOR_STEP);
        }
    }

    // Fixed pre-allocated Color objects for the UNEXPLORED and EXPLORED alphas
    private static final Color COLOR_UNEXPLORED = new Color(0, 0, 0, ALPHA_UNEXPLORED);
    private static final Color COLOR_EXPLORED   = new Color(0, 0, 0, ALPHA_EXPLORED);

    // =========================================================================
    // Instance state
    // =========================================================================

    private int[][] tileState;   // [col][row]
    private int cols;            // current effective columns (updated by resize())
    private int rows;            // current effective rows (updated by resize())
    // effectiveCols/effectiveRows are the same as cols/rows — kept as aliases for resize()
    private int effectiveCols;
    private int effectiveRows;
    private int visibilityRadius;

    /** Player position (tile coordinates) — updated every frame by update(). */
    private int playerCol;
    private int playerRow;

    // =========================================================================
    // Construction
    // =========================================================================

    public FogOfWar(int cols, int rows) {
        this.cols             = cols;
        this.rows             = rows;
        this.effectiveCols    = cols;
        this.effectiveRows    = rows;
        this.visibilityRadius = DEFAULT_RADIUS;
        this.tileState        = new int[cols][rows];
        // All tiles start UNEXPLORED (int default = 0)
    }

    // =========================================================================
    // Per-frame update
    // =========================================================================

    /**
     * Updates visibility around the player each frame.
     * Phase 1: demote all VISIBLE tiles to EXPLORED.
     * Phase 2: mark the circle around the player as VISIBLE.
     *
     * @param pCol current player tile column
     * @param pRow current player tile row
     */
    public void update(int pCol, int pRow) {
        this.playerCol = pCol;
        this.playerRow = pRow;

        int r   = visibilityRadius;
        int rSq = r * r;

        // Phase 1 — demote previously visible cells in a slightly larger sweep area
        int sweepR = r + 1;
        for (int c = pCol - sweepR; c <= pCol + sweepR; c++) {
            for (int row = pRow - sweepR; row <= pRow + sweepR; row++) {
                if (c >= 0 && c < effectiveCols && row >= 0 && row < effectiveRows) {
                    if (tileState[c][row] == VISIBLE) tileState[c][row] = EXPLORED;
                }
            }
        }

        // Phase 2 — mark the circle as VISIBLE
        for (int c = pCol - r; c <= pCol + r; c++) {
            for (int row = pRow - r; row <= pRow + r; row++) {
                if (c >= 0 && c < effectiveCols && row >= 0 && row < effectiveRows) {
                    int dc = c - pCol;
                    int dr = row - pRow;
                    if (dc * dc + dr * dr <= rSq) {
                        tileState[c][row] = VISIBLE;
                    }
                }
            }
        }
    }

    // =========================================================================
    // Query
    // =========================================================================

    /**
     * Returns the exploration state of a tile.
     * Out-of-bounds queries return UNEXPLORED.
     */
    public int getState(int col, int row) {
        if (col < 0 || col >= effectiveCols || row < 0 || row >= effectiveRows) return UNEXPLORED;
        return tileState[col][row];
    }

    // =========================================================================
    // Drawing
    // =========================================================================

    /**
     * Draws the radial-gradient fog overlay for one tile.
     * Called from TileManager.draw() immediately after drawing each tile image.
     * Allocates no objects per call.
     *
     * @param g2      the Graphics2D context
     * @param screenX tile screen X (px)
     * @param screenY tile screen Y (px)
     * @param tileSize tile size in px
     * @param col     tile column (world space)
     * @param row     tile row (world space)
     */
    public void drawOverlay(Graphics2D g2, int screenX, int screenY,
                             int tileSize, int col, int row) {
        // Compute squared distance from player in tile units
        int dc     = col - playerCol;
        int dr     = row - playerRow;
        int distSq = dc * dc + dr * dr;

        int alpha;
        if (distSq <= MAX_DIST_SQ) {
            alpha = DIST_SQ_TO_ALPHA[distSq];
            if (alpha == 255) {
                // Beyond Zone B — fall back to state-based alpha
                alpha = (getState(col, row) == EXPLORED) ? ALPHA_EXPLORED : ALPHA_UNEXPLORED;
            }
        } else {
            // Very far from player — use state-based alpha
            alpha = (getState(col, row) == EXPLORED) ? ALPHA_EXPLORED : ALPHA_UNEXPLORED;
        }

        if (alpha <= 0) return; // Zone A — fully clear, nothing to draw

        // Select pre-allocated Color — quantise to nearest COLOR_STEP bucket
        Color overlay;
        if (alpha >= ALPHA_UNEXPLORED) {
            overlay = COLOR_UNEXPLORED;
        } else if (alpha >= ALPHA_EXPLORED) {
            overlay = COLOR_EXPLORED;
        } else {
            // Zone B gradient — pick nearest bucket
            int bucket = Math.min(FOG_COLORS.length - 1, (alpha + COLOR_STEP / 2) / COLOR_STEP);
            overlay = FOG_COLORS[bucket];
        }

        g2.setColor(overlay);
        g2.fillRect(screenX, screenY, tileSize, tileSize);
    }

    // =========================================================================
    // Level transition & resize
    // =========================================================================

    /** Resets all tiles to UNEXPLORED. Call on every level transition. */
    public void reset() {
        for (int c = 0; c < effectiveCols; c++) {
            for (int r = 0; r < effectiveRows; r++) {
                tileState[c][r] = UNEXPLORED;
            }
        }
        playerCol = 0;
        playerRow = 0;
    }

    /**
     * Resizes the fog-of-war array to match a newly loaded map.
     * All tiles are reset to UNEXPLORED.
     * Called by GamePanel.applyMapDimensions() after a level transition.
     */
    public void resize(int newCols, int newRows) {
        effectiveCols = newCols;
        effectiveRows = newRows;
        tileState = new int[newCols][newRows];
        playerCol = 0;
        playerRow = 0;
    }

    // =========================================================================
    // Configuration
    // =========================================================================

    public void setVisibilityRadius(int radius) {
        this.visibilityRadius = Math.max(1, radius);
    }

    public int getVisibilityRadius() { return visibilityRadius; }
}
