package inventory;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

import main.GamePanel;

/**
 * Minecraft-inspired Inventory & Character Equipment UI.
 *
 * Displays:
 *  - Character preview in the centre with visual representation of equipped items
 *  - 6 Equipment slots: HEAD, CHEST, LEGS, FEET, MAIN_HAND, OFF_HAND
 *  - Character statistics summary (Defense, Damage)
 *  - 27-slot main inventory grid (3 rows × 9 columns)
 *  - 9-slot hotbar row (1 row × 9 columns)
 *  - Floating held item during drag-and-drop
 *  - Slot compatibility validation and visual tooltips
 */
public class InventoryUI {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int SLOT_SIZE   = 42;
    private static final int PADDING     = 4;
    private static final int CELL        = SLOT_SIZE + PADDING; // 46 px

    private static final int TITLE_H     = 24;
    private static final int SECTION_GAP = 10;

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color BG            = new Color(26,  26,  30,  235);
    private static final Color BORDER        = new Color(75,  75,  85,  255);
    private static final Color SLOT_EMPTY    = new Color(45,  45,  52,  230);
    private static final Color SLOT_FILLED   = new Color(55,  50,  40,  230);
    private static final Color SLOT_EQUIP_BG = new Color(38,  42,  55,  230);
    private static final Color SLOT_HOVER    = new Color(110, 110, 70,  240);
    private static final Color SLOT_HELD     = new Color(80,  130, 80,  240);
    private static final Color SLOT_SELECT   = new Color(230, 190, 60,  255);
    private static final Color BORDER_EQUIP  = new Color(80,  110, 160, 255);
    private static final Color TITLE_COLOR   = new Color(210, 215, 225);
    private static final Color TEXT_MUTED    = new Color(135, 140, 150);
    private static final Color TOOLTIP_BG    = new Color(15,  16,  20,  235);
    private static final Color TOOLTIP_FG    = new Color(230, 230, 230);
    private static final Color WARN_COLOR    = new Color(240, 80,  80);

    // ── Fonts ─────────────────────────────────────────────────────────────────
    private static final Font TITLE_FONT     = new Font("Arial", Font.BOLD,  13);
    private static final Font LABEL_FONT     = new Font("Arial", Font.BOLD,  11);
    private static final Font SLOT_TAG_FONT  = new Font("Arial", Font.BOLD,  8);
    private static final Font STACK_FONT     = new Font("Arial", Font.BOLD,  10);
    private static final Font TOOLTIP_FONT   = new Font("Arial", Font.PLAIN, 11);
    private static final Font STAT_FONT      = new Font("Arial", Font.PLAIN, 11);
    private static final Font STAT_BOLD      = new Font("Arial", Font.BOLD,  11);

    // ── References ───────────────────────────────────────────────────────────
    private final Inventory        inventory;
    private final Hotbar           hotbar;
    private final EquipmentManager equipmentManager;
    private final GamePanel        gamePanel;

    // ── Panel geometry (computed in draw) ────────────────────────────────────
    private int panelX, panelY, panelW, panelH;
    private final Map<EquipmentSlot, Rectangle> equipSlotBounds = new EnumMap<>(EquipmentSlot.class);

    // ── Mouse & Drag State ────────────────────────────────────────────────────
    private int mouseX = -1, mouseY = -1;
    private Item heldItem = null;
    private int heldFromSlot = -1;                     // >= 0 if from inventory/hotbar
    private EquipmentSlot heldFromEquipSlot = null;    // non-null if from equipment

    // Hover state
    private int hoverSlot = -1;
    private EquipmentSlot hoverEquipSlot = null;

    // Feedback status message
    private String statusMessage = null;
    private int statusTimer = 0;

    // ── Visibility ────────────────────────────────────────────────────
    private boolean visible = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public InventoryUI(Inventory inventory, Hotbar hotbar,
                       EquipmentManager equipmentManager, GamePanel gamePanel) {
        this.inventory        = inventory;
        this.hotbar           = hotbar;
        this.equipmentManager = equipmentManager;
        this.gamePanel        = gamePanel;

        registerMouseListeners(gamePanel);
    }

    // ── Visibility ────────────────────────────────────────────────────────────

    public boolean isVisible() { return visible; }

    public void show() { visible = true; }

