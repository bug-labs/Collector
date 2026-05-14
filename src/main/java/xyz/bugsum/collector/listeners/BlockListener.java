package xyz.bugsum.collector.listeners;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.inventory.ItemStack;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.config.MessagesConfig;
import xyz.bugsum.collector.config.PluginConfig;
import xyz.bugsum.collector.data.CollectorData;
import xyz.bugsum.collector.items.CollectorItemFactory;
import xyz.bugsum.collector.items.ItemKeys;
import xyz.bugsum.collector.utils.PdcUtil;

public class BlockListener implements Listener {

    private final Collector plugin;

    public BlockListener(Collector plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!CollectorItemFactory.is(item)) return;

        Player p = event.getPlayer();
        PluginConfig cfg = plugin.getPluginManager().getConfigManager();
        MessagesConfig msg = plugin.getPluginManager().getMessages();

        if (cfg.getBlockedWorlds().contains(p.getWorld().getName())) {
            event.setCancelled(true);
            p.sendMessage(msg.get("place.blocked-world"));
            return;
        }

        if (!p.hasPermission("collector.bypass.limit") && cfg.getMaxCollectorsPerPlayer() >= 0) {
            int count = plugin.getPluginManager().getDatabase().countCollectorsByOwner(p.getUniqueId());
            if (count >= cfg.getMaxCollectorsPerPlayer()) {
                event.setCancelled(true);
                p.sendMessage(msg.get("place.limit-reached", "limit", cfg.getMaxCollectorsPerPlayer()));
                return;
            }
        }

        Block block = event.getBlockPlaced();
        Location loc = block.getLocation();

        // One Collector per chunk rule.
        if (cfg.isOneCollectorPerChunk() && !p.hasPermission("collector.bypass.chunklimit")) {
            boolean exists = plugin.getPluginManager().getCollector().hasCollectorInChunk(loc)
                || plugin.getPluginManager().getDatabase().chunkHasCollector(
                    loc.getWorld(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
            if (exists) {
                event.setCancelled(true);
                p.sendMessage(msg.get("place.chunk-occupied"));
                return;
            }
        }

        CollectorData data = new CollectorData(loc, p.getUniqueId());
        CollectorItemFactory.copyFromItem(item, data);

        BlockState state = block.getState();
        if (state instanceof org.bukkit.block.Hopper hopper) {
            PdcUtil.setBool(hopper.getPersistentDataContainer(), ItemKeys.getIsCollector(), true);
            PdcUtil.setString(hopper.getPersistentDataContainer(), ItemKeys.getOwner(), p.getUniqueId().toString());
            PdcUtil.setInt(hopper.getPersistentDataContainer(), ItemKeys.getTierSpeed(), data.getTierSpeed());
            PdcUtil.setInt(hopper.getPersistentDataContainer(), ItemKeys.getTierRange(), data.getTierRange());
            PdcUtil.setInt(hopper.getPersistentDataContainer(), ItemKeys.getTierCapacity(), data.getTierCapacity());
            hopper.update();
        }

        plugin.getPluginManager().getCollector().putCollector(data);
        plugin.getPluginManager().getDatabase().runAsync(() ->
            plugin.getPluginManager().getDatabase().insertCollector(data));

        // Spawn the hologram (must be on the main thread; HologramManager handles scheduling).
        plugin.getPluginManager().getHolograms().spawn(data);

        playEffect(loc, cfg.getPlaceEffect());
        p.sendMessage(msg.get("place.success"));
    }

    /**
     * Hopper is unbreakable. Pickup is performed by {@link PlayerListener#onInteract} via
     * SHIFT+RCLICK. We run at MONITOR + ignoreCancelled=false so we override anything else
     * (e.g. land-claim plugins that try to drop the block) and block the break unconditionally.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        if (!CollectorItemFactory.isPlacedBlock(event.getBlock().getState())) return;
        event.setCancelled(true);
        event.setDropItems(false);
        if (event.getPlayer() != null) {
            event.getPlayer().sendMessage(
                plugin.getPluginManager().getMessages().get("break.unbreakable"));
        }
    }

    /** Block explosions can't destroy the Collector. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> CollectorItemFactory.isPlacedBlock(b.getState()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> CollectorItemFactory.isPlacedBlock(b.getState()));
    }

    /** Don't let pistons move our hopper either. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (CollectorItemFactory.isPlacedBlock(b.getState())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (CollectorItemFactory.isPlacedBlock(b.getState())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (CollectorItemFactory.isPlacedBlock(event.getBlock().getState())) {
            event.setCancelled(true);
        }
    }

    /** Endermen, Ravagers etc. can't grief our hopper. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (CollectorItemFactory.isPlacedBlock(event.getBlock().getState())) {
            event.setCancelled(true);
        }
    }

    private void playEffect(Location loc, PluginConfig.VisualEffect effect) {
        if (effect.hasParticle()) {
            try {
                Particle pa = Particle.valueOf(effect.getParticle().toUpperCase());
                loc.getWorld().spawnParticle(pa, loc.clone().add(0.5, 1.0, 0.5), 20, 0.4, 0.4, 0.4, 0.05);
            } catch (IllegalArgumentException ignored) {}
        }
        if (effect.hasSound()) {
            try {
                Sound s = Sound.valueOf(effect.getSound().toUpperCase());
                loc.getWorld().playSound(loc, s, 0.6f, 1.4f);
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
