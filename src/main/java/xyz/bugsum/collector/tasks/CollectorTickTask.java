package xyz.bugsum.collector.tasks;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.scheduler.BukkitRunnable;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.config.PluginConfig;
import xyz.bugsum.collector.config.TiersConfig;
import xyz.bugsum.collector.data.CollectorData;
import xyz.bugsum.collector.hooks.EconomyShopHook;
import xyz.bugsum.collector.managers.CollectorManager;
import xyz.bugsum.collector.managers.EconomyManager;
import xyz.bugsum.collector.managers.StorageManager;

import java.util.Iterator;

/**
 * Per-collector scheduled scan. Each Collector tracks its own {@code nextScanTick}, so the
 * task body costs O(due_collectors) per tick instead of O(all_collectors). Iterates the
 * relevant N×N chunks via {@link Chunk#getEntities()} (no AABB sweep).
 *
 * Designed to be cheap on the main thread; DB persistence is delegated to
 * {@code DatabaseManager.flushDirty()} which runs on its own executor.
 */
public class CollectorTickTask extends BukkitRunnable {

    private final Collector plugin;
    private long tick = 0;

    public CollectorTickTask(Collector plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tick++;
        CollectorManager mgr = plugin.getPluginManager().getCollector();
        TiersConfig tiers = plugin.getPluginManager().getTiers();
        boolean debug = plugin.getPluginManager().getConfigManager().isDebug();

        for (CollectorData data : mgr.allLoadedCollectors()) {
            if (data.getNextScanTick() > tick) continue;
            int interval = tiers.getSpeedTier(data.getTierSpeed()).getIntervalTicks();
            if (interval <= 0) interval = 20;
            data.setNextScanTick(tick + interval);

            try {
                processCollector(data);
            } catch (Throwable t) {
                if (debug) {
                    plugin.getLogger().warning("Tick error at " + data.getLocation() + ": " + t.getMessage());
                    t.printStackTrace();
                }
            }
        }
    }

    private void processCollector(CollectorData data) {
        Location loc = data.getLocation();
        World world = loc.getWorld();
        if (world == null) return;
        Chunk centre = world.getChunkAt(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        if (!centre.isLoaded()) return;
        if (loc.getBlock().getType() != Material.HOPPER) return;

        TiersConfig tiers = plugin.getPluginManager().getTiers();
        PluginConfig cfg = plugin.getPluginManager().getConfigManager();
        EconomyManager eco = plugin.getPluginManager().getEconomy();
        EconomyShopHook esg = plugin.getPluginManager().getEconomyShop();
        StorageManager storage = plugin.getPluginManager().getStorage();

        int chunks = tiers.getRangeTier(data.getTierRange()).getChunks();
        if (chunks <= 0) chunks = 1;
        // chunks==1 -> own chunk only (1x1); chunks==2 -> 3x3 (radius 1); chunks==N -> (2N-1)x(2N-1)
        int radius = chunks - 1;

        boolean fullSeen = false;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Chunk c = world.getChunkAt(centre.getX() + dx, centre.getZ() + dz);
                if (!c.isLoaded()) continue;

                Entity[] entities = c.getEntities();
                for (Entity e : entities) {
                    // XP orbs: absorb if enabled.
                    if (e instanceof ExperienceOrb orb) {
                        if (!data.isXpCollect()) {
                            if (plugin.getPluginManager().getConfigManager().isDebug()) {
                                plugin.getLogger().info("[debug] skip XP orb at " + orb.getLocation()
                                    + " — Collector " + data.getLocation() + " has xpCollect=false");
                            }
                            continue;
                        }
                        if (orb.isDead() || !orb.isValid()) continue;
                        long xp = Math.max(0, orb.getExperience());
                        if (xp <= 0) continue;
                        data.setXpStored(data.getXpStored() + xp);
                        data.markDirty();
                        orb.remove();
                        continue;
                    }
                    if (!(e instanceof Item itemEntity)) continue;
                    if (itemEntity.isDead() || !itemEntity.isValid()) continue;
                    if (itemEntity.getPickupDelay() > 32767) continue;

                    ItemStack stack = itemEntity.getItemStack();
                    if (stack == null || stack.getType() == Material.AIR) continue;
                    if (hasComplexNbt(stack)) continue; // skip enchanted / custom-named items
                    if (!data.getFilter().accepts(stack.getType())) continue;

                    // Auto-sell first (if priced)
                    if (data.isAutosell() && eco.isAvailable() && data.getOwner() != null) {
                        double price = lookupPrice(esg, tiers, stack);
                        if (!Double.isNaN(price) && price > 0) {
                            eco.deposit(data.getOwner(), price);
                            data.setMoneyEarned(data.getMoneyEarned() + price);
                            data.setItemsCollected(data.getItemsCollected() + stack.getAmount());
                            data.markDirty();
                            itemEntity.remove();
                            continue;
                        }
                    }

                    // Auto-smelt (substitute material)
                    if (data.isAutosmelt()) {
                        ItemStack smelted = smeltResult(stack);
                        if (smelted != null) stack = smelted;
                    }

                    long accepted = storage.add(data, stack.getType(), stack.getAmount());
                    if (accepted >= stack.getAmount()) {
                        itemEntity.remove();
                    } else if (accepted > 0) {
                        ItemStack remain = stack.clone();
                        remain.setAmount(stack.getAmount() - (int) accepted);
                        itemEntity.setItemStack(remain);
                        fullSeen = true;
                    } else {
                        fullSeen = true;
                    }
                }
            }
        }

