package deadend.rendering;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

import java.util.Random;

/**
 * Procedural texture generator producing rich, hand-painted-looking terrain art.
 *
 * All textures are generated at HIGH_RES×HIGH_RES pixels using layered painting:
 *   1. Base colour field with large-scale noise variation
 *   2. Medium-frequency detail (micro-patterns, colour clusters)
 *   3. Fine detail (individual brushstrokes, stray pixels)
 *   4. Highlights and shadows
 *   5. Edge darkening for visual separation
 *
 * Noise is computed with a simple value-noise function (no external deps).
 * All RNG is seeded deterministically so textures are stable across sessions.
 */
public final class PixmapArtist {

    public static final int TEX = 96;   // pixels per tile texture

    // Colour palette — hand-tuned for fantasy forest art direction
    // Grass family
    static final int[] GRASS_BASE    = {54,  90,  38};
    static final int[] GRASS_MID     = {62, 105,  42};
    static final int[] GRASS_LIGHT   = {78, 125,  50};
    static final int[] GRASS_DARK    = {36,  68,  25};
    static final int[] GRASS_PALE    = {88, 135,  58};
    static final int[] GRASS_YELLOW  = {95, 130,  42};
    static final int[] LEAF_BROWN    = {82,  62,  28};
    static final int[] LEAF_TAN      = {98,  80,  40};
    static final int[] FLOWER_WHITE  = {230, 230, 210};
    static final int[] FLOWER_PURPLE = {148,  90, 175};
    static final int[] FLOWER_YELLOW = {210, 185,  50};
    static final int[] PEBBLE        = {128, 118, 105};
    static final int[] PEBBLE_DARK   = { 88,  80,  70};
    // Dirt family
    static final int[] DIRT_BASE     = { 92,  62,  32};
    static final int[] DIRT_MID      = {108,  75,  40};
    static final int[] DIRT_LIGHT    = {128,  92,  52};
    static final int[] DIRT_DARK     = { 68,  44,  20};
    static final int[] DIRT_SHADOW   = { 52,  32,  14};
    static final int[] MUD           = { 76,  52,  26};
    // Sand
    static final int[] SAND_BASE     = {182, 158,  90};
    static final int[] SAND_LIGHT    = {210, 188, 115};
    static final int[] SAND_DARK     = {148, 124,  68};
    // Water
    static final int[] WATER_DEEP    = { 32,  72, 148};
    static final int[] WATER_MID     = { 46,  95, 178};
    static final int[] WATER_LIGHT   = { 78, 140, 210};
    static final int[] WATER_FOAM    = {190, 220, 240};
    // Stone / wall
    static final int[] STONE_BASE    = { 90,  84,  78};
    static final int[] STONE_LIGHT   = {128, 120, 110};
    static final int[] STONE_DARK    = { 58,  54,  50};
    static final int[] MOSS          = { 58,  88,  42};
    // Tree canopy colours (multiple variants)
    static final int[][] TREE_VARIANTS = {
        {36, 82, 28, 52, 112, 38, 22, 60, 18},   // deep forest green
        {45, 98, 32, 65, 128, 42, 28, 72, 22},   // medium green
        {56, 112, 40, 75, 145, 52, 38, 85, 28},  // bright green
        {38, 88, 55, 52, 115, 70, 22, 65, 35},   // teal-green (moss)
        {82, 95, 28, 105, 118, 35, 62, 72, 18},  // autumn yellow-green
        {50, 72, 28, 68,  95, 38, 32, 52, 18},   // dark olive
    };
    // {base_r,base_g,base_b, mid_r,mid_g,mid_b, dark_r,dark_g,dark_b}

    // -------------------------------------------------------------------------
    // Grass variants
    // -------------------------------------------------------------------------

