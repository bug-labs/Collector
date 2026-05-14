package xyz.bugsum.collector.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.items.CollectorItemFactory;

/**
 * The Collector's vanilla hopper inventory is unused — virtual storage drives everything.
 * Cancel any vanilla hopper transfer involving a Collector block so it never participates
 * in pipe networks (preventing free hopper-chains around the chunk).
 */
public class HopperListener implements Listener {

    @SuppressWarnings("unused")
    public HopperListener(Collector plugin) {}

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (isCollector(event.getSource()) || isCollector(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    private boolean isCollector(Inventory inv) {
        InventoryHolder h = inv.getHolder();
        if (!(h instanceof org.bukkit.block.Hopper hopper)) return false;
        return CollectorItemFactory.isPlacedBlock(hopper.getBlock().getState());
    }
}
