package entities;

import animation.Direction;
import inventory.Weapon;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Handles weapon-specific animations, attack phases, synchronized sprite attacks,
 * and visual effects (slashes, impact flashes, particles).
 */
public class WeaponAnimation {

    public enum State { IDLE, ATTACKING }

    // 4 attack animation frames matching weapon attack sheets
    private static final int ATTACK_FRAMES = 4;
    private static final long FRAME_DURATION_NANOS = 75_000_000L; // 75ms per attack frame (~300ms total)
    private static final long COOLDOWN_NANOS = 150_000_000L;

    private final CharacterSpriteManager spriteManager;
    private State state = State.IDLE;
    private int currentFrame = 0;
    private long frameTimer = 0;
    private long cooldownTimer = 0;
    private Weapon activeWeapon = null;
    private Direction attackDirection = Direction.DOWN;

    // Visual particles
    private final List<ImpactParticle> particles = new ArrayList<>();

    public WeaponAnimation(CharacterSpriteManager spriteManager) {
        this.spriteManager = spriteManager;
    }

    public boolean startAttack(Weapon weapon, Direction dir) {
        if (state == State.ATTACKING || cooldownTimer > 0) return false;
        this.activeWeapon = weapon;
        this.attackDirection = dir;
        this.state = State.ATTACKING;
        this.currentFrame = 0;
        this.frameTimer = 0;
        return true;
    }

    public void update(long deltaNanos) {
        // Update particles
        Iterator<ImpactParticle> it = particles.iterator();
        while (it.hasNext()) {
            if (it.next().update()) it.remove();
        }

        if (cooldownTimer > 0) {
            cooldownTimer = Math.max(0, cooldownTimer - deltaNanos);
        }

        if (state == State.ATTACKING) {
            frameTimer += deltaNanos;
            if (frameTimer >= FRAME_DURATION_NANOS) {
                frameTimer -= FRAME_DURATION_NANOS;
                currentFrame++;
                if (currentFrame == 2) {
                    spawnParticles();
                }
                if (currentFrame >= ATTACK_FRAMES) {
                    state = State.IDLE;
                    currentFrame = 0;
                    cooldownTimer = COOLDOWN_NANOS;
                    activeWeapon = null;
                }
            }
        }
    }

    public boolean isAttacking() {
        return state == State.ATTACKING;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public Weapon getActiveWeapon() {
        return activeWeapon;
    }

    public Direction getAttackDirection() {
        return attackDirection;
    }

    public static String getWeaponKey(Weapon weapon) {
        if (weapon == null) return "wooden_sword";
        String name = weapon.name.toLowerCase();
        if (name.contains("axe")) return "axe";
        if (name.contains("iron") || name.contains("steel")) return "iron_sword";
        return "wooden_sword";
    }

    public void drawAttack(Graphics2D g2, int drawX, int drawY, int drawSize, Direction dir) {
        if (state != State.ATTACKING || activeWeapon == null) return;

        String key = getWeaponKey(activeWeapon);
        BufferedImage attackSprite = spriteManager.getWeaponAttackFrame(key, dir, currentFrame);

        Object oldInterp = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        if (attackSprite != null) {
            g2.drawImage(attackSprite, drawX, drawY, drawSize, drawSize, null);
        }

        // Slash effect overlay during mid & extension swings (frames 1 & 2)
        if (currentFrame >= 1 && currentFrame <= 3) {
            BufferedImage slash = spriteManager.getSlashEffectFrame(currentFrame);
            if (slash != null) {
                int slashX = drawX;
                int slashY = drawY;
                switch (dir) {
                    case UP -> slashY -= drawSize / 4;
                    case DOWN -> slashY += drawSize / 4;
                    case LEFT -> slashX -= drawSize / 4;
                    case RIGHT -> slashX += drawSize / 4;
                }
                g2.drawImage(slash, slashX, slashY, drawSize, drawSize, null);
            }
        }

        // Impact flash at full swing
        if (currentFrame == 2) {
            BufferedImage impact = spriteManager.getImpactEffectFrame(0);
            if (impact != null) {
                int impX = drawX + (dir == Direction.LEFT ? -drawSize / 3 : (dir == Direction.RIGHT ? drawSize / 3 : 0));
                int impY = drawY + (dir == Direction.UP ? -drawSize / 3 : (dir == Direction.DOWN ? drawSize / 3 : 0));
                g2.drawImage(impact, impX, impY, drawSize, drawSize, null);
            }
        }

        // Draw particles
        for (ImpactParticle p : particles) {
            p.draw(g2);
        }

        if (oldInterp != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
        }
    }

    private void spawnParticles() {
        Color col = (activeWeapon != null && activeWeapon.name.toLowerCase().contains("axe"))
                ? new Color(220, 170, 70)
                : new Color(190, 225, 255);
        for (int i = 0; i < 6; i++) {
            double angle = Math.random() * Math.PI * 2;
            float speed = 1.0f + (float) (Math.random() * 2.0f);
            particles.add(new ImpactParticle(0, 0, angle, speed, col, 14));
        }
    }

    private static class ImpactParticle {
        float x, y, vx, vy;
        int life, maxLife;
        Color color;

        ImpactParticle(int ox, int oy, double angle, float speed, Color color, int life) {
            this.x = ox;
            this.y = oy;
            this.vx = (float) (Math.cos(angle) * speed);
            this.vy = (float) (Math.sin(angle) * speed);
            this.color = color;
            this.life = life;
            this.maxLife = life;
        }

        boolean update() {
            x += vx;
            y += vy;
            vx *= 0.90f;
            vy *= 0.90f;
            life--;
            return life <= 0;
        }

        void draw(Graphics2D g2) {
            float alpha = life / (float) maxLife;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.85f));
            g2.setColor(color);
            int sz = Math.max(2, (int) (alpha * 4));
            g2.fillOval((int) x - sz / 2, (int) y - sz / 2, sz, sz);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }
    }
}
