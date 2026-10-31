package inventory;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Draws the Minecraft-inspired inventory panel and handles all mouse
 * interaction (pick-up, drop, swap).
 *
 * The panel is rendered directly on the GamePanel's Graphics2D context –
 * no separate Swing component is used.
 *
 * Coordinate notes
 * ─────────────────
 *   panelX / panelY  – top-left corner of the inventory window on screen
 *   slotSize         – pixel size of each inventory slot (icon + border)
 *   PADDING          – gap between slots
 */
public class InventoryUI {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int SLOT_SIZE   = 50;   // px per slot (icon area)
    private static final int PADDING     = 6;    // gap between slots
    private static final int CELL        = SLOT_SIZE + PADDING; // effective cell pitch

    private static final int TITLE_H     = 36;  // height of the section labels
    private static final int SECTION_GAP = 14;  // gap between main grid and hotbar

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color BG           = new Color(30,  30,  30,  210);
    private static final Color SLOT_EMPTY   = new Color(55,  55,  55,  230);
    private static final Color SLOT_FILLED  = new Color(70,  55,  30,  230);
    private static final Color SLOT_HOVER   = new Color(100, 100, 60,  240);
    private static final Color SLOT_HELD    = new Color(90,  140, 90,  240);
    private static final Color SLOT_SELECT  = new Color(220, 180, 60,  255); // hotbar selected
    private static final Color BORDER       = new Color(80,  80,  80,  255);
    private static final Color BORDER_SEL   = new Color(240, 200, 80,  255);
    private static final Color TITLE_COLOR  = new Color(200, 200, 200);
    private static final Color TOOLTIP_BG   = new Color(15,  15,  15,  220);
    private static final Color TOOLTIP_FG   = new Color(230, 230, 230);

    // ── Fonts ─────────────────────────────────────────────────────────────────
    private static final Font TITLE_FONT   = new Font("Arial", Font.BOLD,  14);
    private static final Font LABEL_FONT   = new Font("Arial", Font.PLAIN, 11);
    private static final Font STACK_FONT   = new Font("Arial", Font.BOLD,  10);
    private static final Font TOOLTIP_FONT = new Font("Arial", Font.PLAIN, 12);
    private static final Font HOTBAR_NUM_F = new Font("Arial", Font.BOLD,  10);

    // ── References ───────────────────────────────────────────────────────────
    private final Inventory inventory;
    private final Hotbar    hotbar;
    private final JPanel    gamePanel;  // kept to re-request focus after mouse clicks

    // ── Panel geometry (set in draw based on screen size) ─────────────────────
    private int panelX, panelY, panelW, panelH;

    // ── Mouse state ───────────────────────────────────────────────────────────
    private int mouseX = -1, mouseY = -1;   // current cursor position
    private Item heldItem   = null;          // item "in hand" (being dragged)
    private int  heldFromSlot = -1;          // absolute slot index it came from

    // Hover slot
    private int hoverSlot = -1;             // absolute slot index under cursor

    // ── Visibility ────────────────────────────────────────────────────────────
    private boolean visible = false;

    // ── Registered listeners (kept so we can remove them later if needed) ─────
    private MouseAdapter      mouseAdapter;
    private MouseMotionAdapter motionAdapter;

    // ── Constructor ───────────────────────────────────────────────────────────

    public InventoryUI(Inventory inventory, Hotbar hotbar, JPanel gamePanel) {
        this.inventory = inventory;
        this.hotbar    = hotbar;
        this.gamePanel = gamePanel;
        registerMouseListeners(gamePanel);
    }

    // ── Visibility ────────────────────────────────────────────────────────────

    public boolean isVisible() { return visible; }

    public void show() { visible = true; }

    public void hide() {
        visible = false;
        // If player closes while holding an item, return it to its original slot
        if (heldItem != null) {
            inventory.setSlot(heldFromSlot, heldItem);
            heldItem    = null;
            heldFromSlot = -1;
        }
    }

    public void toggle() {
        if (visible) hide(); else show();
    }

    // ── Main draw ─────────────────────────────────────────────────────────────

