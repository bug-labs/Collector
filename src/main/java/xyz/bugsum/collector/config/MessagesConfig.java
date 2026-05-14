package xyz.bugsum.collector.config;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Loads localized strings from {@code lang/<locale>.yml}. Falls back to {@code en.yml} for any
 * missing key, then to the dotted key path itself. Bundled locales (en, de, es, fr) are exported
 * to the data folder on first run; server admins can drop in additional translations.
 */
public class MessagesConfig {

    /** Locales bundled in the JAR — exported on first start. */
    private static final List<String> BUNDLED = Arrays.asList("en", "de", "es", "fr");

    private final Collector plugin;
    private FileConfiguration cfg;       // active locale
    private FileConfiguration fallback;  // English fallback
    private String prefix;
    private String locale = "en";

    public MessagesConfig(Collector plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        // Export bundled language files to {pluginDir}/lang/ on first run.
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) //noinspection ResultOfMethodCallIgnored
            langDir.mkdirs();
        for (String l : BUNDLED) {
            File f = new File(langDir, l + ".yml");
            if (!f.exists()) exportBundled(l, f);
        }

        // Resolve active locale from config.yml, falling back to en.
        String configured = plugin.getConfig().getString("language", "en").toLowerCase();
        File active = new File(langDir, configured + ".yml");
        if (!active.exists()) {
            plugin.getLogger().warning("Language file '" + configured + ".yml' not found — using English.");
            configured = "en";
            active = new File(langDir, "en.yml");
        }
        this.locale = configured;
        this.cfg = YamlConfiguration.loadConfiguration(active);
        this.fallback = YamlConfiguration.loadConfiguration(new File(langDir, "en.yml"));
        this.prefix = raw("prefix");
        if (prefix == null || prefix.equals("prefix")) prefix = "";
    }

    private void exportBundled(String localeKey, File target) {
        try (InputStream in = plugin.getResource("lang/" + localeKey + ".yml")) {
            if (in == null) {
                plugin.getLogger().warning("Bundled lang/" + localeKey + ".yml not found in JAR.");
                return;
            }
            Files.copy(in, target.toPath());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to export lang/" + localeKey + ".yml: " + e.getMessage());
        }
    }

    public String getLocale() { return locale; }

    /** Get a raw string by dotted path; falls back to en.yml then to the key itself. */
    public String raw(String path) {
        if (cfg != null && cfg.isString(path)) return cfg.getString(path);
        if (fallback != null && fallback.isString(path)) return fallback.getString(path);
        return path;
    }

    public List<String> rawList(String path) {
        if (cfg != null && cfg.isList(path)) return cfg.getStringList(path);
        if (fallback != null && fallback.isList(path)) return fallback.getStringList(path);
        return new ArrayList<>();
    }

    public Component get(String path, Object... placeholders) {
        String s = raw(path);
        if (s == null || s.isEmpty()) return Component.empty();
        return Utils.colorize(prefix + applyPlaceholders(s, placeholders));
    }

    public Component getRaw(String path, Object... placeholders) {
        String s = raw(path);
        if (s == null) return Component.empty();
        return Utils.colorize(applyPlaceholders(s, placeholders));
    }

    public List<Component> getRawList(String path, Object... placeholders) {
        List<Component> out = new ArrayList<>();
        for (String line : rawList(path)) {
            out.add(Utils.colorize(applyPlaceholders(line, placeholders)));
        }
        return out;
    }

    public List<String> getRawStringList(String path, Object... placeholders) {
        List<String> out = new ArrayList<>();
        for (String line : rawList(path)) {
            out.add(applyPlaceholders(line, placeholders));
        }
        return out;
    }

    public static String applyPlaceholders(String s, Object... pairs) {
        if (pairs == null || pairs.length == 0) return s;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            s = s.replace("<" + pairs[i] + ">", String.valueOf(pairs[i + 1]));
        }
        return s;
    }

    public static String applyPlaceholders(String s, Map<String, Object> map) {
        if (map == null || map.isEmpty()) return s;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            s = s.replace("<" + e.getKey() + ">", String.valueOf(e.getValue()));
        }
        return s;
    }
}
