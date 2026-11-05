package animation;

import java.awt.image.BufferedImage;

public final class AnimationFrame {
    private final BufferedImage image;
    private final long durationNanos;

    public AnimationFrame(BufferedImage image, long durationNanos) {
        this.image = image;
        this.durationNanos = durationNanos;
    }

    public BufferedImage getImage() {
        return image;
    }

    public long getDurationNanos() {
        return durationNanos;
    }
}