    /** Create one of 6 grass texture variants */
    public static Texture grassVariant(int variant) {
        long seed = 0xA1B2C3D4L + variant * 0x9E3779B9L;
        Random rng = new Random(seed);
        Pixmap px = new Pixmap(TEX, TEX, Pixmap.Format.RGBA8888);

        // Step 1 — base colour field with large-scale variation
        for (int y = 0; y < TEX; y++) {
            for (int x = 0; x < TEX; x++) {
                float n = valueNoise(x * 0.08f, y * 0.08f, seed);
                float n2 = valueNoise(x * 0.22f, y * 0.22f, seed + 7);
                float blend = n * 0.6f + n2 * 0.4f;

                int[] c = switch (variant) {
                    case 0 -> blendColors(GRASS_BASE, GRASS_MID, blend);
                    case 1 -> blendColors(GRASS_MID, GRASS_LIGHT, blend);
                    case 2 -> blendColors(GRASS_DARK, GRASS_BASE, blend);
                    case 3 -> blendColors(GRASS_BASE, GRASS_PALE, blend);
                    case 4 -> blendColors(GRASS_MID, GRASS_YELLOW, blend);
                    default -> blendColors(GRASS_DARK, GRASS_MID, blend);
                };

                // Medium-freq noise for micro-variation
                float m = valueNoise(x * 0.45f, y * 0.45f, seed + 13) * 0.12f - 0.06f;
                px.setColor(clampf(c[0]/255f + m), clampf(c[1]/255f + m), clampf(c[2]/255f + m), 1f);
                px.drawPixel(x, y);
            }
        }

        // Step 2 — grass blade strokes (short diagonal marks)
        int blades = 28 + variant * 4;
        for (int i = 0; i < blades; i++) {
            int bx = rng.nextInt(TEX - 4);
            int by = rng.nextInt(TEX - 6);
            float bright = 0.85f + rng.nextFloat() * 0.30f;
            float[] gc = variant < 3 ?
                new float[]{GRASS_LIGHT[0]/255f * bright, GRASS_LIGHT[1]/255f * bright, GRASS_LIGHT[2]/255f * bright} :
                new float[]{GRASS_PALE[0]/255f * bright, GRASS_PALE[1]/255f * bright, GRASS_PALE[2]/255f * bright};
            px.setColor(gc[0], gc[1], gc[2], 0.7f + rng.nextFloat() * 0.25f);
            // Short blade: 2-4 pixels tall, 1 pixel wide, slightly angled
            int len = 2 + rng.nextInt(3);
            int dx = rng.nextBoolean() ? 1 : 0;
            for (int l = 0; l < len; l++) px.drawPixel(bx + l * dx, by + l);
        }

        // Step 3 — fallen leaves (organic patches)
        int leaves = 4 + rng.nextInt(6);
        for (int i = 0; i < leaves; i++) {
            int lx = rng.nextInt(TEX - 6);
            int ly = rng.nextInt(TEX - 4);
            int[] lc = rng.nextBoolean() ? LEAF_BROWN : LEAF_TAN;
            px.setColor(lc[0]/255f, lc[1]/255f, lc[2]/255f, 0.55f + rng.nextFloat() * 0.3f);
            fillEllipse(px, lx, ly, 2 + rng.nextInt(4), 2 + rng.nextInt(3));
        }

        // Step 4 — small pebbles (rare)
        if (rng.nextFloat() < 0.45f) {
            int px2 = rng.nextInt(TEX - 4) + 2, py2 = rng.nextInt(TEX - 4) + 2;
            paintPebble(px, px2, py2, rng);
        }

        // Step 5 — tiny flowers (rare, variant-dependent)
        int flowerCount = (variant == 3 || variant == 4) ? rng.nextInt(4) : rng.nextInt(2);
        for (int i = 0; i < flowerCount; i++) {
            int fx = rng.nextInt(TEX - 4) + 2, fy = rng.nextInt(TEX - 4) + 2;
            int[] fc = rng.nextBoolean() ? FLOWER_WHITE : (rng.nextBoolean() ? FLOWER_PURPLE : FLOWER_YELLOW);
            px.setColor(fc[0]/255f, fc[1]/255f, fc[2]/255f, 0.85f);
            px.fillCircle(fx, fy, 1);
            // Petals
            px.setColor(fc[0]/255f * 0.85f, fc[1]/255f * 0.85f, fc[2]/255f * 0.85f, 0.6f);
            px.drawPixel(fx - 2, fy); px.drawPixel(fx + 2, fy);
            px.drawPixel(fx, fy - 2); px.drawPixel(fx, fy + 2);
        }

        // Step 6 — edge darkening (subtle vignette per tile to reduce tiling artifacts)
        applyEdgeDarken(px, 0.12f, 6);

        Texture t = new Texture(px);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        px.dispose();
        return t;
    }

    // -------------------------------------------------------------------------
    // Dirt / path variants
    // -------------------------------------------------------------------------

