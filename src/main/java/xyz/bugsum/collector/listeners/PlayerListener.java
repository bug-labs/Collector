package xyz.bugsum.collector.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.config.MessagesConfig;
import xyz.bugsum.collector.data.CollectorData;
import xyz.bugsum.collector.gui.MainGUI;
import xyz.bugsum.collector.items.CollectorItemFactory;

public class PlayerListener implements Listener {

    private final Collector plugin;

    public PlayerListener(Collector plugin) {
        this.plugin = plugin;
    }

    /**
     * Plain RCLICK on a placed Collector → open Main GUI.
     * SHIFT + LCLICK   → pick the hopper up (owner / admin only).
     * SHIFT + RCLICK   → pass through (so players can still place blocks while sneaking
     *                    in the off-hand etc.); we just suppress vanilla hopper open.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.HOPPER) return;
        BlockState state = block.getState();
        if (!CollectorItemFactory.isPlacedBlock(state)) return;

        Player p = event.getPlayer();
        MessagesConfig msg = plugin.getPluginManager().getMessages();

        if (!p.hasPermission("collector.use")) {
            event.setCancelled(true);
            p.sendMessage(msg.get("general.no-permission"));
            return;
        }

        CollectorData data = plugin.getPluginManager().getCollector().getCollector(block.getLocation());
        if (data == null) return;

        boolean isOwner  = data.getOwner() != null && data.getOwner().equals(p.getUniqueId());
        boolean isFriend = data.getFriends().contains(p.getUniqueId());
        boolean isAdmin  = p.hasPermission("collector.admin");

        // SHIFT + LEFT click → pickup (owner / admin only — friends cannot pick up)
        if (action == Action.LEFT_CLICK_BLOCK && p.isSneaking()) {
            event.setCancelled(true);
            if (!isOwner && !isAdmin) {
                p.sendMessage(msg.get("break.not-owner"));
                return;
            }
            pickup(p, data, block);
            return;
        }

        // GUI access requires owner OR friend (or admin).
        if (!isOwner && !isFriend && !isAdmin) {
            event.setCancelled(true);
            p.sendMessage(msg.get("general.not-allowed"));
            return;
        }

        // Plain LEFT click without sneak: let vanilla handle (BlockBreakEvent will be cancelled
        // by BlockListener so the block survives, but we don't want to silently swallow the
        // click in case the player intended to swing).
        if (action == Action.LEFT_CLICK_BLOCK) return;

        // RIGHT click → open menu (suppress vanilla hopper)
        event.setCancelled(true);
        MainGUI.open(p, data);
    }

    /** Defence in depth — block any other code path that might open the hopper inventory. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        InventoryHolder h = event.getInventory().getHolder();
        if (h instanceof org.bukkit.block.Hopper hopper
                && CollectorItemFactory.isPlacedBlock(hopper.getBlock().getState())) {
            event.setCancelled(true);
            if (event.getPlayer() instanceof Player p) {
                CollectorData data = plugin.getPluginManager().getCollector().getCollector(hopper.getBlock().getLocation());
                if (data != null) MainGUI.open(p, data);
            }
        }
    }

    private void pickup(Player p, CollectorData data, Block block) {
        Location loc = block.getLocation();
        MessagesConfig msg = plugin.getPluginManager().getMessages();

        // Refuse pickup when storage exceeds the configured limit so the server
        // doesn't drown in dropped items. Bypassable with collector.bypass.pickuplimit.
        long limit = plugin.getPluginManager().getConfigManager().getPickupMaxItems();
        if (limit >= 0 && !p.hasPermission("collector.bypass.pickuplimit")) {
            long total = 0;
            for (long v : data.getStorage().values()) total += v;
            if (total > limit) {
                p.sendMessage(msg.get("break.too-full",
                    "total", String.format("%,d", total),
                    "limit", String.format("%,d", limit)));
                return;
            }
        }

        // Drop virtual item storage on the floor.
        for (var entry : data.getStorage().entrySet()) {
            long remaining = entry.getValue();
            int max = entry.getKey().getMaxStackSize();
            if (max <= 0) max = 64;
            while (remaining > 0) {
                int amount = (int) Math.min(remaining, max);
                block.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5),
                    new ItemStack(entry.getKey(), amount));
                remaining -= amount;
            }
        }
        data.getStorage().clear();

        // Hand stored XP back to the player as a real XP bar gain (no orb spam).
        if (data.getXpStored() > 0) {
            int xp = (int) Math.min(Integer.MAX_VALUE, data.getXpStored());
            p.giveExp(xp);
            data.setXpStored(0);
        }

        // Stats are reset on pickup — picked-up Collector becomes a fresh hopper
        // (tiers preserved so upgrades aren't lost; counters cleared).
        data.setItemsCollected(0);
        data.setMoneyEarned(0);

        ItemStack item = CollectorItemFactory.fromData(data);
        var leftover = p.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack s : leftover.values()) {
                block.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), s);
            }
        }

        plugin.getPluginManager().getHolograms().remove(loc);
        block.setType(Material.AIR, false);
        plugin.getPluginManager().getCollector().removeCollector(loc);
        plugin.getPluginManager().getDatabase().runAsync(() ->
            plugin.getPluginManager().getDatabase().deleteCollector(loc));

        p.sendMessage(msg.get("break.success"));
    }
}
