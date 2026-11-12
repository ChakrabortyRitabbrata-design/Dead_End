package entities;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Programmatic pixel-art sprite generator for Dead End!!
 * Generates production-ready sprite sheets:
 *  - Base character (4 dirs × 35 frames)
 *  - Equipment overlays (helmet, chestplate, leggings, boots, shield)
 *  - Weapon attack sprites (wooden sword, iron sword, axe)
 *  - Effect sprites (slash, impact)
 *
 * All sprites use a 64×64 frame with 2x pixel scale (logical 32×32 grid).
 * Character is ~25 logical pixels tall, centered, bottom-aligned.
 */
public class SpriteGenerator {

    // ── Frame / Scale ──────────────────────────────────────────────────────
    static final int F = 64;   // frame pixel size
    static final int S = 2;    // pixel scale: each logical px = 2×2 real px

    // ── Palette ────────────────────────────────────────────────────────────
    static final Color HAIR        = new Color(110, 60, 160);
    static final Color HAIR_DARK   = new Color(80, 40, 130);
    static final Color HAIR_LIGHT  = new Color(140, 80, 190);
    static final Color SKIN        = new Color(235, 190, 160);
    static final Color SKIN_SHAD   = new Color(205, 160, 135);
    static final Color EYE         = new Color(35, 25, 55);
    static final Color MOUTH       = new Color(190, 130, 110);
    static final Color SHIRT       = new Color(185, 40, 40);
    static final Color SHIRT_DARK  = new Color(145, 30, 30);
    static final Color SHIRT_LIGHT = new Color(210, 70, 60);
    static final Color CAPE        = new Color(160, 30, 30);
    static final Color CAPE_DARK   = new Color(120, 20, 20);
    static final Color PANTS       = new Color(50, 45, 58);
    static final Color PANTS_DARK  = new Color(35, 30, 42);
    static final Color BOOT        = new Color(120, 70, 30);
    static final Color BOOT_DARK   = new Color(85, 50, 20);
    static final Color OUTLINE     = new Color(28, 22, 32);

    // Equipment colours
    static final Color METAL       = new Color(170, 185, 205);
    static final Color METAL_DARK  = new Color(120, 140, 165);
    static final Color METAL_LIGHT = new Color(205, 218, 235);
    static final Color METAL_RIM   = new Color(85, 100, 125);
    static final Color GOLD        = new Color(220, 180, 55);
    static final Color SHIELD_BODY = new Color(155, 175, 200);
    static final Color SHIELD_RIM  = new Color(75, 95, 125);

    // Weapon colours
    static final Color WOOD_HANDLE = new Color(120, 70, 25);
    static final Color WOOD_DARK   = new Color(85, 50, 18);
    static final Color IRON_BLADE  = new Color(175, 190, 210);
    static final Color IRON_DARK   = new Color(130, 145, 165);
    static final Color STEEL_BLADE = new Color(200, 215, 235);
    static final Color AXE_HEAD    = new Color(185, 200, 218);

    // Effect colours
    static final Color SLASH_COL   = new Color(220, 235, 255, 200);
    static final Color IMPACT_COL  = new Color(255, 240, 180, 220);

    // ── Animation layout ───────────────────────────────────────────────────
    // Column counts per animation group
    static final int[] ANIM_FRAMES = {4, 6, 6, 4, 3, 6, 6}; // idle walk run jump hurt death revive
    static final int TOTAL_COLS = 35;
    static final String[] ANIM_NAMES = {"idle","walk","run","jump","hurt","death","revive"};

    // Direction indices: 0=Down 1=Left 2=Right 3=Up
    static final int DIR_D = 0, DIR_L = 1, DIR_R = 2, DIR_U = 3;

    // ── Body part base positions (logical coords, DOWN facing, neutral) ───
    // All positions relative to frame top-left in logical pixels (32×32)
    static final int HEAD_X = 12, HEAD_Y = 6, HEAD_W = 8, HEAD_H = 5;
    static final int HAIR_X = 10, HAIR_Y = 2, HAIR_W = 12, HAIR_H = 6;
    static final int BODY_X = 12, BODY_Y = 12, BODY_W = 8, BODY_H = 7;
    static final int LARM_X = 10, LARM_Y = 13, LARM_W = 2, LARM_H = 5;
    static final int RARM_X = 20, RARM_Y = 13, RARM_W = 2, RARM_H = 5;
    static final int LLEG_X = 13, LLEG_Y = 19, LLEG_W = 3, LLEG_H = 5;
    static final int RLEG_X = 16, RLEG_Y = 19, RLEG_W = 3, RLEG_H = 5;
    static final int LBOOT_X = 12, LBOOT_Y = 24, LBOOT_W = 4, LBOOT_H = 3;
    static final int RBOOT_X = 16, RBOOT_Y = 24, RBOOT_W = 4, RBOOT_H = 3;

