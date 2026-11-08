package entities;

import animation.AnimationState;
import animation.Direction;

/**
 * Dedicated frame-specific weapon and hand attachment system.
 *
 * Provides exact pixel-level hand coordinates (in 64×64 sprite space),
 * weapon rotation angles, grip anchor offsets, and draw order (front vs rear)
 * for every animation state, direction, and frame index.
 */
public class WeaponAttachment {

    public static class AttachmentData {
        public final int handX;           // Hand X in 64x64 frame space
        public final int handY;           // Hand Y in 64x64 frame space
        public final double angle;        // Weapon angle in radians (0 = straight up, +CW)
        public final boolean drawBehind;  // True if weapon renders behind character body
        public final int gripOffsetX;     // Fine-tuning anchor offset
        public final int gripOffsetY;

        public AttachmentData(int handX, int handY, double angle, boolean drawBehind, int gripOffsetX, int gripOffsetY) {
            this.handX = handX;
            this.handY = handY;
            this.angle = angle;
            this.drawBehind = drawBehind;
            this.gripOffsetX = gripOffsetX;
            this.gripOffsetY = gripOffsetY;
        }
    }

    /**
     * Resolves the exact attachment data for the given animation state,
     * direction, and frame.
     */
    public static AttachmentData getAttachment(AnimationState state, Direction dir, int frameIndex) {
        if (state == AnimationState.PUNCH || state == AnimationState.ATTACK) {
            return getAttackAttachment(dir, frameIndex);
        } else if (state == AnimationState.WALK || state == AnimationState.RUN) {
            return getWalkAttachment(dir, frameIndex);
        } else {
            return getIdleAttachment(dir);
        }
    }

    private static AttachmentData getIdleAttachment(Direction dir) {
        return switch (dir) {
            case DOWN  -> new AttachmentData(45, 43, Math.toRadians(45),  false, 0, 0);
            case LEFT  -> new AttachmentData(29, 45, Math.toRadians(215), false, 0, 0);
            case RIGHT -> new AttachmentData(34, 45, Math.toRadians(35),  false, 0, 0);
            case UP    -> new AttachmentData(47, 42, Math.toRadians(35),  true,  0, 0);
        };
    }

    private static AttachmentData getWalkAttachment(Direction dir, int frameIndex) {
        int f = frameIndex % 2;
        return switch (dir) {
            case DOWN -> (f == 0)
                    ? new AttachmentData(45, 41, Math.toRadians(40),  false, 0, 0)
                    : new AttachmentData(44, 38, Math.toRadians(55),  false, 0, 0);
            case LEFT -> (f == 0)
                    ? new AttachmentData(26, 46, Math.toRadians(210), false, 0, 0)
                    : new AttachmentData(28, 42, Math.toRadians(225), false, 0, 0);
            case RIGHT -> (f == 0)
                    ? new AttachmentData(38, 43, Math.toRadians(30),  false, 0, 0)
                    : new AttachmentData(35, 37, Math.toRadians(45),  false, 0, 0);
            case UP -> (f == 0)
                    ? new AttachmentData(48, 41, Math.toRadians(30),  true,  0, 0)
                    : new AttachmentData(47, 37, Math.toRadians(45),  true,  0, 0);
        };
    }

    private static AttachmentData getAttackAttachment(Direction dir, int frameIndex) {
        int f = Math.max(0, Math.min(3, frameIndex));
        return switch (dir) {
            case DOWN -> switch (f) {
                case 0 -> new AttachmentData(43, 42, Math.toRadians(-20), false, 0, 0); // windup
                case 1 -> new AttachmentData(43, 43, Math.toRadians(35),  false, 0, 0); // mid swing
                case 2 -> new AttachmentData(42, 39, Math.toRadians(80),  false, 0, 0); // full extension
                default-> new AttachmentData(44, 41, Math.toRadians(50),  false, 0, 0); // follow through
            };
            case LEFT -> switch (f) {
                case 0 -> new AttachmentData(28, 42, Math.toRadians(120), false, 0, 0);
                case 1 -> new AttachmentData(24, 38, Math.toRadians(170), false, 0, 0);
                case 2 -> new AttachmentData(26, 34, Math.toRadians(215), false, 0, 0);
                default-> new AttachmentData(28, 42, Math.toRadians(220), false, 0, 0);
            };
            case RIGHT -> switch (f) {
                case 0 -> new AttachmentData(37, 41, Math.toRadians(-60), false, 0, 0);
                case 1 -> new AttachmentData(36, 37, Math.toRadians(10),  false, 0, 0);
                case 2 -> new AttachmentData(41, 33, Math.toRadians(65),  false, 0, 0);
                default-> new AttachmentData(35, 39, Math.toRadians(40),  false, 0, 0);
            };
            case UP -> switch (f) {
                case 0 -> new AttachmentData(45, 37, Math.toRadians(-30), true,  0, 0);
                case 1 -> new AttachmentData(44, 30, Math.toRadians(0),   true,  0, 0);
                case 2 -> new AttachmentData(47, 30, Math.toRadians(30),  true,  0, 0);
                default-> new AttachmentData(45, 40, Math.toRadians(35),  true,  0, 0);
            };
        };
    }
}
