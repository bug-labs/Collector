package me.iamsamarth.riftcollector.listener;

import me.iamsamarth.riftcollector.RiftCollector;
import me.iamsamarth.riftcollector.collector.Collector;
import me.iamsamarth.riftcollector.collector.CollectorItem;
import me.iamsamarth.riftcollector.collector.CollectorService;
import me.iamsamarth.riftcollector.collector.StoredStack;

import me.iamsamarth.riftcollector.lang.Lang;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class CollectorListener implements Listener {

    private static final String BYPASS_PERMISSION = "riftcollector.bypass";

    private final RiftCollector plugin;
    private final CollectorService collectors;
    private final CollectorItem collectorItem;
    private final Lang lang;

    public CollectorListener(RiftCollector plugin, CollectorService collectors,
                             CollectorItem collectorItem, Lang lang) {
        this.plugin = plugin;
        this.collectors = collectors;
        this.collectorItem = collectorItem;
        this.lang = lang;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!collectorItem.isCollector(event.getItemInHand())) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("riftcollector.place")) {
            event.setCancelled(true);
            lang.send(player, "collector.no-permission");
            return;
        }

        Block block = event.getBlockPlaced();
        int limit = plugin.config().limits().collectorsPerChunk();
        if (collectors.index().countIn(block.getChunk()) >= limit) {
            event.setCancelled(true);
            lang.send(player, "collector.chunk-limit", Placeholder.unparsed("limit", Integer.toString(limit)));
            return;
        }

        Collector collector = collectors.create(block, block.getType(), player);
        // A collector that was broken with items inside carries them in the dropped item.
        List<StoredStack> carried = collectorItem.contents(event.getItemInHand());
        if (!carried.isEmpty()) {
            collector.fill(carried);
        }
        plugin.holograms().refresh(collector);
        lang.send(player, "collector.placed");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Collector collector = collectors.get(block);
        if (collector == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!canManage(player, collector)) {
            event.setCancelled(true);
            sendNotOwner(player, collector);
            return;
        }
        // A collector is only removable while empty, so its contents can never ride along in an
        // item or flood the ground as entities.
        if (collector.itemCount() > 0) {
            event.setCancelled(true);
            lang.send(player, "collector.not-empty");
            return;
        }

        plugin.menus().registry().closeFor(collector);
        boolean dropCollector = event.isDropItems() && player.getGameMode() != GameMode.CREATIVE;
        collectors.remove(collector);

        event.setDropItems(false);
        if (dropCollector) {
            block.getWorld().dropItemNaturally(block.getLocation().toCenterLocation(),
                    collectorItem.create(1, collector.material()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();
        // Sneaking with something in hand is how players build against a block.
        if (player.isSneaking() && event.getItem() != null) {
            return;
        }
        Collector collector = collectors.get(block);
        if (collector == null) {
            return;
        }

        event.setCancelled(true);
        if (!canUse(player, collector)) {
            sendNotOwner(player, collector);
            return;
        }
        plugin.menus().openCollector(player, collector);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (belongsToCollector(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemMove(InventoryMoveItemEvent event) {
        if (belongsToCollector(event.getSource()) || belongsToCollector(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (belongsToCollector(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    /** Opening and using: the owner, anyone they trusted, and staff. */
    private boolean canUse(Player player, Collector collector) {
        return collector.isOwner(player) || collector.isTrusted(player.getUniqueId())
                || player.hasPermission(BYPASS_PERMISSION);
    }

    /** Removing the collector: the owner and staff only. */
    private boolean canManage(Player player, Collector collector) {
        return collector.isOwner(player) || player.hasPermission(BYPASS_PERMISSION);
    }

    private void sendNotOwner(Player player, Collector collector) {
        String ownerName = collector.ownerName();
        lang.send(player, "collector.not-owner", ownerName == null
                ? Placeholder.component("owner", lang.get("collector.unknown-owner"))
                : Placeholder.unparsed("owner", ownerName));
    }

    /** Collector blocks are inert as vanilla containers; everything goes through their storage. */
    private boolean belongsToCollector(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof DoubleChest doubleChest) {
            return isCollector(doubleChest.getLeftSide()) || isCollector(doubleChest.getRightSide());
        }
        return isCollector(holder);
    }

    private boolean isCollector(@Nullable InventoryHolder holder) {
        return holder instanceof BlockState state && collectors.get(state.getBlock()) != null;
    }
}