    // ── Per-frame animation offsets ────────────────────────────────────────
    // {bodyBob, leftLegDx, rightLegDx, leftArmDy, rightArmDy}

    static final int[][] IDLE_OFF = {
        {0,0,0,0,0}, {0,0,0,0,0}, {-1,0,0,0,0}, {-1,0,0,0,0}
    };
    static final int[][] WALK_OFF = {
        {0, 0, 0, 0, 0},
        {0,-1, 1, 1,-1},
        {-1,-2, 2, 2,-2},
        {0, 0, 0, 0, 0},
        {0, 1,-1,-1, 1},
        {-1, 2,-2,-2, 2}
    };
    static final int[][] RUN_OFF = {
        {0, 0, 0, 0, 0},
        {-1,-2, 2, 2,-2},
        {-2,-3, 3, 3,-3},
        {0, 0, 0, 0, 0},
        {-1, 2,-2,-2, 2},
        {-2, 3,-3,-3, 3}
    };
    static final int[][] JUMP_OFF = {
        {2, 0, 0, 0, 0},   // crouch
        {-2, 0, 0,-1,-1},  // rise
        {-4, 0, 0,-2,-2},  // peak
        {-1, 0, 0, 0, 0}   // fall
    };
    static final int[][] HURT_OFF = {
        {0, 1, 1, 1, 1},
        {1, 2, 2, 2, 2},
        {0, 1, 1, 0, 0}
    };
    static final int[][] DEATH_OFF = {
        {0, 0, 0, 0, 0},
        {1, 0, 0, 1, 1},
        {3, 1, 1, 2, 2},
        {5, 2, 2, 3, 3},
        {7, 3, 3, 4, 4},
        {8, 3, 3, 4, 4}
    };
    static final int[][] REVIVE_OFF = {
        {8, 3, 3, 4, 4},
        {7, 3, 3, 3, 3},
        {5, 2, 2, 2, 2},
        {3, 1, 1, 1, 1},
        {1, 0, 0, 0, 0},
        {0, 0, 0, 0, 0}
    };

    static final int[][][] ALL_OFFSETS = {
        IDLE_OFF, WALK_OFF, RUN_OFF, JUMP_OFF, HURT_OFF, DEATH_OFF, REVIVE_OFF
    };

    // ── Weapon attack offsets (4 frames) ───────────────────────────────────
    // {bodyBob, leftLegDx, rightLegDx, leftArmDy, rightArmDy, weaponAngle}
    static final int[][] ATTACK_OFF = {
        {0, 0, 0, -2, -3, 0},   // wind-up
        {-1, 0, -1, 0, -1, 1},  // mid-swing
        {0, 0, 0, 1, 2, 2},     // full extension
        {0, 0, 0, 0, 0, 3}      // follow-through
    };

    // ════════════════════════════════════════════════════════════════════════
    // DRAWING HELPERS
    // ════════════════════════════════════════════════════════════════════════

    static void px(Graphics2D g, int lx, int ly, int lw, int lh, Color c) {
        g.setColor(c);
        g.fillRect(lx * S, ly * S, lw * S, lh * S);
    }

    static void px(Graphics2D g, int lx, int ly, int lw, int lh) {
        g.fillRect(lx * S, ly * S, lw * S, lh * S);
    }

    // ════════════════════════════════════════════════════════════════════════
    // CHARACTER DRAWING — DOWN DIRECTION
    // ════════════════════════════════════════════════════════════════════════

