package deadend.entities;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import deadend.world.TileManager;

/**
 * Player entity.
 *
 * Controls (matching the original Swing game):
 *   W/A/S/D      — movement
 *   Left click   — punch
 *   Right click  — block
 *   E            — kick
 *   Q            — dodge roll
 *
 * Movement uses delta-time so behaviour is frame-rate independent.
 * Collision uses the TileManager's world-space solid check.
 */
public class Player {

    // Gameplay constants
    public static final float SPEED       = 160f;  // pixels/sec
    public static final float ROLL_SPEED  = 300f;
    public static final float ROLL_TIME   = 0.22f;
    public static final int   MAX_HEALTH  = 100;
    public static final float HIT_BOX_W  = 26f;
    public static final float HIT_BOX_H  = 26f;

    // World position (bottom-left of sprite)
    public float worldX, worldY;

    // Combat state
    public int    health        = MAX_HEALTH;
    public String actionState   = "IDLE"; // IDLE PUNCH KICK BLOCK DODGE
    public int    attackSequence = 0;
    public float  actionTimer   = 0f;
    public String direction     = "down";

    // Roll
    private float rollTimer   = 0f;
    private float rollDirX    = 0f;
    private float rollDirY    = 0f;

    // Sprite / animation
    private TextureRegion[][] walkFrames;  // [dir][frame]  dir: 0=up 1=right 2=down 3=left
    private float spriteTimer = 0f;
    private int   spriteFrame = 0;
    private boolean textureLoaded = false;

    private static final int DIR_UP    = 0;
    private static final int DIR_RIGHT = 1;
    private static final int DIR_DOWN  = 2;
    private static final int DIR_LEFT  = 3;
    private static final float FRAME_DURATION = 0.12f;
    private static final float ATTACK_DURATION = 0.35f;
    private static final float DODGE_DURATION  = ROLL_TIME;

    // Render size
    public static final int RENDER_SIZE = 64;

    private final TileManager tileM;
    private Texture walkSheet;

    public Player(TileManager tileM) {
        this.tileM = tileM;
        loadSprites();
    }

    private void loadSprites() {
        try {
            if (Gdx.files.internal("player/player_walk_strip.png").exists()) {
                walkSheet = new Texture(Gdx.files.internal("player/player_walk_strip.png"));
                walkSheet.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                int fw = walkSheet.getWidth() / 4;
                int fh = walkSheet.getHeight() / 4;
                walkFrames = new TextureRegion[4][2];
                // Sheet layout: [frame0, frame1] per row
                // Row 0=down, 1=up, 2=left, 3=right (matching original)
                walkFrames[DIR_DOWN][0]  = new TextureRegion(walkSheet, 0,      0,      fw, fh);
                walkFrames[DIR_DOWN][1]  = new TextureRegion(walkSheet, fw,     0,      fw, fh);
                walkFrames[DIR_UP][0]    = new TextureRegion(walkSheet, 0,      fh,     fw, fh);
                walkFrames[DIR_UP][1]    = new TextureRegion(walkSheet, fw,     fh,     fw, fh);
                walkFrames[DIR_LEFT][0]  = new TextureRegion(walkSheet, 0,      fh*2,   fw, fh);
                walkFrames[DIR_LEFT][1]  = new TextureRegion(walkSheet, fw,     fh*2,   fw, fh);
                walkFrames[DIR_RIGHT][0] = new TextureRegion(walkSheet, 0,      fh*3,   fw, fh);
                walkFrames[DIR_RIGHT][1] = new TextureRegion(walkSheet, fw,     fh*3,   fw, fh);
                textureLoaded = true;
            }
        } catch (Exception e) {
            Gdx.app.error("Player", "Could not load walk strip: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    public void update(float delta) {
        handleCombatInput(delta);
        if (!isActionLocked()) {
            handleMovement(delta);
        }
        updateAnimation(delta);
    }

    private void handleCombatInput(float delta) {
        if (!actionState.equals("IDLE")) {
            actionTimer -= delta;
            if (actionTimer <= 0f) {
                actionState = "IDLE";
                actionTimer = 0f;
            }
            return;
        }

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            actionState = "PUNCH";
            actionTimer = ATTACK_DURATION;
            attackSequence++;
        } else if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
            actionState = "BLOCK";
            actionTimer = 0.05f;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            actionState = "KICK";
            actionTimer = ATTACK_DURATION;
            attackSequence++;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            startDodge();
        }
    }

