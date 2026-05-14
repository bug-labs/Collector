package xyz.bugsum.collector.managers;

import org.bukkit.entity.Player;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.config.MessagesConfig;
import xyz.bugsum.collector.data.CollectorData;

/**
 * Virtual XP "bank" attached to each Collector. XP orbs are absorbed by {@link CollectorTickTask}
 * (when the collector has xp-collect enabled) and stored in {@link CollectorData#getXpStored()}.
 * Players withdraw via the XP GUI; this manager wraps the conversion + persistence.
 */
public class XpBankManager {

    private final Collector plugin;

    public XpBankManager(Collector plugin) {
        this.plugin = plugin;
    }

    /** Withdraws roughly one experience level worth of XP (or whatever is left). */
    public long withdrawOneLevel(Player player, CollectorData data) {
        long stored = data.getXpStored();
        if (stored <= 0) {
            player.sendMessage(plugin.getPluginManager().getMessages().get("xp.empty"));
            return 0;
        }
        // XP needed to advance the player's current level.
        int xpForNextLevel = xpToNextLevel(player.getLevel());
        long give = Math.min(stored, xpForNextLevel);
        give = Math.max(1, give);
        applyWithdraw(player, data, give);
        return give;
    }

    public long withdrawAll(Player player, CollectorData data) {
        long stored = data.getXpStored();
        if (stored <= 0) {
            player.sendMessage(plugin.getPluginManager().getMessages().get("xp.empty"));
            return 0;
        }
        // Cap a single withdraw to Integer.MAX_VALUE; remainder stays for next click.
        long give = Math.min(stored, Integer.MAX_VALUE);
        applyWithdraw(player, data, give);
        return give;
    }

    private void applyWithdraw(Player player, CollectorData data, long amount) {
        data.setXpStored(data.getXpStored() - amount);
        data.markDirty();
        plugin.getPluginManager().getDatabase().enqueueSave(data);
        player.giveExp((int) Math.min(Integer.MAX_VALUE, amount));
        MessagesConfig msg = plugin.getPluginManager().getMessages();
        player.sendMessage(msg.get("xp.withdrew", "amount", String.format("%,d", amount)));
    }

    private int xpToNextLevel(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }
}
