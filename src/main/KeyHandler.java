package main;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashSet;
import java.util.Set;

/**
 * Routes movement, combat and world-map input. One-shot actions are consumed by
 * Player or the world systems so held keys cannot restart an animation.
 */
public class KeyHandler implements KeyListener, FocusListener, MouseListener {

    public boolean upPressed;
    public boolean downPressed;
    public boolean leftPressed;
    public boolean rightPressed;
    public boolean punchPressed;
    public boolean kickPressed;
    public boolean blockPressed;
    public boolean dodgePressed;
    public boolean runPressed;
    public boolean rollPressed;
    public boolean inventoryTogglePressed;
    public boolean inventoryClosePressed;
    public int hotbarKeyPressed = -1;
    public boolean attackPressed;
    private boolean respawnRequested;

    private boolean punchRequested;
    private boolean kickRequested;
    private boolean dodgeRequested;
    private boolean rollRequested;
    private boolean interactRequested;
    private boolean interactHeld;
    private boolean saveRequested;
    private boolean loadRequested;
    private boolean fitRequested;
    private boolean mapToggleRequested;
    private boolean escapeRequested;
    private boolean mapKeyHeld;
    private boolean escapeKeyHeld;

    private long inputOrder;
    private long upOrder;
    private long downOrder;
    private long leftOrder;
    private long rightOrder;
    private final Set<Integer> commandHeld = new HashSet<>();

