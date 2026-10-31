package tile;

import java.awt.Graphics2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import main.GamePanel;

public class TileManager {
    GamePanel gp;
    public Tile[] tile;
    public int[][] mapTilenum;

    public TileManager(GamePanel gp) {
        this.gp = gp;
        tile = new Tile[10];
        
        // Fixed: Use tile grid counts, not pixel width/height
        mapTilenum = new int[gp.maxWorldCol][gp.maxWorldRow];
        
        getTileImage();
        loadMap();
    }

    public void getTileImage() {
        try {
            tile[0] = new Tile();
            tile[0].Image = readImage("tile/grass.png");

            tile[1] = new Tile();
            tile[1].Image = readImage("tile/wall.png");
            tile[1].collision = true;

            tile[2] = new Tile();
            tile[2].Image = readImage("tile/water.png");
            tile[2].collision = true;

            tile[3] = new Tile();
            tile[3].Image = readImage("tile/earth.png");

            tile[4] = new Tile();
            tile[4].Image = readImage("tile/tree.png");
            tile[4].collision = true;

            tile[5] = new Tile();
            tile[5].Image = readImage("tile/sand.png");

        } catch (IOException e) {
            throw new IllegalStateException("Unable to load tile resources.", e);
        }
    }

    public void loadMap() {
        try (InputStream is = openResource("map/map01.txt");
                BufferedReader br = new BufferedReader(new InputStreamReader(is))) {

            int col = 0;
            int row = 0;

            while (col < gp.maxWorldCol && row < gp.maxWorldRow) {
                String line = br.readLine();

                if (line == null) {
                    break;
                }

                String[] numbers = line.split(" ");

                // Fixed: col++ is now included to prevent freezing/infinite loops
                while (col < gp.maxWorldCol && col < numbers.length) {
                    int num = Integer.parseInt(numbers[col]);
                    mapTilenum[col][row] = num;
                    col++;
                }

                if (col == gp.maxWorldCol || col == numbers.length) {
                    col = 0;
                    row++;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load map resource: map/map01.txt", e);
        }
    }

    private BufferedImage readImage(String resourcePath) throws IOException {
        try (InputStream stream = openResource(resourcePath)) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                throw new IOException("Resource is not a readable image: " + resourcePath);
            }
            return image;
        }
    }

    private InputStream openResource(String resourcePath) throws IOException {
        InputStream stream = getClass().getResourceAsStream("/" + resourcePath);
        if (stream != null) {
            return stream;
        }

        Path resource = Paths.get("res", resourcePath);
        if (!Files.exists(resource)) {
            resource = Paths.get("bin", resourcePath);
        }
        if (!Files.exists(resource)) {
            throw new IOException("Resource not found: " + resourcePath
                    + ". Add the res directory to the runtime classpath.");
        }
        return Files.newInputStream(resource);
    }

    public void draw(Graphics2D g2) {
        int worldCol = 0;
        int worldRow = 0;
        int x = 0;
        int y = 0;

        // Fixed: Loop checks maxScreenRow, and drawImage uses (x, y) order
        while (worldCol < gp.maxWorldCol && worldRow < gp.maxWorldRow) {
            int tileNum = mapTilenum[worldCol][worldRow];
            
            int worldX = worldCol * gp.tileSize;
            int worldY = worldRow * gp.tileSize;
            int screenX = worldX - gp.player.worldX + gp.player.screenX;
            int screenY = worldY - gp.player.worldY + gp.player.screenY;
            
            if (worldX + gp.tileSize > gp.player.worldX - gp.player.screenX &&
                    worldX - gp.tileSize < gp.player.worldX + gp.player.screenX &&
                    worldY + gp.tileSize > gp.player.worldY - gp.player.screenY &&
                    worldY - gp.tileSize < gp.player.worldY + gp.player.screenY) {

                    g2.drawImage(tile[tileNum].Image, screenX, screenY, gp.tileSize, gp.tileSize, null);
                }
            
            worldCol++;
            x += gp.tileSize;

            if (worldCol == gp.maxWorldCol) {
                worldCol = 0;
                x = 0;
                worldRow++;
                y += gp.tileSize;
            }
        }
    }
}