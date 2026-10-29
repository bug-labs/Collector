package me.iamsamarth.riftcollector.upgrade;

import me.iamsamarth.riftcollector.collector.Collector;
import me.iamsamarth.riftcollector.hook.EconomyService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/** Buying an upgrade: permission, then money, then the level goes up. */
public final class UpgradeService {

    public enum Result {
        PURCHASED, MAX_LEVEL, NO_PERMISSION, NO_ECONOMY, NOT_ENOUGH_MONEY
    }

    private final Upgrades upgrades;
    private final EconomyService economy;

    public UpgradeService(Upgrades upgrades, EconomyService economy) {
        this.upgrades = upgrades;
        this.economy = economy;
    }

    /** Buys more storage, by the slot or by the page depending on the configured mode. */
    public Result purchaseSlots(Player player, Collector collector, Upgrades.Purchase purchase) {
        int size = upgrades.purchaseSize(purchase);
        if (size <= 0) {
            return Result.MAX_LEVEL;
        }
        int current = upgrades.slots(collector);
        int maximum = upgrades.maxSlots();
        if (current >= maximum) {
            return Result.MAX_LEVEL;
        }
        if (!player.hasPermission(UpgradeType.SLOTS.permission())) {
            return Result.NO_PERMISSION;
        }

        int granted = Math.min(size, maximum - current);
        double cost = upgrades.purchaseCost(granted);
        String currency = upgrades.slotCurrency();
        if (cost > 0) {
            if (!economy.available(currency)) {
                return Result.NO_ECONOMY;
            }
            if (!economy.withdraw(player, currency, cost)) {
                return Result.NOT_ENOUGH_MONEY;
            }
        }
        collector.upgradeLevel(UpgradeType.SLOTS, collector.upgradeLevel(UpgradeType.SLOTS) + granted);
        return Result.PURCHASED;
    }

    public Result purchase(Player player, Collector collector, UpgradeType type) {
        int level = collector.upgradeLevel(type);
        Upgrades.Tier next = upgrades.next(type, level);
        if (next == null) {
            return Result.MAX_LEVEL;
        }
        if (!player.hasPermission(type.permission()) || !allowed(next, player)) {
            return Result.NO_PERMISSION;
        }
        if (next.cost() > 0) {
            if (!economy.available(next.currency())) {
                return Result.NO_ECONOMY;
            }
            if (!economy.withdraw(player, next.currency(), next.cost())) {
                return Result.NOT_ENOUGH_MONEY;
            }
        }
        collector.upgradeLevel(type, next.level());
        return Result.PURCHASED;
    }

    private boolean allowed(Upgrades.Tier tier, Player player) {
        @Nullable String permission = tier.permission();
        return permission == null || player.hasPermission(permission);
    }
}
