package me.iamsamarth.riftcollector.ability;

import me.iamsamarth.riftcollector.collector.Collector;
import me.iamsamarth.riftcollector.collector.StoredStack;
import me.iamsamarth.riftcollector.hook.PriceProvider;
import me.iamsamarth.riftcollector.hook.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Selling picked up items for the collector's owner, through whichever shop plugin has a price. */
public final class SellService {

    /** What a sale came to. */
    public record Sale(double money, long items) {
    }

    private final List<PriceProvider> providers;
    private final EconomyService economy;
    private final String currency;

    public SellService(List<PriceProvider> providers, EconomyService economy, String currency) {
        this.providers = providers;
        this.economy = economy;
        this.currency = currency;
    }

    public boolean usable() {
        return economy.available(currency) && providers.stream().anyMatch(PriceProvider::available);
    }

    /** The currency payouts are made in, so menus can show prices in the same one. */
    public String currency() {
        return currency;
    }

    /**
     * Pays the owner for the stack and records the sale. Returns false when nothing could be
     * sold, in which case the items still have to be stored.
     */
    public boolean sell(Collector collector, ItemStack prototype, long amount) {
        OfflinePlayer recipient = recipient(collector);
        if (recipient == null || amount <= 0) {
            return false;
        }
        double unit = price(recipient, prototype.getAmount() == 1 ? prototype : one(prototype));
        if (unit <= 0) {
            return false;
        }
        double total = unit * amount;
        economy.deposit(recipient, currency, total);
        collector.addSale(total, amount);
        return true;
    }

    private static ItemStack one(ItemStack prototype) {
        ItemStack single = prototype.clone();
        single.setAmount(1);
        return single;
    }

    /** What everything in the collector is worth right now. */
    public double value(Collector collector) {
        OfflinePlayer owner = recipient(collector);
        if (owner == null) {
            return 0;
        }
        double total = 0;
        for (StoredStack stack : collector.contents()) {
            double unit = price(owner, stack.copy(1));
            if (unit > 0) {
                total += unit * stack.amount();
            }
        }
        return total;
    }

    /**
     * Sells everything that has a price and pays the owner. Items nothing will buy are left
     * where they are. Counted storage holds more than any shop prices in one call, so a single
     * item is priced and multiplied.
     */
    public Sale sellAll(Collector collector) {
        OfflinePlayer owner = recipient(collector);
        if (owner == null) {
            return new Sale(0, 0);
        }

        double money = 0;
        long items = 0;
        List<Integer> sold = new ArrayList<>();
        for (int index = 0; index < collector.slotCount(); index++) {
            StoredStack stack = collector.stack(index);
            if (stack == null) {
                continue;
            }
            double unit = price(owner, stack.copy(1));
            if (unit <= 0) {
                continue;
            }
            money += unit * stack.amount();
            items += stack.amount();
            sold.add(index);
        }
        if (money <= 0) {
            return new Sale(0, 0);
        }

        economy.deposit(owner, currency, money);
        collector.addSale(money, items);
        collector.clear(sold);
        return new Sale(money, items);
    }

    private @Nullable OfflinePlayer recipient(Collector collector) {
        UUID owner = collector.owner();
        if (owner == null || !economy.available(currency)) {
            return null;
        }
        return Bukkit.getOfflinePlayer(owner);
    }

    private double price(OfflinePlayer owner, ItemStack stack) {
        for (PriceProvider provider : providers) {
            if (!provider.available()) {
                continue;
            }
            double price = provider.sellPrice(owner, stack);
            if (price > 0) {
                return price;
            }
        }
        return -1;
    }
}
