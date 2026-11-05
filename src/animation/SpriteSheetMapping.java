package animation;

import java.awt.Rectangle;

public final class SpriteSheetMapping {
    public static final int SOURCE_FRAME_SIZE = 64;
    private static final int[] PANEL_START = {
        80, 232, 412, 592, 726, 884, 1031, 1242, 1363
    };
    private static final int[] PANEL_END = {
        228, 406, 587, 720, 880, 1027, 1237, 1358, 1535
    };
    private static final int[] FRAME_COUNTS = {4, 6, 6, 4, 4, 4, 6, 3, 4};
    private static final int[] ROW_TOP = {212, 355, 493, 627};
    private static final int[] ROW_BOTTOM = {344, 480, 617, 750};

    private SpriteSheetMapping() {
    }

    public static int frameCount(AnimationState state) {
        return FRAME_COUNTS[state.ordinal()];
    }

    public static Rectangle frameBounds(Direction direction, AnimationState state, int frame) {
        int stateIndex = state.ordinal();
        int directionIndex;
        switch (direction) {
        case DOWN: directionIndex = 0; break;
        case LEFT: directionIndex = 1; break;
        case RIGHT: directionIndex = 2; break;
        default: directionIndex = 3; break;
        }

        if (state == AnimationState.DIE) {
            int[] topX = {1389, 1430, 1470, 1510};
            int[] bottomX = {1390, 1429, 1468, 1508, 1520, 1520};
            int centerX = frame < 4 ? topX[frame] : bottomX[frame - 4];
            int centerY = (frame < 4 ? 45 : 93) + ROW_TOP[directionIndex];
            return centeredBounds(centerX, centerY);
        }

        int start = PANEL_START[stateIndex];
        int end = PANEL_END[stateIndex];
        int cellWidth = (end - start) / FRAME_COUNTS[stateIndex];
        int nextCellStart = frame == FRAME_COUNTS[stateIndex] - 1
                ? end : start + (frame + 1) * cellWidth;
        int centerX = (start + frame * cellWidth + nextCellStart) / 2;
        int centerY = ROW_TOP[directionIndex] + 61;
        return centeredBounds(centerX, centerY);
    }

    public static Rectangle idleFrameBounds(Direction direction) {
        int col;
        switch (direction) {
        case DOWN: col = 0; break;
        case LEFT: col = 1; break;
        case RIGHT: col = 2; break;
        default: col = 3; break;
        }

        int[] xs = {238, 740, 1250, 1766};
        return new Rectangle(xs[col], 208, 180, 300);
    }

    public static Rectangle deathFrameBounds(Direction direction, int frame) {
        int row;
        switch (direction) {
        case DOWN: row = 0; break;
        case LEFT: row = 1; break;
        case RIGHT: row = 2; break;
        default: row = 3; break;
        }
        int[] left = {390, 680, 970, 1260};
        int[] right = {660, 950, 1240, 1560};
        int top = row * 220 + 8;
        return new Rectangle(left[frame], top, right[frame] - left[frame], 205);
    }

    public static Rectangle walkingFrameBounds(Direction direction, int frame) {
        int row;
        switch (direction) {
        case DOWN: row = 0; break;
        case LEFT: row = 1; break;
        case RIGHT: row = 2; break;
        default: row = 3; break;
        }
        int[] ys = {30, 242, 448, 662};
        int[] hs = {180, 164, 215, 174};
        int[][] xs = {
            {730, 940},
            {722, 940},
            {728, 942},
            {730, 942}
        };
        int[][] ws = {
            {100, 102},
            {112, 106},
            {106, 108},
            {102, 104}
        };
        int f = frame % 2;
        return new Rectangle(xs[row][f], ys[row], ws[row][f], hs[row]);
    }

