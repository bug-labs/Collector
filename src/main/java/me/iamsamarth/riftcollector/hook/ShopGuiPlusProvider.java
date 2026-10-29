package me.iamsamarth.riftcollector.hook;

import net.brcdev.shopgui.ShopGuiPlusApi;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Sell prices from ShopGUI+, which returns a negative price for items it does not sell. */
public final class ShopGuiPlusProvider implements PriceProvider {

    private final boolean present;

    public ShopGuiPlusProvider(Plugin plugin) {
        this.present = plugin.getServer().getPluginManager().getPlugin("ShopGUI+") != null
                && Hooks.classPresent("net.brcdev.shopgui.ShopGuiPlusApi");
    }

    @Override
    public String id() {
        return "SHOPGUI_PLUS";
    }

    @Override
    public boolean available() {
        return present;
    }

    @Override
    public double sellPrice(OfflinePlayer owner, ItemStack stack) {
        Player online = owner.getPlayer();
        return online == null
                ? ShopGuiPlusApi.getItemStackPriceSell(stack)
                : ShopGuiPlusApi.getItemStackPriceSell(online, stack);
    }
}
