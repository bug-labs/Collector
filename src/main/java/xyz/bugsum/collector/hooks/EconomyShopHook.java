package xyz.bugsum.collector.hooks;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import xyz.bugsum.collector.Collector;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Soft hook for EconomyShopGUI / EconomyShopGUI-Premium.
 *
 * Reflection-based so we don't hard-link the API: keeps the plugin functional even if
 * neither variant of ESG is installed.
 *
 * Per the official API (wiki: https://wiki.gpplugins.com/economyshopgui/api/api-usage.md):
 *   modern (ESGUI 6.3+ / Premium 5.6+):
 *     Optional&lt;SellPrice&gt; EconomyShopGUIHook.getSellPrice(OfflinePlayer, ItemStack)
 *     SellPrice#getPrice(EcoType) // EcoType.getFromString("VAULT")
 *   manual:
 *     ShopItem EconomyShopGUIHook.getShopItem(ItemStack)
 *     double   EconomyShopGUIHook.getItemSellPrice(ShopItem, ItemStack, int amount, int sellMultiplier)
 *
 * We try the manual path because it's the most widely supported across versions and
 * doesn't depend on the player being online.
 */
public class EconomyShopHook {

    private final Collector plugin;

    private boolean available = false;
    private Class<?> hookClass;
    private Class<?> shopItemCls;

    // Manual-path methods (try multiple known signatures for version drift).
    private Method getShopItemMethod;
    private Method getItemSellPrice_4int;   // (ShopItem, ItemStack, int amount, int multiplier)
    private Method getItemSellPrice_3int;   // (ShopItem, ItemStack, int amount)
    private Method getItemSellPrice_2;      // (ShopItem, ItemStack)
    private Method getItemSellPrice_player; // (ShopItem, Player, ItemStack, int)
    private Method shopItemGetSellPrice;    // ShopItem.getSellPrice()

    // Modern Optional<SellPrice> path
    private Method getSellPriceModern;        // (OfflinePlayer, ItemStack)
    private Method sellPriceGetPrice;          // SellPrice.getPrice(EcoType)
    private Method ecoTypeFromString;          // EcoType.getFromString(String)
    private Object vaultEcoType;

    private final AtomicBoolean firstErrorLogged = new AtomicBoolean(false);

    public EconomyShopHook(Collector plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        Plugin esg = plugin.getServer().getPluginManager().getPlugin("EconomyShopGUI");
        if (esg == null) esg = plugin.getServer().getPluginManager().getPlugin("EconomyShopGUI-Premium");
        if (esg == null) {
            plugin.getLogger().info("EconomyShopGUI not detected — sell prices will use tiers.yml.");
            return;
        }

        try {
            hookClass = Class.forName("me.gypopo.economyshopgui.api.EconomyShopGUIHook");
            shopItemCls = Class.forName("me.gypopo.economyshopgui.objects.ShopItem");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("ESG installed but API classes not found (" + e.getMessage()
                + "). Sell prices will use tiers.yml.");
            return;
        }

        try { getShopItemMethod = hookClass.getMethod("getShopItem", ItemStack.class); }
        catch (NoSuchMethodException e) {
            plugin.getLogger().warning("ESG: getShopItem(ItemStack) not found.");
            return;
        }

        // Try every known sell-price signature; missing ones stay null.
        getItemSellPrice_4int   = lookup(hookClass, "getItemSellPrice", shopItemCls, ItemStack.class, int.class, int.class);
        getItemSellPrice_3int   = lookup(hookClass, "getItemSellPrice", shopItemCls, ItemStack.class, int.class);
        getItemSellPrice_2      = lookup(hookClass, "getItemSellPrice", shopItemCls, ItemStack.class);
        getItemSellPrice_player = lookup(hookClass, "getItemSellPrice", shopItemCls, Player.class, ItemStack.class, int.class);
        shopItemGetSellPrice    = lookup(shopItemCls, "getSellPrice");

        // Modern Optional<SellPrice> path (try OfflinePlayer overload).
        getSellPriceModern = lookup(hookClass, "getSellPrice", OfflinePlayer.class, ItemStack.class);
        try {
            Class<?> sellPriceCls = Class.forName("me.gypopo.economyshopgui.api.prices.SellPrice");
            Class<?> ecoTypeCls = Class.forName("me.gypopo.economyshopgui.objects.EcoType");
            ecoTypeFromString = lookup(ecoTypeCls, "getFromString", String.class);
            sellPriceGetPrice = lookup(sellPriceCls, "getPrice", ecoTypeCls);
            if (ecoTypeFromString != null) {
                try { vaultEcoType = ecoTypeFromString.invoke(null, "VAULT"); }
                catch (Throwable ignored) {}
            }
        } catch (ClassNotFoundException ignored) {
            // Older API — manual path will be used.
        }

        available = true;
        plugin.getLogger().info("Hooked into " + esg.getName() + " " + pluginVersion(esg)
            + " — sell prices will use the shop's configured values.");
    }

    /** Resolve the plugin's version string, preferring the modern API. */
    @SuppressWarnings("deprecation")
    private static String pluginVersion(Plugin esg) {
        try { return esg.getPluginMeta().getVersion(); }
        catch (Throwable t) { return esg.getDescription().getVersion(); }
    }

    private static Method lookup(Class<?> cls, String name, Class<?>... params) {
        try { return cls.getMethod(name, params); }
        catch (NoSuchMethodException e) { return null; }
    }

    public boolean isAvailable() { return available; }

    /** Convenience overload — keeps existing callers working. */
    public double sellPrice(ItemStack stack) { return sellPrice(stack, null); }

    /**
     * @param owner OfflinePlayer to attribute the price calculation to (used by ESG to apply
     *              per-player discounts/multipliers). May be {@code null}.
     * @return total sell price for the given ItemStack, or {@link Double#NaN} when unpriced.
     */
    public double sellPrice(ItemStack stack, OfflinePlayer owner) {
        if (!available || stack == null || stack.getAmount() <= 0) return Double.NaN;
        try {
            // 1) Modern Optional<SellPrice> path
            if (getSellPriceModern != null && owner != null && sellPriceGetPrice != null
                    && vaultEcoType != null) {
                Object opt = getSellPriceModern.invoke(null, owner, stack);
                if (opt instanceof Optional<?> o && o.isPresent()) {
                    Object sellPrice = o.get();
                    Object price = sellPriceGetPrice.invoke(sellPrice, vaultEcoType);
                    if (price instanceof Number n) {
                        double d = n.doubleValue();
                        if (d > 0) return d;
                    }
                }
            }

            // 2) Manual path — get ShopItem then ask for price
            Object result = getShopItemMethod.invoke(null, stack);
            Object shopItem = (result instanceof Optional<?> opt2)
                ? (opt2.isEmpty() ? null : opt2.get())
                : result;
            if (shopItem == null) return Double.NaN;

            int amount = stack.getAmount();
            Double v;

            if ((v = tryInvoke(getItemSellPrice_4int, shopItem, stack, amount, 0)) != null && v > 0) return v;
            if ((v = tryInvoke(getItemSellPrice_3int, shopItem, stack, amount))   != null && v > 0) return v;
            if (getItemSellPrice_player != null && owner instanceof Player p) {
                if ((v = tryInvoke(getItemSellPrice_player, shopItem, p, stack, amount)) != null && v > 0) return v;
            }
            if ((v = tryInvoke(getItemSellPrice_2, shopItem, stack)) != null && v > 0) return v;

            // 3) Last resort: ShopItem.getSellPrice() × amount
            if (shopItemGetSellPrice != null) {
                Object base = shopItemGetSellPrice.invoke(shopItem);
                if (base instanceof Number n && n.doubleValue() > 0) {
                    return n.doubleValue() * amount;
                }
            }
        } catch (Throwable t) {
            if (firstErrorLogged.compareAndSet(false, true)) {
                plugin.getLogger().warning("ESG sellPrice() failed for "
                    + stack.getType() + ": " + t.getClass().getSimpleName() + " — " + t.getMessage()
                    + ". Falling back to tiers.yml. Subsequent failures suppressed.");
                if (plugin.getPluginManager().getConfigManager().isDebug()) {
                    t.printStackTrace();
                }
            }
        }
        return Double.NaN;
    }

    private static Double tryInvoke(Method m, Object... args) {
        if (m == null) return null;
        try {
            Object r = m.invoke(null, args);
            if (r instanceof Number n) return n.doubleValue();
        } catch (Throwable ignored) { /* method signature mismatch — try next */ }
        return null;
    }
}
