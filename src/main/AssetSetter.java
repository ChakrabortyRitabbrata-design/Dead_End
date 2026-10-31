package main;

import Object.OBJ_House;
import Object.SuperObject;
import tile.MapData;

/**
 * Places SuperObject instances into the world by reading OBJECT records from
 * the currently loaded level's MapData.
 *
 * To add a new object type: implement a SuperObject subclass and add its
 * name to the createObject() switch below.
 */
public class AssetSetter {

    GamePanel gp;

    public AssetSetter(GamePanel gp) {
        this.gp = gp;
    }

    public void setObject() {
        gp.objects.clear();

        if (gp.tileM.currentMap == null) return;

        for (MapData.ObjectPlacement p : gp.tileM.currentMap.objectPlacements) {
            SuperObject obj = createObject(p.typeName());
            if (obj != null) {
                obj.worldX = p.col() * gp.tileSize;
                obj.worldY = p.row() * gp.tileSize;
                gp.objects.add(obj);
            }
        }
    }

    private SuperObject createObject(String typeName) {
        return switch (typeName) {
            case "OBJ_HOUSE" -> new OBJ_House(gp);
            // Future types added here as artwork/classes become available:
            // case "OBJ_GRAVESTONE"  -> new OBJ_Gravestone(gp);
            // case "OBJ_CAMPFIRE"    -> new OBJ_Campfire(gp);
            // case "OBJ_RUIN_PILLAR" -> new OBJ_RuinPillar(gp);
            // case "OBJ_WELL"        -> new OBJ_Well(gp);
            // case "OBJ_SHRINE"      -> new OBJ_Shrine(gp);
            // case "OBJ_CRYSTAL"     -> new OBJ_Crystal(gp);
            // case "OBJ_CAVE"        -> new OBJ_Cave(gp);
            // case "OBJ_PORTAL"      -> new OBJ_Portal(gp);
            default -> {
                System.err.println("AssetSetter: unknown object type '" + typeName + "' — skipped.");
                yield null;
            }
        };
    }
}
