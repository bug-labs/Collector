package me.iamsamarth.riftcollector.hook;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

/** Prices from prices.yml, so auto sell works without any shop plugin installed. */
public final class ConfigPriceProvider implements PriceProvider {

    private static final String FILE_NAME = "prices.yml";

    private final Map<Material, Double> prices = new EnumMap<>(Material.class);

    public static ConfigPriceProvider load(Plugin plugin) {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }

        ConfigPriceProvider provider = new ConfigPriceProvider();
        ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("prices");
        if (section == null) {
            return provider;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                plugin.getLogger().warning("Unknown material '" + key + "' in " + FILE_NAME + ", skipping it.");
                continue;
            }
            double price = section.getDouble(key, -1);
            if (price >= 0) {
                provider.prices.put(material, price);
            }
        }
        return provider;
    }

    @Override
    public String id() {
        return "CUSTOM";
    }

    @Override
    public boolean available() {
        return !prices.isEmpty();
    }

    @Override
    public double sellPrice(OfflinePlayer owner, ItemStack stack) {
        Double price = prices.get(stack.getType());
        return price == null ? -1 : price * stack.getAmount();
    }
}
