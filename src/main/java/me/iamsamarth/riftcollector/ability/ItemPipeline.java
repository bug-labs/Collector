package me.iamsamarth.riftcollector.ability;

import me.iamsamarth.riftcollector.collector.Collector;
import org.bukkit.inventory.ItemStack;

/**
 * What happens to a picked up drop: the filter decides whether the collector wants it, auto
 * smelt changes what it is, auto sell turns it into money for the owner, and whatever is left
 * goes into storage.
 *
 * <p>The count travels separately from the item because a stacker can put far more on the ground
 * than one ItemStack is allowed to say.
 */
public final class ItemPipeline {

    private final AutoSmelter smelter;
    private final SellService sales;

    public ItemPipeline(AutoSmelter smelter, SellService sales) {
        this.smelter = smelter;
        this.sales = sales;
    }

    /** How many of the items were left over and stay on the ground. */
    public long process(Collector collector, ItemStack prototype, long amount, int capacity, long slotCap) {
        if (amount <= 0) {
            return 0;
        }
        if (!collector.accepts(prototype.getType())) {
            return amount;
        }

        ItemStack handled = single(prototype);
        if (collector.enabled(Ability.AUTO_SMELT)) {
            ItemStack smelted = smelter.smelt(handled);
            // Leftovers are counted in the units of the drop, so a recipe that does not return
            // one item per item is left alone rather than miscounted.
            if (smelted != null && smelted.getAmount() == 1) {
                handled = smelted;
            }
        }
        if (collector.enabled(Ability.AUTO_SELL) && sales.sell(collector, handled, amount)) {
            return 0;
        }
        return collector.insert(handled, amount, capacity, slotCap);
    }

    private static ItemStack single(ItemStack prototype) {
        if (prototype.getAmount() == 1) {
            return prototype;
        }
        ItemStack one = prototype.clone();
        one.setAmount(1);
        return one;
    }
}
