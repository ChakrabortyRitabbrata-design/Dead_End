package main;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class KeyHandler implements KeyListener, FocusListener, MouseListener {

    // ── Movement keys ─────────────────────────────────────────────────────────
    public volatile boolean upPressed;
    public volatile boolean downPressed;
    public volatile boolean leftPressed;
    public volatile boolean rightPressed;

    // ── Combat flags ──────────────────────────────────────────────────────────
    public volatile boolean punchPressed;
    public volatile boolean kickPressed;
    public volatile boolean blockPressed;
    public volatile boolean dodgePressed;

    private volatile boolean punchRequested;
    private volatile boolean kickRequested;
    private volatile boolean dodgeRequested;

    // ── Inventory / UI keys ───────────────────────────────────────────────────
    public volatile boolean inventoryTogglePressed;   // TAB
    public volatile boolean inventoryClosePressed;    // ESC

    // ── Hotbar number key (1–9), -1 = none pressed ───────────────────────────
    public volatile int hotbarKeyPressed = -1;

    // ── Weapon attack key (SPACE) ─────────────────────────────────────────────
    public volatile boolean attackPressed;

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public synchronized void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();

        // ── Movement ──────────────────────────────────────────────────────────
        if (code == KeyEvent.VK_W) {
            upPressed = true;
        } else if (code == KeyEvent.VK_S) {
            downPressed = true;
        } else if (code == KeyEvent.VK_A) {
            leftPressed = true;
        } else if (code == KeyEvent.VK_D) {
            rightPressed = true;
        }

        // ── Inventory (TAB / ESC) ─────────────────────────────────────────────
        if (code == KeyEvent.VK_TAB) {
            inventoryTogglePressed = true;
        } else if (code == KeyEvent.VK_ESCAPE) {
            inventoryClosePressed = true;
        }

        // ── Hotbar (1–9) ──────────────────────────────────────────────────────
        if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_9) {
            hotbarKeyPressed = code - KeyEvent.VK_0;
        }

        // ── Combat / Actions ──────────────────────────────────────────────────
        if (code == KeyEvent.VK_SPACE) {
            attackPressed = true;
        } else if (code == KeyEvent.VK_E) {
            if (!kickPressed) {
                kickRequested = true;
            }
            kickPressed = true;
        } else if (code == KeyEvent.VK_Q) {
            if (!dodgePressed) {
                dodgeRequested = true;
            }
            dodgePressed = true;
        }
    }

    @Override
    public synchronized void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();

        // ── Movement ──────────────────────────────────────────────────────────
        if (code == KeyEvent.VK_W) {
            upPressed = false;
        } else if (code == KeyEvent.VK_S) {
            downPressed = false;
        } else if (code == KeyEvent.VK_A) {
            leftPressed = false;
        } else if (code == KeyEvent.VK_D) {
            rightPressed = false;
        }

        // ── Combat / Actions ──────────────────────────────────────────────────
        if (code == KeyEvent.VK_SPACE) {
            attackPressed = false;
        } else if (code == KeyEvent.VK_E) {
            kickPressed = false;
        } else if (code == KeyEvent.VK_Q) {
            dodgePressed = false;
        }
    }

    public synchronized boolean consumePunch() {
        boolean requested = punchRequested;
        punchRequested = false;
        return requested;
    }

    public synchronized boolean consumeKick() {
        boolean requested = kickRequested;
        kickRequested = false;
        return requested;
    }

    public synchronized boolean consumeDodge() {
        boolean requested = dodgeRequested;
        dodgeRequested = false;
        return requested;
    }

    @Override
    public synchronized void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            if (!punchPressed) {
                punchRequested = true;
            }
            punchPressed = true;
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            blockPressed = true;
        }
    }

    @Override
    public synchronized void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            punchPressed = false;
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            blockPressed = false;
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public synchronized void focusLost(FocusEvent e) {
        upPressed = false;
        downPressed = false;
        leftPressed = false;
        rightPressed = false;
        punchPressed = false;
        kickPressed = false;
        blockPressed = false;
        dodgePressed = false;
        punchRequested = false;
        kickRequested = false;
        dodgeRequested = false;
        attackPressed = false;
    }

    @Override
    public void focusGained(FocusEvent e) {
    }
}
