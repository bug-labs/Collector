package xyz.bugsum.collector.managers;

import lombok.Getter;
import org.bukkit.command.PluginCommand;
import org.bukkit.scheduler.BukkitTask;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.commands.CollectorCommand;
import xyz.bugsum.collector.config.GuiConfig;
import xyz.bugsum.collector.config.MessagesConfig;
import xyz.bugsum.collector.config.PluginConfig;
import xyz.bugsum.collector.config.TiersConfig;
import xyz.bugsum.collector.hooks.EconomyShopHook;
import xyz.bugsum.collector.hooks.PlaceholderHook;
import xyz.bugsum.collector.items.ItemKeys;
import xyz.bugsum.collector.listeners.BlockListener;
import xyz.bugsum.collector.listeners.ChunkListener;
import xyz.bugsum.collector.listeners.GUIListener;
import xyz.bugsum.collector.listeners.HopperListener;
import xyz.bugsum.collector.listeners.PlayerListener;
import xyz.bugsum.collector.tasks.CollectorTickTask;
import xyz.bugsum.collector.utils.ConfigUpdater;

import java.io.File;

public class PluginManager {

    private final Collector plugin;

    @Getter private PluginConfig configManager;
    @Getter private MessagesConfig messages;
    @Getter private TiersConfig tiers;
    @Getter private GuiConfig gui;

    @Getter private DatabaseManager database;
    @Getter private CollectorManager collector;
    @Getter private StorageManager storage;
    @Getter private UpgradeManager upgrades;
    @Getter private EconomyManager economy;
    @Getter private EconomyShopHook economyShop;
    @Getter private HologramManager holograms;
    @Getter private XpBankManager xpBank;
    @Getter private FriendManager friends;

    private CollectorTickTask tickTask;
    private BukkitTask flushTask;

    public PluginManager(Collector plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        ItemKeys.init(plugin);

        plugin.saveDefaultConfig();
        File data = plugin.getDataFolder();
        if (!new File(data, "tiers.yml").exists()) plugin.saveResource("tiers.yml", false);
        if (!new File(data, "gui.yml").exists())   plugin.saveResource("gui.yml", false);

        // Auto-merge new keys from the bundled defaults into existing user files so config
        // additions in newer versions never silently break.
        ConfigUpdater.update(plugin, "config.yml", new File(data, "config.yml"));
        ConfigUpdater.update(plugin, "gui.yml",    new File(data, "gui.yml"));
        // Tier curves + sell/smelt maps are user-curated. ConfigUpdater will still create
        // the section itself if deleted, but won't re-add individual tiers/entries the
        // server owner intentionally removed.
        ConfigUpdater.update(plugin, "tiers.yml",  new File(data, "tiers.yml"),
            "speed", "range", "capacity", "autosell.prices", "autosmelt.overrides");
        File langDir = new File(data, "lang");
        if (langDir.isDirectory()) {
            for (String l : new String[] {"en", "de", "es", "fr"}) {
                ConfigUpdater.update(plugin, "lang/" + l + ".yml", new File(langDir, l + ".yml"));
            }
        }

        configManager = new PluginConfig(plugin); configManager.reload();
        messages = new MessagesConfig(plugin);    messages.reload();
        tiers = new TiersConfig(plugin);          tiers.reload();
        gui = new GuiConfig(plugin);              gui.reload();

        database = new DatabaseManager(plugin);
        database.initialize();

        collector = new CollectorManager(plugin);
        collector.initialize();

        storage = new StorageManager(plugin);
        upgrades = new UpgradeManager(plugin);

        economy = new EconomyManager(plugin);
        economy.initialize();

        economyShop = new EconomyShopHook(plugin);
        economyShop.initialize();

        xpBank = new XpBankManager(plugin);
        friends = new FriendManager(plugin);

        holograms = new HologramManager(plugin);
        holograms.initialize();

        var pm = plugin.getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(plugin), plugin);
        pm.registerEvents(new BlockListener(plugin), plugin);
        pm.registerEvents(new HopperListener(plugin), plugin);
        pm.registerEvents(new ChunkListener(plugin), plugin);
        pm.registerEvents(new GUIListener(plugin), plugin);
        pm.registerEvents(new xyz.bugsum.collector.listeners.ChatInputListener(), plugin);

        PluginCommand cmd = plugin.getCommand("collector");
        if (cmd != null) {
            CollectorCommand exec = new CollectorCommand(plugin);
            cmd.setExecutor(exec);
            cmd.setTabCompleter(exec);
        }

        if (configManager.isPapiEnabled() && pm.getPlugin("PlaceholderAPI") != null) {
            try {
                new PlaceholderHook(plugin).register();
                plugin.getLogger().info("PlaceholderAPI hooked.");
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
            }
        }

        // 1-tick scheduling-queue scan (each Collector pops at its own nextScanTick)
        tickTask = new CollectorTickTask(plugin);
        tickTask.runTaskTimer(plugin, 1L, 1L);

        // Async batched DB flush every 5 seconds (configurable)
        long flushPeriodTicks = Math.max(20L, configManager.getDbFlushIntervalTicks());
        flushTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
            () -> database.flushDirty(), flushPeriodTicks, flushPeriodTicks);
    }

    public void reloadConfigs() {
        configManager.reload();
        messages.reload();
        tiers.reload();
        gui.reload();
    }

    /**
     * Reload configs AND propagate config changes to live state — called from the admin GUI
     * after every edit so the server reflects the new value immediately:
     *  - hologram refresh task is cancelled and rescheduled with the new interval
     *  - all live holograms are removed and respawned at the new y-offset / new state
     *  - magnet etc. just pick up the new value next tick (config-driven each cycle)
     */
    public void applyRuntimeChanges() {
        reloadConfigs();
        if (holograms != null) {
            holograms.restartRefreshTask();
            holograms.respawnAll();
        }
    }

    public void shutdown() {
        if (tickTask != null) {
            try { tickTask.cancel(); } catch (Exception ignored) {}
        }
        if (flushTask != null) {
            try { flushTask.cancel(); } catch (Exception ignored) {}
        }
        if (holograms != null) holograms.shutdown();
        if (collector != null) collector.shutdown();
        if (database != null) database.shutdown();
    }
}
