package main;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class KeyHandler implements KeyListener, FocusListener, MouseListener {

    // -------------------------------------------------------------------------
    // Movement / combat (held-state flags)
    // -------------------------------------------------------------------------
    public boolean upPressed;
    public boolean downPressed;
    public boolean leftPressed;
    public boolean rightPressed;
    public boolean punchPressed;
    public boolean kickPressed;
    public boolean blockPressed;
    public boolean dodgePressed;

    // -------------------------------------------------------------------------
    // One-shot requests (consumed after reading)
    // -------------------------------------------------------------------------
    private boolean punchRequested;
    private boolean kickRequested;
    private boolean dodgeRequested;

    // -------------------------------------------------------------------------
    // Map toggle and escape — one-shot with held-key guard
    // -------------------------------------------------------------------------
    private boolean mapToggleRequested;
    private boolean escapeRequested;
    private boolean mapKeyHeld;
    private boolean escapeKeyHeld;

    // -------------------------------------------------------------------------
    // Minimap reference (set by GamePanel after construction)
    // -------------------------------------------------------------------------
    public Minimap minimap;

    // =========================================================================
    // KeyListener
    // =========================================================================

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public synchronized void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();

        switch (code) {
            case KeyEvent.VK_W -> upPressed    = true;
            case KeyEvent.VK_S -> downPressed  = true;
            case KeyEvent.VK_A -> leftPressed  = true;
            case KeyEvent.VK_D -> rightPressed = true;

            case KeyEvent.VK_E -> {
                if (!kickPressed) kickRequested = true;
                kickPressed = true;
            }
            case KeyEvent.VK_Q -> {
                if (!dodgePressed) dodgeRequested = true;
                dodgePressed = true;
            }

            // M — map toggle: fire once per physical press
            case KeyEvent.VK_M -> {
                if (!mapKeyHeld) {
                    mapToggleRequested = true;
                    mapKeyHeld = true;
                }
            }
            // Escape — fire once per physical press
            case KeyEvent.VK_ESCAPE -> {
                if (!escapeKeyHeld) {
                    escapeRequested = true;
                    escapeKeyHeld = true;
                }
            }
        }
    }

    @Override
    public synchronized void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        switch (code) {
            case KeyEvent.VK_W      -> upPressed    = false;
            case KeyEvent.VK_S      -> downPressed  = false;
            case KeyEvent.VK_A      -> leftPressed  = false;
            case KeyEvent.VK_D      -> rightPressed = false;
            case KeyEvent.VK_E      -> kickPressed  = false;
            case KeyEvent.VK_Q      -> dodgePressed = false;
            case KeyEvent.VK_M      -> mapKeyHeld   = false;
            case KeyEvent.VK_ESCAPE -> escapeKeyHeld = false;
        }
    }

    // =========================================================================
    // One-shot consume methods
    // =========================================================================

    public synchronized boolean consumePunch() {
        boolean v = punchRequested; punchRequested = false; return v;
    }

    public synchronized boolean consumeKick() {
        boolean v = kickRequested; kickRequested = false; return v;
    }

    public synchronized boolean consumeDodge() {
        boolean v = dodgeRequested; dodgeRequested = false; return v;
    }

    public synchronized boolean consumeMapToggle() {
        boolean v = mapToggleRequested; mapToggleRequested = false; return v;
    }

    public synchronized boolean consumeEscape() {
        boolean v = escapeRequested; escapeRequested = false; return v;
    }

    // =========================================================================
    // MouseListener
    // =========================================================================

    @Override
    public synchronized void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            // Check if the click lands on the HUD minimap — if so, open the full map
            if (minimap != null && minimap.containsPoint(e.getX(), e.getY())) {
                mapToggleRequested = true;
                return;
            }
            if (!punchPressed) punchRequested = true;
            punchPressed = true;
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            blockPressed = true;
        }
    }

    @Override
    public synchronized void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            punchPressed = false;
            if (minimap != null) minimap.resetDrag();
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            blockPressed = false;
        }
    }

    @Override public void mouseClicked(MouseEvent e)  {}
    @Override public void mouseEntered(MouseEvent e)  {}
    @Override public void mouseExited(MouseEvent e)   {}

    // =========================================================================
    // Input suppression
    // =========================================================================

    /**
     * Clears all held movement and combat inputs.
     * Call when entering a non-PLAYING state (e.g. opening the full map)
     * so that inputs pressed before opening do not fire when gameplay resumes.
     */
    public synchronized void clearAllInputs() {
        upPressed      = false;  downPressed     = false;
        leftPressed    = false;  rightPressed    = false;
        punchPressed   = false;  kickPressed     = false;
        blockPressed   = false;  dodgePressed    = false;
        punchRequested = false;  kickRequested   = false;
        dodgeRequested = false;
    }

    // =========================================================================
    // FocusListener
    // =========================================================================

    @Override
    public synchronized void focusLost(FocusEvent e) {
        clearAllInputs();
    }

    @Override
    public void focusGained(FocusEvent e) {}
}
