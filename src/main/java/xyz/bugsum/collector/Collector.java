package xyz.bugsum.collector;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.bugsum.collector.managers.PluginManager;

public class Collector extends JavaPlugin {

    @Getter private static Collector instance;
    @Getter private PluginManager pluginManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        pluginManager = new PluginManager(this);
        pluginManager.initialize();

        getLogger().info("Collector has been enabled.");
    }

    @Override
    public void onDisable() {
        if (pluginManager != null) pluginManager.shutdown();
        getLogger().info("Collector has been disabled.");
    }
}
