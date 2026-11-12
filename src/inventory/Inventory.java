package inventory;

/**
 * Manages all inventory slots (27 main grid + 9 hotbar).
 *
 * Slot indices:
 *   0–26  →  main inventory (3 rows × 9 columns)
 *   27–35 →  hotbar        (1 row  × 9 columns)
 *
 * Both arrays are backed by a single Item[] of length 36
 * so items can be moved freely between them.
 */
public class Inventory {

    // ── Constants ────────────────────────────────────────────────────────────
    public static final int MAIN_ROWS    = 3;
    public static final int COLS         = 9;
    public static final int MAIN_SLOTS   = MAIN_ROWS * COLS;   // 27
    public static final int HOTBAR_SLOTS = COLS;                // 9
    public static final int TOTAL_SLOTS  = MAIN_SLOTS + HOTBAR_SLOTS; // 36

    // Slot index offset where hotbar starts
    public static final int HOTBAR_OFFSET = MAIN_SLOTS;        // 27

    // ── Storage ──────────────────────────────────────────────────────────────
    private final Item[] slots = new Item[TOTAL_SLOTS];

    // ── Constructor ──────────────────────────────────────────────────────────

    public Inventory() {
        // ── Seed 8 required equipment test items in main inventory (slots 0–7) ─
        slots[0] = new Weapon("Wooden Sword",    "A simple wooden training sword.",  8, 25, 50, "Sword");
        slots[1] = new Weapon("Iron Sword",      "A sturdy forged iron sword.",     12, 30, 60, "Sword");
        slots[2] = new Weapon("Basic Axe",       "A sharp woodcutter's battleaxe.", 15, 45, 55, "Axe");
        slots[3] = new Armor("Basic Helmet",     "Iron cap protecting the head.",    5, "Helmet",     EquipmentSlot.HEAD);
        slots[4] = new Armor("Basic Chestplate", "Reinforced iron cuirass.",        15, "Chestplate", EquipmentSlot.CHEST);
        slots[5] = new Armor("Basic Leggings",   "Protective chainmail leggings.",  10, "Leggings",   EquipmentSlot.LEGS);
        slots[6] = new Armor("Basic Boots",      "Heavy-soled travelling boots.",    5, "Boots",      EquipmentSlot.FEET);
        slots[7] = new Armor("Basic Shield",     "Sturdy wooden shield with boss.", 12, "Shield",     EquipmentSlot.OFF_HAND);

        // ── Additional utility items in main inventory (slots 8–11) ───────────
        slots[8]  = new Item("Torch",    "Lights up dark areas.",   Item.ItemType.MISC);
        slots[9]  = new Item("Herb",     "A restorative herb.",     Item.ItemType.CONSUMABLE);
        slots[10] = new Item("Iron Ore", "Raw iron ore for smithing.", Item.ItemType.MATERIAL);
        slots[11] = new Item("Gem",      "A brilliant sapphire.",   Item.ItemType.MISC);

        // ── Seed hotbar slot 0 with torch so hotbar is functional at start ─────
        slots[HOTBAR_OFFSET + 0] = new Item("Torch", "Torch for exploring.", Item.ItemType.MISC);
    }

    // ── Slot accessors ───────────────────────────────────────────────────────

    /** Returns the item at the given absolute slot index, or null if empty. */
    public Item getSlot(int index) {
        if (index < 0 || index >= TOTAL_SLOTS) return null;
        return slots[index];
    }

    /** Directly sets a slot (used by UI for drag-and-drop). */
    public void setSlot(int index, Item item) {
        if (index < 0 || index >= TOTAL_SLOTS) return;
        slots[index] = item;
    }

    // ── Convenience helpers ──────────────────────────────────────────────────

    /** Main inventory slot → absolute index. */
    public int mainIndex(int row, int col) {
        return row * COLS + col;
    }

    /** Hotbar slot → absolute index. */
    public int hotbarIndex(int col) {
        return HOTBAR_OFFSET + col;
    }

    // ── Add item ─────────────────────────────────────────────────────────────

    /**
     * Tries to add an item to the first empty slot in the main inventory,
     * then falls back to the hotbar. Returns true if successfully placed.
     */
    public boolean addItem(Item item) {
        // First try stacking onto an existing compatible stack
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (slots[i] != null
                    && slots[i].name.equals(item.name)
                    && slots[i].stackSize < slots[i].maxStackSize) {
                slots[i].stackSize += item.stackSize;
                return true;
            }
        }
        // Then try empty slot
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (slots[i] == null) {
                slots[i] = item.copy();
                return true;
            }
        }
        return false; // inventory full
    }

    // ── Swap ─────────────────────────────────────────────────────────────────

    /**
     * Swaps the items at two slot indices.
     * Works for empty↔item, item↔item, and item↔empty.
     */
    public void swapSlots(int a, int b) {
        if (a < 0 || a >= TOTAL_SLOTS) return;
        if (b < 0 || b >= TOTAL_SLOTS) return;
        Item temp = slots[a];
        slots[a]  = slots[b];
        slots[b]  = temp;
    }

    // ── Hotbar helpers ───────────────────────────────────────────────────────

    /** Returns the Item in hotbar slot col (0–8), or null. */
    public Item getHotbarSlot(int col) {
        return getSlot(hotbarIndex(col));
    }
}
