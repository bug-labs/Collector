package xyz.bugsum.collector.config;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import xyz.bugsum.collector.Collector;

import java.util.List;

public class PluginConfig {

    private final Collector plugin;

    @Getter private int defaultRadius;
    @Getter private boolean pickupExperience;
    @Getter private boolean requireFreeSlot;
    @Getter private int maxCollectorsPerPlayer;
    @Getter private List<String> blockedWorlds;
    /** Maximum total items (sum of all stack sizes) allowed in a Collector before pickup is blocked.
     *  -1 disables the limit. Prevents server lag from dumping millions of items at once. */
    @Getter private long pickupMaxItems;
    @Getter private String dbFile;
    @Getter private int dbThreads;
    @Getter private long dbFlushIntervalTicks;
    @Getter private boolean vaultEnabled;
    @Getter private boolean papiEnabled;
    @Getter private boolean wgEnabled;
    @Getter private String wgFlag;
    @Getter private boolean debug;

    @Getter private VisualEffect placeEffect;
    @Getter private VisualEffect linkEffect;
    @Getter private VisualEffect upgradeEffect;
    @Getter private VisualEffect fullStorageEffect;
    @Getter private VisualEffect pickupEffect;

    @Getter private boolean hologramsEnabled;
    @Getter private long hologramRefreshTicks;
    @Getter private double hologramYOffset;
    @Getter private boolean oneCollectorPerChunk;
    @Getter private boolean xpCollectionEnabled;
    @Getter private int xpScanRadius;


    public PluginConfig(Collector plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        defaultRadius = c.getInt("collection.default-radius-blocks", 16);
        pickupExperience = c.getBoolean("collection.pickup-experience", false);
        requireFreeSlot = c.getBoolean("collection.require-free-slot", true);

        maxCollectorsPerPlayer = c.getInt("limits.max-collectors-per-player", 10);
        blockedWorlds = c.getStringList("limits.blocked-worlds");
        pickupMaxItems = c.getLong("limits.pickup-max-items", 100_000L);

        dbFile = c.getString("database.file", "data.db");
        dbThreads = c.getInt("database.threads", 1);
        dbFlushIntervalTicks = c.getLong("database.flush-interval-ticks", 100L);

        vaultEnabled = c.getBoolean("hooks.vault", true);
        papiEnabled = c.getBoolean("hooks.placeholderapi", true);
        wgEnabled = c.getBoolean("hooks.worldguard.enabled", false);
        wgFlag = c.getString("hooks.worldguard.flag", "collector-place");

        debug = c.getBoolean("debug", false);

        placeEffect = VisualEffect.from(c, "visuals.place");
        linkEffect = VisualEffect.from(c, "visuals.link");
        upgradeEffect = VisualEffect.from(c, "visuals.upgrade");
        fullStorageEffect = VisualEffect.from(c, "visuals.full-storage");
        pickupEffect = VisualEffect.from(c, "visuals.pickup");

        hologramsEnabled = c.getBoolean("hologram.enabled", true);
        hologramRefreshTicks = c.getLong("hologram.refresh-interval-ticks", 40L);
        hologramYOffset = c.getDouble("hologram.y-offset", 1.4);

        oneCollectorPerChunk = c.getBoolean("limits.one-per-chunk", true);

        xpCollectionEnabled = c.getBoolean("collection.xp-enabled", true);
        xpScanRadius = c.getInt("collection.xp-scan-radius", 8);
    }

    @Getter
    public static class VisualEffect {
        private final String particle;
        private final String sound;

        public VisualEffect(String particle, String sound) {
            this.particle = particle;
            this.sound = sound;
        }

        static VisualEffect from(FileConfiguration c, String path) {
            return new VisualEffect(
                c.getString(path + ".particle", "NONE"),
                c.getString(path + ".sound", "NONE")
            );
        }

        public boolean hasParticle() { return particle != null && !"NONE".equalsIgnoreCase(particle); }
        public boolean hasSound() { return sound != null && !"NONE".equalsIgnoreCase(sound); }
    }
}
