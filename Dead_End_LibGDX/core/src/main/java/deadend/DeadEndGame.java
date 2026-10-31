package deadend;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import deadend.screens.GameScreen;

/**
 * Main LibGDX application class.
 * Owns shared resources (SpriteBatch, AssetManager, fonts) and manages screens.
 */
public class DeadEndGame extends Game {

    // Shared rendering resources — created once, shared across screens
    public SpriteBatch    batch;
    public ShapeRenderer  shapes;
    public AssetManager   assets;
    public BitmapFont     font;
    public BitmapFont     fontSmall;

    @Override
    public void create() {
        batch  = new SpriteBatch();
        shapes = new ShapeRenderer();
        assets = new AssetManager();
        font   = new BitmapFont();          // default LibGDX built-in font
        fontSmall = new BitmapFont();
        font.getData().setScale(1.4f);
        fontSmall.getData().setScale(0.9f);

        setScreen(new GameScreen(this));
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        assets.dispose();
        font.dispose();
        fontSmall.dispose();
        super.dispose();
    }
}
