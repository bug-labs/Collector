package me.iamsamarth.riftcollector.upgrade;

import org.jetbrains.annotations.Nullable;

public enum UpgradeType {

    /** Ticks between sweeps; a lower value is faster. */
    PICKUP_SPEED("pickup-speed", 20),
    /** Chunks or blocks around the collector, depending on collection.range-type. */
    PICKUP_RANGE("pickup-range", 0),
    /** How many slots of the collector can be used. */
    SLOTS("slots", 28),
    /** How many items one slot can hold. */
    SLOT_CAPACITY("slot-capacity", 10_000);

    private final String key;
    private final long fallback;

    UpgradeType(String key, long fallback) {
        this.key = key;
        this.fallback = fallback;
    }

    public String key() {
        return key;
    }

    /** Used when the config defines no tiers at all for this upgrade. */
    public long fallbackValue() {
        return fallback;
    }

    public String permission() {
        return "riftcollector.upgrade." + key;
    }

    public static @Nullable UpgradeType byKey(String key) {
        for (UpgradeType type : values()) {
            if (type.key.equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }
}