    public static Texture dirtVariant(int variant) {
        long seed = 0xDEADBEEFL + variant * 0x6C62272EL;
        Random rng = new Random(seed);
        Pixmap px = new Pixmap(TEX, TEX, Pixmap.Format.RGBA8888);

        // Base
        for (int y = 0; y < TEX; y++) {
            for (int x = 0; x < TEX; x++) {
                float n1 = valueNoise(x * 0.10f, y * 0.10f, seed);
                float n2 = valueNoise(x * 0.30f, y * 0.30f, seed + 3);
                float n3 = valueNoise(x * 0.55f, y * 0.55f, seed + 7);
                float blend = n1 * 0.5f + n2 * 0.3f + n3 * 0.2f;

                int[] c = switch (variant) {
                    case 0 -> blendColors(DIRT_BASE, DIRT_MID, blend);
                    case 1 -> blendColors(DIRT_DARK, DIRT_BASE, blend);
                    case 2 -> blendColors(DIRT_MID, DIRT_LIGHT, blend);
                    default -> blendColors(MUD, DIRT_BASE, blend);
                };

                float m = valueNoise(x * 0.7f, y * 0.7f, seed + 11) * 0.10f - 0.05f;
                px.setColor(clampf(c[0]/255f + m), clampf(c[1]/255f + m), clampf(c[2]/255f + m), 1f);
                px.drawPixel(x, y);
            }
        }

        // Soil texture lines (erosion lines)
        for (int i = 0; i < 8; i++) {
            int ly = rng.nextInt(TEX);
            int lx0 = rng.nextInt(TEX / 2);
            int lx1 = lx0 + rng.nextInt(TEX - lx0);
            float dark = 0.70f + rng.nextFloat() * 0.12f;
            px.setColor(DIRT_SHADOW[0]/255f * dark, DIRT_SHADOW[1]/255f * dark, DIRT_SHADOW[2]/255f * dark, 0.35f);
            px.drawLine(lx0, ly, lx1, ly + rng.nextInt(3) - 1);
        }

        // Pebbles
        int pebbleCount = 2 + rng.nextInt(5);
        for (int i = 0; i < pebbleCount; i++) {
            int px2 = rng.nextInt(TEX - 6) + 3, py2 = rng.nextInt(TEX - 6) + 3;
            paintPebble(px, px2, py2, rng);
        }

        // Footprint marks (subtle depression ovals)
        if (rng.nextFloat() < 0.4f) {
            int fpx = rng.nextInt(TEX - 12) + 6, fpy = rng.nextInt(TEX - 8) + 4;
            px.setColor(DIRT_SHADOW[0]/255f, DIRT_SHADOW[1]/255f, DIRT_SHADOW[2]/255f, 0.22f);
            fillEllipse(px, fpx, fpy, 5, 8);
            fillEllipse(px, fpx + 7, fpy + 5, 5, 8);
        }

        // Leaf on dirt
        if (rng.nextFloat() < 0.5f) {
            int lx2 = rng.nextInt(TEX - 6) + 3, ly2 = rng.nextInt(TEX - 6) + 3;
            px.setColor(LEAF_BROWN[0]/255f, LEAF_BROWN[1]/255f, LEAF_BROWN[2]/255f, 0.6f);
            fillEllipse(px, lx2, ly2, 4, 3);
        }

        applyEdgeDarken(px, 0.08f, 4);

        Texture t = new Texture(px);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        px.dispose();
        return t;
    }

    // -------------------------------------------------------------------------
    // Sand
    // -------------------------------------------------------------------------

    public static Texture sandTexture() {
        long seed = 0xF0C8A4B2L;
        Random rng = new Random(seed);
        Pixmap px = new Pixmap(TEX, TEX, Pixmap.Format.RGBA8888);

        for (int y = 0; y < TEX; y++) {
            for (int x = 0; x < TEX; x++) {
                float n1 = valueNoise(x * 0.09f, y * 0.09f, seed);
                float n2 = valueNoise(x * 0.35f, y * 0.35f, seed + 5);
                float blend = n1 * 0.65f + n2 * 0.35f;
                int[] c = blendColors(SAND_DARK, SAND_LIGHT, blend);
                float ripple = (float)(Math.sin(x * 0.4 + y * 0.1) * 0.04);
                px.setColor(clampf(c[0]/255f + ripple), clampf(c[1]/255f + ripple*0.8f), clampf(c[2]/255f), 1f);
                px.drawPixel(x, y);
            }
        }
        // Wind ripples
        for (int i = 0; i < 6; i++) {
            int ry = rng.nextInt(TEX - 2) + 1;
            px.setColor(SAND_LIGHT[0]/255f, SAND_LIGHT[1]/255f, SAND_LIGHT[2]/255f, 0.22f);
            for (int x = 0; x < TEX; x++) {
                px.drawPixel(x, ry + (int)(Math.sin(x * 0.3) * 1.5));
            }
        }
        // Small pebble or shell
        if (rng.nextBoolean()) {
            paintPebble(px, rng.nextInt(TEX-8)+4, rng.nextInt(TEX-8)+4, rng);
        }

        Texture t = new Texture(px);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        px.dispose();
        return t;
    }

    // -------------------------------------------------------------------------
    // Water
    // -------------------------------------------------------------------------

    public static Texture waterTexture(float time) {
        // Static water texture — animated shimmer done in renderer via colour tinting
        long seed = 0xC0FFEE00L;
        Pixmap px = new Pixmap(TEX, TEX, Pixmap.Format.RGBA8888);

        for (int y = 0; y < TEX; y++) {
            for (int x = 0; x < TEX; x++) {
                float n1 = valueNoise(x * 0.12f, y * 0.12f, seed);
                float n2 = valueNoise(x * 0.28f, y * 0.28f, seed + 4);
                float blend = n1 * 0.5f + n2 * 0.5f;
                int[] c = blendColors(WATER_DEEP, WATER_MID, blend);
                px.setColor(c[0]/255f, c[1]/255f, c[2]/255f, 0.92f);
                px.drawPixel(x, y);
            }
        }
        // Foam highlights — wavy lines
        for (int i = 0; i < 5; i++) {
            int wy = 8 + i * (TEX/5);
            px.setColor(WATER_FOAM[0]/255f, WATER_FOAM[1]/255f, WATER_FOAM[2]/255f, 0.28f);
            for (int x = 0; x < TEX; x++) {
                int waveY = wy + (int)(Math.sin(x * 0.5 + i) * 3);
                if (waveY >= 0 && waveY < TEX) px.drawPixel(x, waveY);
            }
        }
        // Specular dots
        new Random(seed).ints(8, 0, TEX * TEX).forEach(pos -> {
            int x = pos % TEX, y2 = pos / TEX;
            px.setColor(0.95f, 0.97f, 1f, 0.35f);
            px.drawPixel(x, y2);
        });

        Texture t = new Texture(px);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        px.dispose();
        return t;
    }

