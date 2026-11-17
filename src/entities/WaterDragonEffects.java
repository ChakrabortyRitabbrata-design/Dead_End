package entities;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/** Procedurally builds the transparent animation strips used by the water dragon. */
final class WaterDragonEffects {
    static final int FRAME_COUNT = 8;
    static final int FIREBALL_SIZE = 48;
    static final int EXPLOSION_SIZE = 96;
    static final int PULSE_SIZE = 192;

    private static final BufferedImage FIREBALL_SHEET = buildFireballSheet();
    private static final BufferedImage EXPLOSION_SHEET = buildExplosionSheet();
    private static final BufferedImage PULSE_SHEET = buildPulseSheet();
    private static final BufferedImage[] FIREBALL = slice(FIREBALL_SHEET, FIREBALL_SIZE);
    private static final BufferedImage[] EXPLOSION = slice(EXPLOSION_SHEET, EXPLOSION_SIZE);
    private static final BufferedImage[] PULSE = slice(PULSE_SHEET, PULSE_SIZE);

    private WaterDragonEffects() {}

    static BufferedImage fireball(int frame) {
        return FIREBALL[Math.floorMod(frame, FRAME_COUNT)];
    }

    static BufferedImage explosion(int frame) {
        return EXPLOSION[Math.floorMod(frame, FRAME_COUNT)];
    }

    static BufferedImage pulse(int frame) {
        return PULSE[Math.floorMod(frame, FRAME_COUNT)];
    }

    static BufferedImage fireballSheet() {
        return FIREBALL_SHEET;
    }

    static BufferedImage pulseSheet() {
        return PULSE_SHEET;
    }

    private static BufferedImage buildFireballSheet() {
        BufferedImage sheet = new BufferedImage(
                FIREBALL_SIZE * FRAME_COUNT, FIREBALL_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            Graphics2D g = sheet.createGraphics();
            g.translate(frame * FIREBALL_SIZE, 0);
            configure(g);
            double phase = frame * Math.PI / 4.0;
            int cx = 24;
            int cy = 24;
            int radius = 10 + (frame % 3);
            g.setPaint(new RadialGradientPaint(cx, cy, radius * 1.8f,
                    new float[] {0f, 0.55f, 1f},
                    new Color[] {
                        new Color(255, 255, 208, 245),
                        new Color(255, 143, 37, 235),
                        new Color(227, 43, 16, 0)
                    }));
            g.fill(new Ellipse2D.Double(cx - radius * 1.8, cy - radius * 1.8,
                    radius * 3.6, radius * 3.6));
            g.setColor(new Color(255, 246, 165, 235));
            g.fill(new Ellipse2D.Double(cx - radius * 0.55, cy - radius * 0.65,
                    radius * 1.1, radius * 1.3));
            g.setColor(new Color(255, 255, 226, 220));
            for (int spark = 0; spark < 3; spark++) {
                double angle = phase + spark * Math.PI * 2.0 / 3.0;
                int sx = cx + (int) (Math.cos(angle) * (radius + 7));
                int sy = cy + (int) (Math.sin(angle) * (radius + 7));
                g.fillOval(sx - 2, sy - 2, 4, 4);
            }
            g.dispose();
        }
        return sheet;
    }

    private static BufferedImage buildExplosionSheet() {
        BufferedImage sheet = new BufferedImage(
                EXPLOSION_SIZE * FRAME_COUNT, EXPLOSION_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            Graphics2D g = sheet.createGraphics();
            g.translate(frame * EXPLOSION_SIZE, 0);
            configure(g);
            float progress = frame / (float) (FRAME_COUNT - 1);
            float radius = 8 + progress * 35;
            float alpha = 1f - progress * 0.82f;
            int a = Math.max(0, Math.min(255, Math.round(235 * alpha)));
            g.setPaint(new RadialGradientPaint(48, 48, Math.max(1f, radius),
                    new float[] {0f, 0.38f, 1f},
                    new Color[] {
                        new Color(255, 255, 218, a),
                        new Color(255, 122, 24, a),
                        new Color(205, 26, 10, 0)
                    }));
            g.fill(new Ellipse2D.Double(48 - radius, 48 - radius, radius * 2, radius * 2));
            g.setColor(new Color(255, 236, 154, a));
            for (int spark = 0; spark < 10; spark++) {
                double angle = spark * Math.PI * 2.0 / 10.0 + frame * 0.13;
                double distance = radius * (0.65 + (spark % 3) * 0.1);
                int sx = 48 + (int) (Math.cos(angle) * distance);
                int sy = 48 + (int) (Math.sin(angle) * distance);
                int size = 2 + spark % 3;
                g.fillOval(sx - size / 2, sy - size / 2, size, size);
            }
            g.dispose();
        }
        return sheet;
    }

    private static BufferedImage buildPulseSheet() {
        BufferedImage sheet = new BufferedImage(
                PULSE_SIZE * FRAME_COUNT, PULSE_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            Graphics2D g = sheet.createGraphics();
            g.translate(frame * PULSE_SIZE, 0);
            configure(g);
            float progress = frame / (float) (FRAME_COUNT - 1);
            float radius = 12 + progress * 76;
            float alpha = 1f - progress * 0.86f;
            int a = Math.max(0, Math.min(255, Math.round(230 * alpha)));
            g.setColor(new Color(64, 230, 255, a / 5));
            g.fill(new Ellipse2D.Double(96 - radius, 96 - radius, radius * 2, radius * 2));
            g.setStroke(new BasicStroke(Math.max(2f, 9f - progress * 5f)));
            g.setColor(new Color(82, 241, 255, a));
            g.draw(new Ellipse2D.Double(96 - radius, 96 - radius, radius * 2, radius * 2));
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(202, 255, 255, a));
            g.draw(new Ellipse2D.Double(96 - radius + 5, 96 - radius + 5,
                    Math.max(0, radius * 2 - 10), Math.max(0, radius * 2 - 10)));
            for (int mote = 0; mote < 12; mote++) {
                double angle = mote * Math.PI / 6.0 + frame * 0.15;
                double distance = radius * (0.72 + (mote % 4) * 0.055);
                int x = 96 + (int) (Math.cos(angle) * distance);
                int y = 96 + (int) (Math.sin(angle) * distance);
                g.setColor(new Color(173, 255, 255, a));
                g.fillOval(x - 2, y - 2, 4, 4);
            }
            g.dispose();
        }
        return sheet;
    }

    private static void configure(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static BufferedImage[] slice(BufferedImage sheet, int frameSize) {
        BufferedImage[] frames = new BufferedImage[FRAME_COUNT];
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            frames[frame] = sheet.getSubimage(frame * frameSize, 0, frameSize, frameSize);
        }
        return frames;
    }
}
