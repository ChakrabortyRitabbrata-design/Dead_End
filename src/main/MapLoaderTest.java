package main;

import tile.MapData;
import tile.MapLoader;

/**
 * Headless smoke-test for MapLoader.
 * Run: java -cp bin:res --module-path bin -m Myproject/main.MapLoaderTest
 * Does not require a display — safe to run in the sandbox.
 */
public class MapLoaderTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== MapLoader Smoke Test ===");

        MapData data = MapLoader.loadLevel(1);

        System.out.println("Level ID   : " + data.levelId);
        System.out.println("Level Name : " + data.levelName);
        System.out.println("Dimensions : " + data.mapWidth + " x " + data.mapHeight);
        System.out.println("Tile size  : " + data.tileSize);

        int[] ps = data.playerSpawn;
        System.out.println("Player spawn: col=" + ps[0] + " row=" + ps[1]);
        System.out.println("Dragon spawn: " + (data.dragonSpawn == null ? "NONE" :
                "col=" + data.dragonSpawn[0] + " row=" + data.dragonSpawn[1]));
        System.out.println("Score threshold: " + data.scoreThreshold);
        System.out.println("Next level     : " + data.nextLevel);
        System.out.println("Locations      : " + data.locations.size());
        System.out.println("Enemy regions  : " + data.enemyRegions.size());
        System.out.println("Object placements: " + data.objectPlacements.size());

        // Verify ground layer dimensions
        int nonZero = 0;
        int treeCount = 0;
        int grassCount = 0;
        int waterCount = 0;
        int pathCount = 0;
        int sandCount = 0;
        int wallCount = 0;
        int outOfRange = 0;
        for (int c = 0; c < data.mapWidth; c++) {
            for (int r = 0; r < data.mapHeight; r++) {
                int id = data.groundLayer[c][r];
                if (id != 0) nonZero++;
                switch (id) {
                    case 0 -> grassCount++;
                    case 1 -> wallCount++;
                    case 2 -> waterCount++;
                    case 3 -> pathCount++;
                    case 4 -> treeCount++;
                    case 5 -> sandCount++;
                    default -> {
                        if (id < 0 || id > 19) outOfRange++;
                    }
                }
            }
        }
        System.out.println("\n--- Ground layer tile distribution ---");
        System.out.println("Total tiles  : " + (data.mapWidth * data.mapHeight));
        System.out.println("Grass   (0)  : " + grassCount);
        System.out.println("Wall    (1)  : " + wallCount);
        System.out.println("Water   (2)  : " + waterCount);
        System.out.println("Path    (3)  : " + pathCount);
        System.out.println("Tree    (4)  : " + treeCount);
        System.out.println("Sand    (5)  : " + sandCount);
        System.out.println("Out-of-range : " + outOfRange);

        // Verify collision layer
        int blocked = 0;
        for (int c = 0; c < data.mapWidth; c++)
            for (int r = 0; r < data.mapHeight; r++)
                if (data.collisionLayer[c][r] == 1) blocked++;
        System.out.println("\nCollision layer blocked cells: " + blocked);

        // Verify player spawn is on a non-tree tile
        int spawnTile = data.groundLayer[ps[0]][ps[1]];
        System.out.println("Player spawn tile ID: " + spawnTile
                + (spawnTile == 4 ? " *** WARNING: player spawns on TREE ***" : " (OK)"));

        // Print locations
        System.out.println("\n--- Named Locations ---");
        for (MapData.LocationRecord loc : data.locations)
            System.out.printf("  %-22s @ (%d,%d)  %dx%d tiles%n",
                    loc.name(), loc.col(), loc.row(), loc.width(), loc.height());

        // Print enemy regions
        System.out.println("\n--- Enemy Regions ---");
        for (MapData.EnemyRegion r : data.enemyRegions)
            System.out.printf("  centre(%d,%d) r=%d  max=%d  delay=%d%n",
                    r.centerCol(), r.centerRow(), r.radius(), r.maxCount(), r.respawnDelay());

        // Print object placements
        System.out.println("\n--- Object Placements ---");
        for (MapData.ObjectPlacement p : data.objectPlacements)
            System.out.printf("  %-16s @ col=%d row=%d  footprint=%dx%d%n",
                    p.typeName(), p.col(), p.row(), p.widthTiles(), p.heightTiles());

        System.out.println("\n=== PASSED ===");
    }
}
