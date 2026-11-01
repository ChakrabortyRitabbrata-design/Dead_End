package main;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

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

    private boolean punchRequested;
    private boolean kickRequested;
    private boolean dodgeRequested;
    private boolean rollRequested;
    private long inputOrder;
    private long upOrder;
    private long downOrder;
    private long leftOrder;
    private long rightOrder;

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public synchronized void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();

        if (code == KeyEvent.VK_W) {
            upPressed = true;
            upOrder = ++inputOrder;
        } else if (code == KeyEvent.VK_S) {
            downPressed = true;
            downOrder = ++inputOrder;
        } else if (code == KeyEvent.VK_A) {
            leftPressed = true;
            leftOrder = ++inputOrder;
        } else if (code == KeyEvent.VK_D) {
            rightPressed = true;
            rightOrder = ++inputOrder;
        } else if (code == KeyEvent.VK_J) {
            if (!punchPressed) {
                punchRequested = true;
            }
            punchPressed = true;
        } else if (code == KeyEvent.VK_E || code == KeyEvent.VK_K) {
            if (!kickPressed) {
                kickRequested = true;
            }
            kickPressed = true;
        } else if (code == KeyEvent.VK_Q || code == KeyEvent.VK_SPACE) {
            if (!dodgePressed) {
                dodgeRequested = true;
            }
            dodgePressed = true;
        } else if (code == KeyEvent.VK_CONTROL) {
            if (!rollPressed) {
                rollRequested = true;
            }
            rollPressed = true;
        } else if (code == KeyEvent.VK_SHIFT) {
            runPressed = true;
        }
    }

    @Override
    public synchronized void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_W) {
            upPressed = false;
        } else if (code == KeyEvent.VK_S) {
            downPressed = false;
        } else if (code == KeyEvent.VK_A) {
            leftPressed = false;
        } else if (code == KeyEvent.VK_D) {
            rightPressed = false;
        } else if (code == KeyEvent.VK_J) {
            punchPressed = false;
        } else if (code == KeyEvent.VK_E || code == KeyEvent.VK_K) {
            kickPressed = false;
        } else if (code == KeyEvent.VK_Q || code == KeyEvent.VK_SPACE) {
            dodgePressed = false;
        } else if (code == KeyEvent.VK_CONTROL) {
            rollPressed = false;
        } else if (code == KeyEvent.VK_SHIFT) {
            runPressed = false;
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

    public synchronized boolean consumeRoll() {
        boolean requested = rollRequested;
        rollRequested = false;
        return requested;
    }

    public synchronized String getMovementDirection() {
        long latest = Math.max(Math.max(upPressed ? upOrder : 0,
                downPressed ? downOrder : 0),
                Math.max(leftPressed ? leftOrder : 0,
                        rightPressed ? rightOrder : 0));
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
            if (!punchPressed) {
                punchRequested = true;
            }
            punchPressed = true;
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            if (!kickPressed) {
                kickRequested = true;
            }
            kickPressed = true;
        }
    }

    @Override
    public synchronized void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            punchPressed = false;
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            kickPressed = false;
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
        runPressed = false;
        rollPressed = false;
        punchRequested = false;
        kickRequested = false;
        dodgeRequested = false;
        rollRequested = false;
        inputOrder = 0;
        upOrder = 0;
        downOrder = 0;
        leftOrder = 0;
        rightOrder = 0;
    }

    @Override
    public void focusGained(FocusEvent e) {
    }
}