    static void drawCharDown(Graphics2D g, int[] off) {
        int bob = off[0]; int llx = off[1]; int rlx = off[2];
        int lay = off[3]; int ray = off[4];

        // Cape (behind everything)
        px(g, 13, 11+bob, 6, 9, CAPE);
        px(g, 12, 12+bob, 8, 7, CAPE_DARK);

        // Hair
        px(g, HAIR_X, HAIR_Y+bob, HAIR_W, HAIR_H, HAIR);
        px(g, 11, 2+bob, 2, 1, HAIR_LIGHT); // left spike
        px(g, 14, 1+bob, 2, 1, HAIR_LIGHT); // center spike
        px(g, 18, 2+bob, 2, 1, HAIR_LIGHT); // right spike
        px(g, 10, 4+bob, 1, 4, HAIR_DARK);  // left side hair
        px(g, 21, 4+bob, 1, 4, HAIR_DARK);  // right side hair

        // Face
        px(g, HEAD_X, HEAD_Y+bob, HEAD_W, HEAD_H, SKIN);
        px(g, 12, 7+bob, 1, 3, SKIN_SHAD);  // left shadow
        // Eyes
        px(g, 13, 8+bob, 2, 2, EYE);
        px(g, 17, 8+bob, 2, 2, EYE);
        // Mouth
        px(g, 15, 10+bob, 2, 1, MOUTH);

        // Body / Shirt
        px(g, BODY_X, BODY_Y+bob, BODY_W, BODY_H, SHIRT);
        px(g, 12, 12+bob, 8, 1, SHIRT_LIGHT); // collar highlight
        px(g, 15, 14+bob, 2, 3, SHIRT_DARK);  // center detail

        // Arms
        px(g, LARM_X, LARM_Y+bob+lay, LARM_W, LARM_H, SKIN);
        px(g, LARM_X, LARM_Y+bob+lay, LARM_W, 2, SHIRT); // sleeve
        px(g, RARM_X, RARM_Y+bob+ray, RARM_W, RARM_H, SKIN);
        px(g, RARM_X, RARM_Y+bob+ray, RARM_W, 2, SHIRT); // sleeve

        // Legs
        px(g, LLEG_X+llx, LLEG_Y, LLEG_W, LLEG_H, PANTS);
        px(g, RLEG_X+rlx, RLEG_Y, RLEG_W, RLEG_H, PANTS);
        px(g, LLEG_X+llx, LLEG_Y, LLEG_W, 1, PANTS_DARK); // waist
        px(g, RLEG_X+rlx, RLEG_Y, RLEG_W, 1, PANTS_DARK);

        // Boots
        px(g, LBOOT_X+llx, LBOOT_Y, LBOOT_W, LBOOT_H, BOOT);
        px(g, RBOOT_X+rlx, RBOOT_Y, RBOOT_W, RBOOT_H, BOOT);
        px(g, LBOOT_X+llx, LBOOT_Y+LBOOT_H-1, LBOOT_W, 1, BOOT_DARK);
        px(g, RBOOT_X+rlx, RBOOT_Y+RBOOT_H-1, RBOOT_W, 1, BOOT_DARK);
    }

    // ════════════════════════════════════════════════════════════════════════
    // CHARACTER DRAWING — UP DIRECTION
    // ════════════════════════════════════════════════════════════════════════

    static void drawCharUp(Graphics2D g, int[] off) {
        int bob = off[0]; int llx = off[1]; int rlx = off[2];
        int lay = off[3]; int ray = off[4];

        // Cape (fully visible from back)
        px(g, 11, 10+bob, 10, 12, CAPE);
        px(g, 10, 11+bob, 12, 10, CAPE_DARK);
        px(g, 14, 12+bob, 4, 8, CAPE); // lighter center stripe

        // Hair (back of head, more visible)
        px(g, HAIR_X, HAIR_Y+bob, HAIR_W, HAIR_H+2, HAIR);
        px(g, 11, 2+bob, 2, 1, HAIR_LIGHT);
        px(g, 14, 1+bob, 3, 1, HAIR_LIGHT);
        px(g, 19, 2+bob, 2, 1, HAIR_LIGHT);
        px(g, 10, 4+bob, 1, 5, HAIR_DARK);
        px(g, 21, 4+bob, 1, 5, HAIR_DARK);

        // Back of head (no face)
        px(g, HEAD_X, HEAD_Y+bob, HEAD_W, HEAD_H, HAIR);
        px(g, 13, 8+bob, 6, 3, HAIR_DARK); // back of head darker

        // Body / Shirt (back view)
        px(g, BODY_X, BODY_Y+bob, BODY_W, BODY_H, SHIRT_DARK);
        px(g, 15, 13+bob, 2, 5, SHIRT); // center seam

        // Arms
        px(g, LARM_X, LARM_Y+bob+lay, LARM_W, LARM_H, SKIN);
        px(g, LARM_X, LARM_Y+bob+lay, LARM_W, 2, SHIRT_DARK);
        px(g, RARM_X, RARM_Y+bob+ray, RARM_W, RARM_H, SKIN);
        px(g, RARM_X, RARM_Y+bob+ray, RARM_W, 2, SHIRT_DARK);

        // Legs
        px(g, LLEG_X+llx, LLEG_Y, LLEG_W, LLEG_H, PANTS);
        px(g, RLEG_X+rlx, RLEG_Y, RLEG_W, RLEG_H, PANTS);

        // Boots
        px(g, LBOOT_X+llx, LBOOT_Y, LBOOT_W, LBOOT_H, BOOT);
        px(g, RBOOT_X+rlx, RBOOT_Y, RBOOT_W, RBOOT_H, BOOT);
        px(g, LBOOT_X+llx, LBOOT_Y+LBOOT_H-1, LBOOT_W, 1, BOOT_DARK);
        px(g, RBOOT_X+rlx, RBOOT_Y+RBOOT_H-1, RBOOT_W, 1, BOOT_DARK);
    }

