package me.iamsamarth.riftcollector.ability;

import java.util.Locale;

public enum FilterMode {

    DISABLED, WHITELIST, BLACKLIST;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public FilterMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static FilterMode parse(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return DISABLED;
        }
    }
}
