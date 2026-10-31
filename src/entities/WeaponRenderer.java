package entities;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import inventory.Weapon;
import main.GamePanel;

/**
 * WeaponRenderer
 * ─────────────
 * Draws the equipped weapon in the player's hand (idle) and plays
 * the full swing animation (attacking).  All rendering is pure Java 2D –
 * no external sprites are needed.
 *
 * Animation model
 * ───────────────
 *  • IDLE    – weapon sits at the player's side, angle fixed per direction.
 *  • WINDUP  – fast counter-rotation before the swing (~6 frames).
 *  • SWING   – main arc sweeps through ~130 ° (~14 frames).
 *  • IMPACT  – brief flash / trail fade (~8 frames).
 *  Total attack duration ≈ 28 frames @ 60 FPS ≈ 0.47 s.
 *
 * Particles
 * ─────────
 *  A handful of short-lived slash particles are spawned at IMPACT and
 *  drift outward.  They are stored in screen-space so they don't scroll.
 */
public class WeaponRenderer {

    // ─── Animation states ────────────────────────────────────────────────────
    public enum AttackState { IDLE, WINDUP, SWING, IMPACT }

    // ─── Timing (frames at 60 FPS) ───────────────────────────────────────────
    private static final int WINDUP_FRAMES = 6;
    private static final int SWING_FRAMES  = 14;
    private static final int IMPACT_FRAMES = 8;
    private static final int TOTAL_FRAMES  = WINDUP_FRAMES + SWING_FRAMES + IMPACT_FRAMES;

    /** Cooldown after a full combo before the player can attack again. */
    public static final int ATTACK_COOLDOWN = 30; // frames

    // ─── State ───────────────────────────────────────────────────────────────
    private AttackState state       = AttackState.IDLE;
    private int         attackFrame = 0;  // counts 0 … TOTAL_FRAMES-1
    private int         cooldown    = 0;  // counts down to 0

    // Weapon being animated (captured at attack-start so hotbar swap mid-swing is safe)
    private Weapon      activeWeapon = null;

    // ─── Particle system ─────────────────────────────────────────────────────
    private final List<Particle> particles = new ArrayList<>();

    // ─── Reference ───────────────────────────────────────────────────────────
    private final GamePanel gp;

    // ─── Colours per weapon class ─────────────────────────────────────────────
    private static final Color SWORD_BLADE  = new Color(200, 215, 230);
    private static final Color SWORD_GUARD  = new Color(180, 140,  40);
    private static final Color SWORD_HANDLE = new Color(110,  65,  20);

    private static final Color IRON_BLADE   = new Color(170, 185, 200);
    private static final Color IRON_GUARD   = new Color(100, 100, 110);
    private static final Color IRON_HANDLE  = new Color( 60,  60,  70);

    private static final Color AXE_HEAD     = new Color(180, 195, 210);
    private static final Color AXE_SHAFT    = new Color(120,  70,  25);

    // ─── Constructor ─────────────────────────────────────────────────────────
    public WeaponRenderer(GamePanel gp) { this.gp = gp; }

    // ═════════════════════════════════════════════════════════════════════════
    // Public API called by Player
    // ═════════════════════════════════════════════════════════════════════════

    /** Try to start an attack with the given weapon. Returns true if started. */
    public boolean startAttack(Weapon weapon) {
        if (weapon == null)               return false;
        if (cooldown > 0)                 return false;
        if (state != AttackState.IDLE)    return false;

        activeWeapon = weapon;
        state        = AttackState.WINDUP;
        attackFrame  = 0;
        return true;
    }

    public boolean isAttacking() { return state != AttackState.IDLE; }
    public boolean onCooldown()  { return cooldown > 0; }

    /** Advance the animation by one tick. Call once per game update. */
    public void update() {
        // Advance particles
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) { if (it.next().update()) it.remove(); }

        // Cooldown countdown
        if (cooldown > 0) { cooldown--; return; }

        if (state == AttackState.IDLE) return;

        attackFrame++;

