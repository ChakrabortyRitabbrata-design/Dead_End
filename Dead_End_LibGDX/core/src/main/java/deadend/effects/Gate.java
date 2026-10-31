package deadend.effects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayList;
import java.util.List;

/**
 * The Level 2 Gate — a distinctive ancient magical landmark.
 *
 * Rendered as:
 *   - Two large stone pillars (dark stone rectangles)
 *   - A glowing blue magical portal arch between them
 *   - Floating arcane particles
 *   - A "LOCKED" indicator
 *
 * All rendering is via ShapeRenderer (no texture dependency).
 * Set locked = false to change visual state (future unlock system).
 */
public class Gate {

    public boolean locked = true;

    // World position (bottom-left of gate structure)
    public final float worldX, worldY;

    // Gate dimensions
    private static final float PILLAR_W   = 22f;
    private static final float PILLAR_H   = 90f;
    private static final float GATE_W     = 80f;  // total width incl. pillars
    private static final float ARCH_W     = GATE_W - PILLAR_W * 2;
    private static final float ARCH_H     = 70f;

    private float time = 0f;

    // Particles
    private static final int PARTICLE_COUNT = 18;
    private final float[] px = new float[PARTICLE_COUNT];
    private final float[] py = new float[PARTICLE_COUNT];
    private final float[] pVx = new float[PARTICLE_COUNT];
    private final float[] pVy = new float[PARTICLE_COUNT];
    private final float[] pLife = new float[PARTICLE_COUNT];
    private final float[] pMaxLife = new float[PARTICLE_COUNT];

    public Gate(float worldX, float worldY) {
        this.worldX = worldX;
        this.worldY = worldY;
        initParticles();
    }

    private void initParticles() {
        for (int i = 0; i < PARTICLE_COUNT; i++) resetParticle(i);
    }

    private void resetParticle(int i) {
        // Spawn inside the arch opening
        float archX = worldX + PILLAR_W;
        px[i]     = archX + MathUtils.random(0f, ARCH_W);
        py[i]     = worldY + MathUtils.random(0f, ARCH_H);
        pVx[i]    = MathUtils.random(-12f, 12f);
        pVy[i]    = MathUtils.random(20f, 55f);
        pMaxLife[i] = MathUtils.random(0.8f, 2.2f);
        pLife[i]  = pMaxLife[i];
    }

    public void update(float delta) {
        time += delta;
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            pLife[i] -= delta;
            px[i] += pVx[i] * delta;
            py[i] += pVy[i] * delta;
            if (pLife[i] <= 0f) resetParticle(i);
        }
    }

    /**
     * Draw in world coordinates using the provided ShapeRenderer.
     * Caller must call sr.begin(Filled) before and sr.end() after.
     */
    public void draw(ShapeRenderer sr) {
        float gx = worldX;
        float gy = worldY;

        // --- Ground base stone slab ---
        sr.setColor(0.25f, 0.23f, 0.20f, 1f);
        sr.rect(gx - 6, gy - 6, GATE_W + 12, 12);

        // --- Left pillar ---
        drawPillar(sr, gx, gy);
        // --- Right pillar ---
        drawPillar(sr, gx + GATE_W - PILLAR_W, gy);

        // --- Portal arch glow (background) ---
        float archX = gx + PILLAR_W;
        float pulse = 0.5f + 0.5f * MathUtils.sin(time * 3.0f);
        Color archColor = locked
            ? new Color(0.05f, 0.15f + 0.1f * pulse, 0.55f + 0.2f * pulse, 0.85f)
            : new Color(0.10f, 0.60f + 0.2f * pulse, 0.30f, 0.90f);

        sr.setColor(archColor);
        sr.rect(archX, gy + 4, ARCH_W, ARCH_H - 8);

        // Glowing inner arch (lighter)
        Color innerColor = locked
            ? new Color(0.3f, 0.5f + 0.3f * pulse, 1.0f, 0.6f)
            : new Color(0.3f, 1.0f, 0.5f, 0.6f);
        sr.setColor(innerColor);
        sr.rect(archX + 6, gy + 10, ARCH_W - 12, ARCH_H - 24);

        // --- Particles ---
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float lifeRatio = pLife[i] / pMaxLife[i];
            float alpha = lifeRatio * 0.9f;
            sr.setColor(0.4f, 0.7f + 0.3f * pulse, 1.0f, alpha);
            float size = 3f + 2f * lifeRatio;
            sr.circle(px[i], py[i], size);
        }

        // --- Pillar runes (glowing dots) ---
        float runeAlpha = 0.6f + 0.4f * pulse;
        sr.setColor(0.4f, 0.8f, 1.0f, runeAlpha);
        drawRunes(sr, gx + PILLAR_W / 2f, gy + 20, gy + 60);
        drawRunes(sr, gx + GATE_W - PILLAR_W / 2f, gy + 20, gy + 60);

        // --- Top arch stone ---
        sr.setColor(0.28f, 0.25f, 0.22f, 1f);
        sr.rect(gx + PILLAR_W - 4, gy + ARCH_H - 2, ARCH_W + 8, 12);
    }

    private void drawPillar(ShapeRenderer sr, float x, float y) {
        // Base stone
        sr.setColor(0.30f, 0.27f, 0.24f, 1f);
        sr.rect(x, y, PILLAR_W, PILLAR_H);
        // Highlight edge
        sr.setColor(0.50f, 0.46f, 0.40f, 1f);
        sr.rectLine(x + 2, y, x + 2, y + PILLAR_H, 2f);
        // Shadow edge
        sr.setColor(0.15f, 0.13f, 0.11f, 1f);
        sr.rectLine(x + PILLAR_W - 2, y, x + PILLAR_W - 2, y + PILLAR_H, 2f);
        // Top cap
        sr.setColor(0.22f, 0.20f, 0.18f, 1f);
        sr.rect(x - 3, y + PILLAR_H - 8, PILLAR_W + 6, 10);
    }

    private void drawRunes(ShapeRenderer sr, float cx, float y0, float y1) {
        float spacing = (y1 - y0) / 4f;
        for (int i = 0; i < 4; i++) {
            sr.circle(cx, y0 + i * spacing, 3f);
        }
    }

    /**
     * Draw the "LOCKED" label — call with a BitmapFont separately.
     * Returns the world-space X/Y centre above the gate for caller to use.
     */
    public float getLabelX() { return worldX + GATE_W / 2f; }
    public float getLabelY() { return worldY + PILLAR_H + 10f; }

    /** Collision rectangle for the gate structure. */
    public com.badlogic.gdx.math.Rectangle getCollision() {
        return new com.badlogic.gdx.math.Rectangle(worldX - 4, worldY, GATE_W + 8, ARCH_H);
    }
}