    /**
     * Called every frame from GamePanel.paintComponent only when visible.
     *
     * @param g2          the game's Graphics2D context
     * @param screenW     GamePanel width  (pixels)
     * @param screenH     GamePanel height (pixels)
     */
    public void draw(Graphics2D g2, int screenW, int screenH) {
        if (!visible) return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ── Compute panel dimensions ──────────────────────────────────────────
        int gridW = Inventory.COLS * CELL - PADDING;   // width of slot grid
        panelW = gridW + PADDING * 3;                  // extra margin on sides

        // Heights: title + main grid + section-gap + hotbar-label + hotbar
        int mainGridH  = Inventory.MAIN_ROWS * CELL - PADDING;
        int hotbarH    = CELL - PADDING;
        panelH = PADDING + TITLE_H + mainGridH + SECTION_GAP
                + TITLE_H + hotbarH + PADDING * 2;

        // Centre the panel on screen
        panelX = (screenW - panelW) / 2;
        panelY = (screenH - panelH) / 2;

        // ── Background ────────────────────────────────────────────────────────
        g2.setColor(BG);
        g2.fillRoundRect(panelX, panelY, panelW, panelH, 16, 16);
        g2.setColor(BORDER);
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(panelX, panelY, panelW, panelH, 16, 16);
        g2.setStroke(new BasicStroke(1));

        // ── Title "Inventory" ─────────────────────────────────────────────────
        int cx = panelX + PADDING + PADDING / 2;   // left edge of slot grid
        int cy = panelY + PADDING;

        g2.setFont(TITLE_FONT);
        g2.setColor(TITLE_COLOR);
        g2.drawString("Inventory", cx, cy + 20);

        cy += TITLE_H;

        // ── Main grid (27 slots, 3 rows × 9 cols) ────────────────────────────
        drawSlotGrid(g2, cx, cy,
                     Inventory.MAIN_ROWS, Inventory.COLS,
                     0 /* startSlot */);

        cy += Inventory.MAIN_ROWS * CELL - PADDING + SECTION_GAP;

        // ── Hotbar label ──────────────────────────────────────────────────────
        g2.setFont(LABEL_FONT);
        g2.setColor(TITLE_COLOR);
        g2.drawString("Hotbar", cx, cy + 14);
        cy += TITLE_H;

        // ── Hotbar row (9 slots) ──────────────────────────────────────────────
        drawSlotGrid(g2, cx, cy,
                     1, Inventory.COLS,
                     Inventory.HOTBAR_OFFSET /* startSlot = 27 */);

        // ── Held item drawn at cursor ──────────────────────────────────────────
        if (heldItem != null) {
            drawHeldItem(g2);
        }

        // ── Tooltip ───────────────────────────────────────────────────────────
        if (hoverSlot >= 0 && heldItem == null) {
            Item hoveredItem = inventory.getSlot(hoverSlot);
            if (hoveredItem != null) {
                drawTooltip(g2, hoveredItem, mouseX, mouseY, screenW, screenH);
            }
        }

        // ── Close hint ────────────────────────────────────────────────────────
        g2.setFont(LABEL_FONT);
        g2.setColor(new Color(140, 140, 140));
        String hint = "TAB / ESC to close";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(hint,
                panelX + panelW - fm.stringWidth(hint) - PADDING * 2,
                panelY + panelH - PADDING / 2 - 4);
    }

    // ── Slot grid helper ──────────────────────────────────────────────────────

    private void drawSlotGrid(Graphics2D g2, int originX, int originY,
                               int rows, int cols, int startSlot) {

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {

                int slotIndex = startSlot + row * cols + col;
                int sx = originX + col * CELL;
                int sy = originY + row * CELL;

                Item item = inventory.getSlot(slotIndex);

                // Determine slot colour
                boolean isHover    = (slotIndex == hoverSlot);
                boolean isSelected = (slotIndex >= Inventory.HOTBAR_OFFSET)
                                     && (slotIndex - Inventory.HOTBAR_OFFSET
                                         == hotbar.getSelectedSlot());
                boolean isHeldSrc  = (slotIndex == heldFromSlot);

                Color bg = SLOT_EMPTY;
                if (item != null && !isHeldSrc) bg = SLOT_FILLED;
                if (isHeldSrc) bg = SLOT_HELD;
                if (isHover)   bg = SLOT_HOVER;

                g2.setColor(bg);
                g2.fillRoundRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 6, 6);

                // Border
                g2.setStroke(isSelected
                        ? new BasicStroke(2.5f)
                        : new BasicStroke(1.5f));
                g2.setColor(isSelected ? SLOT_SELECT
                                       : isHover ? Color.LIGHT_GRAY : BORDER);
                g2.drawRoundRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 6, 6);
                g2.setStroke(new BasicStroke(1));

                // Item icon
                if (item != null && !isHeldSrc) {
                    drawItemInSlot(g2, item, sx, sy);
                }

