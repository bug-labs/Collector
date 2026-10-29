package me.iamsamarth.riftcollector.ability;

import org.jetbrains.annotations.Nullable;

/** Per-collector switches, shown in the Settings menu. */
public enum Ability {

    AUTO_SELL("auto-sell"),
    AUTO_SMELT("auto-smelt"),
    /** On by default, so a new collector shows its hologram straight away. */
    HOLOGRAM("hologram");

    private final String key;

    Ability(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String permission() {
        return "riftcollector.ability." + key;
    }

    public static @Nullable Ability byKey(String key) {
        for (Ability ability : values()) {
            if (ability.key.equalsIgnoreCase(key)) {
                return ability;
            }
        }
        return null;
    }
}
