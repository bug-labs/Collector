package xyz.bugsum.collector.config;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xyz.bugsum.collector.Collector;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class TiersConfig {

    private final Collector plugin;
    private FileConfiguration cfg;

    @Getter private TreeMap<Integer, SpeedTier> speed = new TreeMap<>();
    @Getter private TreeMap<Integer, RangeTier> range = new TreeMap<>();
    @Getter private TreeMap<Integer, CapacityTier> capacity = new TreeMap<>();

    @Getter private Map<Material, Double> sellPrices = new HashMap<>();
    @Getter private Map<Material, Material> smeltOverrides = new HashMap<>();

    @Getter private Material speedIcon = Material.SUGAR;
    @Getter private Material rangeIcon = Material.SPYGLASS;
    @Getter private Material capacityIcon = Material.CHEST;
    @Getter private Material autosellIcon = Material.GOLD_INGOT;
    @Getter private Material autosmeltIcon = Material.BLAST_FURNACE;

    public TiersConfig(Collector plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "tiers.yml");
        if (!file.exists()) plugin.saveResource("tiers.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);

        speed = loadSpeed();
        range = loadRange();
        capacity = loadCapacity();
        sellPrices = loadSellPrices();
        smeltOverrides = loadSmeltOverrides();

        speedIcon = mat(cfg.getString("speed.icon"), Material.SUGAR);
        rangeIcon = mat(cfg.getString("range.icon"), Material.SPYGLASS);
        capacityIcon = mat(cfg.getString("capacity.icon"), Material.CHEST);
        autosellIcon = mat(cfg.getString("autosell.icon"), Material.GOLD_INGOT);
        autosmeltIcon = mat(cfg.getString("autosmelt.icon"), Material.BLAST_FURNACE);
    }

    private TreeMap<Integer, SpeedTier> loadSpeed() {
        TreeMap<Integer, SpeedTier> map = new TreeMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("speed");
        if (sec == null) return map;
        for (String k : sec.getKeys(false)) {
            Integer tier = parseInt(k);
            if (tier == null) continue;
            ConfigurationSection s = sec.getConfigurationSection(k);
            if (s == null) continue;
            map.put(tier, new SpeedTier(tier, s.getInt("interval-ticks", 20), s.getDouble("price", 0)));
        }
        return map;
    }

    private TreeMap<Integer, RangeTier> loadRange() {
        TreeMap<Integer, RangeTier> map = new TreeMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("range");
        if (sec == null) return map;
        for (String k : sec.getKeys(false)) {
            Integer tier = parseInt(k);
            if (tier == null) continue;
            ConfigurationSection s = sec.getConfigurationSection(k);
            if (s == null) continue;
            // Range is now expressed in chunks (1 = own chunk, 2 = 3x3, 3 = 5x5, ...).
            map.put(tier, new RangeTier(tier, s.getInt("chunks", 1), s.getDouble("price", 0)));
        }
        return map;
    }

    private TreeMap<Integer, CapacityTier> loadCapacity() {
        TreeMap<Integer, CapacityTier> map = new TreeMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("capacity");
        if (sec == null) return map;
        for (String k : sec.getKeys(false)) {
            Integer tier = parseInt(k);
            if (tier == null) continue;
            ConfigurationSection s = sec.getConfigurationSection(k);
            if (s == null) continue;
            // Capacity = max distinct materials (== pages * slots-per-page logically).
            map.put(tier, new CapacityTier(tier, s.getInt("max-storage-slots", 28), s.getDouble("price", 0)));
        }
        return map;
    }

    private Map<Material, Double> loadSellPrices() {
        Map<Material, Double> map = new HashMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("autosell.prices");
        if (sec == null) return map;
        for (String k : sec.getKeys(false)) {
            Material m = Material.matchMaterial(k);
            if (m != null) map.put(m, sec.getDouble(k));
        }
        return map;
    }

    private Map<Material, Material> loadSmeltOverrides() {
        Map<Material, Material> map = new HashMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("autosmelt.overrides");
        if (sec == null) return map;
        for (String k : sec.getKeys(false)) {
            Material src = Material.matchMaterial(k);
            Material dst = Material.matchMaterial(sec.getString(k, ""));
            if (src != null && dst != null) map.put(src, dst);
        }
        return map;
    }

    private static Integer parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static Material mat(String name, Material fallback) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name);
        return m != null ? m : fallback;
    }

    public SpeedTier getSpeedTier(int t)       { return clamp(speed, t); }
    public RangeTier getRangeTier(int t)       { return clamp(range, t); }
    public CapacityTier getCapacityTier(int t) { return clamp(capacity, t); }

    public int maxSpeedTier()    { return speed.isEmpty() ? 1 : speed.lastKey(); }
    public int maxRangeTier()    { return range.isEmpty() ? 1 : range.lastKey(); }
    public int maxCapacityTier() { return capacity.isEmpty() ? 1 : capacity.lastKey(); }

    private static <T> T clamp(TreeMap<Integer, T> m, int tier) {
        if (m.isEmpty()) return null;
        if (tier < m.firstKey()) tier = m.firstKey();
        if (tier > m.lastKey()) tier = m.lastKey();
        T v = m.get(tier);
        if (v != null) return v;
        return m.floorEntry(tier).getValue();
    }

    @Getter
    public static class SpeedTier {
        private final int tier;
        private final int intervalTicks;
        private final double price;
        public SpeedTier(int t, int i, double p) { tier=t; intervalTicks=i; price=p; }
    }

    @Getter
    public static class RangeTier {
        private final int tier;
        private final int chunks; // 1 = own chunk, 2 = 3x3, 3 = 5x5, ...
        private final double price;
        public RangeTier(int t, int r, double p) { tier=t; chunks=r; price=p; }
    }

    @Getter
    public static class CapacityTier {
        private final int tier;
        /** Maximum distinct materials this Collector can hold. */
        private final int maxStorageSlots;
        private final double price;
        public CapacityTier(int t, int m, double p) { tier=t; maxStorageSlots=m; price=p; }
    }
}
