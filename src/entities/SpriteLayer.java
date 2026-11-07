package entities;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Wraps a sprite sheet (PNG atlas) and provides frame extraction.
 * Frames are arranged in rows (directions) and columns (animation frames).
 *
 * Supports two layouts:
 *  - Full sheet: 4 rows × 35 columns (base character, equipment overlays)
 *  - Attack sheet: 4 rows × 4 columns (weapon attacks)
 */
public class SpriteLayer {

    private final BufferedImage sheet;
    private final int frameW;
    private final int frameH;
    private final int cols;

    /**
     * @param sheet    sprite sheet image
     * @param frameW   width of a single frame in pixels
     * @param frameH   height of a single frame in pixels
     */
    public SpriteLayer(BufferedImage sheet, int frameW, int frameH) {
        this.sheet  = sheet;
        this.frameW = frameW;
        this.frameH = frameH;
        this.cols   = sheet.getWidth() / frameW;
    }

    /** Extract a single frame by row (direction) and column. */
    public BufferedImage getFrame(int row, int col) {
        int x = col * frameW;
        int y = row * frameH;
        if (x + frameW > sheet.getWidth() || y + frameH > sheet.getHeight()) {
            // Out of bounds — return transparent frame
            return new BufferedImage(frameW, frameH, BufferedImage.TYPE_INT_ARGB);
        }
        return sheet.getSubimage(x, y, frameW, frameH);
    }

    /** Get frame by direction row and animation column. */
    public BufferedImage getFrame(int dirRow, int animIndex, int frameIdx) {
        int col = AttachmentPoint.getColumn(animIndex, frameIdx);
        return getFrame(dirRow, col);
    }

    public int getFrameWidth()  { return frameW; }
    public int getFrameHeight() { return frameH; }
    public int getColumnCount() { return cols; }

    /** True if this layer has a valid sheet loaded. */
    public boolean isLoaded() { return sheet != null; }
}
