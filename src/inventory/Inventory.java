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
        // ── Seed hotbar with basic weapons (slots 27–29) ───────────────────────
        slots[HOTBAR_OFFSET + 0] = new Weapon("Iron Sword",   "A sturdy iron sword.",     10, 30, 60, "Sword");
        slots[HOTBAR_OFFSET + 1] = new Weapon("War Axe",      "Heavy but devastating.",   16, 50, 55, "Axe");
        slots[HOTBAR_OFFSET + 2] = new Weapon("Shortbow",     "Fast ranged weapon.",       7, 20, 200, "Bow");

        // ── Seed main inventory with misc items (slots 0–4) ───────────────────
        slots[0] = new Item("Torch",   "Lights up dark areas.",   Item.ItemType.MISC);
        slots[1] = new Item("Herb",    "A healing herb.",          Item.ItemType.CONSUMABLE);
        slots[2] = new Item("Iron Ore","Raw iron ore.",            Item.ItemType.MATERIAL);
        slots[3] = new Item("Gem",     "A shiny gem.",             Item.ItemType.MISC);
        slots[4] = new Item("Bone",    "An old bone.",             Item.ItemType.MISC);

        // ── Add a couple more weapons in the main inventory ────────────────────
        slots[9]  = new Weapon("Steel Sword",  "Forged from pure steel.",   14, 28, 60, "Sword");
        slots[10] = new Weapon("Hunting Bow",  "For swift kills at range.", 10, 18, 250, "Bow");
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