    // -------------------------------------------------------------------------
    // Wall / Stone
    // -------------------------------------------------------------------------

    public static Texture wallTexture() {
        long seed = 0x51073EEDL;
        Random rng = new Random(seed);
        Pixmap px = new Pixmap(TEX, TEX, Pixmap.Format.RGBA8888);

        // Base stone
        for (int y = 0; y < TEX; y++) {
            for (int x = 0; x < TEX; x++) {
                float n = valueNoise(x * 0.15f, y * 0.15f, seed);
                float n2 = valueNoise(x * 0.4f, y * 0.4f, seed + 2);
                float blend = n * 0.6f + n2 * 0.4f;
                int[] c = blendColors(STONE_DARK, STONE_LIGHT, blend);
                px.setColor(c[0]/255f, c[1]/255f, c[2]/255f, 1f);
                px.drawPixel(x, y);
            }
        }
        // Mortar lines (block pattern)
        int BLOCK_W = TEX / 3, BLOCK_H = TEX / 4;
        px.setColor(STONE_DARK[0]/255f * 0.7f, STONE_DARK[1]/255f * 0.7f, STONE_DARK[2]/255f * 0.7f, 0.7f);
        for (int row = 0; row <= 4; row++) {
            int y = row * BLOCK_H;
            if (y < TEX) px.drawLine(0, y, TEX, y);
        }
        for (int row = 0; row <= 4; row++) {
            int offset = (row % 2 == 0) ? 0 : BLOCK_W / 2;
            for (int col = 0; col < 4; col++) {
                int x = offset + col * BLOCK_W;
                int y0 = row * BLOCK_H, y1 = y0 + BLOCK_H;
                if (x < TEX && y0 < TEX && y1 < TEX) px.drawLine(x, y0, x, y1);
            }
        }
        // Moss patches on stone
        int mossCount = 3 + rng.nextInt(4);
        for (int i = 0; i < mossCount; i++) {
            int mx = rng.nextInt(TEX - 8) + 4, my = rng.nextInt(TEX - 8) + 4;
            px.setColor(MOSS[0]/255f, MOSS[1]/255f, MOSS[2]/255f, 0.4f + rng.nextFloat() * 0.3f);
            fillEllipse(px, mx, my, 4 + rng.nextInt(8), 3 + rng.nextInt(5));
        }
        // Cracks
        for (int i = 0; i < 2; i++) {
            int cx = rng.nextInt(TEX - 10) + 5, cy = rng.nextInt(TEX - 10) + 5;
            px.setColor(STONE_DARK[0]/255f * 0.6f, STONE_DARK[1]/255f * 0.6f, STONE_DARK[2]/255f * 0.6f, 0.6f);
            px.drawLine(cx, cy, cx + rng.nextInt(10) - 5, cy + rng.nextInt(8));
        }

        Texture t = new Texture(px);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        px.dispose();
        return t;
    }

    // -------------------------------------------------------------------------
    // Tree canopy sprites (multiple variants)
    // -------------------------------------------------------------------------

