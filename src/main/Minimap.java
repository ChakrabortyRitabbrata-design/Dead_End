package main;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.List;

import entities.Monster;
import tile.MapData;

/**
 * Renders two minimap modes:
 *
 *  HUD minimap  — 180×180 px square, top-right corner, shows the 25×25-tile
 *                 area centred on the player.  Player marker is dynamic (not
 *                 always centre) when near world edges.
 *
 *  Full-screen  — opened by pressing M or clicking the minimap.  Shows the
 *                 entire level with zoom (0.5×–4×) and click-drag panning.
 *                 Opening freezes gameplay (GamePanel.update() short-circuits).
 *
 * Fog-of-war colour policy (full map AND HUD):
 *   VISIBLE    → full tile colour.
 *   EXPLORED   → tile colour at 60 % brightness.
 *   UNEXPLORED → tile colour at 25 % brightness (faintly visible — never black).
 *
 * Dimmed colours are pre-cached at construction time (20 tile types × 2 states
 * = 40 Color objects) so neither the HUD nor the full-map loop allocates on the
 * hot path.
 */
public class Minimap {

    // -------------------------------------------------------------------------
    // HUD constants
    // -------------------------------------------------------------------------
    private static final int MINIMAP_PX      = 180;
    private static final int PREFERRED_TILES = 25;
    private static final int INSET           = 10;   // px from screen edge

    // -------------------------------------------------------------------------
    // Per-frame window state (recomputed each frame)
    // -------------------------------------------------------------------------
    private int startCol, startRow, endCol, endRow;
    private int effectiveCols, effectiveRows;
    private float scaleX, scaleY;

    // -------------------------------------------------------------------------
    // Full-screen map state
    // -------------------------------------------------------------------------
    private float  fullZoom   = 1.0f;
    private float  fullPanX   = 0f;
    private float  fullPanY   = 0f;
    private int    lastDragX  = -1;
    private int    lastDragY  = -1;

    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 4.0f;

    // -------------------------------------------------------------------------
    // Pre-cached tile colours by [tileId][fogState]
    // fogState: 0=UNEXPLORED, 1=EXPLORED, 2=VISIBLE
    // Built once in buildColorCache(); rebuilt on loadLevel().
    // -------------------------------------------------------------------------
    private static final int MAX_TILE_IDS = 20;
    private Color[][] cachedColors; // [tileId][0..2]

    // -------------------------------------------------------------------------
    // Reference
    // -------------------------------------------------------------------------
    private final GamePanel gp;

    // =========================================================================
    // Construction
    // =========================================================================

    public Minimap(GamePanel gp) {
        this.gp = gp;
        buildColorCache();
        registerInputListeners();
    }

    /**
     * Pre-computes dimmed colour variants for every tile type.
     * Call once at startup and again whenever tile definitions change.
     *
     *   VISIBLE    (state 2) → full tile colour (minimapColor)
     *   EXPLORED   (state 1) → 60 % brightness
     *   UNEXPLORED (state 0) → 25 % brightness  (faint, never pure black)
     */
    public void buildColorCache() {
        cachedColors = new Color[MAX_TILE_IDS][3];
        for (int id = 0; id < MAX_TILE_IDS; id++) {
            Color base;
            if (gp.tileM != null && gp.tileM.tile != null && gp.tileM.tile[id] != null
                    && gp.tileM.tile[id].minimapColor != null) {
                base = gp.tileM.tile[id].minimapColor;
            } else {
                base = new Color(0x5D, 0xA8, 0x32); // default grass fallback
            }
            cachedColors[id][FogOfWar.VISIBLE]    = base;
            cachedColors[id][FogOfWar.EXPLORED]   = dim(base, 0.60);
            cachedColors[id][FogOfWar.UNEXPLORED] = dim(base, 0.25);
        }
    }

