package deadend.world;

/**
 * Tile type definitions.
 * Separates collision behaviour from visual representation.
 */
public enum TileType {
    GRASS        (0,  false, 0.36f, 0.62f, 0.35f),
    WALL         (1,  true,  0.47f, 0.47f, 0.47f),
    WATER        (2,  true,  0.35f, 0.55f, 0.80f),
    DIRT         (3,  false, 0.50f, 0.35f, 0.23f),
    TREE         (4,  true,  0.17f, 0.42f, 0.17f),
    SAND         (5,  false, 0.79f, 0.62f, 0.32f),
    DARK_GRASS   (6,  false, 0.18f, 0.23f, 0.10f),
    SWAMP_WATER  (7,  true,  0.23f, 0.31f, 0.13f),
    STONE_FLOOR  (8,  false, 0.44f, 0.44f, 0.44f),
    COBBLESTONE  (9,  false, 0.60f, 0.53f, 0.47f),
    BRIDGE_PLANK (14, false, 0.63f, 0.47f, 0.29f),
    DEAD_GRASS   (16, false, 0.42f, 0.42f, 0.23f);

    public final int   id;
    public final boolean collision;
    public final float r, g, b;   // minimap / fallback colour

    TileType(int id, boolean collision, float r, float g, float b) {
        this.id = id;
        this.collision = collision;
        this.r = r; this.g = g; this.b = b;
    }

    private static final TileType[] BY_ID = new TileType[20];
    static {
        for (TileType t : values()) if (t.id < 20) BY_ID[t.id] = t;
        // fill gaps with GRASS
        for (int i = 0; i < 20; i++) if (BY_ID[i] == null) BY_ID[i] = GRASS;
    }

    public static TileType byId(int id) {
        if (id < 0 || id >= 20) return GRASS;
        return BY_ID[id];
    }
}
