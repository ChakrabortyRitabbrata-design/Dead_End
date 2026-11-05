package animation;

import java.awt.Rectangle;

public final class Hitbox {
    private Hitbox() {
    }

    public static Rectangle inFront(int x, int y, Direction direction,
            int reach, int width, int height) {
        int side = Math.max(1, width);
        int depth = Math.max(1, height);
        switch (direction) {
        case UP:
            return new Rectangle(x - side / 2, y - reach, side, depth);
        case RIGHT:
            return new Rectangle(x + reach - depth, y - side / 2, depth, side);
        case LEFT:
            return new Rectangle(x - reach, y - side / 2, depth, side);
        default:
            return new Rectangle(x - side / 2, y + reach - depth, side, depth);
        }
    }
}