    // ════════════════════════════════════════════════════════════════════════
    // CHARACTER DRAWING — RIGHT DIRECTION
    // ════════════════════════════════════════════════════════════════════════

    static void drawCharRight(Graphics2D g, int[] off) {
        int bob = off[0]; int llx = off[1]; int rlx = off[2];
        int lay = off[3]; int ray = off[4];

        // Cape (partial, left side)
        px(g, 11, 11+bob, 3, 10, CAPE);
        px(g, 10, 12+bob, 3, 8, CAPE_DARK);

        // Hair (right-facing profile)
        px(g, 12, 2+bob, 10, 6, HAIR);
        px(g, 13, 1+bob, 2, 1, HAIR_LIGHT); // spike
        px(g, 17, 1+bob, 2, 1, HAIR_LIGHT);
        px(g, 11, 4+bob, 1, 4, HAIR_DARK);  // back hair

        // Face (profile facing right)
        px(g, 14, HEAD_Y+bob, 7, HEAD_H, SKIN);
        // Eye (one visible)
        px(g, 18, 8+bob, 2, 2, EYE);

        // Body
        px(g, 13, BODY_Y+bob, 7, BODY_H, SHIRT);
        px(g, 13, 12+bob, 7, 1, SHIRT_LIGHT);

        // Visible arm (right side)
        px(g, 20, RARM_Y+bob+ray, 2, RARM_H, SKIN);
        px(g, 20, RARM_Y+bob+ray, 2, 2, SHIRT);

        // Legs (slight perspective offset)
        px(g, 14+llx, LLEG_Y, 3, LLEG_H, PANTS);
        px(g, 17+rlx, RLEG_Y, 3, RLEG_H, PANTS);

        // Boots
        px(g, 13+llx, LBOOT_Y, 4, LBOOT_H, BOOT);
        px(g, 17+rlx, RBOOT_Y, 4, RBOOT_H, BOOT);
        px(g, 13+llx, LBOOT_Y+LBOOT_H-1, 4, 1, BOOT_DARK);
        px(g, 17+rlx, RBOOT_Y+RBOOT_H-1, 4, 1, BOOT_DARK);
    }

    // ════════════════════════════════════════════════════════════════════════
    // CHARACTER DRAWING — LEFT DIRECTION (mirror of right)
    // ════════════════════════════════════════════════════════════════════════

