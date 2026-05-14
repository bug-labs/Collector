package xyz.bugsum.collector.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class PdcUtil {
    private PdcUtil() {}

    public static int getInt(PersistentDataContainer pdc, NamespacedKey key, int def) {
        Integer v = pdc.get(key, PersistentDataType.INTEGER);
        return v == null ? def : v;
    }

    public static long getLong(PersistentDataContainer pdc, NamespacedKey key, long def) {
        Long v = pdc.get(key, PersistentDataType.LONG);
        return v == null ? def : v;
    }

    public static double getDouble(PersistentDataContainer pdc, NamespacedKey key, double def) {
        Double v = pdc.get(key, PersistentDataType.DOUBLE);
        return v == null ? def : v;
    }

    public static String getString(PersistentDataContainer pdc, NamespacedKey key, String def) {
        String v = pdc.get(key, PersistentDataType.STRING);
        return v == null ? def : v;
    }

    public static boolean getBool(PersistentDataContainer pdc, NamespacedKey key, boolean def) {
        Byte v = pdc.get(key, PersistentDataType.BYTE);
        return v == null ? def : v != 0;
    }

    public static void setInt(PersistentDataContainer pdc, NamespacedKey key, int value) {
        pdc.set(key, PersistentDataType.INTEGER, value);
    }

    public static void setLong(PersistentDataContainer pdc, NamespacedKey key, long value) {
        pdc.set(key, PersistentDataType.LONG, value);
    }

    public static void setDouble(PersistentDataContainer pdc, NamespacedKey key, double value) {
        pdc.set(key, PersistentDataType.DOUBLE, value);
    }

    public static void setString(PersistentDataContainer pdc, NamespacedKey key, String value) {
        pdc.set(key, PersistentDataType.STRING, value);
    }

    public static void setBool(PersistentDataContainer pdc, NamespacedKey key, boolean value) {
        pdc.set(key, PersistentDataType.BYTE, (byte) (value ? 1 : 0));
    }

    public static boolean has(PersistentDataContainer pdc, NamespacedKey key) {
        return pdc.has(key);
    }
}
