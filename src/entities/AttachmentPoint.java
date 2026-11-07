package entities;

import animation.AnimationState;
import animation.Direction;

/**
 * Defines per-frame attachment point offsets for body parts.
 * Used by EquipmentRenderer to position equipment overlays
 * on the character's body correctly for every animation frame.
 *
 * These offsets are synchronised with SpriteGenerator's drawing positions,
 * so equipment sprites line up perfectly with the base character.
 */
public final class AttachmentPoint {

    /** Body-part attachment identifiers. */
    public enum Part {
        HEAD, TORSO, LEFT_HAND, RIGHT_HAND, LEFT_LEG, RIGHT_LEG, LEFT_FOOT, RIGHT_FOOT
    }

    // ── Frame-count table (must match SpriteGenerator.ANIM_FRAMES) ─────────
    private static final int[] FRAME_COUNTS = {4, 6, 6, 4, 3, 6, 6};
    // Maps: idle=0, walk=1, run=2, jump=3, hurt=4, death=5, revive=6

    // ── Per-frame offsets from SpriteGenerator ──────────────────────────────
    // {bodyBob, leftLegDx, rightLegDx, leftArmDy, rightArmDy}
    private static final int[][][] OFFSETS = {
        // IDLE
        {{0,0,0,0,0},{0,0,0,0,0},{-1,0,0,0,0},{-1,0,0,0,0}},
        // WALK
        {{0,0,0,0,0},{0,-1,1,1,-1},{-1,-2,2,2,-2},{0,0,0,0,0},{0,1,-1,-1,1},{-1,2,-2,-2,2}},
        // RUN
        {{0,0,0,0,0},{-1,-2,2,2,-2},{-2,-3,3,3,-3},{0,0,0,0,0},{-1,2,-2,-2,2},{-2,3,-3,-3,3}},
        // JUMP
        {{2,0,0,0,0},{-2,0,0,-1,-1},{-4,0,0,-2,-2},{-1,0,0,0,0}},
        // HURT
        {{0,1,1,1,1},{1,2,2,2,2},{0,1,1,0,0}},
        // DEATH
        {{0,0,0,0,0},{1,0,0,1,1},{3,1,1,2,2},{5,2,2,3,3},{7,3,3,4,4},{8,3,3,4,4}},
        // REVIVE
        {{8,3,3,4,4},{7,3,3,3,3},{5,2,2,2,2},{3,1,1,1,1},{1,0,0,0,0},{0,0,0,0,0}}
    };

    /** Returns the column in the sprite sheet for the given animation and frame. */
    public static int getColumn(int animIndex, int frame) {
        int col = 0;
        for (int i = 0; i < animIndex && i < FRAME_COUNTS.length; i++) {
            col += FRAME_COUNTS[i];
        }
        return col + frame;
    }

    /** Returns the per-frame offsets {bodyBob, lLegDx, rLegDx, lArmDy, rArmDy}. */
    public static int[] getOffsets(int animIndex, int frame) {
        if (animIndex < 0 || animIndex >= OFFSETS.length) return new int[]{0,0,0,0,0};
        int[][] animOff = OFFSETS[animIndex];
        int f = Math.max(0, Math.min(frame, animOff.length - 1));
        return animOff[f];
    }

    /** Map AnimationState to the sprite-sheet animation index. */
    public static int animIndex(AnimationState state) {
        return switch (state) {
            case IDLE    -> 0;
            case WALK    -> 1;
            case RUN     -> 2;
            case JUMP    -> 3;
            case HURT    -> 4;
            case DIE     -> 5;
            case REVIVE  -> 6;
            case ATTACK  -> -1; // uses separate weapon sheet
            default      -> 0;  // PUNCH, KICK, DODGE, ROLL → fallback to idle
        };
    }

    /** Frame count for the given animation index. */
    public static int frameCount(int animIndex) {
        if (animIndex < 0 || animIndex >= FRAME_COUNTS.length) return 1;
        return FRAME_COUNTS[animIndex];
    }

    /** Map Direction to sprite-sheet row index. */
    public static int dirRow(Direction dir) {
        return switch (dir) {
            case DOWN  -> 0;
            case LEFT  -> 1;
            case RIGHT -> 2;
            case UP    -> 3;
        };
    }

    private AttachmentPoint() {}
}