    public void hide() {
        visible = false;
        returnHeldItem();
    }

    public void toggle() {
        if (visible) hide(); else show();
    }

    // ── Safe return of held item ──────────────────────────────────────────────

    private void returnHeldItem() {
        if (heldItem == null) return;

        if (heldFromEquipSlot != null) {
            if (equipmentManager.getEquipped(heldFromEquipSlot) == null
                    && equipmentManager.canEquip(heldItem, heldFromEquipSlot)) {
                equipmentManager.equip(heldFromEquipSlot, heldItem);
            } else if (!inventory.addItem(heldItem)) {
                equipmentManager.equip(heldFromEquipSlot, heldItem);
            }
        } else if (heldFromSlot >= 0) {
            if (inventory.getSlot(heldFromSlot) == null) {
                inventory.setSlot(heldFromSlot, heldItem);
            } else if (!inventory.addItem(heldItem)) {
                inventory.setSlot(heldFromSlot, heldItem);
            }
        } else {
            inventory.addItem(heldItem);
        }

        heldItem          = null;
        heldFromSlot      = -1;
        heldFromEquipSlot = null;
    }

    // ── Main Draw ─────────────────────────────────────────────────────────────

    public void draw(Graphics2D g2, int screenW, int screenH) {
        if (!visible) return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (statusTimer > 0) {
            statusTimer--;
            if (statusTimer == 0) statusMessage = null;
        }

        // ── Dimensions ────────────────────────────────────────────────────────
        panelW = 470;
        panelH = 456;
        panelX = (screenW - panelW) / 2;
        panelY = (screenH - panelH) / 2;

        // ── Main window background ────────────────────────────────────────────
        g2.setColor(BG);
        g2.fillRoundRect(panelX, panelY, panelW, panelH, 14, 14);
        g2.setColor(BORDER);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(panelX, panelY, panelW, panelH, 14, 14);
        g2.setStroke(new BasicStroke(1f));

        // ── Top Title ─────────────────────────────────────────────────────────
        g2.setFont(TITLE_FONT);
        g2.setColor(TITLE_COLOR);
        g2.drawString("Character & Equipment", panelX + 24, panelY + 22);

        if (statusMessage != null) {
            g2.setFont(LABEL_FONT);
            g2.setColor(WARN_COLOR);
            g2.drawString(statusMessage, panelX + 200, panelY + 22);
        }

        // ── Section 1: Equipment & Character Preview ──────────────────────────
        int equipTopY = panelY + 30;
        int colLeftX  = panelX + 24;

        // Draw Left Equipment Column (HEAD, CHEST, LEGS, FEET)
        drawEquipmentSlot(g2, EquipmentSlot.HEAD,  colLeftX, equipTopY + 0 * CELL, "HEAD");
        drawEquipmentSlot(g2, EquipmentSlot.CHEST, colLeftX, equipTopY + 1 * CELL, "CHEST");
        drawEquipmentSlot(g2, EquipmentSlot.LEGS,  colLeftX, equipTopY + 2 * CELL, "LEGS");
        drawEquipmentSlot(g2, EquipmentSlot.FEET,  colLeftX, equipTopY + 3 * CELL, "FEET");

        // Center Preview Box
        int previewX = colLeftX + CELL + 12;
        int previewY = equipTopY;
        int previewW = 140;
        int previewH = 4 * CELL - PADDING;
        drawCharacterPreview(g2, previewX, previewY, previewW, previewH);

        // Draw Right Equipment Column (MAIN_HAND, OFF_HAND)
        int colRightX = previewX + previewW + 12;
        drawEquipmentSlot(g2, EquipmentSlot.MAIN_HAND, colRightX, equipTopY + 16,        "MAIN");
        drawEquipmentSlot(g2, EquipmentSlot.OFF_HAND,  colRightX, equipTopY + 16 + CELL + 8, "OFF");

        // Character Stats Card
        int statsX = colRightX + CELL + 12;
        int statsY = equipTopY + 4;
        int statsW = panelW - (statsX - panelX) - 24;
        int statsH = previewH - 8;
        drawStatsBox(g2, statsX, statsY, statsW, statsH);

        // ── Divider line ──────────────────────────────────────────────────────
        int divY = equipTopY + previewH + SECTION_GAP;
        g2.setColor(new Color(60, 60, 70));
        g2.drawLine(panelX + 20, divY, panelX + panelW - 20, divY);

        // ── Section 2: Main Inventory (3 rows × 9 cols) ──────────────────────
        int invGridX = panelX + (panelW - (Inventory.COLS * CELL - PADDING)) / 2;
        int invGridY = divY + 18;

        g2.setFont(LABEL_FONT);
        g2.setColor(TITLE_COLOR);
        g2.drawString("Inventory", invGridX, invGridY - 5);

        drawSlotGrid(g2, invGridX, invGridY, Inventory.MAIN_ROWS, Inventory.COLS, 0);

        // ── Section 3: Hotbar (1 row × 9 cols) ───────────────────────────────
        int hotbarY = invGridY + Inventory.MAIN_ROWS * CELL - PADDING + 18;

        g2.setFont(LABEL_FONT);
        g2.setColor(TITLE_COLOR);
        g2.drawString("Hotbar", invGridX, hotbarY - 5);

        drawSlotGrid(g2, invGridX, hotbarY, 1, Inventory.COLS, Inventory.HOTBAR_OFFSET);

        // ── Bottom Hint ───────────────────────────────────────────────────────
        g2.setFont(TOOLTIP_FONT);
        g2.setColor(TEXT_MUTED);
        String hint = "TAB / ESC to Close  |  Left-Click to Drag & Equip";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(hint, panelX + (panelW - fm.stringWidth(hint)) / 2, panelY + panelH - 8);

        // ── Held item floating at cursor ──────────────────────────────────────
        if (heldItem != null) {
            drawHeldItem(g2);
        }

        // ── Tooltip ───────────────────────────────────────────────────────────
        if (heldItem == null) {
            if (hoverEquipSlot != null) {
                Item item = equipmentManager.getEquipped(hoverEquipSlot);
                if (item != null) {
                    drawTooltip(g2, item, mouseX, mouseY, screenW, screenH);
                } else {
                    drawEmptySlotTooltip(g2, hoverEquipSlot, mouseX, mouseY, screenW, screenH);
                }
            } else if (hoverSlot >= 0) {
                Item item = inventory.getSlot(hoverSlot);
                if (item != null) {
                    drawTooltip(g2, item, mouseX, mouseY, screenW, screenH);
                }
            }
        }
    }

