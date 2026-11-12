package inventory;

/**
 * Enumeration of all equipment slots in Dead End!!
 *
 * Used by EquipmentManager to categorise and validate item placement,
 * and by Item / Weapon / Armor to declare which slot they are compatible with.
 */
public enum EquipmentSlot {
    MAIN_HAND,   // Swords, axes, bows, etc.
    OFF_HAND,    // Shields
    HEAD,        // Helmets
    CHEST,       // Chestplates
    LEGS,        // Leggings
    FEET,        // Boots
    NONE         // Not equippable in any slot
}