                // Hotbar slot number (1–9)
                if (startSlot == Inventory.HOTBAR_OFFSET) {
                    g2.setFont(HOTBAR_NUM_F);
                    g2.setColor(isSelected ? SLOT_SELECT : new Color(160, 160, 160));
                    g2.drawString(String.valueOf(col + 1), sx + 3, sy + 12);
                }
            }
        }
    }

    // ── Draw a single item inside its slot ────────────────────────────────────

    private void drawItemInSlot(Graphics2D g2, Item item, int sx, int sy) {
        int iconSize  = SLOT_SIZE - 8;
        int iconX     = sx + 4;
        int iconY     = sy + 4;

        BufferedImage icon = item.getIcon(iconSize);
        g2.drawImage(icon, iconX, iconY, iconSize, iconSize, null);

        // Stack count (bottom-right corner)
        if (item.stackSize > 1) {
            String cnt = String.valueOf(item.stackSize);
            g2.setFont(STACK_FONT);
            FontMetrics fm = g2.getFontMetrics();
            int tx = sx + SLOT_SIZE - fm.stringWidth(cnt) - 3;
            int ty = sy + SLOT_SIZE - 3;
            // Shadow
            g2.setColor(Color.BLACK);
            g2.drawString(cnt, tx + 1, ty + 1);
            g2.setColor(Color.WHITE);
            g2.drawString(cnt, tx, ty);
        }
    }

    // ── Draw held item floating under the cursor ──────────────────────────────

    private void drawHeldItem(Graphics2D g2) {
        int iconSize = SLOT_SIZE - 8;
        BufferedImage icon = heldItem.getIcon(iconSize);

        // Draw with slight transparency to show it's being carried
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
        g2.drawImage(icon,
                mouseX - iconSize / 2,
                mouseY - iconSize / 2,
                iconSize, iconSize, null);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    private void drawTooltip(Graphics2D g2, Item item,
                              int mx, int my, int screenW, int screenH) {
        g2.setFont(TOOLTIP_FONT);
        FontMetrics fm = g2.getFontMetrics();

        // Build lines based on item type
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(item.name);
        lines.add(item.description);
        lines.add("Type: " + item.type.name());

        // Extra stats for weapons
        if (item instanceof Weapon w) {
            lines.add("Damage: " + w.damage + "  |  Speed: " + w.attackSpeed);
            lines.add("Range: " + w.range + " px  |  Class: " + w.weaponClass);
        }

        int tw = 0;
        for (String line : lines) tw = Math.max(tw, fm.stringWidth(line));
        tw += 16;
        int th = fm.getHeight() * lines.size() + 10;

        int tx = mx + 14;
        int ty = my - th / 2;

        // Keep on screen
        if (tx + tw > screenW) tx = mx - tw - 6;
        if (ty < 0)            ty = 0;
        if (ty + th > screenH) ty = screenH - th;

        g2.setColor(TOOLTIP_BG);
        g2.fillRoundRect(tx, ty, tw, th, 8, 8);
        g2.setColor(BORDER);
        g2.drawRoundRect(tx, ty, tw, th, 8, 8);

        int lineH = fm.getHeight();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i == 0) {
                g2.setColor(Color.WHITE);  // name: white
            } else if (i == 2) {
                g2.setColor(new Color(160, 200, 255)); // type: blue
            } else if (i >= 3) {
                g2.setColor(new Color(255, 200, 120)); // weapon stats: gold
            } else {
                g2.setColor(TOOLTIP_FG);   // description: light grey
            }
            g2.drawString(line, tx + 8, ty + lineH * (i + 1));
        }
    }

    // ── Mouse listener registration ───────────────────────────────────────────

    private void registerMouseListeners(JPanel panel) {

        mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // ── FIX: Always return keyboard focus to the game panel so
                //         TAB / ESC / WASD keep working after a mouse click.
                gamePanel.requestFocusInWindow();

                if (!visible) return;
                handleMousePress(e.getX(), e.getY(), e.getButton());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // Not needed – we handle press-based pick/drop
            }
        };

        motionAdapter = new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                if (visible) updateHoverSlot();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                if (visible) updateHoverSlot();
            }
        };

        panel.addMouseListener(mouseAdapter);
        panel.addMouseMotionListener(motionAdapter);
    }

    // ── Mouse press handler ───────────────────────────────────────────────────

    private void handleMousePress(int mx, int my, int button) {
        int clickedSlot = getSlotAt(mx, my);

        if (button == MouseEvent.BUTTON1) {
            // Left click: pick up / place / swap
            if (heldItem == null) {
                // Pick up item from slot
                if (clickedSlot >= 0) {
                    Item slotItem = inventory.getSlot(clickedSlot);
                    if (slotItem != null) {
                        heldItem     = slotItem;
                        heldFromSlot = clickedSlot;
                        inventory.setSlot(clickedSlot, null); // remove from slot
                    }
                }
            } else {
                // Place held item
                if (clickedSlot >= 0) {
                    Item target = inventory.getSlot(clickedSlot);
                    // Swap: put target into heldFromSlot, place heldItem here
                    inventory.setSlot(clickedSlot, heldItem);
                    heldItem     = target;  // target might be null (empty drop)
                    heldFromSlot = (heldItem != null) ? clickedSlot : -1;
                } else {
                    // Clicked outside panel → return item
                    inventory.setSlot(heldFromSlot, heldItem);
                    heldItem     = null;
                    heldFromSlot = -1;
                }
            }
        } else if (button == MouseEvent.BUTTON3) {
            // Right click: cancel drag / return item
            if (heldItem != null) {
                inventory.setSlot(heldFromSlot, heldItem);
                heldItem     = null;
                heldFromSlot = -1;
            }
        }
    }

    // ── Slot hit-testing ──────────────────────────────────────────────────────

    /**
     * Returns the absolute slot index under point (px, py),
     * or -1 if the point is not over any slot.
     */
    private int getSlotAt(int px, int py) {
        // We need to know the grid origin – re-derive from panelX/Y
        int cx = panelX + PADDING + PADDING / 2;

        // ── Main grid ─────────────────────────────────────────────────────────
        int mainGridOriginY = panelY + PADDING + TITLE_H;
        int slot = slotHit(px, py, cx, mainGridOriginY,
                           Inventory.MAIN_ROWS, Inventory.COLS, 0);
        if (slot >= 0) return slot;

        // ── Hotbar ────────────────────────────────────────────────────────────
        int hotbarOriginY = mainGridOriginY
                + Inventory.MAIN_ROWS * CELL - PADDING
                + SECTION_GAP + TITLE_H;
        slot = slotHit(px, py, cx, hotbarOriginY,
                       1, Inventory.COLS, Inventory.HOTBAR_OFFSET);
        return slot;
    }

    private int slotHit(int px, int py,
                        int originX, int originY,
                        int rows, int cols, int startSlot) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int sx = originX + col * CELL;
                int sy = originY + row * CELL;
                if (px >= sx && px <= sx + SLOT_SIZE
                 && py >= sy && py <= sy + SLOT_SIZE) {
                    return startSlot + row * cols + col;
                }
            }
        }
        return -1;
    }

    // ── Hover tracking ────────────────────────────────────────────────────────

    private void updateHoverSlot() {
        hoverSlot = getSlotAt(mouseX, mouseY);
    }

    // ── Hotbar-only draw (always on screen, even when inventory is closed) ────

    /**
     * Draws just the hotbar bar at the bottom-centre of the screen.
     * Called every frame regardless of inventory visibility.
     */
    public void drawHotbar(Graphics2D g2, int screenW, int screenH) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        int barW = Inventory.COLS * CELL - PADDING + PADDING * 2;
        int barH = SLOT_SIZE + PADDING * 2;
        int barX = (screenW - barW) / 2;
        int barY = screenH - barH - 8;

        // Background
        g2.setColor(new Color(30, 30, 30, 200));
        g2.fillRoundRect(barX, barY, barW, barH, 10, 10);
        g2.setColor(BORDER);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(barX, barY, barW, barH, 10, 10);
        g2.setStroke(new BasicStroke(1));

        int originX = barX + PADDING;
        int originY = barY + PADDING;

        for (int col = 0; col < Inventory.COLS; col++) {
            int sx = originX + col * CELL;
            int sy = originY;
            boolean isSelected = (col == hotbar.getSelectedSlot());
            Item item = inventory.getHotbarSlot(col);

            // Slot background
            g2.setColor(item != null ? SLOT_FILLED : SLOT_EMPTY);
            g2.fillRoundRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 6, 6);

            // Border
            g2.setStroke(isSelected ? new BasicStroke(2.5f) : new BasicStroke(1.5f));
            g2.setColor(isSelected ? SLOT_SELECT : BORDER);
            g2.drawRoundRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 6, 6);
            g2.setStroke(new BasicStroke(1));

            // Item icon
            if (item != null) drawItemInSlot(g2, item, sx, sy);

            // Slot number
            g2.setFont(HOTBAR_NUM_F);
            g2.setColor(isSelected ? SLOT_SELECT : new Color(160, 160, 160));
            g2.drawString(String.valueOf(col + 1), sx + 3, sy + 12);
        }
    }
}
