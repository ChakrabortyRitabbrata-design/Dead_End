package main;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

import tile.TileManager;

/**
 * DecorRenderer — draws environmental objects on top of the ground layer.
 *
 * Uses only the VERIFIED sprites extracted from the actual Forest RPG tileset
 * (media_1793520886268_0e8f9f36.jpg).
 *
 * Two rendering passes:
 *   drawBelowPlayer() — flat objects: grass tufts, small rocks, stumps, logs
 *   drawAbovePlayer() — tall objects: trees, large rocks, bushes (Y-sorted)
 *
 * Placement is DETERMINISTIC (seeded RNG) — same map = same visual every run.
 */
public class DecorRenderer {

    // =========================================================================
    // Entry class
    // =========================================================================

    public static class DecorEntry {
        public int worldX, worldY, baseY;
        public BufferedImage image;
        public int shadowX, shadowY, shadowR;

        DecorEntry(int wx, int wy, BufferedImage img, int shX, int shY, int shR) {
            worldX = wx; worldY = wy; image = img;
            baseY  = wy + img.getHeight();
            shadowX = shX; shadowY = shY; shadowR = shR;
        }
    }

    // =========================================================================
    // Fields
    // =========================================================================

    private final GamePanel gp;

    // Trees — large (row 0 of tileset)
    private BufferedImage treeDecidLg, treePineLg, treePineLg2, treeExtraLg;
    // Trees — medium (row 1 of tileset)
    private BufferedImage treeDecidMd, treePineMd, treeDecidMd2, treeExtraMd;
    // Rocks
    private BufferedImage rockMossyLg, rockGreyMd, rockGreySm, rockPebbles;
    private BufferedImage stonesCluster, stoneSingle;
    // Stumps
    private BufferedImage stumpLarge, stumpSmall;
    // Logs
    private BufferedImage logPlain, logMossy;
    // Vegetation
    private BufferedImage bushLarge, bushFlowers, bushBerryLg;
    private BufferedImage grassTuft1, grassTuft2, grassTuft3;
    private BufferedImage flowerWhite, flowerYellow;

    private final List<DecorEntry> below = new ArrayList<>();
    private final List<DecorEntry> above = new ArrayList<>();

    // =========================================================================
    // Construction
    // =========================================================================

    public DecorRenderer(GamePanel gp) {
        this.gp = gp;
        loadImages();
    }

    /** Load each verified sprite at its intended render size. */
    private void loadImages() {
        // Large trees — taller than wide, need good vertical space
        treeDecidLg  = load("tree_deciduous_lg",  96, 120);
        treePineLg   = load("tree_pine_lg",        80, 120);
        treePineLg2  = load("tree_pine_lg2",       72, 120);
        treeExtraLg  = load("tree_extra_lg",       80, 120);
        // Medium trees
        treeDecidMd  = load("tree_deciduous_md",   80,  90);
        treePineMd   = load("tree_pine_md",        72,  90);
        treeDecidMd2 = load("tree_deciduous_md2",  88,  90);
        treeExtraMd  = load("tree_extra_md",       72,  90);
        // Rocks
        rockMossyLg    = load("rock_mossy_lg",   80, 56);
        rockGreyMd     = load("rock_grey_md",    70, 56);
        rockGreySm     = load("rock_grey_sm",    56, 44);
        rockPebbles    = load("rock_pebbles",    44, 40);
        stonesCluster  = load("stones_cluster",  40, 36);
        stoneSingle    = load("stone_single",    36, 32);
        // Stumps & logs
        stumpLarge = load("stump_large", 64, 56);
        stumpSmall = load("stump_small", 56, 48);
        logPlain   = load("log_plain",   80, 48);
        logMossy   = load("log_mossy",   64, 48);
        // Vegetation
        bushLarge    = load("bush_large",    64, 48);
        bushFlowers  = load("bush_flowers",  56, 48);
        bushBerryLg  = load("bush_berry_lg", 96, 48);
        grassTuft1   = load("grass_tuft1",   48, 40);
        grassTuft2   = load("grass_tuft2",   36, 36);
        grassTuft3   = load("grass_tuft3",   44, 36);
        flowerWhite  = load("flower_white",  44, 36);
        flowerYellow = load("flower_yellow", 36, 36);
    }

    private BufferedImage load(String name, int w, int h) {
        try (InputStream is = openResource("tile/" + name + ".png")) {
            BufferedImage raw = ImageIO.read(is);
            if (raw == null) return fallback(w, h);
            return TileManager.scaleNearest(raw, w, h);
        } catch (Exception e) {
            return fallback(w, h);
        }
    }

