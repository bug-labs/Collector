package xyz.bugsum.collector.managers;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.data.CollectorData;

import java.util.Map;

/**
 * Pure-data manipulation of the virtual storage map. All methods are O(1) on the storage
 * (excepting takeMaxFitting which iterates player-inventory slots, capped at 36).
 *
 * Thread-safety: storage is a ConcurrentHashMap; all calls in this class are expected
 * from the main server thread (so no extra locking around player inventory ops).
 */
public class StorageManager {

    private final Collector plugin;

    public StorageManager(Collector plugin) {
        this.plugin = plugin;
    }

    /** Add to virtual storage. Returns the amount actually accepted (== amount unless capped). */
    public long add(CollectorData data, Material mat, long amount) {
        if (amount <= 0 || mat == null || mat == Material.AIR) return 0;
        // No per-material cap currently; tier_capacity gates *number of distinct materials*.
        int maxDistinct = plugin.getPluginManager().getTiers().getCapacityTier(data.getTierCapacity()).getMaxStorageSlots();
        if (!data.getStorage().containsKey(mat) && data.getStorage().size() >= maxDistinct) {
            return 0; // No room for a new material; storage of existing mats unaffected.
        }
        data.getStorage().merge(mat, amount, Long::sum);
        data.setItemsCollected(data.getItemsCollected() + amount);
        data.markDirty();
        return amount;
    }

    /** Remove up to {@code amount} of {@code mat}. Returns actually removed. */
    public long take(CollectorData data, Material mat, long amount) {
        if (amount <= 0 || mat == null) return 0;
        Long current = data.getStorage().get(mat);
        if (current == null || current <= 0) return 0;
        long taken = Math.min(current, amount);
        if (taken >= current) data.getStorage().remove(mat);
        else data.getStorage().put(mat, current - taken);
        data.markDirty();
        return taken;
    }

    /** Withdraw N items into the player's inventory. Returns actually given (might drop excess on the floor). */
    public int giveToPlayer(CollectorData data, Player player, Material mat, int desired) {
        long available = data.getStorage().getOrDefault(mat, 0L);
        if (available <= 0) return 0;
        int give = (int) Math.min(desired, available);
        give = Math.min(give, mat.getMaxStackSize() == 0 ? 64 : 64 * 36); // safety cap
        ItemStack stack = new ItemStack(mat, give);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        int leftoverAmount = 0;
        for (ItemStack s : leftover.values()) leftoverAmount += s.getAmount();
        int actuallyGiven = give - leftoverAmount;
        if (actuallyGiven > 0) take(data, mat, actuallyGiven);
        return actuallyGiven;
    }

    /** How many of this material the player can still fit in their inventory right now. */
    public int playerCapacityFor(Player player, Material mat) {
        int cap = mat.getMaxStackSize();
        if (cap <= 0) cap = 64;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        for (ItemStack s : contents) {
            if (s == null || s.getType() == Material.AIR) total += cap;
            else if (s.getType() == mat && s.getAmount() < cap) total += (cap - s.getAmount());
        }
        return total;
    }

    /** Used by the GUI: take 64 (or whatever is left) and give to player. */
    public int takeOneStack(CollectorData data, Player p, Material mat) {
        int cap = mat.getMaxStackSize();
        if (cap <= 0) cap = 64;
        int desired = (int) Math.min(cap, data.getStorage().getOrDefault(mat, 0L));
        return giveToPlayer(data, p, mat, desired);
    }

    /** Used by the GUI: take as many as the player can still hold. */
    public int takeMax(CollectorData data, Player p, Material mat) {
        int desired = playerCapacityFor(p, mat);
        if (desired <= 0) return 0;
        return giveToPlayer(data, p, mat, desired);
    }
}
