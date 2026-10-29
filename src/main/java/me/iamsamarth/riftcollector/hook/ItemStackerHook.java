package me.iamsamarth.riftcollector.hook;

import fr.maxlego08.itemstacker.api.ItemManager;
import fr.maxlego08.itemstacker.api.StackedItem;
import org.bukkit.entity.Item;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Makes collectors see what a stacker really put on the ground.
 *
 * <p>zItemStacker merges dropped items into one entity that stands for far more items than its
 * own ItemStack says. Without this, a collector would take 64 from a pile of 5000 and then
 * delete the entity, destroying the rest. With it, the real count is read and only what was
 * taken is removed.
 *
 * <p>Its API jar holds interfaces only, with no static accessor and no service registration, so
 * the manager is fetched from the plugin instance by reflection. The interfaces themselves are
 * compiled against, which keeps the actual calls type checked.
 */
public final class ItemStackerHook {

    private static final String PLUGIN_NAME = "zItemStacker";

    private final Plugin plugin;
    private @Nullable ItemManager manager;

    public ItemStackerHook(Plugin plugin) {
        this.plugin = plugin;
    }

    public void hook() {
        Plugin stacker = plugin.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
        if (stacker == null || !Hooks.classPresent("fr.maxlego08.itemstacker.api.ItemManager")) {
            return;
        }
        try {
            Method accessor = stacker.getClass().getMethod("getItemManager");
            if (accessor.invoke(stacker) instanceof ItemManager found) {
                manager = found;
                plugin.getLogger().info("Hooked into " + PLUGIN_NAME + "; stacked drops are collected whole.");
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().log(Level.WARNING, "Found " + PLUGIN_NAME
                    + " but could not reach its item manager; stacked drops will be collected"
                    + " one vanilla stack at a time.", exception);
        }
    }

    public boolean active() {
        return manager != null;
    }

    /** How many items the entity really stands for, which is its own stack size without a stacker. */
    public long amountOf(Item item) {
        if (manager != null) {
            int stacked = manager.getItemAmount(item);
            if (stacked > 0) {
                return stacked;
            }
        }
        return item.getItemStack().getAmount();
    }

    /**
     * Takes items off the entity, removing it once nothing is left. Returns false when the
     * entity could not be changed, so the caller can leave the drop alone.
     */
    public boolean take(Item item, long taken) {
        if (taken <= 0) {
            return false;
        }
        long available = amountOf(item);

        if (manager != null) {
            StackedItem stacked = manager.getItem(item);
            if (stacked != null) {
                // remove() drops the entity itself once the count reaches zero.
                stacked.remove((int) Math.min(taken, Integer.MAX_VALUE));
                return true;
            }
        }

        if (taken >= available) {
            item.remove();
            return true;
        }
        var remaining = item.getItemStack().clone();
        remaining.setAmount((int) (available - taken));
        item.setItemStack(remaining);
        return true;
    }
}