    static void drawCharLeft(Graphics2D g, int[] off) {
        int bob = off[0]; int llx = -off[1]; int rlx = -off[2];
        int lay = off[3]; int ray = off[4];

        // Cape (partial, right side)
        px(g, 18, 11+bob, 3, 10, CAPE);
        px(g, 19, 12+bob, 3, 8, CAPE_DARK);

        // Hair (left-facing profile)
        px(g, 10, 2+bob, 10, 6, HAIR);
        px(g, 13, 1+bob, 2, 1, HAIR_LIGHT);
        px(g, 17, 1+bob, 2, 1, HAIR_LIGHT);
        px(g, 20, 4+bob, 1, 4, HAIR_DARK);

        // Face (profile facing left)
        px(g, 11, HEAD_Y+bob, 7, HEAD_H, SKIN);
        // Eye
        px(g, 12, 8+bob, 2, 2, EYE);

        // Body
        px(g, 12, BODY_Y+bob, 7, BODY_H, SHIRT);
        px(g, 12, 12+bob, 7, 1, SHIRT_LIGHT);

        // Visible arm (left side)
        px(g, 10, LARM_Y+bob+lay, 2, LARM_H, SKIN);
        px(g, 10, LARM_Y+bob+lay, 2, 2, SHIRT);

        // Legs
        px(g, 13+llx, LLEG_Y, 3, LLEG_H, PANTS);
        px(g, 16+rlx, RLEG_Y, 3, RLEG_H, PANTS);

        // Boots
        px(g, 12+llx, LBOOT_Y, 4, LBOOT_H, BOOT);
        px(g, 16+rlx, RBOOT_Y, 4, RBOOT_H, BOOT);
        px(g, 12+llx, LBOOT_Y+LBOOT_H-1, 4, 1, BOOT_DARK);
        px(g, 16+rlx, RBOOT_Y+RBOOT_H-1, 4, 1, BOOT_DARK);
    }

    // ════════════════════════════════════════════════════════════════════════
    // DRAW CHARACTER DISPATCHER
    // ════════════════════════════════════════════════════════════════════════

