package Object;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.io.IOException;
import javax.imageio.ImageIO;
import main.GamePanel;

public class OBJ_House extends SuperObject {

    GamePanel gp;

    public OBJ_House(GamePanel gp) {
        this.gp = gp;
        name = "House";
        collision = true;

        // Hitbox covers the bottom portion (walls and base)
        solidArea = new Rectangle(8, gp.tileSize, (gp.tileSize * 3) - 16, (gp.tileSize * 2) - 10);
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;

        try {
            // Semicolon added and switched to getResourceAsStream
            image = ImageIO.read(getClass().getResourceAsStream("/objects/House.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void draw(Graphics2D g2, GamePanel gp) {
        int screenX = worldX - gp.player.worldX + gp.player.screenX;
        int screenY = worldY - gp.player.worldY + gp.player.screenY;

        // Render multi-tile 3x3 footprint
        if (worldX + (gp.tileSize * 3) > gp.player.worldX - gp.player.screenX &&
            worldX - (gp.tileSize * 3) < gp.player.worldX + gp.player.screenX &&
            worldY + (gp.tileSize * 3) > gp.player.worldY - gp.player.screenY &&
            worldY - (gp.tileSize * 3) < gp.player.worldY + gp.player.screenY) {

            g2.drawImage(image, screenX, screenY, gp.tileSize * 3, gp.tileSize * 3, null);
        }
    }
}