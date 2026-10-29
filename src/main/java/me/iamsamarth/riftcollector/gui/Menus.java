package me.iamsamarth.riftcollector.gui;

import me.iamsamarth.riftcollector.collector.Collector;
import me.iamsamarth.riftcollector.lang.Lang;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads the menu files and opens the menus, so no menu needs to know how to build another. */
public final class Menus {

    private static final List<String> FILES = List.of("collector", "upgrades", "settings", "filter", "trust");
    private static final Map<String, String> REQUIRED_SECTIONS =
            Map.of("upgrades", "upgrades", "settings", "settings", "filter", "mode", "trust", "list");

    private final Plugin plugin;
    private final Lang lang;
    private final MenuRegistry registry = new MenuRegistry();
    private final Map<String, MenuLayout> layouts = new HashMap<>();

    private MenuServices services;

    public Menus(Plugin plugin, Lang lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    public void services(MenuServices services) {
        this.services = services;
    }

    public MenuServices services() {
        return services;
    }

    public MenuRegistry registry() {
        return registry;
    }

    public void load() {
        layouts.clear();
        for (String name : FILES) {
            String resource = "gui/" + name + ".yml";
            File file = new File(plugin.getDataFolder(), resource);
            if (!file.exists()) {
                plugin.saveResource(resource, false);
            }
            layouts.put(name, MenuLayout.load(file, lang, plugin.getLogger()));
        }
        REQUIRED_SECTIONS.forEach((name, section) -> {
            MenuLayout layout = layouts.get(name);
            if (layout != null && layout.section(section) == null) {
                plugin.getLogger().warning("gui/" + name + ".yml has no '" + section
                        + "' section, so that menu will be empty. Compare it with the file in the jar.");
            }
        });
        services.upgrades().pageSize(collectorPageSize());
    }

    /** How many stored stacks one page of the collector menu shows. */
    public int collectorPageSize() {
        MenuLayout layout = layouts.get("collector");
        return layout == null ? 28 : Math.max(1, layout.contentSlotCount());
    }

    public void openCollector(Player player, Collector collector) {
        new CollectorMenu(this, layouts.get("collector"), collector).open(player);
    }

    public void openUpgrades(Player player, Collector collector) {
        new UpgradesMenu(this, layouts.get("upgrades"), collector).open(player);
    }

    public void openSettings(Player player, Collector collector) {
        new SettingsMenu(this, layouts.get("settings"), collector).open(player);
    }

    public void openFilter(Player player, Collector collector) {
        new FilterMenu(this, layouts.get("filter"), collector).open(player);
    }

    public void openTrust(Player player, Collector collector) {
        new TrustMenu(this, layouts.get("trust"), collector).open(player);
    }
}
