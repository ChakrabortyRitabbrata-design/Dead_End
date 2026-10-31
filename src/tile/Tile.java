package tile;

import java.awt.Color;
import java.awt.image.BufferedImage;

public class Tile {
    public BufferedImage Image;
    public boolean collision;

    /** Name used for debug output and minimap tooltip. */
    public String name = "";

    /**
     * Pre-computed minimap colour for this tile type.
     * Set once in TileManager.getTileImage(); read every frame by Minimap.
     */
    public Color minimapColor = Color.BLACK;
}