    private void startDodge() {
        actionState = "DODGE";
        actionTimer = DODGE_DURATION;
        rollTimer   = DODGE_DURATION;
        // Roll in current facing direction
        switch (direction) {
            case "up"    -> { rollDirX =  0; rollDirY =  1; }
            case "down"  -> { rollDirX =  0; rollDirY = -1; }
            case "left"  -> { rollDirX = -1; rollDirY =  0; }
            case "right" -> { rollDirX =  1; rollDirY =  0; }
        }
    }

    private void handleMovement(float delta) {
        float dx = 0, dy = 0;

        if (actionState.equals("DODGE") && rollTimer > 0f) {
            rollTimer -= delta;
            dx = rollDirX * ROLL_SPEED * delta;
            dy = rollDirY * ROLL_SPEED * delta;
        } else {
            if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP))    { dy =  SPEED * delta; direction = "up";    }
            if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN))   { dy = -SPEED * delta; direction = "down";  }
            if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT))   { dx = -SPEED * delta; direction = "left";  }
            if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT))  { dx =  SPEED * delta; direction = "right"; }
        }

        // Separate axis collision resolution
        float hbX = (RENDER_SIZE - HIT_BOX_W) / 2f;
        float hbY = 4f;

        if (dx != 0) {
            float nx = worldX + dx;
            if (!tileM.isSolid(nx + hbX, worldY + hbY, HIT_BOX_W, HIT_BOX_H)) {
                worldX = nx;
            }
        }
        if (dy != 0) {
            float ny = worldY + dy;
            if (!tileM.isSolid(worldX + hbX, ny + hbY, HIT_BOX_W, HIT_BOX_H)) {
                worldY = ny;
            }
        }

        // Clamp to world bounds
        worldX = Math.max(0, Math.min(tileM.worldWidth()  - RENDER_SIZE, worldX));
        worldY = Math.max(0, Math.min(tileM.worldHeight() - RENDER_SIZE, worldY));
    }

    private void updateAnimation(float delta) {
        spriteTimer += delta;
        if (spriteTimer >= FRAME_DURATION) {
            spriteTimer = 0f;
            spriteFrame = 1 - spriteFrame;  // toggle 0/1
        }
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    public void draw(SpriteBatch batch) {
        if (textureLoaded && walkFrames != null) {
            TextureRegion frame = walkFrames[getDirIndex()][spriteFrame];
            batch.draw(frame, worldX, worldY, RENDER_SIZE, RENDER_SIZE);
        } else {
            // Fallback: coloured rectangle
            batch.setColor(0.2f, 0.6f, 1f, 1f);
            // draw nothing — batch can't draw a rect directly; we'd need ShapeRenderer
            batch.setColor(Color.WHITE);
        }
    }

    // -------------------------------------------------------------------------
    // Combat
    // -------------------------------------------------------------------------

    public boolean isAttacking() { return actionState.equals("PUNCH") || actionState.equals("KICK"); }
    public boolean isBlocking()  { return actionState.equals("BLOCK"); }
    public boolean isDodging()   { return actionState.equals("DODGE"); }
    public boolean isActionLocked() { return !actionState.equals("IDLE") && !actionState.equals("BLOCK"); }

    public float getAttackDamage() { return actionState.equals("KICK") ? 40f : 25f; }

    public void takeDamage(float damage) {
        if (isBlocking() || isDodging()) return;
        health = Math.max(0, (int)(health - damage));
    }

    // -------------------------------------------------------------------------
    // Hitbox (world space)
    // -------------------------------------------------------------------------

    public Rectangle getHitbox() {
        float hbX = (RENDER_SIZE - HIT_BOX_W) / 2f;
        float hbY = 4f;
        return new Rectangle(worldX + hbX, worldY + hbY, HIT_BOX_W, HIT_BOX_H);
    }

    public float getCenterX() { return worldX + RENDER_SIZE / 2f; }
    public float getCenterY() { return worldY + RENDER_SIZE / 2f; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int getDirIndex() {
        return switch (direction) {
            case "up"    -> DIR_UP;
            case "right" -> DIR_RIGHT;
            case "left"  -> DIR_LEFT;
            default      -> DIR_DOWN;
        };
    }

    public void setPosition(float x, float y) {
        worldX = x;
        worldY = y;
    }

    public void dispose() {
        if (walkSheet != null) walkSheet.dispose();
    }
}