    static void drawChar(Graphics2D g, int dir, int[] off) {
        switch (dir) {
            case DIR_D -> drawCharDown(g, off);
            case DIR_L -> drawCharLeft(g, off);
            case DIR_R -> drawCharRight(g, off);
            case DIR_U -> drawCharUp(g, off);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // EQUIPMENT OVERLAY DRAWING
    // ════════════════════════════════════════════════════════════════════════

    static void drawHelmet(Graphics2D g, int dir, int[] off) {
        int bob = off[0];
        int hx, hy;
        switch (dir) {
            case DIR_D -> { hx = 11; hy = 3+bob; }
            case DIR_U -> { hx = 11; hy = 3+bob; }
            case DIR_R -> { hx = 12; hy = 3+bob; }
            default    -> { hx = 10; hy = 3+bob; } // LEFT
        }
        int hw = (dir == DIR_R || dir == DIR_L) ? 10 : 10;
        // Helmet dome
        px(g, hx, hy, hw, 5, METAL);
        px(g, hx+1, hy, hw-2, 1, METAL_LIGHT); // top highlight
        px(g, hx, hy+4, hw, 2, METAL_DARK);    // visor/brim
        px(g, hx, hy+4, hw, 1, METAL_RIM);     // brim edge
        // Visor slit (front only)
        if (dir == DIR_D) {
            px(g, hx+2, hy+4, hw-4, 1, OUTLINE);
        }
        // Side decorations
        if (dir == DIR_D || dir == DIR_U) {
            px(g, hx, hy+2, 1, 2, METAL_RIM);
            px(g, hx+hw-1, hy+2, 1, 2, METAL_RIM);
        }
    }

    static void drawChestplate(Graphics2D g, int dir, int[] off) {
        int bob = off[0];
        int bx, by, bw;
        switch (dir) {
            case DIR_R -> { bx = 12; by = 12+bob; bw = 9; }
            case DIR_L -> { bx = 11; by = 12+bob; bw = 9; }
            default    -> { bx = 11; by = 12+bob; bw = 10; }
        }
        // Main plate
        px(g, bx, by, bw, 7, METAL);
        px(g, bx+1, by, bw-2, 1, METAL_LIGHT); // top highlight
        px(g, bx, by+6, bw, 1, METAL_DARK);    // bottom
        // Center ridge
        px(g, bx+bw/2, by+1, 1, 5, METAL_LIGHT);
        // Pauldrons (shoulders)
        if (dir == DIR_D || dir == DIR_U) {
            px(g, bx-1, by, 3, 2, METAL_DARK);      // left pauldron
            px(g, bx+bw-2, by, 3, 2, METAL_DARK);   // right pauldron
        } else if (dir == DIR_R) {
            px(g, bx+bw-2, by, 3, 2, METAL_DARK);
        } else {
            px(g, bx-1, by, 3, 2, METAL_DARK);
        }
    }

    static void drawLeggings(Graphics2D g, int dir, int[] off) {
        int llx = (dir == DIR_L) ? -off[1] : off[1];
        int rlx = (dir == DIR_L) ? -off[2] : off[2];
        int lx1, lx2;
        switch (dir) {
            case DIR_R -> { lx1 = 14; lx2 = 17; }
            case DIR_L -> { lx1 = 13; lx2 = 16; }
            default    -> { lx1 = LLEG_X; lx2 = RLEG_X; }
        }
        // Left leg armor
        px(g, lx1+llx, LLEG_Y, 3, LLEG_H, METAL);
        px(g, lx1+llx, LLEG_Y, 3, 1, METAL_RIM); // waist band
        px(g, lx1+llx+1, LLEG_Y+1, 1, LLEG_H-2, METAL_LIGHT); // highlight
        // Right leg armor
        px(g, lx2+rlx, RLEG_Y, 3, RLEG_H, METAL);
        px(g, lx2+rlx, RLEG_Y, 3, 1, METAL_RIM);
        px(g, lx2+rlx+1, RLEG_Y+1, 1, RLEG_H-2, METAL_LIGHT);
    }

    static void drawBootsEquip(Graphics2D g, int dir, int[] off) {
        int llx = (dir == DIR_L) ? -off[1] : off[1];
        int rlx = (dir == DIR_L) ? -off[2] : off[2];
        int bx1, bx2;
        switch (dir) {
            case DIR_R -> { bx1 = 13; bx2 = 17; }
            case DIR_L -> { bx1 = 12; bx2 = 16; }
            default    -> { bx1 = LBOOT_X; bx2 = RBOOT_X; }
        }
        // Left boot armor
        px(g, bx1+llx, LBOOT_Y, LBOOT_W, LBOOT_H, METAL_DARK);
        px(g, bx1+llx, LBOOT_Y+LBOOT_H-1, LBOOT_W, 1, METAL_RIM); // sole
        px(g, bx1+llx+1, LBOOT_Y, LBOOT_W-2, 1, METAL); // top highlight
        // Right boot armor
        px(g, bx2+rlx, RBOOT_Y, RBOOT_W, RBOOT_H, METAL_DARK);
        px(g, bx2+rlx, RBOOT_Y+RBOOT_H-1, RBOOT_W, 1, METAL_RIM);
        px(g, bx2+rlx+1, RBOOT_Y, RBOOT_W-2, 1, METAL);
    }

    static void drawShieldEquip(Graphics2D g, int dir, int[] off) {
        int bob = off[0];
        int sx, sy;
        switch (dir) {
            case DIR_D -> { sx = 8;  sy = 14+bob; }
            case DIR_U -> { sx = 8;  sy = 14+bob; }
            case DIR_R -> { sx = 9;  sy = 14+bob; }
            default    -> { sx = 19; sy = 14+bob; } // LEFT
        }
        // Shield body
        px(g, sx, sy, 5, 7, SHIELD_BODY);
        px(g, sx+1, sy-1, 3, 1, SHIELD_BODY); // top rounded
        px(g, sx+1, sy+7, 3, 1, SHIELD_BODY); // bottom
        px(g, sx+2, sy+8, 1, 1, SHIELD_BODY); // bottom point
        // Boss (center knob)
        px(g, sx+2, sy+2, 1, 2, GOLD);
        // Rim
        px(g, sx, sy, 1, 7, SHIELD_RIM);
        px(g, sx+4, sy, 1, 7, SHIELD_RIM);
        px(g, sx+1, sy-1, 3, 1, SHIELD_RIM);
    }

    static void drawEquipment(Graphics2D g, String type, int dir, int[] off) {
        switch (type) {
            case "helmet"     -> drawHelmet(g, dir, off);
            case "chestplate" -> drawChestplate(g, dir, off);
            case "leggings"   -> drawLeggings(g, dir, off);
            case "boots"      -> drawBootsEquip(g, dir, off);
            case "shield"     -> drawShieldEquip(g, dir, off);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // WEAPON DRAWING (for attack animation sheets)
    // ════════════════════════════════════════════════════════════════════════

    static void drawWeapon(Graphics2D g, String weaponType, int dir, int attackFrame) {
        // Weapon position relative to the hand
        int hx, hy; // hand position
        switch (dir) {
            case DIR_D -> { hx = 21; hy = 14; }
            case DIR_U -> { hx = 10; hy = 14; }
            case DIR_R -> { hx = 21; hy = 14; }
            default    -> { hx = 10; hy = 14; } // LEFT
        }

        // Weapon offset based on attack frame
        int wox = 0, woy = 0;
        int wLen = 8; // weapon length in logical pixels
        switch (attackFrame) {
            case 0 -> { wox = -1; woy = -4; } // wind-up (behind)
            case 1 -> { wox = 2;  woy = -6; } // mid-swing (up)
            case 2 -> { wox = 4;  woy = -2; } // extension (forward)
            case 3 -> { wox = 2;  woy = 2;  } // follow-through (down)
        }

        // Flip for left/up
        if (dir == DIR_L) { wox = -wox; }
        if (dir == DIR_U) { wox = -wox; }

        int wx = hx + wox;
        int wy = hy + woy;

        switch (weaponType) {
            case "wooden_sword" -> {
                // Handle
                px(g, wx, wy+wLen-2, 1, 3, WOOD_HANDLE);
                px(g, wx, wy+wLen-3, 3, 1, GOLD); // guard
                // Blade
                px(g, wx, wy, 1, wLen-3, STEEL_BLADE);
                px(g, wx+1, wy+1, 1, wLen-4, new Color(220,230,240,150));
            }
            case "iron_sword" -> {
                px(g, wx, wy+wLen-2, 1, 3, new Color(60,60,70));
                px(g, wx-1, wy+wLen-3, 3, 1, new Color(100,100,110));
                px(g, wx, wy, 1, wLen-3, IRON_BLADE);
                px(g, wx+1, wy+1, 1, wLen-4, IRON_DARK);
            }
            case "axe" -> {
                // Shaft
                px(g, wx, wy, 1, wLen, WOOD_HANDLE);
                // Axe head
                px(g, wx+1, wy, 3, 3, AXE_HEAD);
                px(g, wx+1, wy, 3, 1, METAL_LIGHT);
                px(g, wx+3, wy, 1, 3, METAL_RIM);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // SHEET GENERATION
    // ════════════════════════════════════════════════════════════════════════

    static BufferedImage createFrame() {
        return new BufferedImage(F, F, BufferedImage.TYPE_INT_ARGB);
    }

    static Graphics2D pixelGraphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_SPEED);
        return g;
    }

    /** Generate the base character sprite sheet (4 rows × 35 columns). */
    static void generateBaseSheet(String path) {
        BufferedImage sheet = new BufferedImage(TOTAL_COLS * F, 4 * F,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = pixelGraphics(sheet);

        for (int dir = 0; dir < 4; dir++) {
            int col = 0;
            for (int anim = 0; anim < ANIM_FRAMES.length; anim++) {
                int[][] offsets = ALL_OFFSETS[anim];
                for (int frame = 0; frame < ANIM_FRAMES[anim]; frame++) {
                    int fx = col * F;
                    int fy = dir * F;
                    Graphics2D fg = (Graphics2D) sg.create(fx, fy, F, F);
                    fg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_OFF);
                    drawChar(fg, dir, offsets[frame]);
                    fg.dispose();
                    col++;
                }
            }
        }
        sg.dispose();
        saveImage(sheet, path);
        System.out.println("Generated: " + path + " (" + sheet.getWidth() + "x" + sheet.getHeight() + ")");
    }

    /** Generate an equipment overlay sheet (same layout as base). */
    static void generateEquipmentSheet(String path, String equipType) {
        BufferedImage sheet = new BufferedImage(TOTAL_COLS * F, 4 * F,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = pixelGraphics(sheet);

        for (int dir = 0; dir < 4; dir++) {
            int col = 0;
            for (int anim = 0; anim < ANIM_FRAMES.length; anim++) {
                int[][] offsets = ALL_OFFSETS[anim];
                for (int frame = 0; frame < ANIM_FRAMES[anim]; frame++) {
                    int fx = col * F;
                    int fy = dir * F;
                    Graphics2D fg = (Graphics2D) sg.create(fx, fy, F, F);
                    fg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_OFF);
                    drawEquipment(fg, equipType, dir, offsets[frame]);
                    fg.dispose();
                    col++;
                }
            }
        }
        sg.dispose();
        saveImage(sheet, path);
        System.out.println("Generated: " + path);
    }

    /** Generate a weapon attack sheet (4 rows × 4 columns). */
    static void generateWeaponSheet(String path, String weaponType) {
        BufferedImage sheet = new BufferedImage(4 * F, 4 * F,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = pixelGraphics(sheet);

        for (int dir = 0; dir < 4; dir++) {
            for (int frame = 0; frame < 4; frame++) {
                int fx = frame * F;
                int fy = dir * F;
                Graphics2D fg = (Graphics2D) sg.create(fx, fy, F, F);
                fg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_OFF);
                // Draw base character in attack pose
                drawChar(fg, dir, ATTACK_OFF[frame]);
                // Draw weapon overlay
                drawWeapon(fg, weaponType, dir, frame);
                fg.dispose();
            }
        }
        sg.dispose();
        saveImage(sheet, path);
        System.out.println("Generated: " + path);
    }

    /** Generate effect sprites. */
    static void generateEffects(String outDir) {
        // Slash effect: 4 frames × 64×64
        BufferedImage slash = new BufferedImage(4 * F, F, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = pixelGraphics(slash);
        for (int i = 0; i < 4; i++) {
            Graphics2D fg = (Graphics2D) sg.create(i * F, 0, F, F);
            int alpha = 255 - i * 50;
            Color c = new Color(220, 235, 255, Math.max(50, alpha));
            fg.setColor(c);
            // Arc shape that expands
            int arcSize = 10 + i * 6;
            int ax = 16 - arcSize / 2;
            int ay = 16 - arcSize / 2;
            for (int r = 0; r < 3; r++) {
                px(fg, ax + i * 2 + r, ay + r, arcSize - r * 2, 1);
                px(fg, ax + i * 2 + r, ay + arcSize - r, arcSize - r * 2, 1);
            }
            fg.dispose();
        }
        sg.dispose();
        saveImage(slash, outDir + "/effect_slash.png");
        System.out.println("Generated: " + outDir + "/effect_slash.png");

        // Impact flash: 3 frames × 64×64
        BufferedImage impact = new BufferedImage(3 * F, F, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ig = pixelGraphics(impact);
        for (int i = 0; i < 3; i++) {
            Graphics2D fg = (Graphics2D) ig.create(i * F, 0, F, F);
            int alpha = 255 - i * 70;
            Color c = new Color(255, 240, 180, Math.max(40, alpha));
            fg.setColor(c);
            int size = 4 + i * 3;
            // Star burst
            px(fg, 16, 16 - size, 1, size * 2);
            px(fg, 16 - size, 16, size * 2, 1);
            px(fg, 16 - size / 2, 16 - size / 2, size, size);
            fg.dispose();
        }
        ig.dispose();
        saveImage(impact, outDir + "/effect_impact.png");
        System.out.println("Generated: " + outDir + "/effect_impact.png");
    }

    // ════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ════════════════════════════════════════════════════════════════════════

    static void saveImage(BufferedImage img, String path) {
        try {
            File f = new File(path);
            f.getParentFile().mkdirs();
            ImageIO.write(img, "png", f);
        } catch (IOException e) {
            System.err.println("Failed to save: " + path + " — " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // MAIN
    // ════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        String outDir = args.length > 0 ? args[0] : "res/player";
        System.out.println("=== Dead End!! Sprite Generator ===");
        System.out.println("Output directory: " + outDir);
        generateAllSprites(outDir);
        System.out.println("=== Sprite generation complete! ===");
    }

    public static void generateAllSprites(String outDir) {
        new File(outDir).mkdirs();

        // Base character sprite sheet
        generateBaseSheet(outDir + "/character_base.png");

        // Equipment overlay sheets
        generateEquipmentSheet(outDir + "/equipment_helmet.png", "helmet");
        generateEquipmentSheet(outDir + "/equipment_chestplate.png", "chestplate");
        generateEquipmentSheet(outDir + "/equipment_leggings.png", "leggings");
        generateEquipmentSheet(outDir + "/equipment_boots.png", "boots");
        generateEquipmentSheet(outDir + "/equipment_shield.png", "shield");

        // Weapon attack sheets
        generateWeaponSheet(outDir + "/weapon_wooden_sword.png", "wooden_sword");
        generateWeaponSheet(outDir + "/weapon_iron_sword.png", "iron_sword");
        generateWeaponSheet(outDir + "/weapon_axe.png", "axe");

        // Effect sprites
        generateEffects(outDir);
    }
}
