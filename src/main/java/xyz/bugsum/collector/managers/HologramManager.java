package xyz.bugsum.collector.managers;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.config.MessagesConfig;
import xyz.bugsum.collector.data.CollectorData;
import xyz.bugsum.collector.gui.MainGUI;
import xyz.bugsum.collector.items.ItemKeys;
import xyz.bugsum.collector.utils.LocationUtil;
import xyz.bugsum.collector.utils.Utils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns / refreshes a TextDisplay above each placed Collector. Each spawned entity
 * carries a PDC tag with the serialized Collector location so we can:
 *  - identify orphans on startup (server crashed mid-tick) and remove them
 *  - identify out-of-position holograms (config y-offset changed) and respawn them
 *
 * Updates are throttled to once every {@code refresh-interval-ticks} (default 40 ticks
 * = 2s) to keep main-thread cost negligible even with hundreds of placed Collectors.
 */
public class HologramManager {

    private final Collector plugin;
    private final Map<String, UUID> holograms = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;

    public HologramManager(Collector plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        // Schedule for next tick so loaded chunks/entities are settled.
        Bukkit.getScheduler().runTask(plugin, () -> {
            cleanupAllOrphans();
            for (CollectorData d : plugin.getPluginManager().getCollector().allLoadedCollectors()) {
                if (d.isHologramVisible()) spawn(d);
            }
        });
        startRefreshTask();
    }

    public void shutdown() {
        if (refreshTask != null) {
            try { refreshTask.cancel(); } catch (Exception ignored) {}
            refreshTask = null;
        }
        for (UUID id : holograms.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        holograms.clear();
    }

    /** Cancel + restart with the current config interval. Called by the admin GUI. */
    public void restartRefreshTask() {
        if (refreshTask != null) {
            try { refreshTask.cancel(); } catch (Exception ignored) {}
        }
        startRefreshTask();
    }

    private void startRefreshTask() {
        long period = Math.max(20L,
            plugin.getPluginManager().getConfigManager().getHologramRefreshTicks());
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, period, period);
    }

    /** Wipe and re-create every hologram. Called when y-offset / enabled / refresh changes. */
    public void respawnAll() {
        // First, remove every TextDisplay we own across loaded worlds — covers
        // both tracked and untracked (orphan) entities at the new y-offset.
        for (World w : Bukkit.getWorlds()) {
            for (TextDisplay td : w.getEntitiesByClass(TextDisplay.class)) {
                if (isOurs(td)) td.remove();
            }
        }
        holograms.clear();

        if (!plugin.getPluginManager().getConfigManager().isHologramsEnabled()) return;
        for (CollectorData d : plugin.getPluginManager().getCollector().allLoadedCollectors()) {
            if (d.isHologramVisible()) spawn(d);
        }
    }

    /** Sweep every loaded world for TextDisplays we own with no live Collector backing. */
    public void cleanupAllOrphans() {
        double yOff = plugin.getPluginManager().getConfigManager().getHologramYOffset();
        for (World w : Bukkit.getWorlds()) {
            for (TextDisplay td : w.getEntitiesByClass(TextDisplay.class)) {
                if (!isOurs(td)) continue;
                String tagged = td.getPersistentDataContainer()
                    .get(ItemKeys.getHologramOwner(), PersistentDataType.STRING);
                Location collectorLoc = LocationUtil.deserialize(tagged);
                if (collectorLoc == null || collectorLoc.getWorld() != w
                        || collectorLoc.getBlock().getType() != Material.HOPPER) {
                    td.remove();
                    continue;
                }
                // Stale offset — config y-offset was changed; force a respawn.
                double expectedY = collectorLoc.getY() + yOff;
                if (Math.abs(td.getLocation().getY() - expectedY - 0.5) > 0.05) {
                    td.remove();
                }
            }
        }
    }

    public void spawn(CollectorData data) {
        if (!plugin.getPluginManager().getConfigManager().isHologramsEnabled()) return;
        if (!data.isHologramVisible()) return;
        Location loc = data.getLocation();
        if (loc == null || loc.getWorld() == null) return;
        World w = loc.getWorld();
        if (!w.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

        String key = LocationUtil.serialize(loc);

        // Defensive: kill any stray TextDisplay we own at this Collector first.
        for (TextDisplay td : w.getNearbyEntitiesByType(TextDisplay.class,
                loc.clone().add(0.5, 1.0, 0.5), 1.5)) {
            if (!isOurs(td)) continue;
            String tagged = td.getPersistentDataContainer()
                .get(ItemKeys.getHologramOwner(), PersistentDataType.STRING);
            if (key.equals(tagged)) td.remove();
        }

        UUID existing = holograms.get(key);
        if (existing != null) {
            Entity e = Bukkit.getEntity(existing);
            if (e instanceof TextDisplay td && !td.isDead()) {
                updateText(data);
                return;
            }
            holograms.remove(key);
        }

        double yOff = plugin.getPluginManager().getConfigManager().getHologramYOffset();
        Location spawnLoc = loc.clone().add(0.5, yOff, 0.5);
        Bukkit.getScheduler().runTask(plugin, () -> {
            TextDisplay td = w.spawn(spawnLoc, TextDisplay.class, e -> {
                e.setBillboard(Display.Billboard.VERTICAL);
                e.setShadowed(true);
                e.setSeeThrough(false);
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                e.setDefaultBackground(false);
                e.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f), new AxisAngle4f(),
                    new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
                e.text(buildText(data));
                e.getPersistentDataContainer().set(
                    ItemKeys.getHologramOwner(), PersistentDataType.STRING, key);
                e.addScoreboardTag("collector_hologram");
            });
            holograms.put(key, td.getUniqueId());
        });
    }