    /**
     * Creates a top-down tree canopy sprite.
     * Returns a TEX*2 × TEX*2 pixmap (192×192) for large organic shape.
     * variant 0-5: different colour palettes
     * size 0=small, 1=medium, 2=large, 3=ancient
     */
    public static Texture treeSprite(int variant, int size) {
        long seed = 0x73EE5EEDL + variant * 0x1234567L + size * 0xABCDEFL;
        Random rng = new Random(seed);

        int[] vc = TREE_VARIANTS[variant % TREE_VARIANTS.length];
        int baseR = vc[0], baseG = vc[1], baseB = vc[2];
        int midR  = vc[3], midG  = vc[4], midB  = vc[5];
        int darkR = vc[6], darkG = vc[7], darkB = vc[8];

        int dim = switch (size) {
            case 0 -> 80;   // small
            case 2 -> 144;  // large
            case 3 -> 160;  // ancient
            default -> 112; // medium
        };
        int halfDim = dim / 2;

        Pixmap px = new Pixmap(dim, dim, Pixmap.Format.RGBA8888);
        px.setColor(0, 0, 0, 0);
        px.fill(); // transparent

        float cx = halfDim, cy = halfDim;
        float baseR2 = halfDim - 4;

        // Step 1 — shadow blob (darker, offset down-right)
        int shadowOff = 5 + size * 2;
        px.setColor(0f, 0f, 0f, 0.22f);
        fillBlobCircle(px, (int)cx + shadowOff, (int)cy + shadowOff, (int)(baseR2 * 0.85f), rng.nextLong());

        // Step 2 — outer canopy edge (dark)
        px.setColor(darkR/255f, darkG/255f, darkB/255f, 0.95f);
        fillBlobCircle(px, (int)cx, (int)cy, (int)baseR2, seed);

        // Step 3 — main canopy (base colour)
        px.setColor(baseR/255f, baseG/255f, baseB/255f, 1f);
        fillBlobCircle(px, (int)cx, (int)cy, (int)(baseR2 * 0.82f), seed + 1);

        // Step 4 — mid-tone cluster (upper-left, lit side)
        px.setColor(midR/255f, midG/255f, midB/255f, 0.88f);
        int litX = (int)(cx - baseR2 * 0.25f);
        int litY = (int)(cy - baseR2 * 0.20f);
        fillBlobCircle(px, litX, litY, (int)(baseR2 * 0.55f), seed + 2);

        // Step 5 — highlight cluster (bright, upper-left)
        int brR = Math.min(255, midR + 28), brG = Math.min(255, midG + 28), brB = Math.min(255, midB + 20);
        px.setColor(brR/255f, brG/255f, brB/255f, 0.65f);
        fillBlobCircle(px, litX - 4, litY - 4, (int)(baseR2 * 0.30f), seed + 3);

        // Step 6 — secondary highlight blobs
        int blobCount = 3 + size;
        for (int i = 0; i < blobCount; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = rng.nextDouble() * baseR2 * 0.5;
            int bx = (int)(cx + Math.cos(angle) * dist);
            int by = (int)(cy + Math.sin(angle) * dist);
            float bAlpha = 0.35f + rng.nextFloat() * 0.30f;
            px.setColor(midR/255f, midG/255f + 0.05f, midB/255f, bAlpha);
            fillBlobCircle(px, bx, by, 4 + rng.nextInt(8 + size * 3), seed + 4 + i);
        }

        // Step 7 — small bright dots (dappled light through canopy)
        int dappleCount = 5 + size * 3;
        for (int i = 0; i < dappleCount; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double d = rng.nextDouble() * baseR2 * 0.65;
            int dx = (int)(cx + Math.cos(a) * d);
            int dy = (int)(cy + Math.sin(a) * d);
            if (dx >= 0 && dx < dim && dy >= 0 && dy < dim) {
                px.setColor(brR/255f, brG/255f, brB/255f, 0.25f + rng.nextFloat() * 0.25f);
                px.fillCircle(dx, dy, 1 + rng.nextInt(2));
            }
        }

        // Step 8 — trunk hint at bottom centre
        int trunkW = 6 + size * 2;
        int trunkH = 10 + size * 3;
        int trunkX = (int)(cx - trunkW/2f), trunkY = (int)(cy + baseR2 * 0.45f);
        px.setColor(0.28f, 0.20f, 0.12f, 0.85f);
        if (trunkX >= 0 && trunkY >= 0 && trunkX + trunkW < dim && trunkY + trunkH < dim)
            px.fillRectangle(trunkX, trunkY, trunkW, trunkH);

        // Step 9 — ancient size: visible roots
        if (size == 3) {
            px.setColor(0.25f, 0.18f, 0.10f, 0.55f);
            int rootY = (int)(cy + baseR2 * 0.60f);
            for (int r = -2; r <= 2; r++) {
                px.drawLine((int)cx + r * 12, rootY, (int)cx + r * 18, rootY + 12 + rng.nextInt(8));
            }
        }

        Texture t = new Texture(px);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        px.dispose();
        return t;
    }

    // -------------------------------------------------------------------------
    // Rock sprite
    // -------------------------------------------------------------------------

