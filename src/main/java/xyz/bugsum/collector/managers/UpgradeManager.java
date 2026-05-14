package xyz.bugsum.collector.managers;

import org.bukkit.entity.Player;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.config.MessagesConfig;
import xyz.bugsum.collector.config.TiersConfig;
import xyz.bugsum.collector.data.CollectorData;

public class UpgradeManager {

    public enum Track { SPEED, RANGE, CAPACITY }

    private final Collector plugin;

    public UpgradeManager(Collector plugin) {
        this.plugin = plugin;
    }

    public boolean attemptUpgrade(Player player, CollectorData data, Track track) {
        TiersConfig tiers = plugin.getPluginManager().getTiers();
        MessagesConfig msg = plugin.getPluginManager().getMessages();
        EconomyManager eco = plugin.getPluginManager().getEconomy();

        int current = currentTier(data, track);
        int max = maxTier(track);
        if (current >= max) {
            player.sendMessage(msg.get("upgrade.max-tier"));
            return false;
        }

        int next = current + 1;
        double price = priceFor(track, next);

        if (!eco.isAvailable()) {
            player.sendMessage(msg.get("economy.unavailable"));
            return false;
        }
        if (!eco.withdraw(player.getUniqueId(), price)) {
            player.sendMessage(msg.get("upgrade.cant-afford", "price", String.format("%.2f", price)));
            return false;
        }

        applyTier(data, track, next);
        data.markDirty();
        plugin.getPluginManager().getDatabase().enqueueSave(data);

        player.sendMessage(msg.get("upgrade.success",
            "track", trackDisplay(track), "tier", next));
        return true;
    }

    public void toggleAutosell(Player player, CollectorData data) {
        boolean newValue = !data.isAutosell();
        data.setAutosell(newValue);
        data.markDirty();
        plugin.getPluginManager().getDatabase().enqueueSave(data);
        MessagesConfig msg = plugin.getPluginManager().getMessages();
        player.sendMessage(msg.get(newValue ? "upgrade.toggled-on" : "upgrade.toggled-off",
            "feature", "Auto-Sell"));
    }

    public void toggleAutosmelt(Player player, CollectorData data) {
        boolean newValue = !data.isAutosmelt();
        data.setAutosmelt(newValue);
        data.markDirty();
        plugin.getPluginManager().getDatabase().enqueueSave(data);
        MessagesConfig msg = plugin.getPluginManager().getMessages();
        player.sendMessage(msg.get(newValue ? "upgrade.toggled-on" : "upgrade.toggled-off",
            "feature", "Auto-Smelt"));
    }

    public void toggleXp(Player player, CollectorData data) {
        boolean newValue = !data.isXpCollect();
        data.setXpCollect(newValue);
        data.markDirty();
        plugin.getPluginManager().getDatabase().enqueueSave(data);
        MessagesConfig msg = plugin.getPluginManager().getMessages();
        player.sendMessage(msg.get(newValue ? "upgrade.toggled-on" : "upgrade.toggled-off",
            "feature", "XP Collect"));
    }

    public int currentTier(CollectorData d, Track track) {
        return switch (track) {
            case SPEED    -> d.getTierSpeed();
            case RANGE    -> d.getTierRange();
            case CAPACITY -> d.getTierCapacity();
        };
    }

    public int maxTier(Track track) {
        TiersConfig t = plugin.getPluginManager().getTiers();
        return switch (track) {
            case SPEED    -> t.maxSpeedTier();
            case RANGE    -> t.maxRangeTier();
            case CAPACITY -> t.maxCapacityTier();
        };
    }

    public double priceFor(Track track, int tier) {
        TiersConfig t = plugin.getPluginManager().getTiers();
        return switch (track) {
            case SPEED    -> t.getSpeedTier(tier).getPrice();
            case RANGE    -> t.getRangeTier(tier).getPrice();
            case CAPACITY -> t.getCapacityTier(tier).getPrice();
        };
    }

    public int effectValue(Track track, int tier) {
        TiersConfig t = plugin.getPluginManager().getTiers();
        return switch (track) {
            case SPEED    -> t.getSpeedTier(tier).getIntervalTicks();
            case RANGE    -> t.getRangeTier(tier).getChunks();
            case CAPACITY -> t.getCapacityTier(tier).getMaxStorageSlots();
        };
    }

    private void applyTier(CollectorData d, Track track, int tier) {
        switch (track) {
            case SPEED    -> d.setTierSpeed(tier);
            case RANGE    -> d.setTierRange(tier);
            case CAPACITY -> d.setTierCapacity(tier);
        }
    }

    private String trackDisplay(Track t) {
        return switch (t) {
            case SPEED -> "Speed";
            case RANGE -> "Range";
            case CAPACITY -> "Capacity";
        };
    }
}