    public static Rectangle runningFrameBounds(Direction direction, int frame) {
        int row;
        switch (direction) {
        case DOWN: row = 0; break;
        case LEFT: row = 1; break;
        case RIGHT: row = 2; break;
        default: row = 3; break;
        }
        int[] ys = {25, 242, 445, 662};
        int[] hs = {182, 168, 218, 174};
        int[][] xs = {
            {718, 950},
            {696, 942},
            {684, 940},
            {716, 948}
        };
        int[][] ws = {
            {105, 105},
            {146, 145},
            {144, 140},
            {106, 112}
        };
        int f = frame % 2;
        return new Rectangle(xs[row][f], ys[row], ws[row][f], hs[row]);
    }

    public static Rectangle dodgeFrameBounds(Direction direction, int frame) {
        int row;
        switch (direction) {
        case DOWN: row = 0; break;
        case LEFT: row = 1; break;
        case RIGHT: row = 2; break;
        default: row = 3; break;
        }
        int f = Math.max(0, Math.min(3, frame));
        int[][] xs = {
            {430, 686, 935, 1257},
            {418, 670, 948, 1253},
            {430, 686, 935, 1258},
            {428, 687, 935, 1256}
        };
        int[][] ys = {
            {34, 55, 68, 36},
            {245, 263, 282, 247},
            {452, 469, 486, 452},
            {658, 676, 692, 660}
        };
        int[][] ws = {
            {102, 121, 169, 103},
            {112, 135, 181, 109},
            {108, 133, 177, 111},
            {104, 133, 174, 101}
        };
        int[][] hs = {
            {163, 140, 123, 162},
            {156, 134, 113, 154},
            {156, 138, 117, 156},
            {177, 154, 135, 175}
        };
        return new Rectangle(xs[row][f], ys[row][f], ws[row][f], hs[row][f]);
    }

    public static Rectangle punchFrameBounds(Direction direction, int frame) {
        int row;
        switch (direction) {
        case DOWN: row = 0; break;
        case LEFT: row = 1; break;
        case RIGHT: row = 2; break;
        default: row = 3; break;
        }
        int f = Math.max(0, Math.min(3, frame));
        int[][] xs = {
            {49, 170, 290, 413},
            {575, 699, 824, 957},
            {1123, 1243, 1368, 1495},
            {1663, 1784, 1910, 2049}
        };
        int[][] ys = {
            {152, 152, 152, 152},
            {152, 152, 152, 152},
            {151, 151, 151, 151},
            {151, 149, 149, 149}
        };
        int[][] ws = {
            {80, 79, 90, 80},
            {86, 85, 91, 87},
            {84, 85, 94, 83},
            {80, 97, 105, 80}
        };
        int[][] hs = {
            {134, 134, 134, 135},
            {135, 135, 135, 135},
            {136, 136, 136, 136},
            {135, 138, 139, 135}
        };
        return new Rectangle(xs[row][f], ys[row][f], ws[row][f], hs[row][f]);
    }

    public static Rectangle kickFrameBounds(Direction direction, int frame) {
        int row;
        switch (direction) {
        case DOWN: row = 0; break;
        case LEFT: row = 1; break;
        case RIGHT: row = 2; break;
        default: row = 3; break;
        }
        int f = Math.max(0, Math.min(3, frame));
        int[][] xs = {
            {49, 170, 291, 414},
            {577, 701, 824, 958},
            {1125, 1244, 1371, 1495},
            {1665, 1790, 1915, 2049}
        };
        int[][] ys = {
            {470, 470, 470, 470},
            {470, 470, 469, 469},
            {469, 469, 469, 470},
            {469, 469, 469, 469}
        };
        int[][] ws = {
            {79, 79, 87, 79},
            {86, 85, 90, 86},
            {84, 85, 92, 84},
            {80, 89, 98, 80}
        };
        int[][] hs = {
            {136, 135, 135, 136},
            {136, 135, 136, 137},
            {137, 136, 137, 136},
            {137, 137, 137, 137}
        };
        return new Rectangle(xs[row][f], ys[row][f], ws[row][f], hs[row][f]);
    }

    private static Rectangle centeredBounds(int centerX, int centerY) {
        int halfSize = SOURCE_FRAME_SIZE / 2;
        return new Rectangle(centerX - halfSize, centerY - halfSize,
                SOURCE_FRAME_SIZE, SOURCE_FRAME_SIZE);
    }
}