        if (fullSeen) fullStorageEffect(loc);
    }

    private double lookupPrice(EconomyShopHook esg, TiersConfig tiers, ItemStack stack) {
        if (esg.isAvailable()) {
            double p = esg.sellPrice(stack);
            if (!Double.isNaN(p)) return p;
        }
        Double yamlPrice = tiers.getSellPrices().get(stack.getType());
        if (yamlPrice == null) return Double.NaN;
        return yamlPrice * stack.getAmount();
    }

    private boolean hasComplexNbt(ItemStack stack) {
        if (!stack.hasItemMeta()) return false;
        var meta = stack.getItemMeta();
        if (meta == null) return false;
        if (meta.hasDisplayName()) return true;
        if (meta.hasLore()) return true;
        if (meta.hasEnchants()) return true;
        if (meta.hasCustomModelData()) return true;
        if (!meta.getPersistentDataContainer().isEmpty()) return true;
        // Shulker boxes / chests / dispensers / etc. dropped with items inside store their
        // inventory in BlockStateMeta. Absorbing them silently would erase the contents.
        if (meta instanceof org.bukkit.inventory.meta.BlockStateMeta bsm && bsm.hasBlockState()) {
            return true;
        }
        // Bundles holding items.
        if (meta instanceof org.bukkit.inventory.meta.BundleMeta bm && !bm.getItems().isEmpty()) {
            return true;
        }
        // Written books / armor with trims / banners etc. — anything that's a non-default ItemMeta subtype
        // is also worth skipping conservatively.
        return false;
    }

    private ItemStack smeltResult(ItemStack input) {
        TiersConfig tiers = plugin.getPluginManager().getTiers();
        Material override = tiers.getSmeltOverrides().get(input.getType());
        if (override != null) return new ItemStack(override, input.getAmount());
        Iterator<Recipe> it = plugin.getServer().recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (r instanceof FurnaceRecipe fr && fr.getInput().getType() == input.getType()) {
                ItemStack result = fr.getResult().clone();
                result.setAmount(input.getAmount());
                return result;
            }
        }
        return null;
    }

    private void fullStorageEffect(Location loc) {
        PluginConfig.VisualEffect effect = plugin.getPluginManager().getConfigManager().getFullStorageEffect();
        if (effect.hasParticle()) {
            try {
                Particle p = Particle.valueOf(effect.getParticle().toUpperCase());
                loc.getWorld().spawnParticle(p, loc.clone().add(0.5, 1.0, 0.5), 8, 0.3, 0.3, 0.3, 0.01);
            } catch (IllegalArgumentException ignored) {}
        }
        if (effect.hasSound()) {
            try {
                Sound s = Sound.valueOf(effect.getSound().toUpperCase());
                loc.getWorld().playSound(loc, s, 0.4f, 0.8f);
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
