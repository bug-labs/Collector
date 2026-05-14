package xyz.bugsum.collector.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Adds keys present in the bundled JAR resource but missing from the user's on-disk file.
 * User values are preserved verbatim; only missing keys are added.
 *
 * Some sections are user-curated lists (tier curves, sell-price maps) where re-adding a
 * removed entry is wrong — pass them as {@code userManagedSections} and the updater will
 * never modify keys *inside* those sections (it'll still create the section itself if
 * the user deleted the entire branch, populated with the bundled defaults).
 */
public final class ConfigUpdater {

    private ConfigUpdater() {}

    public static int update(Plugin plugin, String resource, File target, String... userManagedSections) {
        if (!target.exists()) return 0;
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) return 0;
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8));
            YamlConfiguration user = YamlConfiguration.loadConfiguration(target);

            Set<String> managed = userManagedSections == null || userManagedSections.length == 0
                ? Collections.emptySet()
                : new HashSet<>(Arrays.asList(userManagedSections));

            int added = mergeMissing(bundled, user, "", managed);
            if (added > 0) {
                user.save(target);
                plugin.getLogger().info("Added " + added + " new key(s) to " + resource);
            }
            return added;
        } catch (IOException e) {
            plugin.getLogger().warning("ConfigUpdater failed for " + resource + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * Recursively copies keys from {@code src} absent in {@code dst}. When the current path
     * matches an entry in {@code managed}, child keys are NOT auto-added — the user is in
     * charge of that subtree.
     */
    private static int mergeMissing(ConfigurationSection src, ConfigurationSection dst,
                                    String pathPrefix, Set<String> managed) {
        int added = 0;
        for (String key : src.getKeys(false)) {
            String fullPath = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
            Object srcVal = src.get(key);

            if (srcVal instanceof ConfigurationSection srcSection) {
                ConfigurationSection dstSection = dst.getConfigurationSection(key);
                if (dstSection == null) {
                    // Whole subtree missing — copy verbatim (the user hasn't seen this section yet).
                    dst.set(key, srcSection);
                    added += countLeafKeys(srcSection);
                } else if (managed.contains(fullPath)) {
                    // User curates this branch; do NOT add removed entries back.
                    continue;
                } else {
                    added += mergeMissing(srcSection, dstSection, fullPath, managed);
                }
            } else if (!dst.contains(key)) {
                dst.set(key, srcVal);
                added++;
            }
        }
        return added;
    }

    private static int countLeafKeys(ConfigurationSection section) {
        int count = 0;
        for (String key : section.getKeys(false)) {
            Object v = section.get(key);
            if (v instanceof ConfigurationSection child) count += countLeafKeys(child);
            else count++;
        }
        return Math.max(1, count);
    }
}
