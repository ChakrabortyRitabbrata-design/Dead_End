package deadend.enemies;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import deadend.entities.Player;
import deadend.world.TileManager;

/**
 * Undead enemy entity.
 *
 * Behaviour mirrors the original Swing game:
 *   - Idle when far from player
 *   - Pursues player when within detection range
 *   - Attacks when within attack range
 *   - Takes damage from player attacks
 *   - Death animation then removal
 */
public class Enemy {

    private static final float MAX_HEALTH      = 100f;
    private static final float SPEED           = 60f;
    private static final float DETECTION_RANGE = 450f; // pixels
    private static final float ATTACK_RANGE    = 60f;
    private static final float ATTACK_DAMAGE   = 10f;
    private static final float ATTACK_COOLDOWN = 1.5f; // seconds
    private static final float RENDER_SIZE     = 80f;

    public float worldX, worldY;
    public float health = MAX_HEALTH;

    private String direction   = "down";
    private String state       = "idle";   // idle, walk, attack, die, dead
    private float  actionTimer = 0f;
    private float  attackCooldown = 0f;
    private int    lastAttackSeq  = -1;

    // Animation
    private TextureRegion[][] frames;  // [row][frame]  rows: 0=idle,1=walk,2=attack,3=die
    private static final int FRAME_COUNT = 4;
    private static final int DIR_UP=0, DIR_RIGHT=1, DIR_DOWN=2, DIR_LEFT=3;
    private float spriteTimer = 0f;
    private int   spriteFrame = 0;
    private static final float FRAME_DUR = 0.15f;

    private static Texture sharedSheet;
    private static int     refCount = 0;

    private final TileManager tileM;

    public Enemy(float worldX, float worldY, TileManager tileM) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.tileM  = tileM;
        loadSharedSheet();
    }

    private void loadSharedSheet() {
        if (sharedSheet == null) {
            String path = "enemies/Pixel-Art Undead Monster Spritesheet.png";
            if (Gdx.files.internal(path).exists()) {
                sharedSheet = new Texture(Gdx.files.internal(path));
            } else {
                Gdx.app.log("Enemy", "Monster spritesheet not found at " + path);
            }
        }
        refCount++;

        if (sharedSheet != null) {
            frames = new TextureRegion[4][FRAME_COUNT];
            int fw = sharedSheet.getWidth()  / 16;
            int fh = sharedSheet.getHeight() / 4;
            for (int row = 0; row < 4; row++) {
                for (int dir = 0; dir < 4; dir++) {
                    for (int f = 0; f < FRAME_COUNT; f++) {
                        int col = dir * FRAME_COUNT + f;
                        if (frames[row] == null) frames[row] = new TextureRegion[FRAME_COUNT];
                        // Use down-direction frames as reference (dir=2)
                        frames[row][f] = new TextureRegion(sharedSheet, col * fw, row * fh, fw, fh);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    public void update(float delta, Player player) {
        if (isDead()) return;

        attackCooldown -= delta;

        float distToPlayer = distanceTo(player.worldX + Player.RENDER_SIZE/2f,
                                        player.worldY + Player.RENDER_SIZE/2f);

        // React to player attacks
        if (player.isAttacking() && distToPlayer < TileManager.TILE_PX * 2f
                && lastAttackSeq != player.attackSequence) {
            lastAttackSeq = player.attackSequence;
            takeDamage(player.getAttackDamage());
        }

        if (state.equals("die")) {
            actionTimer -= delta;
            if (actionTimer <= 0f) state = "dead";
            updateAnim(delta, 3);
            return;
        }

        if (distToPlayer < ATTACK_RANGE && attackCooldown <= 0f) {
            performAttack(player);
        } else if (distToPlayer < DETECTION_RANGE) {
            pursuePlayer(delta, player);
        } else {
            state = "idle";
            updateAnim(delta, 0);
        }
    }

    private void pursuePlayer(float delta, Player player) {
        state = "walk";
        float px = player.getCenterX() - (worldX + RENDER_SIZE / 2f);
        float py = player.getCenterY() - (worldY + RENDER_SIZE / 2f);
        float len = (float) Math.sqrt(px * px + py * py);
        if (len > 0f) { px /= len; py /= len; }

        float nx = worldX + px * SPEED * delta;
        float ny = worldY + py * SPEED * delta;

        // Simple tile collision
        float hitW = 28f, hitH = 20f, hbX = (RENDER_SIZE - hitW) / 2f, hbY = 4f;
        if (!tileM.isSolid(nx + hbX, worldY + hbY, hitW, hitH)) worldX = nx;
        if (!tileM.isSolid(worldX + hbX, ny + hbY, hitW, hitH)) worldY = ny;

        direction = Math.abs(px) > Math.abs(py) ? (px > 0 ? "right" : "left") : (py > 0 ? "up" : "down");
        updateAnim(delta, 1);
    }

    private void performAttack(Player player) {
        state = "attack";
        attackCooldown = ATTACK_COOLDOWN;
        player.takeDamage(ATTACK_DAMAGE);
        updateAnim(0f, 2);
    }

    private void updateAnim(float delta, int row) {
        spriteTimer += delta;
        if (spriteTimer >= FRAME_DUR) {
            spriteTimer = 0f;
            spriteFrame = (spriteFrame + 1) % FRAME_COUNT;
        }
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    public void draw(SpriteBatch batch) {
        if (state.equals("dead")) return;

        if (frames != null) {
            int row = state.equals("die") ? 3 : state.equals("attack") ? 2 :
                      state.equals("walk") ? 1 : 0;
            TextureRegion fr = frames[row][Math.min(spriteFrame, frames[row].length - 1)];
            batch.draw(fr, worldX, worldY, RENDER_SIZE, RENDER_SIZE);
        }

        // Health bar
        float barW = 44f, barH = 5f;
        float barX = worldX + (RENDER_SIZE - barW) / 2f;
        float barY = worldY + RENDER_SIZE + 3f;
        batch.setColor(0.2f, 0.2f, 0.2f, 1f);
        // ShapeRenderer needed for filled rects — draw as coloured region via a pixel texture
        // We'll leave health bar to the rendering layer that has access to ShapeRenderer
        batch.setColor(Color.WHITE);
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    public boolean isDead()   { return state.equals("dead"); }
    public boolean isDying()  { return state.equals("die"); }

    public void takeDamage(float damage) {
        if (isDead() || isDying()) return;
        health = Math.max(0f, health - damage);
        if (health <= 0f) {
            state       = "die";
            actionTimer = 0.6f;
        }
    }

    public Rectangle getHitbox() {
        float hitW = 28f, hitH = 20f;
        return new Rectangle(worldX + (RENDER_SIZE - hitW) / 2f, worldY + 4f, hitW, hitH);
    }

    public float getCenterX() { return worldX + RENDER_SIZE / 2f; }
    public float getCenterY() { return worldY + RENDER_SIZE / 2f; }

    private float distanceTo(float px, float py) {
        float dx = getCenterX() - px, dy = getCenterY() - py;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public static void disposeSharedSheet() {
        refCount--;
        if (refCount <= 0 && sharedSheet != null) {
            sharedSheet.dispose();
            sharedSheet = null;
            refCount = 0;
        }
    }
}
