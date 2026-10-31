package inventory;

/**
 * Manages the 9-slot hotbar: which slot is selected, and
 * delegates actual item storage to the shared Inventory backing array.
 */
public class Hotbar {

    // ── State ─────────────────────────────────────────────────────────────────
    private int selectedSlot = 0;           // 0–8
    private final Inventory inventory;

    // ── Constructor ───────────────────────────────────────────────────────────

    public Hotbar(Inventory inventory) {
        this.inventory = inventory;
    }

    // ── Selected slot ─────────────────────────────────────────────────────────

    public int getSelectedSlot() {
        return selectedSlot;
    }

    /** Sets the selected hotbar slot. Clamps to 0–8. */
    public void setSelectedSlot(int slot) {
        if (slot < 0) slot = 0;
        if (slot > Inventory.COLS - 1) slot = Inventory.COLS - 1;
        this.selectedSlot = slot;
    }

    /** Selects a slot via keyboard number keys (1–9 → index 0–8). */
    public void selectByKeyNumber(int keyNumber) {
        // keyNumber is 1-based from VK_1…VK_9
        setSelectedSlot(keyNumber - 1);
    }

    // ── Item access ───────────────────────────────────────────────────────────

    /** Returns the item currently in the selected hotbar slot, or null. */
    public Item getSelectedItem() {
        return inventory.getHotbarSlot(selectedSlot);
    }

    /**
     * Returns the item in hotbar slot col (0–8), or null.
     * Delegates straight to Inventory so there is one source of truth.
     */
    public Item getSlotItem(int col) {
        return inventory.getHotbarSlot(col);
    }
}
