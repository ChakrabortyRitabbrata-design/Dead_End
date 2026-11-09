package entities;

import animation.AnimationState;
import animation.Direction;
import inventory.Weapon;
import main.GamePanel;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Precision Frame-Anchored Weapon Renderer.
 *
 * Positions equipped weapons directly into the character's hand using
 * frame-by-frame attachment metadata from {@link WeaponAttachment}.
 * Hand anchors move dynamically as the player walks, idles, and attacks.
 */
public class WeaponRenderer {

    public enum AttackState { IDLE, WINDUP, SWING, IMPACT }

    private static final int WINDUP_FRAMES = 4;
    private static final int SWING_FRAMES  = 8;
    private static final int IMPACT_FRAMES = 6;

    public static final int ATTACK_COOLDOWN = 15;

    private AttackState state       = AttackState.IDLE;
    private int         attackFrame = 0;
    private int         cooldown    = 0;
    private Weapon      activeWeapon = null;

    private final List<Particle> particles = new ArrayList<>();
    private final GamePanel gp;

    // Palette per weapon
    private static final Color WOOD_BLADE   = new Color(195, 155, 105);
    private static final Color WOOD_GUARD   = new Color(140, 95, 45);
    private static final Color WOOD_HANDLE  = new Color(100, 60, 25);

    private static final Color IRON_BLADE   = new Color(205, 220, 235);
    private static final Color IRON_BLADE_DARK = new Color(150, 165, 185);
    private static final Color IRON_GUARD   = new Color(115, 125, 140);
    private static final Color IRON_HANDLE  = new Color(65, 65, 75);

    private static final Color AXE_HEAD     = new Color(185, 200, 218);
    private static final Color AXE_HEAD_EDGE = new Color(230, 240, 252);
    private static final Color AXE_SHAFT    = new Color(125, 75, 30);

    public WeaponRenderer(GamePanel gp) {
        this.gp = gp;
    }

    public boolean startAttack(Weapon weapon) {
        if (weapon == null)            return false;
        if (cooldown > 0)              return false;
        if (state != AttackState.IDLE) return false;

        activeWeapon = weapon;
        state        = AttackState.WINDUP;
        attackFrame  = 0;
        return true;
    }

    public boolean isAttacking() {
        return state != AttackState.IDLE;
    }