    public void remove(Location loc) {
        if (loc == null) return;
        String key = LocationUtil.serialize(loc);
        UUID id = holograms.remove(key);
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Remove the tracked entity if still alive.
            if (id != null) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) e.remove();
            }
            // Plus any stray tagged TextDisplays at this location (orphans).
            World w = loc.getWorld();
            if (w == null) return;
            for (TextDisplay td : w.getNearbyEntitiesByType(TextDisplay.class,
                    loc.clone().add(0.5, 1.0, 0.5), 1.5)) {
                if (!isOurs(td)) continue;
                String tagged = td.getPersistentDataContainer()
                    .get(ItemKeys.getHologramOwner(), PersistentDataType.STRING);
                if (key.equals(tagged)) td.remove();
            }
        });
    }

    public void updateText(CollectorData data) {
        UUID id = holograms.get(LocationUtil.serialize(data.getLocation()));
        if (id == null) return;
        Entity e = Bukkit.getEntity(id);
        if (e instanceof TextDisplay td) td.text(buildText(data));
    }

    public void toggle(CollectorData data) {
        if (data.isHologramVisible()) spawn(data);
        else remove(data.getLocation());
    }

    /** Sweep one chunk for orphans. Called by ChunkListener so newly loaded chunks get cleaned. */
    public void cleanupChunk(org.bukkit.Chunk chunk) {
        if (chunk == null) return;
        World w = chunk.getWorld();
        for (Entity e : chunk.getEntities()) {
            if (!(e instanceof TextDisplay td) || !isOurs(td)) continue;
            String tagged = td.getPersistentDataContainer()
                .get(ItemKeys.getHologramOwner(), PersistentDataType.STRING);
            Location collectorLoc = LocationUtil.deserialize(tagged);
            if (collectorLoc == null || collectorLoc.getWorld() != w
                    || collectorLoc.getBlock().getType() != Material.HOPPER) {
                td.remove();
            }
        }
    }

    private boolean isOurs(TextDisplay td) {
        return td.getPersistentDataContainer().has(
            ItemKeys.getHologramOwner(), PersistentDataType.STRING);
    }

    private void refreshAll() {
        if (!plugin.getPluginManager().getConfigManager().isHologramsEnabled()) return;
        for (CollectorData d : plugin.getPluginManager().getCollector().allLoadedCollectors()) {
            if (!d.isHologramVisible()) {
                remove(d.getLocation());
                continue;
            }
            String key = LocationUtil.serialize(d.getLocation());
            UUID id = holograms.get(key);
            if (id == null || Bukkit.getEntity(id) == null) {
                spawn(d);
            } else {
                updateText(d);
            }
        }
    }

    private Component buildText(CollectorData data) {
        MessagesConfig msg = plugin.getPluginManager().getMessages();
        String name = data.getDisplayName() == null || data.getDisplayName().isBlank()
            ? msg.raw("hologram.default-name") : data.getDisplayName();
        if (name.equals("hologram.default-name")) name = "Collector";
        List<String> lines = msg.getRawStringList("hologram.lines",
            "name", name,
            "owner", ownerName(data),
            // <items> = currently stored. <lifetime_items> = ever collected.
            "items", MainGUI.formatNumber(data.currentItems()),
            "lifetime_items", MainGUI.formatNumber(data.getItemsCollected()),
            "money", String.format("%,.0f", data.getMoneyEarned()),
            "xp", MainGUI.formatNumber(data.getXpStored()),
            "friends", data.getFriends().size(),
            "tier_speed", data.getTierSpeed(),
            "tier_range", data.getTierRange(),
            "tier_capacity", data.getTierCapacity());
        if (lines.isEmpty()) return Component.text("Collector");
        Component out = Utils.colorize(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            out = out.appendNewline().append(Utils.colorize(lines.get(i)));
        }
        return out;
    }

    private String ownerName(CollectorData d) {
        if (d.getOwner() == null) return "Unknown";
        var op = Bukkit.getOfflinePlayer(d.getOwner());
        return op.getName() == null ? d.getOwner().toString() : op.getName();
    }
}
