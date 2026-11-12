package main;

import animation.AnimationState;
import animation.Direction;
import entities.Player;
import java.awt.event.KeyEvent;

public class TestDodge {
    public static void main(String[] args) throws Exception {
        System.out.println("--- Starting Dodge Animation & Logic Tests ---");
        GamePanel gp = new GamePanel();
        KeyHandler kh = gp.keyH;
        Player p = gp.player;

        // Verify initial state
        if (!p.actionState.equals("IDLE")) throw new AssertionError("Expected initial state IDLE");

        // Test Dodge in each direction
        Direction[] directions = {Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT};
        int[] keyCodes = {KeyEvent.VK_W, KeyEvent.VK_S, KeyEvent.VK_A, KeyEvent.VK_D};

        for (int i = 0; i < directions.length; i++) {
            Direction dir = directions[i];
            int keyCode = keyCodes[i];

            System.out.println("Testing dodge direction: " + dir);

            // Press movement key to set direction
            kh.keyPressed(new KeyEvent(gp, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode, ' '));
            p.update(16_666_667L);
            kh.keyReleased(new KeyEvent(gp, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, keyCode, ' '));

            // Press SPACE
            int startX = p.worldX;
            int startY = p.worldY;
            kh.keyPressed(new KeyEvent(gp, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
            p.update(16_666_667L);

            if (!p.actionState.equals("DODGE")) throw new AssertionError("Expected state DODGE for " + dir);
            if (!p.isDodging()) throw new AssertionError("Expected isDodging() == true");

            // Test invulnerability during dodge
            int hpBefore = p.getHealth();
            p.takeDamage(50);
            if (p.getHealth() != hpBefore) throw new AssertionError("Player took damage during dodge!");

            // Run dodge frames (4 frames * 75ms = 300ms)
            for (int step = 0; step < 25; step++) {
                p.update(16_666_667L);
            }

            // Player should have moved
            boolean moved = (dir == Direction.UP && p.worldY < startY)
                    || (dir == Direction.DOWN && p.worldY > startY)
                    || (dir == Direction.LEFT && p.worldX < startX)
                    || (dir == Direction.RIGHT && p.worldX > startX);
            System.out.println("  Moved in direction " + dir + ": " + moved + " (deltaX=" + (p.worldX - startX) + ", deltaY=" + (p.worldY - startY) + ")");

            // Should have returned to IDLE after dodge completion
            System.out.println("  State after dodge: " + p.actionState + ", facing=" + p.direction);
            if (!p.actionState.equals("IDLE")) throw new AssertionError("Expected return to IDLE, got " + p.actionState);

            kh.keyReleased(new KeyEvent(gp, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));

            // Elapse cooldown
            for (int c = 0; c < 35; c++) {
                p.update(16_666_667L);
            }
        }

        // Test smooth transition to WALK if movement key is held when dodge finishes
        System.out.println("Testing dodge completion transition into WALK...");
        kh.keyPressed(new KeyEvent(gp, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_D, 'D'));
        p.update(16_666_667L);
        kh.keyPressed(new KeyEvent(gp, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
        p.update(16_666_667L);
        if (!p.actionState.equals("DODGE")) throw new AssertionError("Expected state DODGE");

        // Keep D pressed throughout dodge
        for (int step = 0; step < 25; step++) {
            p.update(16_666_667L);
        }
        System.out.println("State after dodge with D held: " + p.actionState);
        if (!p.actionState.equals("WALK")) throw new AssertionError("Expected transition to WALK when D held");

        kh.keyReleased(new KeyEvent(gp, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_D, 'D'));
        kh.keyReleased(new KeyEvent(gp, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
        p.update(16_666_667L);
        if (!p.actionState.equals("IDLE")) throw new AssertionError("Expected IDLE after releasing D");

        System.out.println("ALL DODGE TESTS PASSED SUCCESSFULLY!");
    }
}
