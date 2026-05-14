package xyz.bugsum.collector.config;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xyz.bugsum.collector.Collector;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GuiConfig {

    private final Collector plugin;
    private FileConfiguration cfg;

    @Getter private String mainTitle;
    @Getter private Material mainFiller;
    @Getter private String mainFillerName;

    @Getter private String upgradesTitle;
    @Getter private String togglesTitle;
    @Getter private String xpTitle;
    @Getter private String filterTitle;
    @Getter private int filterRows;
    @Getter private GuiButton filterModeButton;

    /** Dotted button keys, e.g. "main.upgrades", "upgrades.speed", "common.back". */
    private final Map<String, GuiButton> buttons = new HashMap<>();

    public GuiConfig(Collector plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "gui.yml");
        if (!file.exists()) plugin.saveResource("gui.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);

        mainTitle      = cfg.getString("main.title", "Collector");
        mainFiller     = mat(cfg.getString("main.filler.material"), Material.BLACK_STAINED_GLASS_PANE);
        mainFillerName = cfg.getString("main.filler.name", " ");

        upgradesTitle = cfg.getString("upgrades.title", "Upgrades");
        // "settings" is the new section name; "toggles" is kept as a fallback for legacy files.
        togglesTitle  = cfg.getString("settings.title",
            cfg.getString("toggles.title", "Settings"));
        xpTitle       = cfg.getString("xp.title", "XP Bank");
        filterTitle   = cfg.getString("filter.title", "Filter");
        filterRows    = clampRows(cfg.getInt("filter.rows", 6));

        ConfigurationSection mb = cfg.getConfigurationSection("filter.mode-button");
        filterModeButton = mb != null ? GuiButton.from(mb) : null;

        // Load all "buttons" maps from each section.
        buttons.clear();
        loadButtons("main");
        loadButtons("upgrades");
        loadButtons("toggles");
        loadButtons("settings");
        loadButtons("xp");
        loadButtons("common");
    }

    private void loadButtons(String section) {
        ConfigurationSection btns = cfg.getConfigurationSection(section + ".buttons");
        if (btns == null) return;
        for (String key : btns.getKeys(false)) {
            ConfigurationSection s = btns.getConfigurationSection(key);
            if (s == null) continue;
            buttons.put(section + "." + key, GuiButton.from(s));
        }
    }

    /** Look up a button by its dotted "section.key" identifier. */
    public GuiButton getButton(String key) {
        return buttons.get(key);
    }

    private static int clampRows(int rows) {
        if (rows < 1) return 1;
        if (rows > 6) return 6;
        return rows;
    }

    private static Material mat(String name, Material fb) {
        if (name == null) return fb;
        Material m = Material.matchMaterial(name);
        return m != null ? m : fb;
    }

    @Getter
    public static class GuiButton {
        private final int slot;
        private final Material material;
        private final String name;
        private final List<String> lore;

        public GuiButton(int slot, Material material, String name, List<String> lore) {
            this.slot = slot;
            this.material = material;
            this.name = name;
            this.lore = lore == null ? Collections.emptyList() : lore;
        }

        static GuiButton from(ConfigurationSection s) {
            return new GuiButton(
                s.getInt("slot", 0),
                mat(s.getString("material"), Material.STONE),
                s.getString("name", ""),
                s.getStringList("lore")
            );
        }
    }
}
