package entities;

import animation.AnimationState;
import animation.Direction;

/**
 * Precision frame-specific armor attachment and animation synchronization system.
 *
 * Positions equipped armor overlays (Helmet, Chestplate, Leggings, Boots, Shield)
 * directly over the character's body parts and dynamically moves them during
 * idle breathing, walking, and combat attack swings across all four directions.
 */
public class ArmorAttachment {

    public static class ArmorFrameData {
        public final int helmX, helmY;
        public final int chestX, chestY;
        public final int legsX, legsY;
        public final int bootsX, bootsY;
        public final int shieldX, shieldY;

        public ArmorFrameData(int hx, int hy, int cx, int cy, int lx, int ly, int bx, int by, int sx, int sy) {
            this.helmX = hx; this.helmY = hy;
            this.chestX = cx; this.chestY = cy;
            this.legsX = lx; this.legsY = ly;
            this.bootsX = bx; this.bootsY = by;
            this.shieldX = sx; this.shieldY = sy;
        }
    }

    public static ArmorFrameData getOffsets(AnimationState state, Direction dir, int frameIndex) {
        if (state == AnimationState.PUNCH || state == AnimationState.ATTACK) {
            return getAttackOffsets(dir, frameIndex);
        } else if (state == AnimationState.WALK || state == AnimationState.RUN) {
            return getWalkOffsets(dir, frameIndex);
        } else {
            return getIdleOffsets(dir);
        }
    }

    private static ArmorFrameData getIdleOffsets(Direction dir) {
        return switch (dir) {
            case DOWN  -> new ArmorFrameData(32, -2, 32, 34, 32, 46, 32, 51, 15, 33);
            case LEFT  -> new ArmorFrameData(27, -2, 30, 34, 30, 46, 30, 51, 27, 33);
            case RIGHT -> new ArmorFrameData(37, -2, 34, 34, 34, 46, 34, 51, 13, 33);
            case UP    -> new ArmorFrameData(32, -2, 32, 34, 32, 46, 32, 51, 15, 31);
        };
    }

    private static ArmorFrameData getWalkOffsets(Direction dir, int frameIndex) {
        int bob = (frameIndex % 2 == 1) ? 1 : 0;
        return switch (dir) {
            case DOWN  -> new ArmorFrameData(32, -2 + bob, 32, 34 + bob, 32, 46 + bob, 32, 51, 15, 33 + bob);
            case LEFT  -> new ArmorFrameData(27, -2 + bob, 30, 34 + bob, 30, 46 + bob, 30, 51, 27, 33 + bob);
            case RIGHT -> new ArmorFrameData(37, -2 + bob, 34, 34 + bob, 34, 46 + bob, 34, 51, 13, 33 + bob);
            case UP    -> new ArmorFrameData(32, -2 + bob, 32, 34 + bob, 32, 46 + bob, 32, 51, 15, 31 + bob);
        };
    }

    private static ArmorFrameData getAttackOffsets(Direction dir, int frameIndex) {
        int f = Math.max(0, Math.min(3, frameIndex));
        return switch (dir) {
            case DOWN -> switch (f) {
                case 0 -> new ArmorFrameData(32, -3, 32, 33, 32, 46, 32, 51, 14, 30); // windup pull
                case 1 -> new ArmorFrameData(32,  0, 32, 36, 32, 47, 32, 51, 15, 34); // swing lunge
                case 2 -> new ArmorFrameData(30,  1, 30, 37, 30, 48, 31, 51, 12, 35); // impact extension
                default-> new ArmorFrameData(31,  0, 31, 35, 31, 47, 32, 51, 14, 32); // follow-through
            };
            case LEFT -> switch (f) {
                case 0 -> new ArmorFrameData(29, -2, 31, 34, 31, 46, 31, 51, 28, 32); // windup coil right
                case 1 -> new ArmorFrameData(25, -1, 27, 35, 28, 47, 29, 51, 24, 34); // swing slash left
                case 2 -> new ArmorFrameData(23, -1, 25, 35, 26, 47, 28, 51, 22, 34); // impact extension
                default-> new ArmorFrameData(26, -2, 28, 34, 29, 46, 30, 51, 25, 33); // follow-through
            };
            case RIGHT -> switch (f) {
                case 0 -> new ArmorFrameData(35, -2, 33, 34, 33, 46, 33, 51, 12, 32); // windup coil left
                case 1 -> new ArmorFrameData(39, -1, 37, 35, 36, 47, 35, 51, 16, 34); // swing slash right
                case 2 -> new ArmorFrameData(41, -1, 39, 35, 38, 47, 36, 51, 18, 34); // impact extension
                default-> new ArmorFrameData(38, -2, 36, 34, 35, 46, 34, 51, 15, 33); // follow-through
            };
            case UP -> switch (f) {
                case 0 -> new ArmorFrameData(32, -1, 32, 35, 32, 47, 32, 51, 15, 32); // windup
                case 1 -> new ArmorFrameData(30, -4, 30, 32, 31, 44, 32, 51, 13, 29); // swing up
                case 2 -> new ArmorFrameData(29, -5, 29, 31, 30, 43, 32, 51, 12, 28); // impact extension
                default-> new ArmorFrameData(31, -3, 31, 33, 31, 45, 32, 51, 14, 30); // follow-through
            };
        };
    }
}