    /** size: 0=small pebble, 1=medium, 2=large boulder */
    public static Texture rockSprite(int size, long seed) {
        Random rng = new Random(seed);
        int dim = switch (size) {
            case 0 -> 24;
            case 2 -> 80;
            default -> 48;
        };

        Pixmap px = new Pixmap(dim, dim, Pixmap.Format.RGBA8888);
        px.setColor(0, 0, 0, 0);
        px.fill();

        int cx = dim / 2, cy = dim / 2;
        int rad = dim / 2 - 3;

        // Shadow
        px.setColor(0, 0, 0, 0.25f);
        fillBlobCircle(px, cx + 3, cy + 4, (int)(rad * 0.85f), seed + 99);

        // Base rock
        for (int y = 0; y < dim; y++) {
            for (int x = 0; x < dim; x++) {
                float dx = x - cx, dy = y - cy;
                float d = (float)Math.sqrt(dx*dx + dy*dy);
                float n = valueNoise(x * 0.25f, y * 0.25f, seed) * 4;
                if (d < rad + n - 1) {
                    float lit = 1f - (dx * 0.3f + dy * 0.4f) / (rad * 2);
                    lit = clampf(lit * 0.6f + 0.4f);
                    int[] c = blendColors(STONE_DARK, STONE_LIGHT, lit);
                    px.setColor(c[0]/255f, c[1]/255f, c[2]/255f, 1f);
                    px.drawPixel(x, y);
                }
            }
        }

        // Moss patches on larger rocks
        if (size >= 1) {
            int mossCount = 1 + rng.nextInt(3);
            for (int i = 0; i < mossCount; i++) {
                int mx = cx + rng.nextInt(rad) - rad/2;
                int my = cy + rng.nextInt(rad) - rad/2;
                px.setColor(MOSS[0]/255f, MOSS[1]/255f, MOSS[2]/255f, 0.45f + rng.nextFloat() * 0.3f);
                fillEllipse(px, mx, my, 4 + rng.nextInt(rad/2), 3 + rng.nextInt(Math.max(1, rad/3)));
            }
        }

        // Highlight
        px.setColor(STONE_LIGHT[0]/255f, STONE_LIGHT[1]/255f, STONE_LIGHT[2]/255f, 0.45f);
        px.fillCircle(cx - rad/3, cy - rad/3, Math.max(1, rad/4));

        Texture t = new Texture(px);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        px.dispose();
        return t;
    }

    // -------------------------------------------------------------------------
    // Bush / undergrowth sprite
    // -------------------------------------------------------------------------

    public static Texture bushSprite(int variant) {
        long seed = 0xB050000L + variant * 0x777L;
        Random rng = new Random(seed);
        int dim = 56 + variant * 8;

        Pixmap px = new Pixmap(dim, dim, Pixmap.Format.RGBA8888);
        px.setColor(0, 0, 0, 0);
        px.fill();

        int cx = dim/2, cy = (int)(dim * 0.52f);
        int[] vc = TREE_VARIANTS[variant % TREE_VARIANTS.length];

        // Shadow
        px.setColor(0f, 0f, 0f, 0.18f);
        fillEllipse(px, cx - dim/4, cy + 4, dim/2, dim/5);

        // Base blob
        px.setColor(vc[6]/255f, vc[7]/255f, vc[8]/255f, 1f);
        fillEllipse(px, cx - dim/3, cy - dim/4, (int)(dim*0.65f), (int)(dim*0.45f));

        // Mid
        px.setColor(vc[0]/255f, vc[1]/255f, vc[2]/255f, 0.9f);
        fillEllipse(px, cx - dim/4, cy - dim/3, (int)(dim*0.50f), (int)(dim*0.40f));

        // Sub-blobs
        for (int i = 0; i < 3 + variant; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            int bx = cx + (int)(Math.cos(a) * dim * 0.18);
            int by = cy + (int)(Math.sin(a) * dim * 0.12);
            px.setColor(vc[3]/255f, vc[4]/255f, vc[5]/255f, 0.7f);
            px.fillCircle(bx, by, 6 + rng.nextInt(8));
        }

        // Highlight
        px.setColor(vc[3]/255f + 0.1f, vc[4]/255f + 0.1f, vc[5]/255f + 0.08f, 0.5f);
        px.fillCircle(cx - dim/6, cy - dim/5, dim/8);

        // Small berries (variant 2-3)
        if (variant >= 2) {
            for (int i = 0; i < 4; i++) {
                int bx = cx - dim/4 + rng.nextInt(dim/2);
                int by = cy - dim/4 + rng.nextInt(dim/3);
                px.setColor(0.82f, 0.18f, 0.18f, 0.9f);
                px.fillCircle(bx, by, 2);
            }
        }

        Texture t = new Texture(px);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        px.dispose();
        return t;
    }

    // -------------------------------------------------------------------------
    // Fern sprite
    // -------------------------------------------------------------------------

    public static Texture fernSprite() {
        long seed = 0xFE45EEDL;
        Random rng = new Random(seed);
        int dim = 48;
        Pixmap px = new Pixmap(dim, dim, Pixmap.Format.RGBA8888);
        px.setColor(0, 0, 0, 0);
        px.fill();

        // Draw fronds radiating from base
        int stemX = dim/2, stemY = (int)(dim * 0.8f);
        int frondCount = 5 + rng.nextInt(4);
        for (int i = 0; i < frondCount; i++) {
            double angle = (Math.PI * 1.2) + (i - frondCount/2.0) * (Math.PI * 0.22);
            int len = 12 + rng.nextInt(10);
            float shade = 0.7f + rng.nextFloat() * 0.3f;
            px.setColor(GRASS_DARK[0]/255f * shade, (GRASS_DARK[1]+22)/255f * shade, GRASS_DARK[2]/255f * shade, 0.9f);
            int ex = stemX + (int)(Math.cos(angle) * len);
            int ey = stemY + (int)(Math.sin(angle) * len);
            px.drawLine(stemX, stemY, ex, ey);
            // Leaflets along frond
            for (int l = 1; l < len - 2; l += 3) {
                int lx = stemX + (int)(Math.cos(angle) * l);
                int ly = stemY + (int)(Math.sin(angle) * l);
                double la = angle + Math.PI/3;
                double la2 = angle - Math.PI/3;
                px.drawLine(lx, ly, lx + (int)(Math.cos(la) * 3), ly + (int)(Math.sin(la) * 3));
                px.drawLine(lx, ly, lx + (int)(Math.cos(la2) * 3), ly + (int)(Math.sin(la2) * 3));
            }
        }

        Texture t = new Texture(px);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        px.dispose();
        return t;
    }

