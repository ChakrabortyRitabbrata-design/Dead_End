package main;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class KeyHandler implements KeyListener {

    // ── Movement keys ─────────────────────────────────────────────────────────
    public volatile boolean upPressed;
    public volatile boolean downPressed;
    public volatile boolean leftPressed;
    public volatile boolean rightPressed;

    // ── Inventory / UI keys ───────────────────────────────────────────────────
    // volatile: written on the EDT (key events), read on the game thread.
    public volatile boolean inventoryTogglePressed;   // TAB
    public volatile boolean inventoryClosePressed;    // ESC

    // ── Hotbar number key (1–9), -1 = none pressed ───────────────────────────
    public volatile int hotbarKeyPressed = -1;

    // ── Attack keys ───────────────────────────────────────────────────────────
    public volatile boolean attackPressed;    // SPACE bar
    // Left mouse click attack is handled separately via MouseListener in GamePanel

    @Override
    public void keyTyped(KeyEvent e) {
        // intentionally empty
    }

    @Override
    public void keyPressed(KeyEvent e) {

        int code = e.getKeyCode();

        // ── Movement ──────────────────────────────────────────────────────────
        if (code == KeyEvent.VK_W) upPressed    = true;
        if (code == KeyEvent.VK_S) downPressed  = true;
        if (code == KeyEvent.VK_A) leftPressed  = true;
        if (code == KeyEvent.VK_D) rightPressed = true;

        // ── Inventory ─────────────────────────────────────────────────────────
        // NOTE: TAB only reaches here when setFocusTraversalKeysEnabled(false)
        //       is called on the GamePanel (done in GamePanel constructor).
        if (code == KeyEvent.VK_TAB)    inventoryTogglePressed = true;
        if (code == KeyEvent.VK_ESCAPE) inventoryClosePressed  = true;

        // ── Attack ────────────────────────────────────────────────────────────
        if (code == KeyEvent.VK_SPACE)  attackPressed = true;

        // ── Hotbar 1–9 ────────────────────────────────────────────────────────
        if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_9) {
            hotbarKeyPressed = code - KeyEvent.VK_0; // converts to 1–9
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {

        int code = e.getKeyCode();

        // ── Movement ──────────────────────────────────────────────────────────
        if (code == KeyEvent.VK_W) upPressed    = false;
        if (code == KeyEvent.VK_S) downPressed  = false;
        if (code == KeyEvent.VK_A) leftPressed  = false;
        if (code == KeyEvent.VK_D) rightPressed = false;

        // ── Attack (reset on release so next press fires again) ───────────────
        if (code == KeyEvent.VK_SPACE) attackPressed = false;
    }
}
