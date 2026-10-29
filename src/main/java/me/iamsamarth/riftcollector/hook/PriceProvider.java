package me.iamsamarth.riftcollector.hook;

import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

/** A source of sell prices. Providers are asked in the order set in the config. */
public interface PriceProvider {

    /** The name used in config under collection.price-providers. */
    String id();

    boolean available();

    /**
     * What the whole stack is worth to this owner, or a negative number when this provider has no
     * price for it. The owner may well be offline, since collectors keep working either way.
     */
    double sellPrice(OfflinePlayer owner, ItemStack stack);
}