    private BufferedImage fallback(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(60, 120, 40, 180));
        g.fillOval(0, h/3, w, h*2/3);
        g.setColor(new Color(80, 50, 20, 200));
        g.fillRect(w/2-3, h/2, 6, h/2);
        g.dispose();
        return img;
    }

    // =========================================================================
    // Build decor list
    // =========================================================================

    /**
     * Scan the tile map and deterministically place decor objects.
     * Called once after each level load.
     *
     * Tile IDs that trigger decor:
     *   0  = grass  → sparse trees, bushes, grass tufts
     *   1  = dirt   → occasional small rock/pebble (not in road centre)
     *   2  = water  → nothing
     *   8  = flower grass → flowers, grass tufts
     *  18  = lake   → nothing
     * all shore IDs (3-7,19) → occasional stump or small rock
     *
     * Forest zones are identified by surrounding context (dense tree tiles
     * in the collision layer drive large tree placement).
     */
    public void buildDecorList() {
        below.clear();
        above.clear();

        int[][] map  = gp.tileM.mapTilenum;
        int[][] coll = gp.tileM.collisionLayer;
        int     ts   = gp.tileSize;
        int     cols = gp.maxWorldCol;
        int     rows = gp.maxWorldRow;

        Random rng = new Random(98765L);  // Fixed seed for determinism

        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                int tid = map[col][row];
                int wx  = col * ts;
                int wy  = row * ts;

                // ── FOREST ZONES: collision=1 tiles surrounded by more collision ──
                // Place large/medium trees on collision tiles
                if (coll[col][row] == 1 && (tid == 0 || tid == 8)) {
                    // Count collision neighbours
                    int collNeighbours = 0;
                    for (int dc = -1; dc <= 1; dc++)
                        for (int dr = -1; dr <= 1; dr++) {
                            int nc = col+dc, nr = row+dr;
                            if (nc>=0 && nc<cols && nr>=0 && nr<rows && coll[nc][nr]==1)
                                collNeighbours++;
                        }
                    // More neighbours = denser forest = larger trees
                    placeTree(rng, wx, wy, ts, collNeighbours);
                    continue;  // skip other decor on this tile
                }

                // ── OPEN GRASS (tile 0, walkable) ──
                if (tid == 0 && coll[col][row] == 0) {
                    float r = rng.nextFloat();
                    if (r < 0.025f) {
                        // Isolated medium tree in clearing
                        BufferedImage t = rng.nextBoolean() ? treeDecidMd : treePineMd;
                        int dx = ts/2 - t.getWidth()/2;
                        int dy = -t.getHeight() + ts;
                        addAbove(wx+dx, wy+dy, t, ts*5/8);
                    } else if (r < 0.06f) {
                        // Bush
                        BufferedImage b = pickBush(rng);
                        addBelow(wx + rng.nextInt(ts - b.getWidth() + 1),
                                 wy + ts - b.getHeight(), b);
                    } else if (r < 0.14f) {
                        // Grass tuft or flower
                        BufferedImage v = pickVegetation(rng);
                        addBelow(wx + rng.nextInt(Math.max(1, ts - v.getWidth())),
                                 wy + ts - v.getHeight(), v);
                    } else if (r < 0.17f) {
                        // Small rock
                        BufferedImage rk = rng.nextBoolean() ? stonesCluster : stoneSingle;
                        addBelow(wx + rng.nextInt(Math.max(1, ts - rk.getWidth())),
                                 wy + ts - rk.getHeight(), rk);
                    }
                }

                // ── FLOWER GRASS (tile 8) ──
                else if (tid == 8) {
                    float r = rng.nextFloat();
                    if (r < 0.20f) {
                        BufferedImage v = pickVegetation(rng);
                        addBelow(wx + rng.nextInt(Math.max(1, ts - v.getWidth())),
                                 wy + ts - v.getHeight(), v);
                    } else if (r < 0.25f) {
                        BufferedImage b = pickBush(rng);
                        addBelow(wx + rng.nextInt(Math.max(1, ts - b.getWidth())),
                                 wy + ts - b.getHeight(), b);
                    }
                }

                // ── SHORELINES / LAKE EDGE (tiles 3–7, 19) ──
                else if (tid >= 3 && tid <= 7 || tid == 19) {
                    float r = rng.nextFloat();
                    if (r < 0.05f) {
                        // Stump near water
                        BufferedImage st = rng.nextBoolean() ? stumpLarge : stumpSmall;
                        addBelow(wx + rng.nextInt(Math.max(1, ts - st.getWidth())),
                                 wy + ts - st.getHeight(), st);
                    } else if (r < 0.09f) {
                        addBelow(wx + rng.nextInt(Math.max(1, ts - rockGreySm.getWidth())),
                                 wy + ts - rockGreySm.getHeight(), rockGreySm);
                    }
                }

                // ── DIRT PATH (tile 1) ──
                else if (tid == 1) {
                    float r = rng.nextFloat();
                    if (r < 0.03f) {
                        // Rare pebble on path
                        addBelow(wx + rng.nextInt(Math.max(1, ts - rockPebbles.getWidth())),
                                 wy + ts - rockPebbles.getHeight(), rockPebbles);
                    }
                }
            }
        }

        // Y-sort tall objects for correct depth rendering
        above.sort(Comparator.comparingInt(e -> e.baseY));
    }

    // -------------------------------------------------------------------------
    // Tree placement
    // -------------------------------------------------------------------------

    private void placeTree(Random rng, int wx, int wy, int ts, int density) {
        // Choose size based on density of surrounding collision tiles
        boolean lg = density >= 6;
        boolean md = density >= 3;

        BufferedImage img;
        float r = rng.nextFloat();
        if (lg) {
            img = r < 0.25f ? treePineLg : r < 0.50f ? treePineLg2
                : r < 0.70f ? treeExtraLg : treeDecidLg;
        } else if (md) {
            img = r < 0.30f ? treePineMd : r < 0.55f ? treeDecidMd2
                : r < 0.75f ? treeExtraMd : treeDecidMd;
        } else {
            // Edge — small/medium
            img = r < 0.50f ? treeDecidMd : treePineMd;
        }

        int dx = ts/2 - img.getWidth()/2 + rng.nextInt(9) - 4;
        int dy = -img.getHeight() + ts;
        int shR = img.getWidth() * 2/5;
        addAbove(wx+dx, wy+dy, img, shR);
    }

    private BufferedImage pickBush(Random rng) {
        int r = rng.nextInt(3);
        return r == 0 ? bushLarge : r == 1 ? bushFlowers : bushBerryLg;
    }

    private BufferedImage pickVegetation(Random rng) {
        int r = rng.nextInt(5);
        return switch(r) {
            case 0 -> grassTuft1;
            case 1 -> grassTuft2;
            case 2 -> grassTuft3;
            case 3 -> flowerWhite;
            default -> flowerYellow;
        };
    }

    private void addAbove(int wx, int wy, BufferedImage img, int shR) {
        int baseY = wy + img.getHeight();
        above.add(new DecorEntry(wx, wy, img,
                  wx + img.getWidth()/2, baseY - 4, shR));
    }

    private void addBelow(int wx, int wy, BufferedImage img) {
        below.add(new DecorEntry(wx, wy, img, 0, 0, 0));
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    /** Draw flat ground-level objects (tufts, rocks, logs). */
    public void drawBelowPlayer(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (DecorEntry e : below) {
            int sx = e.worldX - gp.player.worldX + gp.player.screenX;
            int sy = e.worldY - gp.player.worldY + gp.player.screenY;
            if (onScreen(sx, sy, e.image)) g2.drawImage(e.image, sx, sy, null);
        }
    }

    /** Draw tall Y-sorted objects (trees, large rocks). */
    public void drawAbovePlayer(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (DecorEntry e : above) {
            int sx = e.worldX - gp.player.worldX + gp.player.screenX;
            int sy = e.worldY - gp.player.worldY + gp.player.screenY;
            if (!onScreen(sx, sy, e.image)) continue;
            if (e.shadowR > 0) drawShadow(g2, e.shadowX, e.shadowY, e.shadowR);
            g2.drawImage(e.image, sx, sy, null);
        }
    }

    private void drawShadow(Graphics2D g2, int wx, int wy, int r) {
        int sx = wx - gp.player.worldX + gp.player.screenX;
        int sy = wy - gp.player.worldY + gp.player.screenY;
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
        g2.setColor(Color.BLACK);
        g2.fillOval(sx - r, sy - r/4, r*2, r/2);
        g2.setComposite(old);
    }

    private boolean onScreen(int sx, int sy, BufferedImage img) {
        return sx + img.getWidth()  > -32 && sx < gp.screenWidth  + 32
            && sy + img.getHeight() > -64 && sy < gp.screenHeight + 64;
    }

    // =========================================================================
    // Resource loading
    // =========================================================================

    private InputStream openResource(String path) throws IOException {
        InputStream s = getClass().getResourceAsStream("/" + path);
        if (s != null) return s;
        Path p = Paths.get("res", path);
        if (!Files.exists(p)) p = Paths.get("bin", path);
        if (!Files.exists(p)) throw new IOException("Not found: " + path);
        return Files.newInputStream(p);
    }
}
