package me.iamsamarth.riftcollector.hook;

import me.iamsamarth.riftcollector.config.PluginConfig;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** Builds the price providers in the order the config asks for. */
public final class Hooks {

    private Hooks() {
    }

    public static List<PriceProvider> priceProviders(Plugin plugin, PluginConfig config) {
        List<PriceProvider> known = List.of(
                ConfigPriceProvider.load(plugin),
                new ShopGuiPlusProvider(plugin),
                new EconomyShopGuiProvider(plugin));

        List<PriceProvider> ordered = new ArrayList<>();
        for (String id : config.collection().priceProviders()) {
            known.stream()
                    .filter(provider -> provider.id().equalsIgnoreCase(id))
                    .findFirst()
                    .ifPresentOrElse(ordered::add, () ->
                            plugin.getLogger().warning("Unknown price provider '" + id
                                    + "' in collection.price-providers."));
        }
        for (PriceProvider provider : ordered) {
            if (provider.available()) {
                plugin.getLogger().info("Sell prices available from " + provider.id() + ".");
            }
        }
        return ordered;
    }

    /** True when the class is on the classpath, so a missing shop plugin cannot break a lookup. */
    static boolean classPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError exception) {
            return false;
        }
    }
}