        // State transitions
        if (state == AttackState.WINDUP && attackFrame >= WINDUP_FRAMES) {
            state = AttackState.SWING;
            attackFrame = 0;
        } else if (state == AttackState.SWING && attackFrame >= SWING_FRAMES) {
            state = AttackState.IMPACT;
            attackFrame = 0;
            spawnImpactParticles();
        } else if (state == AttackState.IMPACT && attackFrame >= IMPACT_FRAMES) {
            state        = AttackState.IDLE;
            attackFrame  = 0;
            activeWeapon = null;
            cooldown     = ATTACK_COOLDOWN;
        }
    }

    /**
     * Draw the weapon (idle or mid-swing) at the player's screen position.
     *
     * @param g2        Graphics2D context (game canvas)
     * @param px        player's screenX (top-left of the sprite tile)
     * @param py        player's screenY
     * @param direction player facing direction
     * @param weapon    currently equipped weapon (may be null)
     */
    public void draw(Graphics2D g2, int px, int py, String direction, Weapon weapon) {

        // Always draw particles first (they live after attack ends)
        drawParticles(g2);

        // Which weapon to draw?
        Weapon w = isAttacking() ? activeWeapon : weapon;
        if (w == null) return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        // Pivot: approximate "hand" position relative to sprite tile
        int ts = gp.tileSize; // 48 px
        int hx, hy;           // hand anchor in screen coords

        switch (direction) {
            case "up"    -> { hx = px + ts / 2 - 4; hy = py + ts / 2 - 2; }
            case "down"  -> { hx = px + ts / 2 + 4; hy = py + ts / 2 + 4; }
            case "left"  -> { hx = px + 8;           hy = py + ts / 2 + 2; }
            default      -> { hx = px + ts - 10;     hy = py + ts / 2 + 2; } // right
        }

        // Base (idle) angle for each direction
        double baseAngle = switch (direction) {
            case "up"    -> -Math.PI / 4;          //  -45°  (blade points up-right)
            case "down"  ->  Math.PI * 3 / 4;      // 135°   (blade points down-left)
            case "left"  ->  Math.PI + Math.PI / 4;// 225°
            default      ->  Math.PI / 4;           //  45°   (blade points right-down)
        };

        double angle = baseAngle;

        // Apply animation angle delta
        if (isAttacking()) {
            angle += getSwingDelta(direction);
        }

        // Draw weapon rotated around the hand pivot
        AffineTransform old = g2.getTransform();
        g2.rotate(angle, hx, hy);

        int wLen = ts - 6;   // weapon draw length (42 px for 48 px tiles)
        drawWeaponShape(g2, hx, hy, wLen, w, isAttacking());

        g2.setTransform(old);

        // Impact flash at end of swing
        if (state == AttackState.IMPACT && attackFrame < 4) {
            drawImpactFlash(g2, hx, hy, direction, w.weaponClass);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ═════════════════════════════════════════════════════════════════════════

    /** Returns the extra rotation (radians) applied during the attack animation. */
    private double getSwingDelta(String direction) {
        // Swing arc: 130° total from windup start to swing end
        double arcTotal = Math.toRadians(130);
        double windupPull = Math.toRadians(30); // counter-pull before swing

        // Flip arc direction for left-facing
        double flip = direction.equals("left") ? -1 : 1;

        return switch (state) {
            case WINDUP -> -flip * windupPull * (attackFrame / (double) WINDUP_FRAMES);
            case SWING  -> {
                double t = attackFrame / (double) SWING_FRAMES;
                // ease-in-out: starts fast, decelerates
                double tEased = t < 0.5
                        ? 2 * t * t
                        : 1 - Math.pow(-2 * t + 2, 2) / 2;
                yield flip * (arcTotal * tEased - windupPull);
            }
            case IMPACT -> flip * (arcTotal - windupPull);
            default     -> 0;
        };
    }

    /** Draws the weapon shape (sword blade+guard+handle OR axe head+shaft) from pivot. */
    private void drawWeaponShape(Graphics2D g2, int hx, int hy,
                                  int len, Weapon w, boolean attacking) {

        // Handle glow when swinging
        float alpha = 1.0f;
        if (attacking && state == AttackState.SWING) {
            alpha = 0.85f + 0.15f * (float)(attackFrame / (double) SWING_FRAMES);
        }
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        switch (w.weaponClass) {
            case "Axe"  -> drawAxeShape(g2, hx, hy, len, w.name);
            case "Bow"  -> drawBowShape(g2, hx, hy, len);
            default     -> drawSwordShape(g2, hx, hy, len, w.name); // Sword (all variants)
        }

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    private void drawSwordShape(Graphics2D g2, int hx, int hy, int len, String name) {
        boolean isIron = name.toLowerCase().contains("iron")
                      || name.toLowerCase().contains("steel");

        Color blade  = isIron ? IRON_BLADE  : SWORD_BLADE;
        Color guard  = isIron ? IRON_GUARD  : SWORD_GUARD;
        Color handle = isIron ? IRON_HANDLE : SWORD_HANDLE;

        int bladeStart = -len + 4;   // tip  (towards the swing direction)
        int bladeEnd   = -len / 3;   // where blade meets guard
        int handleEnd  = len / 5;    // pommel

        // Blade — tapered trapezoid drawn as polygon
        GeneralPath blade2d = new GeneralPath();
        blade2d.moveTo(hx - 1, hy + bladeStart);
        blade2d.lineTo(hx + 1, hy + bladeStart + 4);  // tip (narrowed)
        blade2d.lineTo(hx + 3, hy + bladeEnd);
        blade2d.lineTo(hx - 3, hy + bladeEnd);
        blade2d.closePath();
        g2.setColor(blade);
        g2.fill(blade2d);
        g2.setColor(blade.darker());
        g2.setStroke(new BasicStroke(0.8f));
        g2.draw(blade2d);

        // Blade fuller (center line detail)
        g2.setColor(new Color(240, 245, 255, 120));
        g2.setStroke(new BasicStroke(0.8f));
        g2.drawLine(hx, hy + bladeStart + 2, hx, hy + bladeEnd - 2);

        // Guard (crossguard)
        g2.setColor(guard);
        g2.setStroke(new BasicStroke(3.5f));
        g2.drawLine(hx - 6, hy + bladeEnd, hx + 6, hy + bladeEnd);
        g2.setColor(guard.darker());
        g2.setStroke(new BasicStroke(0.8f));
        g2.drawLine(hx - 6, hy + bladeEnd, hx + 6, hy + bladeEnd);

        // Handle / grip
        g2.setColor(handle);
        g2.setStroke(new BasicStroke(4f));
        g2.drawLine(hx, hy + bladeEnd + 2, hx, hy + handleEnd - 3);

        // Pommel
        g2.setColor(guard);
        g2.fillOval(hx - 4, hy + handleEnd - 4, 8, 8);
    }

    private void drawAxeShape(Graphics2D g2, int hx, int hy, int len, String name) {
        int shaftEnd = -len + 6;
        int shaftStart = len / 5;

        // Shaft
        g2.setColor(AXE_SHAFT);
        g2.setStroke(new BasicStroke(4f));
        g2.drawLine(hx, hy + shaftStart, hx, hy + shaftEnd);

        // Shaft wrap
        g2.setColor(new Color(90, 50, 15));
        g2.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < 3; i++) {
            int wy = hy + shaftEnd + 8 + i * 6;
            g2.drawLine(hx - 3, wy, hx + 3, wy);
        }

        // Axe head
        int headY = hy + shaftEnd + 4;
        int headH = len / 3;
        int headW = len / 3;

        // Crescent-shaped head using polygon
        int[] hxs = { hx, hx + headW, hx + headW - 4, hx - 2 };
        int[] hys = { headY, headY + 4, headY + headH, headY + headH - 2 };
        g2.setColor(AXE_HEAD);
        g2.fillPolygon(hxs, hys, 4);
        g2.setColor(AXE_HEAD.darker());
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawPolygon(hxs, hys, 4);

        // Blade edge highlight
        g2.setColor(new Color(220, 230, 240, 200));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(hx + headW, headY + 4, hx + headW - 4, headY + headH);
    }

    private void drawBowShape(Graphics2D g2, int hx, int hy, int len) {
        // Bow arc
        g2.setColor(AXE_SHAFT);
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawArc(hx - 8, hy - len / 2, 16, len, 0, 180);

        // Bowstring
        g2.setColor(new Color(220, 210, 180));
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(hx + 8, hy - len / 2, hx + 8, hy + len / 2);
    }

    // ─── Impact flash ────────────────────────────────────────────────────────

    private void drawImpactFlash(Graphics2D g2, int hx, int hy,
                                  String direction, String weaponClass) {
        float prog   = attackFrame / (float) IMPACT_FRAMES; // 0 → 1
        float alpha2 = 1f - prog;

        int flashX = hx, flashY = hy;
        int ts = gp.tileSize;
        switch (direction) {
            case "up"   -> flashY -= ts;
            case "down" -> flashY += ts / 2;
            case "left" -> flashX -= ts;
            case "right"-> flashX += ts;
        }

        Color flashColor = weaponClass.equals("Axe")
                ? new Color(200, 160, 80, (int)(180 * alpha2))
                : new Color(180, 220, 255, (int)(180 * alpha2));

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha2));
        g2.setColor(flashColor);

        // Star-burst shape
        double[] flashAngles = { 0, Math.PI/4, Math.PI/2, 3*Math.PI/4,
                                  Math.PI, 5*Math.PI/4, 3*Math.PI/2, 7*Math.PI/4 };
        for (double a : flashAngles) {
            int r = (int)(14 * alpha2);
            int ex = flashX + (int)(Math.cos(a) * r);
            int ey = flashY + (int)(Math.sin(a) * r);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(flashX, flashY, ex, ey);
        }
        g2.fillOval(flashX - 4, flashY - 4, 8, 8);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    // ─── Particles ───────────────────────────────────────────────────────────

    private void spawnImpactParticles() {
        int hx = gp.player.screenX + gp.tileSize / 2;
        int hy = gp.player.screenY + gp.tileSize / 2;

        // Shift spawn point toward the attack direction
        int ts = gp.tileSize;
        switch (gp.player.direction) {
            case "up"    -> hy -= ts;
            case "down"  -> hy += ts / 2;
            case "left"  -> hx -= ts;
            case "right" -> hx += ts;
        }

        // 8 particles in a burst
        for (int i = 0; i < 8; i++) {
            double angle = Math.random() * Math.PI * 2;
            float  speed = 1.5f + (float)(Math.random() * 2.5f);
            Color  col   = (activeWeapon != null && activeWeapon.weaponClass.equals("Axe"))
                    ? new Color(220, 160, 60)
                    : new Color(180, 220, 255);
            particles.add(new Particle(hx, hy, angle, speed, col, 18));
        }
    }

    private void drawParticles(Graphics2D g2) {
        for (Particle p : particles) p.draw(g2);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Inner class: Particle
    // ═════════════════════════════════════════════════════════════════════════

    private static class Particle {
        float x, y;
        float vx, vy;
        int   life, maxLife;
        Color color;

        Particle(int ox, int oy, double angle, float speed, Color color, int life) {
            this.x       = ox;
            this.y       = oy;
            this.vx      = (float)(Math.cos(angle) * speed);
            this.vy      = (float)(Math.sin(angle) * speed);
            this.color   = color;
            this.life    = life;
            this.maxLife = life;
        }

        /** Returns true when the particle is dead and should be removed. */
        boolean update() {
            x += vx;
            y += vy;
            vx *= 0.88f;   // drag
            vy *= 0.88f;
            life--;
            return life <= 0;
        }

        void draw(Graphics2D g2) {
            float alpha3 = life / (float) maxLife;
            g2.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, alpha3 * 0.9f));
            g2.setColor(color);
            int sz = Math.max(2, (int)(alpha3 * 5));
            g2.fillOval((int)x - sz / 2, (int)y - sz / 2, sz, sz);
            g2.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, 1f));
        }
    }
}
