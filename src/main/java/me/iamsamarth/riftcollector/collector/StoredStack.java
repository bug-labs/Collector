package me.iamsamarth.riftcollector.collector;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * One slot of a collector: a single-item template plus how many of it are stored. Counting
 * separately from the item is what lets a slot hold a million of something when a vanilla slot
 * would stop at 64.
 */
public record StoredStack(ItemStack prototype, long amount) {

    public StoredStack(ItemStack prototype, long amount) {
        ItemStack template = prototype.clone();
        template.setAmount(1);
        this.prototype = template;
        this.amount = Math.max(0, amount);
    }

    public boolean matches(ItemStack stack) {
        return prototype.isSimilar(stack);
    }

    public Material type() {
        return prototype.getType();
    }

    public int maxStackSize() {
        return prototype.getMaxStackSize();
    }

    public StoredStack plus(long count) {
        return new StoredStack(prototype, amount + count);
    }

    public StoredStack minus(long count) {
        return new StoredStack(prototype, amount - count);
    }

    /** A real stack of this item, for handing to a player or dropping on the ground. */
    public ItemStack copy(int count) {
        ItemStack stack = prototype.clone();
        stack.setAmount(count);
        return stack;
    }
}