    /** Returns a darkened copy of {@code base} at the given brightness fraction [0..1]. */
    private static Color dim(Color base, double fraction) {
        int r = (int) Math.round(base.getRed()   * fraction);
        int g = (int) Math.round(base.getGreen() * fraction);
        int b = (int) Math.round(base.getBlue()  * fraction);
        // Clamp to valid range; minimum 1 so no channel is ever pure zero on dark tiles
        r = Math.max(1, Math.min(255, r));
        g = Math.max(1, Math.min(255, g));
        b = Math.max(1, Math.min(255, b));
        return new Color(r, g, b);
    }

    /** Register wheel + drag listeners on the parent GamePanel. */
    private void registerInputListeners() {
        gp.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (gp.gameState == GamePanel.GameState.FULL_MAP) {
                    applyZoom((float) -e.getPreciseWheelRotation() * 0.15f);
                }
            }
        });

        gp.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (gp.gameState == GamePanel.GameState.FULL_MAP) {
                    applyPan(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                lastDragX = -1;
                lastDragY = -1;
            }
        });
    }

    // =========================================================================
    // HUD minimap
    // =========================================================================

    public void drawHUD(Graphics2D g2) {
        int playerCol = gp.player.worldX / gp.tileSize;
        int playerRow = gp.player.worldY / gp.tileSize;

        computeWindow(playerCol, playerRow);

        int originX = gp.screenWidth  - MINIMAP_PX - INSET;
        int originY = INSET;

        // --- Tile grid ---
        for (int c = startCol; c < endCol; c++) {
            for (int r = startRow; r < endRow; r++) {
                Color col = tileColor(c, r);
                float px = (c - startCol) * scaleX;
                float py = (r - startRow) * scaleY;
                int drawW = (int) Math.ceil(scaleX);
                int drawH = (int) Math.ceil(scaleY);
                g2.setColor(col);
                g2.fillRect(originX + (int) px, originY + (int) py, drawW, drawH);
            }
        }

        // --- Enemy markers (only if the tile is VISIBLE) ---
        g2.setColor(Color.RED);
        for (Monster m : gp.monsters) {
            int mc = m.worldX / gp.tileSize;
            int mr = m.worldY / gp.tileSize;
            if (gp.fogOfWar.getState(mc, mr) == FogOfWar.VISIBLE) {
                float mx = (mc - startCol) * scaleX;
                float my = (mr - startRow) * scaleY;
                if (mx >= 0 && mx < MINIMAP_PX && my >= 0 && my < MINIMAP_PX) {
                    g2.fillRect(originX + (int) mx - 1, originY + (int) my - 1, 3, 3);
                }
            }
        }

        for(var gate:gp.tileM.currentMap.gates) {
            int c=gate.col(),r=gate.row();
            if(c<startCol||c>=endCol||r<startRow||r>=endRow||gp.fogOfWar.getState(c,r)==FogOfWar.UNEXPLORED) continue;
            g2.setColor(gp.levelM.progression.unlocked(gate.id())?Color.CYAN:new Color(0xdab67c));
            g2.fillRect(originX+(int)((c-startCol)*scaleX)-2,originY+(int)((r-startRow)*scaleY)-2,5,5);
        }
        for(var location:gp.tileM.currentMap.landmarks) {
            int c=location.col(),r=location.row();
            if(c<startCol||c>=endCol||r<startRow||r>=endRow||gp.fogOfWar.getState(c,r)==FogOfWar.UNEXPLORED) continue;
            g2.setColor(new Color(0xd9d0b3));g2.drawRect(originX+(int)((c-startCol)*scaleX)-2,originY+(int)((r-startRow)*scaleY)-2,4,4);
        }
        // --- Player marker ---
        float markerX = (playerCol - startCol) * scaleX;
        float markerY = (playerRow - startRow) * scaleY;
        g2.setColor(Color.WHITE);
        g2.fillRect(originX + (int) (markerX - 2), originY + (int) (markerY - 2), 5, 5);

        // --- Border ---
        g2.setColor(new Color(80, 80, 80));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRect(originX - 1, originY - 1, MINIMAP_PX + 2, MINIMAP_PX + 2);
        g2.setStroke(new BasicStroke(1f));
    }

    /**
     * Computes the visible tile window centred on the player, clamped to world bounds.
     * Works correctly for any world size ≥ 1 tile.
     */
    private void computeWindow(int playerCol, int playerRow) {
        effectiveCols = Math.min(PREFERRED_TILES, gp.maxWorldCol);
        effectiveRows = Math.min(PREFERRED_TILES, gp.maxWorldRow);

        scaleX = (float) MINIMAP_PX / effectiveCols;
        scaleY = (float) MINIMAP_PX / effectiveRows;

        int halfC = effectiveCols / 2;
        int halfR = effectiveRows / 2;

        startCol = Math.max(0, Math.min(playerCol - halfC, gp.maxWorldCol - effectiveCols));
        startRow = Math.max(0, Math.min(playerRow - halfR, gp.maxWorldRow - effectiveRows));
        endCol   = startCol + effectiveCols;
        endRow   = startRow + effectiveRows;
    }

    // =========================================================================
    // Full-screen map
    // =========================================================================

    public void drawFullMap(Graphics2D g2) {
        if(gp.tileM.currentMap.authoredWorld) {
            gp.worldRenderer.drawAtlas(g2,fullZoom,fullPanX,fullPanY);return;
        }
        // Dark (but not fully opaque) background — terrain shows through the overlay
        // The map itself is drawn below, so a darker background is fine here.
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // Base px-per-tile at zoom=1 so the whole world fits on screen
        float baseScale = Math.min(
                (float)(gp.screenWidth  - 60) / gp.maxWorldCol,
                (float)(gp.screenHeight - 60) / gp.maxWorldRow);
        float scale = baseScale * fullZoom;

        // Centred map origin before panning
        float mapW = gp.maxWorldCol * scale;
        float mapH = gp.maxWorldRow * scale;
        float originX = (gp.screenWidth  - mapW) / 2f + fullPanX;
        float originY = (gp.screenHeight - mapH) / 2f + fullPanY;

        // --- Tile grid — always show terrain; fog state only affects brightness ---
        for (int c = 0; c < gp.maxWorldCol; c++) {
            for (int r = 0; r < gp.maxWorldRow; r++) {
                int drawX = (int)(originX + c * scale);
                int drawY = (int)(originY + r * scale);
                int drawW = Math.max(1, (int) Math.ceil(scale));
                int drawH = Math.max(1, (int) Math.ceil(scale));

                // Clip to screen
                if (drawX + drawW < 0 || drawX > gp.screenWidth
                        || drawY + drawH < 0 || drawY > gp.screenHeight) continue;

                g2.setColor(tileColor(c, r));
                g2.fillRect(drawX, drawY, drawW, drawH);
            }
        }

        // --- Named location outlines ---
        if (gp.tileM.currentMap != null) {
            g2.setFont(new Font("Arial", Font.BOLD, 9));
            for (MapData.LocationRecord loc : gp.tileM.currentMap.locations) {
                int lx = (int)(originX + loc.col()   * scale);
                int ly = (int)(originY + loc.row()   * scale);
                int lw = (int)(loc.width()  * scale);
                int lh = (int)(loc.height() * scale);
                g2.setColor(new Color(255, 220, 100, 80));
                g2.fillRect(lx, ly, lw, lh);
                g2.setColor(new Color(255, 220, 100, 180));
                g2.drawRect(lx, ly, lw, lh);
                if (lw > 20) {
                    g2.setColor(Color.WHITE);
                    FontMetrics fm = g2.getFontMetrics();
                    String name = loc.name();
                    if (fm.stringWidth(name) > lw) name = name.substring(0, Math.max(1, lw / fm.charWidth('A')));
                    g2.drawString(name, lx + 2, ly + fm.getAscent() + 1);
                }
            }
        }

        // --- Enemy markers (only VISIBLE enemies) ---
        g2.setColor(new Color(255, 60, 60));
        for (Monster m : gp.monsters) {
            int mc = m.worldX / gp.tileSize;
            int mr = m.worldY / gp.tileSize;
            if (gp.fogOfWar.getState(mc, mr) == FogOfWar.VISIBLE) {
                int mx = (int)(originX + mc * scale);
                int my = (int)(originY + mr * scale);
                g2.fillOval(mx - 2, my - 2, 5, 5);
            }
        }

        // --- Player marker (pulsing white dot) ---
        int playerCol = gp.player.worldX / gp.tileSize;
        int playerRow = gp.player.worldY / gp.tileSize;
        int px = (int)(originX + playerCol * scale);
        int py = (int)(originY + playerRow * scale);
        double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0);
        int markerR = (int)(5 + pulse * 3);
        g2.setColor(new Color(255, 255, 255, 200));
        g2.fillOval(px - markerR, py - markerR, markerR * 2, markerR * 2);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(px - markerR - 1, py - markerR - 1, markerR * 2 + 2, markerR * 2 + 2);
        g2.setStroke(new BasicStroke(1f));

        // --- Controls hint ---
        g2.setFont(new Font("Arial", Font.PLAIN, 11));
        g2.setColor(new Color(200, 200, 200, 200));
        String hint = "M / ESC — close   Scroll — zoom   Drag — pan";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(hint, (gp.screenWidth - fm.stringWidth(hint)) / 2, gp.screenHeight - 8);
    }

    // =========================================================================
    // Full-map interaction
    // =========================================================================

    public void applyZoom(float delta) {
        fullZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, fullZoom + delta));
    }

    public void applyPan(int mouseX, int mouseY) {
        if (lastDragX >= 0) {
            fullPanX += mouseX - lastDragX;
            fullPanY += mouseY - lastDragY;
        }
        lastDragX = mouseX;
        lastDragY = mouseY;
    }

    public void resetDrag() {
        lastDragX = -1;
        lastDragY = -1;
    }

    /** Resets zoom and pan to defaults. Called each time the full map is opened. */
    public void resetFullMapView() {
        fullZoom = 1.0f;
        fullPanX = 0f;
        fullPanY = 0f;
        lastDragX = -1;
        lastDragY = -1;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Returns the minimap display colour for a tile, taking fog state into account.
     * Uses the pre-cached dimmed-colour array — no allocation.
     *
     *   VISIBLE    → full palette colour
     *   EXPLORED   → 60 % brightness
     *   UNEXPLORED → 25 % brightness  (faintly visible — never black)
     */
    private Color tileColor(int col, int row) {
        int tileId   = gp.tileM.mapTilenum[col][row];
        int fogState = gp.fogOfWar.getState(col, row);
        if(gp.tileM.currentMap.authoredWorld) {
            if(fogState==FogOfWar.UNEXPLORED) return new Color(0x17222b);
            Color base=WorldRenderer.color(gp.levelM.currentLevel,tileId);
            return fogState==FogOfWar.VISIBLE?base:dim(base,0.6);
        }

        // Guard against out-of-range tile IDs
        if (tileId < 0 || tileId >= MAX_TILE_IDS) tileId = 0;
        // Clamp fogState to valid range
        if (fogState < 0 || fogState > 2) fogState = FogOfWar.UNEXPLORED;

        return cachedColors[tileId][fogState];
    }

    // =========================================================================
    // Minimap click detection
    // =========================================================================

    /**
     * Returns true if the given screen-space point is inside the HUD minimap rectangle.
     * Used by KeyHandler to decide whether a mouse click should open the full map.
     */
    public boolean containsPoint(int screenX, int screenY) {
        int originX = gp.screenWidth  - MINIMAP_PX - INSET;
        int originY = INSET;
        return screenX >= originX && screenX <= originX + MINIMAP_PX
            && screenY >= originY && screenY <= originY + MINIMAP_PX;
    }
}