    // -------------------------------------------------------------------------
    // Fallen log sprite
    // -------------------------------------------------------------------------

    public static Texture fallenLogSprite() {
        long seed = 0x10654A7EL;
        Random rng = new Random(seed);
        int W = 80, H = 28;
        Pixmap px = new Pixmap(W, H, Pixmap.Format.RGBA8888);
        px.setColor(0, 0, 0, 0);
        px.fill();

        // Shadow
        px.setColor(0f, 0f, 0f, 0.2f);
        fillEllipse(px, 8, H-10, W-12, 10);

        // Log body
        for (int x = 4; x < W - 4; x++) {
            float bark = valueNoise(x * 0.3f, 0, seed) * 5 - 2;
            int[] c = blendColors(new int[]{62,38,18}, new int[]{90,58,28}, (x % 8) / 8f);
            float lit = 1f - (float)Math.abs(bark) * 0.1f;
            px.setColor(c[0]/255f * lit, c[1]/255f * lit, c[2]/255f * lit, 1f);
            // Draw log cross-section oval at each x
            int r = 7 + (int)bark;
            r = Math.max(3, Math.min(r, 11));
            for (int dy = -r; dy <= r; dy++) {
                int py = H/2 + dy;
                if (py >= 0 && py < H) {
                    float alpha = 1f - (float)(Math.abs(dy)) / (r + 1);
                    px.setColor(c[0]/255f * lit * alpha + (1f-alpha)*0.1f,
                                c[1]/255f * lit * alpha,
                                c[2]/255f * lit * alpha,
                                0.85f + alpha * 0.15f);
                    px.drawPixel(x, py);
                }
            }
        }
        // Bark lines
        px.setColor(0.22f, 0.13f, 0.06f, 0.4f);
        for (int i = 0; i < 5; i++) {
            int bx = 8 + rng.nextInt(W - 16);
            px.drawLine(bx, H/2 - 4, bx, H/2 + 4);
        }
        // End rings (circles for wood grain look)
        px.setColor(0.38f, 0.24f, 0.12f, 0.9f);
        fillEllipse(px, 4, H/2 - 7, 10, 14);
        px.setColor(0.52f, 0.36f, 0.18f, 0.7f);
        fillEllipse(px, 6, H/2 - 5, 6, 10);

        Texture t = new Texture(px);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        px.dispose();
        return t;
    }

    // -------------------------------------------------------------------------
    // Grass tuft overlay (placed over ground tiles)
    // -------------------------------------------------------------------------

    public static Texture grassTuftSprite(int variant) {
        long seed = 0x70F0000L + variant;
        Random rng = new Random(seed);
        int dim = 24;
        Pixmap px = new Pixmap(dim, dim, Pixmap.Format.RGBA8888);
        px.setColor(0, 0, 0, 0);
        px.fill();

        int blades = 4 + rng.nextInt(5);
        for (int i = 0; i < blades; i++) {
            int bx = dim/2 + rng.nextInt(8) - 4;
            int by = dim - 2;
            int len = 8 + rng.nextInt(8);
            int dx = rng.nextInt(5) - 2;
            float[] c = {GRASS_LIGHT[0]/255f + rng.nextFloat()*0.1f,
                         GRASS_LIGHT[1]/255f + rng.nextFloat()*0.1f,
                         GRASS_LIGHT[2]/255f};
            px.setColor(c[0], c[1], c[2], 0.85f);
            px.drawLine(bx, by, bx + dx, by - len);
            px.setColor(c[0]*0.85f, c[1]*0.85f, c[2]*0.85f, 0.5f);
            px.drawLine(bx + 1, by, bx + dx + 1, by - len + 2);
        }

        Texture t = new Texture(px);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        px.dispose();
        return t;
    }

    // -------------------------------------------------------------------------
    // Terrain transition: grass→dirt blended edge strip
    // -------------------------------------------------------------------------