    /** Set by GamePanel so a minimap click can open the atlas. */
    public Minimap minimap;

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public synchronized void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        switch (code) {
            case KeyEvent.VK_W -> {
                upPressed = true;
                upOrder = ++inputOrder;
            }
            case KeyEvent.VK_S -> {
                downPressed = true;
                downOrder = ++inputOrder;
            }
            case KeyEvent.VK_A -> {
                leftPressed = true;
                leftOrder = ++inputOrder;
            }
            case KeyEvent.VK_D -> {
                rightPressed = true;
                rightOrder = ++inputOrder;
            }
            case KeyEvent.VK_J -> requestPunch();
            case KeyEvent.VK_E, KeyEvent.VK_K -> requestKick();
            case KeyEvent.VK_Q -> requestDodge();
            case KeyEvent.VK_SPACE -> attackPressed = true;
            case KeyEvent.VK_R -> {
                respawnRequested = true;
                blockPressed = true;
            }
            case KeyEvent.VK_CONTROL -> requestRoll();
            case KeyEvent.VK_SHIFT -> runPressed = true;
            case KeyEvent.VK_F -> {
                if (!interactHeld) {
                    interactRequested = true;
                }
                interactHeld = true;
            }
            case KeyEvent.VK_F5 -> {
                if (commandHeld.add(code)) {
                    saveRequested = true;
                }
            }
            case KeyEvent.VK_F9 -> {
                if (commandHeld.add(code)) {
                    loadRequested = true;
                }
            }
            case KeyEvent.VK_HOME -> {
                if (commandHeld.add(code)) {
                    fitRequested = true;
                }
            }
            case KeyEvent.VK_M -> {
                if (!mapKeyHeld) {
                    mapToggleRequested = true;
                    mapKeyHeld = true;
                }
            }
            case KeyEvent.VK_ESCAPE -> {
                if (!escapeKeyHeld) {
                    escapeRequested = true;
                    escapeKeyHeld = true;
                }
            }
            default -> {
            }
        }
        if (code == KeyEvent.VK_TAB) inventoryTogglePressed = true;
        if (code == KeyEvent.VK_ESCAPE) inventoryClosePressed = true;
        if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_9) {
            hotbarKeyPressed = code - KeyEvent.VK_0;
        }
    }

    @Override
    public synchronized void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        commandHeld.remove(code);
        switch (code) {
            case KeyEvent.VK_W -> upPressed = false;
            case KeyEvent.VK_S -> downPressed = false;
            case KeyEvent.VK_A -> leftPressed = false;
            case KeyEvent.VK_D -> rightPressed = false;
            case KeyEvent.VK_J -> punchPressed = false;
            case KeyEvent.VK_E, KeyEvent.VK_K -> kickPressed = false;
            case KeyEvent.VK_Q -> dodgePressed = false;
            case KeyEvent.VK_CONTROL -> rollPressed = false;
            case KeyEvent.VK_SHIFT -> runPressed = false;
            case KeyEvent.VK_SPACE -> {
                dodgePressed = false;
                attackPressed = false;
            }
            case KeyEvent.VK_R -> blockPressed = false;
            case KeyEvent.VK_F -> interactHeld = false;
            case KeyEvent.VK_M -> mapKeyHeld = false;
            case KeyEvent.VK_ESCAPE -> escapeKeyHeld = false;
            default -> {
            }
        }
    }

    private void requestPunch() {
        if (!punchPressed) {
            punchRequested = true;
        }
        punchPressed = true;
    }

    private void requestKick() {
        if (!kickPressed) {
            kickRequested = true;
        }
        kickPressed = true;
    }

    private void requestDodge() {
        if (!dodgePressed) {
            dodgeRequested = true;
        }
        dodgePressed = true;
    }

    private void requestRoll() {
        if (!rollPressed) {
            rollRequested = true;
        }
        rollPressed = true;
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

    public synchronized boolean consumeRoll() {
        boolean requested = rollRequested;
        rollRequested = false;
        return requested;
    }

    public synchronized boolean consumeInteract() {
        boolean requested = interactRequested;
        interactRequested = false;
        return requested;
    }

    public synchronized boolean consumeSave() {
        boolean requested = saveRequested;
        saveRequested = false;
        return requested;
    }

    public synchronized boolean consumeLoad() {
        boolean requested = loadRequested;
        loadRequested = false;
        return requested;
    }

    public synchronized boolean consumeFit() {
        boolean requested = fitRequested;
        fitRequested = false;
        return requested;
    }

    public synchronized boolean consumeMapToggle() {
        boolean requested = mapToggleRequested;
        mapToggleRequested = false;
        return requested;
    }

    public synchronized boolean consumeEscape() {
        boolean requested = escapeRequested;
        escapeRequested = false;
        return requested;
    }

    public synchronized void requestRespawn() {
        respawnRequested = true;
    }

    public synchronized boolean consumeRespawn() {
        boolean requested = respawnRequested;
        respawnRequested = false;
        return requested;
    }

    /** The most recently pressed held direction wins, matching animation facing. */
    public synchronized String getMovementDirection() {
        long latest = Math.max(Math.max(upPressed ? upOrder : 0, downPressed ? downOrder : 0),
                Math.max(leftPressed ? leftOrder : 0, rightPressed ? rightOrder : 0));
        if (latest == upOrder && upPressed) {
            return "up";
        }
        if (latest == downOrder && downPressed) {
            return "down";
        }
        if (latest == leftOrder && leftPressed) {
            return "left";
        }
        if (latest == rightOrder && rightPressed) {
            return "right";
        }
        return null;
    }

    @Override
    public synchronized void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            if (minimap != null && minimap.containsPoint(e.getX(), e.getY())) {
                mapToggleRequested = true;
                return;
            }
            requestPunch();
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            // Preserve the existing guard control; K/E provide the teammate kick action.
            blockPressed = true;
        }
    }

    @Override
    public synchronized void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            punchPressed = false;
            if (minimap != null) {
                minimap.resetDrag();
            }
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            blockPressed = false;
        }
    }

    /** Clears held state when UI mode changes or the window loses focus. */
    public synchronized void clearAllInputs() {
        upPressed = false;
        downPressed = false;
        leftPressed = false;
        rightPressed = false;
        punchPressed = false;
        kickPressed = false;
        blockPressed = false;
        dodgePressed = false;
        runPressed = false;
        rollPressed = false;
        inventoryTogglePressed = false;
        inventoryClosePressed = false;
        hotbarKeyPressed = -1;
        attackPressed = false;
        respawnRequested = false;
        punchRequested = false;
        kickRequested = false;
        dodgeRequested = false;
        rollRequested = false;
        interactRequested = false;
        interactHeld = false;
        saveRequested = false;
        loadRequested = false;
        fitRequested = false;
        commandHeld.clear();
        inputOrder = 0;
        upOrder = 0;
        downOrder = 0;
        leftOrder = 0;
        rightOrder = 0;
    }

    @Override
    public synchronized void focusLost(FocusEvent e) {
        clearAllInputs();
    }

    @Override
    public void focusGained(FocusEvent e) {
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
}