    public void update() {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            if (it.next().update()) it.remove();
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (state == AttackState.IDLE) return;

        attackFrame++;

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
     * Precision render method using exact 64×64 frame hand attachment coordinates.
     *
     * @param g2          Graphics2D context
     * @param drawX       Screen X where the 64×64 character sprite is drawn
     * @param drawY       Screen Y where the 64×64 character sprite is drawn
     * @param drawSize    Width/height of the drawn character (e.g. 96 or 72)
     * @param animState   Current animation state (IDLE, WALK, RUN, PUNCH)
     * @param dir         Current character direction
     * @param frameIndex  Current frame index of the animation
     * @param weapon      Equipped weapon to draw
     * @param renderPhase "BEHIND" to draw rear weapons (e.g. UP), "FRONT" to draw front weapons
     */
    public void drawHandAnchored(Graphics2D g2, int drawX, int drawY, int drawSize,
                                AnimationState animState, Direction dir, int frameIndex,
                                Weapon weapon, String renderPhase) {

        Weapon w = isAttacking() ? activeWeapon : weapon;
        if (w == null) return;

        WeaponAttachment.AttachmentData att = WeaponAttachment.getAttachment(animState, dir, frameIndex);

        // Filter based on draw order phase
        boolean wantBehind = "BEHIND".equalsIgnoreCase(renderPhase);
        if (att.drawBehind != wantBehind) {
            return; // Not our turn to draw
        }

        // Always draw particles in FRONT phase
        if (!wantBehind) {
            drawParticles(g2);
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Precise screen coordinates for the hand
        double scale = (double) drawSize / 64.0;
        int hx = drawX + (int) Math.round(att.handX * scale) + att.gripOffsetX;
        int hy = drawY + (int) Math.round(att.handY * scale) + att.gripOffsetY;

        double angle = att.angle;
        if (isAttacking()) {
            angle += getAttackSwingDelta(dir);
        }

        AffineTransform old = g2.getTransform();
        g2.rotate(angle, hx, hy);

        int weaponLength = (int) Math.round(30 * scale); // Scaled weapon length
        drawWeaponShape(g2, hx, hy, weaponLength, w, isAttacking());

        g2.setTransform(old);

        // Slash arc effect during weapon swing
        if (isAttacking() && state == AttackState.SWING && !wantBehind) {
            drawSlashArc(g2, hx, hy, dir, scale);
        }

        // Impact flash during attack
        if (isAttacking() && state == AttackState.IMPACT && attackFrame < 4 && !wantBehind) {
            drawImpactFlash(g2, hx, hy, dir, w.weaponClass);
        }
    }

    /**
     * Overload for backward compatibility.
     */
    public void draw(Graphics2D g2, int px, int py, String direction, Weapon weapon) {
        Direction dir = Direction.valueOf(direction.toUpperCase());
        int drawSize = gp != null ? gp.tileSize * 2 : 96;
        int drawX = px + (gp != null ? (gp.tileSize - drawSize) / 2 : -24);
        int drawY = py + (gp != null ? gp.tileSize - drawSize : -48);
        drawHandAnchored(g2, drawX, drawY, drawSize, AnimationState.IDLE, dir, 0, weapon, "FRONT");
    }

    private double getAttackSwingDelta(Direction dir) {
        double arcTotal = Math.toRadians(90);
        double flip = (dir == Direction.LEFT) ? -1.0 : 1.0;

        return switch (state) {
            case WINDUP -> -flip * Math.toRadians(25) * (attackFrame / (double) WINDUP_FRAMES);
            case SWING  -> {
                double t = attackFrame / (double) SWING_FRAMES;
                yield flip * (arcTotal * t - Math.toRadians(25));
            }
            case IMPACT -> flip * (arcTotal - Math.toRadians(25));
            default     -> 0.0;
        };
    }

    private void drawWeaponShape(Graphics2D g2, int hx, int hy, int len, Weapon w, boolean attacking) {
        String name = w.name != null ? w.name.toLowerCase() : "";
        String wClass = w.weaponClass != null ? w.weaponClass.toLowerCase() : "";

        if (wClass.contains("axe") || name.contains("axe")) {
            drawAxe(g2, hx, hy, len);
        } else if (name.contains("iron") || name.contains("steel")) {
            drawSword(g2, hx, hy, len, IRON_BLADE, IRON_BLADE_DARK, IRON_GUARD, IRON_HANDLE);
        } else {
            // Wooden Sword or default
            drawSword(g2, hx, hy, len, WOOD_BLADE, WOOD_GUARD.darker(), WOOD_GUARD, WOOD_HANDLE);
        }
    }

    /**
     * Draws a sword centered on the hand grip at (hx, hy).
     * Grip sits right at (hx, hy), blade extends in -Y direction, pommel at +Y.
     */
    private void drawSword(Graphics2D g2, int hx, int hy, int len,
                          Color bladeCol, Color bladeDark, Color guardCol, Color handleCol) {

        int gripLen = Math.max(5, len / 5);
        int pommelY = hy + gripLen;
        int guardY  = hy - 2;
        int tipY    = hy - len;

        // 1. Grip / Handle (centered at hx, hy)
        g2.setColor(handleCol);
        g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(hx, guardY, hx, pommelY);

        // 2. Pommel (bottom knob)
        g2.setColor(guardCol);
        g2.fillOval(hx - 3, pommelY - 2, 6, 6);
        g2.setColor(guardCol.darker());
        g2.setStroke(new BasicStroke(1f));
        g2.drawOval(hx - 3, pommelY - 2, 6, 6);

        // 3. Crossguard (horizontal bar at top of grip)
        int guardW = Math.max(10, len / 3);
        g2.setColor(guardCol);
        g2.fillRect(hx - guardW / 2, guardY - 2, guardW, 3);
        g2.setColor(guardCol.darker());
        g2.drawRect(hx - guardW / 2, guardY - 2, guardW, 3);

        // 4. Blade
        GeneralPath blade = new GeneralPath();
        blade.moveTo(hx, tipY);                // sharp tip
        blade.lineTo(hx + 3, tipY + 4);         // right edge top
        blade.lineTo(hx + 2, guardY - 2);       // right base
        blade.lineTo(hx - 2, guardY - 2);       // left base
        blade.lineTo(hx - 3, tipY + 4);         // left edge top
        blade.closePath();

        g2.setColor(bladeCol);
        g2.fill(blade);
        g2.setColor(bladeDark);
        g2.setStroke(new BasicStroke(0.8f));
        g2.draw(blade);

        // Fuller (center line)
        g2.setColor(new Color(255, 255, 255, 130));
        g2.drawLine(hx, tipY + 5, hx, guardY - 3);
    }

    /**
     * Draws a battle axe with shaft gripped at (hx, hy).
     */
    private void drawAxe(Graphics2D g2, int hx, int hy, int len) {
        int shaftBottom = hy + len / 4;
        int shaftTop    = hy - len;

        // Shaft
        g2.setColor(AXE_SHAFT);
        g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(hx, shaftBottom, hx, shaftTop);

        // Axe Head (crescent blade)
        int headTop = shaftTop + 2;
        int headH   = len / 3;
        int headW   = len / 3;

        GeneralPath head = new GeneralPath();
        head.moveTo(hx, headTop);
        head.lineTo(hx + headW, headTop - 2);
        head.lineTo(hx + headW + 2, headTop + headH / 2);
        head.lineTo(hx + headW, headTop + headH + 2);
        head.lineTo(hx, headTop + headH);
        head.closePath();

        g2.setColor(AXE_HEAD);
        g2.fill(head);
        g2.setColor(AXE_HEAD_EDGE);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawLine(hx + headW, headTop - 2, hx + headW + 2, headTop + headH / 2);
        g2.drawLine(hx + headW + 2, headTop + headH / 2, hx + headW, headTop + headH + 2);

        // Shaft cap
        g2.setColor(new Color(80, 90, 100));
        g2.fillRect(hx - 2, shaftTop - 1, 4, 3);
    }

    private void drawSlashArc(Graphics2D g2, int hx, int hy, Direction dir, double scale) {
        float t = attackFrame / (float) SWING_FRAMES;
        float alpha = (float) Math.sin(t * Math.PI) * 0.95f;
        if (alpha <= 0.05f) return;

        Composite oldComp = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        int arcRadius = (int) (38 * scale);
        int centerX = hx;
        int centerY = hy;

        int startAngle;
        int arcExtent;
        switch (dir) {
            case DOWN -> {
                centerY += (int)(6 * scale);
                startAngle = 200;
                arcExtent = 140;
            }
            case UP -> {
                centerY -= (int)(6 * scale);
                startAngle = 20;
                arcExtent = 140;
            }
            case LEFT -> {
                centerX -= (int)(6 * scale);
                startAngle = 110;
                arcExtent = 140;
            }
            default -> { // RIGHT
                centerX += (int)(6 * scale);
                startAngle = 290;
                arcExtent = 140;
            }
        }

        // Outer bright blue-white glow
        g2.setColor(new Color(180, 220, 255, 150));
        g2.setStroke(new BasicStroke((float)(6 * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc(centerX - arcRadius, centerY - arcRadius, arcRadius * 2, arcRadius * 2, startAngle, arcExtent);

        // Core sharp white blade slash
        g2.setColor(new Color(255, 255, 255, 240));
        g2.setStroke(new BasicStroke((float)(2.5 * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc(centerX - arcRadius, centerY - arcRadius, arcRadius * 2, arcRadius * 2, startAngle + 10, arcExtent - 20);

        g2.setStroke(new BasicStroke(1f));
        g2.setComposite(oldComp);
    }

    private void drawImpactFlash(Graphics2D g2, int hx, int hy, Direction dir, String wClass) {
        float prog   = attackFrame / (float) IMPACT_FRAMES;
        float alpha  = Math.max(0f, 1f - prog);

        int fx = hx, fy = hy;
        int dist = (int)(24 * (gp != null ? (double)gp.tileSize / 48.0 : 1.0));
        switch (dir) {
            case UP    -> fy -= dist;
            case DOWN  -> fy += dist;
            case LEFT  -> fx -= dist;
            case RIGHT -> fx += dist;
        }

        Composite oldComp = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        int starSize = (int)(16 * (1.2f - prog * 0.4f));

        // Diagonal star rays (gold/red/yellow)
        g2.setColor(new Color(255, 210, 80));
        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(fx - starSize, fy, fx + starSize, fy);
        g2.drawLine(fx, fy - starSize, fx, fy + starSize);

        int diag = (int)(starSize * 0.7f);
        g2.setColor(new Color(255, 160, 40));
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(fx - diag, fy - diag, fx + diag, fy + diag);
        g2.drawLine(fx - diag, fy + diag, fx + diag, fy - diag);

        // Bright white center core
        g2.setColor(Color.WHITE);
        g2.fillOval(fx - 4, fy - 4, 8, 8);

        g2.setStroke(new BasicStroke(1f));
        g2.setComposite(oldComp);
    }

    private void spawnImpactParticles() {
        if (gp == null || gp.player == null) return;
        int hx = gp.player.screenX + gp.tileSize / 2;
        int hy = gp.player.screenY + gp.tileSize / 2;
        for (int i = 0; i < 6; i++) {
            double angle = Math.random() * Math.PI * 2;
            float speed = 1.2f + (float) (Math.random() * 2.0f);
            particles.add(new Particle(hx, hy, angle, speed, new Color(240, 245, 255), 14));
        }
    }

    private void drawParticles(Graphics2D g2) {
        for (Particle p : particles) p.draw(g2);
    }

    private static class Particle {
        float x, y, vx, vy;
        int life, maxLife;
        Color color;

        Particle(int ox, int oy, double angle, float speed, Color color, int life) {
            this.x = ox; this.y = oy;
            this.vx = (float) (Math.cos(angle) * speed);
            this.vy = (float) (Math.sin(angle) * speed);
            this.color = color;
            this.life = life;
            this.maxLife = life;
        }

        boolean update() {
            x += vx; y += vy;
            vx *= 0.90f; vy *= 0.90f;
            life--;
            return life <= 0;
        }

        void draw(Graphics2D g2) {
            float alpha = life / (float) maxLife;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.9f));
            g2.setColor(color);
            int sz = Math.max(2, (int) (alpha * 4));
            g2.fillOval((int) x - sz / 2, (int) y - sz / 2, sz, sz);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }
    }
}