    // ── Equipment Slot Drawing ────────────────────────────────────────────────

    private void drawEquipmentSlot(Graphics2D g2, EquipmentSlot slot, int sx, int sy, String tag) {
        equipSlotBounds.put(slot, new Rectangle(sx, sy, SLOT_SIZE, SLOT_SIZE));

        Item item = equipmentManager.getEquipped(slot);
        boolean isHovered = (hoverEquipSlot == slot);
        boolean isHeldSrc = (heldFromEquipSlot == slot);

        // Background
        Color bg = (item != null && !isHeldSrc) ? SLOT_FILLED : SLOT_EQUIP_BG;
        if (isHeldSrc) bg = SLOT_HELD;
        if (isHovered) bg = SLOT_HOVER;

        g2.setColor(bg);
        g2.fillRoundRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 6, 6);

        // Border
        g2.setColor(isHovered ? SLOT_SELECT : BORDER_EQUIP);
        g2.setStroke(new BasicStroke(isHovered ? 2f : 1.5f));
        g2.drawRoundRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 6, 6);
        g2.setStroke(new BasicStroke(1f));

        if (item != null && !isHeldSrc) {
            // Draw item icon
            drawItemInSlot(g2, item, sx, sy);
        } else {
            // Draw empty watermark tag
            g2.setFont(SLOT_TAG_FONT);
            g2.setColor(new Color(110, 125, 145));
            FontMetrics fm = g2.getFontMetrics();
            int tx = sx + (SLOT_SIZE - fm.stringWidth(tag)) / 2;
            int ty = sy + SLOT_SIZE / 2 + 3;
            g2.drawString(tag, tx, ty);
        }
    }

    // ── Character Preview Drawing ─────────────────────────────────────────────

    private void drawCharacterPreview(Graphics2D g2, int px, int py, int pw, int ph) {
        // Preview Box Background
        g2.setColor(new Color(18, 20, 25));
        g2.fillRoundRect(px, py, pw, ph, 8, 8);
        g2.setColor(new Color(60, 68, 80));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(px, py, pw, ph, 8, 8);
        g2.setStroke(new BasicStroke(1f));

        // Pedestal / Floor shadow
        g2.setColor(new Color(10, 11, 15, 220));
        g2.fillOval(px + pw / 2 - 36, py + ph - 24, 72, 16);

        // Character Sprite & Layered Equipment
        int charSize = 72;
        int charX = px + (pw - charSize) / 2;
        int charY = py + (ph - charSize) / 2 - 4;

        if (gamePanel != null && gamePanel.player != null) {
            BufferedImage sprite = gamePanel.player.getPlayerPreviewImage();
            if (sprite != null) {
                Object oldInterp = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(sprite, charX, charY, charSize, charSize, null);
                if (oldInterp != null) g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
            }

            // Draw synchronized armor overlays (Helmet, Chestplate, Leggings, Boots)
            gamePanel.player.drawArmorOverlays(g2, charX, charY, charSize, animation.Direction.DOWN);

            // Draw synchronized offhand shield
            gamePanel.player.drawOffHand(g2, charX, charY, charSize, animation.Direction.DOWN);

            // Draw equipped weapon anchored to hand
            Weapon weapon = (equipmentManager.getEquipped(EquipmentSlot.MAIN_HAND) instanceof Weapon w) ? w : null;
            if (weapon != null && gamePanel.player.getWeaponRenderer() != null) {
                gamePanel.player.getWeaponRenderer().drawHandAnchored(g2, charX, charY, charSize,
                        animation.AnimationState.IDLE, animation.Direction.DOWN, 0, weapon, "FRONT");
            }
        }

        // Label at top of preview
        g2.setFont(SLOT_TAG_FONT);
        g2.setColor(new Color(150, 165, 185));
        g2.drawString("PREVIEW", px + 8, py + 14);
    }

    // ── Stats Box Drawing ─────────────────────────────────────────────────────

    private void drawStatsBox(Graphics2D g2, int sx, int sy, int sw, int sh) {
        g2.setColor(new Color(20, 22, 28));
        g2.fillRoundRect(sx, sy, sw, sh, 8, 8);
        g2.setColor(new Color(55, 60, 72));
        g2.drawRoundRect(sx, sy, sw, sh, 8, 8);

        g2.setFont(LABEL_FONT);
        g2.setColor(new Color(200, 210, 230));
        g2.drawString("HERO STATS", sx + 10, sy + 18);

        int def = equipmentManager.getTotalDefense();
        Item mainHand = equipmentManager.getEquipped(EquipmentSlot.MAIN_HAND);

        int lineY = sy + 40;
        g2.setFont(STAT_FONT);
        g2.setColor(new Color(160, 190, 230));
        g2.drawString("Total Defense:", sx + 10, lineY);
        g2.setFont(STAT_BOLD);
        g2.setColor(new Color(100, 220, 120));
        g2.drawString("+" + def, sx + 10, lineY + 16);

        lineY += 42;
        g2.setFont(STAT_FONT);
        g2.setColor(new Color(230, 180, 160));
        g2.drawString("Attack Weapon:", sx + 10, lineY);

        g2.setFont(STAT_BOLD);
        if (mainHand instanceof Weapon w) {
            g2.setColor(new Color(240, 120, 100));
            g2.drawString(w.name, sx + 10, lineY + 16);
            g2.setFont(STAT_FONT);
            g2.setColor(new Color(220, 200, 140));
            g2.drawString("DMG: " + w.damage + " | SPD: " + w.attackSpeed, sx + 10, lineY + 30);
        } else {
            g2.setColor(TEXT_MUTED);
            g2.drawString("Bare Hands", sx + 10, lineY + 16);
            g2.setFont(STAT_FONT);
            g2.drawString("DMG: 25 (Fists)", sx + 10, lineY + 30);
        }
    }

    // ── Slot Grid Drawing (Inventory & Hotbar) ─────────────────────────────────

    private void drawSlotGrid(Graphics2D g2, int originX, int originY,
                              int rows, int cols, int startSlot) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int slotIndex = startSlot + row * cols + col;
                int sx = originX + col * CELL;
                int sy = originY + row * CELL;

                Item item = inventory.getSlot(slotIndex);

                boolean isHover    = (slotIndex == hoverSlot);
                boolean isSelected = (slotIndex >= Inventory.HOTBAR_OFFSET)
                                     && (slotIndex - Inventory.HOTBAR_OFFSET == hotbar.getSelectedSlot());
                boolean isHeldSrc  = (slotIndex == heldFromSlot);

                Color bg = SLOT_EMPTY;
                if (item != null && !isHeldSrc) bg = SLOT_FILLED;
                if (isHeldSrc) bg = SLOT_HELD;
                if (isHover)   bg = SLOT_HOVER;

                g2.setColor(bg);
                g2.fillRoundRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 6, 6);

                // Border
                g2.setStroke(isSelected ? new BasicStroke(2.5f) : new BasicStroke(1.5f));
                g2.setColor(isSelected ? SLOT_SELECT : isHover ? Color.LIGHT_GRAY : BORDER);
                g2.drawRoundRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 6, 6);
                g2.setStroke(new BasicStroke(1f));

                // Item icon
                if (item != null && !isHeldSrc) {
                    drawItemInSlot(g2, item, sx, sy);
                }

                // Hotbar slot number (1–9)
                if (startSlot == Inventory.HOTBAR_OFFSET) {
                    g2.setFont(SLOT_TAG_FONT);
                    g2.setColor(isSelected ? SLOT_SELECT : TEXT_MUTED);
                    g2.drawString(String.valueOf(col + 1), sx + 3, sy + 10);
                }
            }
        }
    }

    private void drawItemInSlot(Graphics2D g2, Item item, int sx, int sy) {
        int iconSize = SLOT_SIZE - 6;
        int iconX    = sx + 3;
        int iconY    = sy + 3;

        BufferedImage icon = item.getIcon(iconSize);
        g2.drawImage(icon, iconX, iconY, iconSize, iconSize, null);

        if (item.stackSize > 1) {
            String cnt = String.valueOf(item.stackSize);
            g2.setFont(STACK_FONT);
            FontMetrics fm = g2.getFontMetrics();
            int tx = sx + SLOT_SIZE - fm.stringWidth(cnt) - 3;
            int ty = sy + SLOT_SIZE - 3;
            g2.setColor(Color.BLACK);
            g2.drawString(cnt, tx + 1, ty + 1);
            g2.setColor(Color.WHITE);
            g2.drawString(cnt, tx, ty);
        }
    }

    private void drawHeldItem(Graphics2D g2) {
        int iconSize = SLOT_SIZE - 4;
        BufferedImage icon = heldItem.getIcon(iconSize);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
        g2.drawImage(icon, mouseX - iconSize / 2, mouseY - iconSize / 2, iconSize, iconSize, null);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }

    // ── Tooltips ──────────────────────────────────────────────────────────────

    private void drawTooltip(Graphics2D g2, Item item,
                              int mx, int my, int screenW, int screenH) {
        g2.setFont(TOOLTIP_FONT);
        FontMetrics fm = g2.getFontMetrics();

        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(item.name);
        lines.add(item.description);
        lines.add("Type: " + item.type.name());

        if (item instanceof Weapon w) {
            lines.add("Damage: " + w.damage + "  |  Speed: " + w.attackSpeed);
            lines.add("Class: " + w.weaponClass + "  |  Slot: MAIN HAND");
        } else if (item instanceof Armor a) {
            lines.add("Defense: +" + a.defense + "  |  Slot: " + a.equipSlot);
        }

        int tw = 0;
        for (String line : lines) tw = Math.max(tw, fm.stringWidth(line));
        tw += 16;
        int th = fm.getHeight() * lines.size() + 12;

        int tx = mx + 12;
        int ty = my - th / 2;

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
            if (i == 0)      g2.setColor(Color.WHITE);
            else if (i == 2) g2.setColor(new Color(160, 200, 255));
            else if (i >= 3) g2.setColor(new Color(255, 200, 120));
            else             g2.setColor(TOOLTIP_FG);
            g2.drawString(line, tx + 8, ty + lineH * (i + 1));
        }
    }

    private void drawEmptySlotTooltip(Graphics2D g2, EquipmentSlot slot,
                                      int mx, int my, int screenW, int screenH) {
        String msg = switch (slot) {
            case HEAD      -> "Head Slot: Accepts helmets";
            case CHEST     -> "Chest Slot: Accepts chestplates";
            case LEGS      -> "Legs Slot: Accepts leggings";
            case FEET      -> "Feet Slot: Accepts boots";
            case MAIN_HAND -> "Main Hand Slot: Accepts swords & weapons";
            case OFF_HAND  -> "Off Hand Slot: Accepts shields";
            default        -> "Equipment Slot";
        };

        g2.setFont(TOOLTIP_FONT);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(msg) + 16;
        int th = fm.getHeight() + 8;
        int tx = mx + 12;
        int ty = my - th / 2;
        if (tx + tw > screenW) tx = mx - tw - 6;

        g2.setColor(TOOLTIP_BG);
        g2.fillRoundRect(tx, ty, tw, th, 6, 6);
        g2.setColor(BORDER_EQUIP);
        g2.drawRoundRect(tx, ty, tw, th, 6, 6);
        g2.setColor(new Color(180, 210, 240));
        g2.drawString(msg, tx + 8, ty + fm.getAscent() + 4);
    }

    // ── Mouse Handling ────────────────────────────────────────────────────────

    private void registerMouseListeners(GamePanel panel) {
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                panel.requestFocusInWindow();
                if (!visible) return;
                handleMousePress(e.getX(), e.getY(), e.getButton());
            }
        });

        panel.addMouseMotionListener(new MouseMotionAdapter() {
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
        });
    }

    private void handleMousePress(int mx, int my, int button) {
        EquipmentSlot clickedEquip = getEquipmentSlotAt(mx, my);
        int clickedInv = getSlotAt(mx, my);

        if (button == MouseEvent.BUTTON1) {
            if (heldItem == null) {
                // ── Picking up an item ────────────────────────────────────────
                if (clickedEquip != null) {
                    Item item = equipmentManager.getEquipped(clickedEquip);
                    if (item != null) {
                        heldItem          = equipmentManager.unequip(clickedEquip);
                        heldFromEquipSlot = clickedEquip;
                        heldFromSlot      = -1;
                    }
                } else if (clickedInv >= 0) {
                    Item item = inventory.getSlot(clickedInv);
                    if (item != null) {
                        heldItem          = item;
                        heldFromSlot      = clickedInv;
                        heldFromEquipSlot = null;
                        inventory.setSlot(clickedInv, null);
                    }
                }
            } else {
                // ── Dropping / Equipping / Swapping held item ──────────────────
                if (clickedEquip != null) {
                    // Try to equip to clicked equipment slot
                    if (equipmentManager.canEquip(heldItem, clickedEquip)) {
                        Item previous = equipmentManager.equip(clickedEquip, heldItem);
                        if (heldFromSlot >= 0) {
                            inventory.setSlot(heldFromSlot, previous);
                            heldItem          = null;
                            heldFromSlot      = -1;
                            heldFromEquipSlot = null;
                        } else if (heldFromEquipSlot != null) {
                            if (previous == null) {
                                heldItem          = null;
                                heldFromEquipSlot = null;
                            } else if (equipmentManager.canEquip(previous, heldFromEquipSlot)) {
                                equipmentManager.equip(heldFromEquipSlot, previous);
                                heldItem          = null;
                                heldFromEquipSlot = null;
                            } else {
                                if (inventory.addItem(previous)) {
                                    heldItem          = null;
                                    heldFromEquipSlot = null;
                                } else {
                                    heldItem = previous;
                                }
                            }
                        }
                    } else {
                        // Incompatible equipment slot rejection
                        statusMessage = "Cannot equip " + heldItem.name + " in " + clickedEquip + " slot!";
                        statusTimer = 90;
                    }
                } else if (clickedInv >= 0) {
                    // Placing into inventory or hotbar slot
                    Item targetItem = inventory.getSlot(clickedInv);

                    if (heldFromEquipSlot != null) {
                        // Came from equipment slot
                        if (targetItem != null && equipmentManager.canEquip(targetItem, heldFromEquipSlot)) {
                            // Swap compatible equipment
                            equipmentManager.equip(heldFromEquipSlot, targetItem);
                            inventory.setSlot(clickedInv, heldItem);
                            heldItem          = null;
                            heldFromEquipSlot = null;
                        } else if (targetItem == null) {
                            // Unequip to empty slot
                            inventory.setSlot(clickedInv, heldItem);
                            heldItem          = null;
                            heldFromEquipSlot = null;
                        } else {
                            // Target has non-compatible item: place here, hold target
                            inventory.setSlot(clickedInv, heldItem);
                            heldItem          = targetItem;
                            heldFromSlot      = clickedInv;
                            heldFromEquipSlot = null;
                        }
                    } else {
                        // Normal inventory-to-inventory swap
                        inventory.setSlot(clickedInv, heldItem);
                        heldItem     = targetItem;
                        heldFromSlot = (heldItem != null) ? clickedInv : -1;
                    }
                } else {
                    // Clicked outside valid slots: return item safely
                    returnHeldItem();
                }
            }
        } else if (button == MouseEvent.BUTTON3) {
            // Right-click: cancel drag & drop, return held item
            returnHeldItem();
        }
    }

    // ── Hit-testing ───────────────────────────────────────────────────────────

    private EquipmentSlot getEquipmentSlotAt(int px, int py) {
        for (Map.Entry<EquipmentSlot, Rectangle> entry : equipSlotBounds.entrySet()) {
            if (entry.getValue().contains(px, py)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private int getSlotAt(int px, int py) {
        int invGridX = panelX + (panelW - (Inventory.COLS * CELL - PADDING)) / 2;
        int divY = panelY + 30 + 4 * CELL - PADDING + SECTION_GAP;
        int invGridY = divY + 18;

        int slot = slotHit(px, py, invGridX, invGridY, Inventory.MAIN_ROWS, Inventory.COLS, 0);
        if (slot >= 0) return slot;

        int hotbarY = invGridY + Inventory.MAIN_ROWS * CELL - PADDING + 18;
        return slotHit(px, py, invGridX, hotbarY, 1, Inventory.COLS, Inventory.HOTBAR_OFFSET);
    }

    private int slotHit(int px, int py, int originX, int originY, int rows, int cols, int startSlot) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int sx = originX + c * CELL;
                int sy = originY + r * CELL;
                if (px >= sx && px <= sx + SLOT_SIZE && py >= sy && py <= sy + SLOT_SIZE) {
                    return startSlot + r * cols + c;
                }
            }
        }
        return -1;
    }

    private void updateHoverSlot() {
        hoverEquipSlot = getEquipmentSlotAt(mouseX, mouseY);
        if (hoverEquipSlot != null) {
            hoverSlot = -1;
        } else {
            hoverSlot = getSlotAt(mouseX, mouseY);
        }
    }

    // ── Hotbar-only Draw (Always on screen during gameplay) ───────────────────

    public void drawHotbar(Graphics2D g2, int screenW, int screenH) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int barW = Inventory.COLS * CELL - PADDING + PADDING * 2;
        int barH = SLOT_SIZE + PADDING * 2;
        int barX = (screenW - barW) / 2;
        int barY = screenH - barH - 8;

        g2.setColor(new Color(26, 26, 32, 210));
        g2.fillRoundRect(barX, barY, barW, barH, 10, 10);
        g2.setColor(BORDER);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(barX, barY, barW, barH, 10, 10);
        g2.setStroke(new BasicStroke(1f));

        int originX = barX + PADDING;
        int originY = barY + PADDING;

        for (int col = 0; col < Inventory.COLS; col++) {
            int sx = originX + col * CELL;
            int sy = originY;
            boolean isSelected = (col == hotbar.getSelectedSlot());
            Item item = inventory.getHotbarSlot(col);

            g2.setColor(item != null ? SLOT_FILLED : SLOT_EMPTY);
            g2.fillRoundRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 6, 6);

            g2.setStroke(isSelected ? new BasicStroke(2.5f) : new BasicStroke(1.5f));
            g2.setColor(isSelected ? SLOT_SELECT : BORDER);
            g2.drawRoundRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 6, 6);
            g2.setStroke(new BasicStroke(1f));

            if (item != null) drawItemInSlot(g2, item, sx, sy);

            g2.setFont(SLOT_TAG_FONT);
            g2.setColor(isSelected ? SLOT_SELECT : TEXT_MUTED);
            g2.drawString(String.valueOf(col + 1), sx + 3, sy + 10);
        }
    }
}
