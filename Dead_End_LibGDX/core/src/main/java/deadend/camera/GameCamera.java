package deadend.camera;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/**
 * Smooth-following camera that keeps the player near centre
 * and clamps to world boundaries.
 */
public class GameCamera {

    private final OrthographicCamera camera;
    private float worldW, worldH;
    private static final float LERP = 6f;  // smooth factor (higher = faster)

    public GameCamera(int viewportW, int viewportH) {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, viewportW, viewportH);
    }

    public void setWorldBounds(float worldW, float worldH) {
        this.worldW = worldW;
        this.worldH = worldH;
    }

    public void update(float targetX, float targetY, float delta) {
        // Lerp towards the target
        float desiredX = targetX - camera.viewportWidth  / 2f;
        float desiredY = targetY - camera.viewportHeight / 2f;

        // Clamp so we don't show beyond world edges
        float clampedX = MathUtils.clamp(desiredX, 0, Math.max(0, worldW - camera.viewportWidth));
        float clampedY = MathUtils.clamp(desiredY, 0, Math.max(0, worldH - camera.viewportHeight));

        float lerpFactor = Math.min(1f, LERP * delta);
        camera.position.x += (clampedX + camera.viewportWidth  / 2f - camera.position.x) * lerpFactor;
        camera.position.y += (clampedY + camera.viewportHeight / 2f - camera.position.y) * lerpFactor;

        camera.update();
    }

    public OrthographicCamera getCamera() { return camera; }

    /** Returns the camera's bottom-left world position. */
    public Vector2 getWorldOrigin() {
        return new Vector2(
            camera.position.x - camera.viewportWidth  / 2f,
            camera.position.y - camera.viewportHeight / 2f
        );
    }

    public void resize(int w, int h) {
        camera.setToOrtho(false, w, h);
    }
}
