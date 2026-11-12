package inventory;

import java.util.EnumMap;
import java.util.Map;

/**
 * Manages the character's 6 equipment slots:
 * MAIN_HAND, OFF_HAND, HEAD, CHEST, LEGS, FEET.
 *
 * Enforces slot compatibility rules and tracks equipped items.
 */
public class EquipmentManager {

    private final Map<EquipmentSlot, Item> equipped = new EnumMap<>(EquipmentSlot.class);

    public EquipmentManager() {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot != EquipmentSlot.NONE) {
                equipped.put(slot, null);
            }
        }
    }

    /**
     * Checks whether an item can be placed in the specified equipment slot.
     *
     * @param item the item to test (null is always acceptable as clearing the slot)
     * @param slot target equipment slot
     * @return true if compatible
     */
    public boolean canEquip(Item item, EquipmentSlot slot) {
        if (slot == null || slot == EquipmentSlot.NONE) return false;
        if (item == null) return true;
        return item.compatibleSlot == slot;
    }

    /**
     * Equips an item into the given slot.
     *
     * @param slot target equipment slot
     * @param item the item to equip
     * @return the previously equipped item in that slot, or null if none
     * @throws IllegalArgumentException if the item is not compatible
     */
    public Item equip(EquipmentSlot slot, Item item) {
        if (slot == null || slot == EquipmentSlot.NONE) {
            throw new IllegalArgumentException("Invalid equipment slot: " + slot);
        }
        if (item != null && !canEquip(item, slot)) {
            throw new IllegalArgumentException("Item " + item.name + " cannot be equipped in " + slot);
        }
        return equipped.put(slot, item);
    }

    /**
     * Unequips whatever item is currently in the given slot.
     *
     * @param slot equipment slot to empty
     * @return the unequipped item, or null if slot was already empty
     */
    public Item unequip(EquipmentSlot slot) {
        if (slot == null || slot == EquipmentSlot.NONE) return null;
        return equipped.put(slot, null);
    }

    /**
     * Gets the item currently equipped in the given slot.
     */
    public Item getEquipped(EquipmentSlot slot) {
        if (slot == null || slot == EquipmentSlot.NONE) return null;
        return equipped.get(slot);
    }

    /**
     * Returns true if there is an item equipped in the given slot.
     */
    public boolean isEquipped(EquipmentSlot slot) {
        return getEquipped(slot) != null;
    }

    /**
     * Computes the total defence provided by all currently equipped armor pieces.
     */
    public int getTotalDefense() {
        int total = 0;
        for (Item item : equipped.values()) {
            if (item instanceof Armor armor) {
                total += armor.defense;
            }
        }
        return total;
    }

    /**
     * Returns all equipped items.
     */
    public Map<EquipmentSlot, Item> getAllEquipped() {
        return equipped;
    }
}
