package main;

import Object.OBJ_House;

public class AssetSetter {

    GamePanel gp;

    public AssetSetter(GamePanel gp) {
        this.gp = gp;
    }

    public void setObject() {
        // Places the 3x3 house directly at the top of the road
        gp.obj[0] = new OBJ_House(gp);
        gp.obj[0].worldX = (14 * gp.tileSize) + (gp.tileSize / 2);
        gp.obj[0].worldY = 18 * gp.tileSize;
    }
}