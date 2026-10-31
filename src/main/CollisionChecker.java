package main;

import entities.Entity;

/**
 * Checks whether an entity's next movement would collide with a solid tile.
 *
 * Two-source collision:
 *   1. tile[id].collision — inherent impassability of the tile type
 *   2. collisionLayer[col][row] — independent per-cell override from levelN_collision.txt
 *
 * A cell is blocked if EITHER source returns true.
 *
 * All array accesses are bounds-checked; out-of-world positions are treated as solid
 * to prevent ArrayIndexOutOfBoundsExceptions at map edges.
 */
public class CollisionChecker {

    GamePanel gp;

    public CollisionChecker(GamePanel gp) {
        this.gp = gp;
    }

    public void checkTile(Entity entity) {

        int entityLeftWorldX   = entity.worldX + entity.solidArea.x;
        int entityRightWorldX  = entity.worldX + entity.solidArea.x + entity.solidArea.width - 1;
        int entityTopWorldY    = entity.worldY + entity.solidArea.y;
        int entityBottomWorldY = entity.worldY + entity.solidArea.y + entity.solidArea.height - 1;

        int entityLeftCol   = entityLeftWorldX   / gp.tileSize;
        int entityRightCol  = entityRightWorldX  / gp.tileSize;
        int entityTopRow    = entityTopWorldY    / gp.tileSize;
        int entityBottomRow = entityBottomWorldY / gp.tileSize;

        int tileNum1, tileNum2;

        switch (entity.direction) {
            case "up":
                entityTopRow = (entityTopWorldY - entity.speed) / gp.tileSize;
                tileNum1 = safeGetTile(entityLeftCol,  entityTopRow);
                tileNum2 = safeGetTile(entityRightCol, entityTopRow);
                if (isSolid(entityLeftCol,  entityTopRow, tileNum1)
                        || isSolid(entityRightCol, entityTopRow, tileNum2))
                    entity.collisionOn = true;
                break;

            case "down":
                entityBottomRow = (entityBottomWorldY + entity.speed) / gp.tileSize;
                tileNum1 = safeGetTile(entityLeftCol,  entityBottomRow);
                tileNum2 = safeGetTile(entityRightCol, entityBottomRow);
                if (isSolid(entityLeftCol,  entityBottomRow, tileNum1)
                        || isSolid(entityRightCol, entityBottomRow, tileNum2))
                    entity.collisionOn = true;
                break;

            case "left":
                entityLeftCol = (entityLeftWorldX - entity.speed) / gp.tileSize;
                tileNum1 = safeGetTile(entityLeftCol, entityTopRow);
                tileNum2 = safeGetTile(entityLeftCol, entityBottomRow);
                if (isSolid(entityLeftCol, entityTopRow,    tileNum1)
                        || isSolid(entityLeftCol, entityBottomRow, tileNum2))
                    entity.collisionOn = true;
                break;

            case "right":
                entityRightCol = (entityRightWorldX + entity.speed) / gp.tileSize;
                tileNum1 = safeGetTile(entityRightCol, entityTopRow);
                tileNum2 = safeGetTile(entityRightCol, entityBottomRow);
                if (isSolid(entityRightCol, entityTopRow,    tileNum1)
                        || isSolid(entityRightCol, entityBottomRow, tileNum2))
                    entity.collisionOn = true;
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the tile ID at (col, row), clamped to world bounds.
     * Out-of-bounds positions return tile 0 (grass, non-solid by default)
     * but isSolid() will still block them via the bounds-as-solid rule.
     */
    private int safeGetTile(int col, int row) {
        if (col < 0 || col >= gp.maxWorldCol || row < 0 || row >= gp.maxWorldRow) return 0;
        return gp.tileM.mapTilenum[col][row];
    }

    /**
     * Returns true if the cell at (col, row) is solid.
     * Out-of-bounds → always solid (prevents walking off the map edge).
     */
    private boolean isSolid(int col, int row, int tileId) {
        // Bounds check → solid
        if (col < 0 || col >= gp.maxWorldCol || row < 0 || row >= gp.maxWorldRow) return true;
        // Tile type collision
        if (gp.tileM.tile[tileId].collision) return true;
        // Independent collision layer
        if (gp.tileM.collisionLayer[col][row] == 1) return true;
        return false;
    }
}
