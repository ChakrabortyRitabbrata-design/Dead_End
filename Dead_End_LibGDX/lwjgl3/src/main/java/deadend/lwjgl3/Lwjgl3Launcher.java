package deadend.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import deadend.DeadEndGame;

/**
 * Desktop (LWJGL3) entry point.
 * Must be run with -XstartOnFirstThread on macOS.
 */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("DEAD_END — The Forgotten Kingdom");
        config.setWindowedMode(1024, 768);
        config.setForegroundFPS(60);
        config.setResizable(true);
        config.useVsync(true);

        // macOS: disable decorations to avoid titlebar mouse offset issues
        // config.setDecorated(true);

        new Lwjgl3Application(new DeadEndGame(), config);
    }
}