    /** Creates an edge transition strip: one side = fromColor, other = to, with organic boundary */
    public static Texture transitionEdge(int[] fromColor, int[] toColor, boolean horizontal, long seed) {
        Pixmap px = new Pixmap(TEX, TEX, Pixmap.Format.RGBA8888);

        for (int y = 0; y < TEX; y++) {
            for (int x = 0; x < TEX; x++) {
                float pos = horizontal ? x / (float)TEX : y / (float)TEX;
                float noise = valueNoise(
                    horizontal ? y * 0.25f : x * 0.25f,
                    horizontal ? x * 0.05f : y * 0.05f,
                    seed) * 0.35f - 0.175f;
                float t2 = clampf(pos + noise);
                // Smooth step
                t2 = t2 * t2 * (3 - 2 * t2);
                int[] c = blendColors(fromColor, toColor, t2);
                px.setColor(c[0]/255f, c[1]/255f, c[2]/255f, 1f);
                px.drawPixel(x, y);
            }
        }

        Texture t = new Texture(px);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        px.dispose();
        return t;
    }

    // -------------------------------------------------------------------------
    // Utility methods
    // -------------------------------------------------------------------------

    /** Simple value noise using hash function — no external deps */
    static float valueNoise(float x, float y, long seed) {
        int xi = (int)Math.floor(x), yi = (int)Math.floor(y);
        float xf = x - xi, yf = y - yi;
        float tl = hash(xi,   yi,   seed);
        float tr = hash(xi+1, yi,   seed);
        float bl = hash(xi,   yi+1, seed);
        float br = hash(xi+1, yi+1, seed);
        float tx = xf * xf * (3 - 2 * xf); // smooth step
        float ty = yf * yf * (3 - 2 * yf);
        return lerp(lerp(tl, tr, tx), lerp(bl, br, tx), ty);
    }

    static float hash(int x, int y, long seed) {
        long h = seed + x * 374761393L + y * 668265263L;
        h = (h ^ (h >> 13)) * 1274126177L;
        return ((h ^ (h >> 16)) & 0xFFFFL) / (float)0xFFFFL;
    }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    static float clampf(float v) { return Math.max(0f, Math.min(1f, v)); }

    static int[] blendColors(int[] a, int[] b, float t) {
        t = clampf(t);
        return new int[]{
            (int)(a[0] + (b[0] - a[0]) * t),
            (int)(a[1] + (b[1] - a[1]) * t),
            (int)(a[2] + (b[2] - a[2]) * t)
        };
    }

    static void paintPebble(Pixmap px, int cx, int cy, Random rng) {
        int size = 2 + rng.nextInt(3);
        px.setColor(PEBBLE_DARK[0]/255f, PEBBLE_DARK[1]/255f, PEBBLE_DARK[2]/255f, 0.7f);
        fillEllipse(px, cx - size/2, cy - size/2, size + 1, size);
        px.setColor(PEBBLE[0]/255f, PEBBLE[1]/255f, PEBBLE[2]/255f, 0.55f);
        fillEllipse(px, cx - size/2, cy - size/2, size - 1, size - 1);
    }

    static void applyEdgeDarken(Pixmap px, float strength, int edgeWidth) {
        for (int y = 0; y < TEX; y++) {
            for (int x = 0; x < TEX; x++) {
                int minDist = Math.min(Math.min(x, TEX - 1 - x), Math.min(y, TEX - 1 - y));
                if (minDist < edgeWidth) {
                    float alpha = strength * (1f - minDist / (float)edgeWidth);
                    int col = px.getPixel(x, y);
                    float r = ((col >> 24) & 0xFF) / 255f;
                    float g = ((col >> 16) & 0xFF) / 255f;
                    float b = ((col >> 8) & 0xFF) / 255f;
                    float a = (col & 0xFF) / 255f;
                    px.setColor(r * (1 - alpha), g * (1 - alpha), b * (1 - alpha), a);
                    px.drawPixel(x, y);
                }
            }
        }
    }

    /**
     * Draw a filled ellipse on a Pixmap (LibGDX Pixmap has no fillOval).
     * Uses midpoint ellipse algorithm.
     */
    static void fillEllipse(Pixmap px, int x0, int y0, int w, int h) {
        if (w <= 0 || h <= 0) return;
        int dim = px.getWidth(), dimH = px.getHeight();
        float cx = x0 + w / 2f, cy = y0 + h / 2f;
        float rx = w / 2f, ry = h / 2f;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                if (x < 0 || x >= dim || y < 0 || y >= dimH) continue;
                float dx = (x - cx) / rx, dy = (y - cy) / ry;
                if (dx * dx + dy * dy <= 1f) px.drawPixel(x, y);
            }
        }
    }

    /** Fill a roughly circular organic blob */
    static void fillBlobCircle(Pixmap px, int cx, int cy, int r, long seed) {
        if (r <= 0) return;
        int dim = px.getWidth();
        int dimH = px.getHeight();
        for (int y = cy - r - 4; y <= cy + r + 4; y++) {
            for (int x = cx - r - 4; x <= cx + r + 4; x++) {
                if (x < 0 || x >= dim || y < 0 || y >= dimH) continue;
                float dx = x - cx, dy = y - cy;
                float dist = (float)Math.sqrt(dx*dx + dy*dy);
                float noise = valueNoise(x * 0.25f, y * 0.25f, seed) * r * 0.3f;
                if (dist < r + noise - 1) {
                    px.drawPixel(x, y);
                }
            }
        }
    }
}
