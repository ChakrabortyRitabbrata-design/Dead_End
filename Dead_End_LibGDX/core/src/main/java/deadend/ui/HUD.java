package deadend.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;

import deadend.entities.Player;

/**
 * Heads-up display — health bar, action state, score.
 */
public class HUD {

    private final ShapeRenderer sr;
    private final BitmapFont    font;

    private static final float BAR_W  = 180f;
    private static final float BAR_H  = 16f;
    private static final float BAR_X  = 14f;
    private static final float BAR_Y_FROM_TOP = 14f;

    public HUD(ShapeRenderer sr, BitmapFont font) {
        this.sr   = sr;
        this.font = font;
    }

    public void draw(SpriteBatch batch, int screenW, int screenH, Player player, int score) {
        batch.end();

        Matrix4 proj = new Matrix4().setToOrtho2D(0, 0, screenW, screenH);
        sr.setProjectionMatrix(proj);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        float barY = screenH - BAR_Y_FROM_TOP - BAR_H;

        // Health bar background
        sr.setColor(0.12f, 0.12f, 0.12f, 0.85f);
        sr.rect(BAR_X - 2, barY - 2, BAR_W + 4, BAR_H + 4);

        // Health fill — colour shifts red as health drops
        float ratio = Math.max(0f, player.health / (float) Player.MAX_HEALTH);
        sr.setColor(1f - ratio * 0.3f, ratio * 0.85f, 0.05f, 1f);
        sr.rect(BAR_X, barY, BAR_W * ratio, BAR_H);

        // Health bar border
        sr.end();
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0.7f, 0.7f, 0.7f, 1f);
        sr.rect(BAR_X - 2, barY - 2, BAR_W + 4, BAR_H + 4);
        sr.end();

        // Text labels
        batch.begin();
        batch.setProjectionMatrix(proj);

        font.setColor(1f, 1f, 1f, 1f);
        font.draw(batch, "HP  " + player.health + " / " + Player.MAX_HEALTH,
                BAR_X, barY - 4);

        // Action state indicator
        if (!player.actionState.equals("IDLE")) {
            font.setColor(1f, 0.85f, 0.2f, 1f);
            font.draw(batch, player.actionState, BAR_X + BAR_W + 12, barY + BAR_H);
        }

        // Score
        font.setColor(0.9f, 0.9f, 0.7f, 1f);
        font.draw(batch, "Score: " + score, screenW - 130f, screenH - BAR_Y_FROM_TOP);

        // Controls hint at start
        font.setColor(0.55f, 0.55f, 0.55f, 0.85f);
        font.draw(batch, "WASD=Move  LMB=Punch  RMB=Block  E=Kick  Q=Dodge  M=Map", 14, 22);

        font.setColor(Color.WHITE);
    }
}
