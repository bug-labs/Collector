package me.iamsamarth.riftcollector.upgrade;

import me.iamsamarth.riftcollector.collector.Collector;
import me.iamsamarth.riftcollector.config.PluginConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** The upgrade tiers from the config, and what a given collector's levels mean. */
public final class Upgrades {

    /**
     * One level of an upgrade. {@code radius} is only read for the pickup range upgrade when the
     * range type is RADIUS, where the reach is counted in blocks rather than chunks.
     */
    public record Tier(int level, long value, long radius, double cost,
                       @Nullable String currency, @Nullable String permission) {
    }

    /** Whether storage is sold by the slot or a whole page at a time. */
    public enum SlotMode {
        SLOT, PAGE
    }

    /** What one click buys. */
    public enum Purchase {
        ONE, BULK, PAGE
    }

    public record SlotModel(SlotMode mode,
                            int slotStart,
                            int slotMax,
                            double costPerSlot,
                            int bulk,
                            int pageStart,
                            int pageMax,
                            double costPerPage,
                            @Nullable String currency) {
    }

    private final Map<UpgradeType, List<Tier>> tiers;
    private final SlotModel slotModel;

    private int pageSize = 28;

    private Upgrades(Map<UpgradeType, List<Tier>> tiers, SlotModel slotModel) {
        this.tiers = tiers;
        this.slotModel = slotModel;
    }

    public static Upgrades load(FileConfiguration config, Logger logger) {
        Map<UpgradeType, List<Tier>> tiers = new EnumMap<>(UpgradeType.class);
        for (UpgradeType type : UpgradeType.values()) {
            // Slots are bought by the slot or by the page, so they have no fixed levels.
            if (type != UpgradeType.SLOTS) {
                tiers.put(type, readTiers(config, type, logger));
            }
        }
        return new Upgrades(tiers, readSlotModel(config, logger));
    }

    private static SlotModel readSlotModel(FileConfiguration config, Logger logger) {
        String rawMode = config.getString("upgrades.slots.mode", "SLOT");
        SlotMode mode;
        try {
            mode = SlotMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            logger.warning("upgrades.slots.mode '" + rawMode + "' is not SLOT or PAGE, using SLOT.");
            mode = SlotMode.SLOT;
        }

        return new SlotModel(mode,
                Math.max(1, config.getInt("upgrades.slots.slot.start", 7)),
                Math.max(1, config.getInt("upgrades.slots.slot.max", 84)),
                Math.max(0, config.getDouble("upgrades.slots.slot.cost-per-slot", 2500)),
                Math.max(1, config.getInt("upgrades.slots.slot.bulk", 7)),
                Math.max(1, config.getInt("upgrades.slots.page.start", 1)),
                Math.max(1, config.getInt("upgrades.slots.page.max", 3)),
                Math.max(0, config.getDouble("upgrades.slots.page.cost-per-page", 60000)),
                config.getString("upgrades.slots.currency", ""));
    }

    private static List<Tier> readTiers(FileConfiguration config, UpgradeType type, Logger logger) {
        List<Map<?, ?>> raw = config.getMapList("upgrades." + type.key() + ".levels");
        List<Tier> tiers = new ArrayList<>(raw.size());
        for (Map<?, ?> entry : raw) {
            Object value = entry.get("value");
            if (!(value instanceof Number number)) {
                logger.warning("Upgrade " + type.key() + " has a level without a numeric 'value', skipping it.");
                continue;
            }
            Object cost = entry.get("cost");
            Object radius = entry.get("radius");
            Object currency = entry.get("currency");
            Object permission = entry.get("permission");
            tiers.add(new Tier(tiers.size(), number.longValue(),
                    radius instanceof Number radiusNumber ? radiusNumber.longValue() : number.longValue(),
                    cost instanceof Number costNumber ? Math.max(0, costNumber.doubleValue()) : 0,
                    currency == null ? null : currency.toString(),
                    permission == null ? null : permission.toString()));
        }
        if (tiers.isEmpty()) {
            logger.warning("Upgrade " + type.key() + " has no levels configured; using "
                    + type.fallbackValue() + " with no way to upgrade.");
            tiers.add(new Tier(0, type.fallbackValue(), type.fallbackValue(), 0, null, null));
        }
        return List.copyOf(tiers);
    }

    /** The number of content slots in the collector menu, which is what a page holds. */
    public void pageSize(int pageSize) {
        this.pageSize = Math.max(1, pageSize);
    }

    public int pageSize() {
        return pageSize;
    }

    public SlotModel slotModel() {
        return slotModel;
    }

    public List<Tier> tiers(UpgradeType type) {
        return tiers.getOrDefault(type, List.of());
    }

    public int maxLevel(UpgradeType type) {
        return Math.max(0, tiers(type).size() - 1);
    }

    public Tier tier(UpgradeType type, int level) {
        List<Tier> available = tiers(type);
        return available.get(Math.clamp(level, 0, available.size() - 1));
    }

    public @Nullable Tier next(UpgradeType type, int level) {
        List<Tier> available = tiers(type);
        int next = level + 1;
        return next < available.size() ? available.get(next) : null;
    }

    public long value(UpgradeType type, int level) {
        return tier(type, level).value();
    }

    /** How many slots this collector can use right now. */
    public int slots(Collector collector) {
        int bought = collector.upgradeLevel(UpgradeType.SLOTS);
        if (slotModel.mode() == SlotMode.PAGE) {
            return Math.min(slotModel.pageStart() * pageSize + bought, slotModel.pageMax() * pageSize);
        }
        return Math.min(slotModel.slotStart() + bought, slotModel.slotMax());
    }

    public int maxSlots() {
        return slotModel.mode() == SlotMode.PAGE
                ? slotModel.pageMax() * pageSize
                : slotModel.slotMax();
    }

    /** How many slots a purchase adds, or 0 when that purchase is not offered in this mode. */
    public int purchaseSize(Purchase purchase) {
        if (slotModel.mode() == SlotMode.PAGE) {
            return purchase == Purchase.PAGE ? pageSize : 0;
        }
        return switch (purchase) {
            case ONE -> 1;
            case BULK -> slotModel.bulk();
            case PAGE -> pageSize;
        };
    }

    /** The currency storage is sold in, blank for the default one. */
    public @Nullable String slotCurrency() {
        return slotModel.currency();
    }

    public double purchaseCost(int slots) {
        if (slotModel.mode() == SlotMode.PAGE) {
            return slotModel.costPerPage() * Math.ceil((double) slots / pageSize);
        }
        return slotModel.costPerSlot() * slots;
    }

    public long slotCapacity(Collector collector) {
        return Math.max(1, value(UpgradeType.SLOT_CAPACITY, collector.upgradeLevel(UpgradeType.SLOT_CAPACITY)));
    }

    public long sweepTicks(Collector collector) {
        return Math.max(1, value(UpgradeType.PICKUP_SPEED, collector.upgradeLevel(UpgradeType.PICKUP_SPEED)));
    }

    public int range(Collector collector, PluginConfig.RangeType rangeType) {
        Tier tier = tier(UpgradeType.PICKUP_RANGE, collector.upgradeLevel(UpgradeType.PICKUP_RANGE));
        return (int) Math.max(0, rangeType == PluginConfig.RangeType.RADIUS ? tier.radius() : tier.value());
    }

    /** Sections of a menu file can hide an upgrade a player is not allowed to buy. */
    public boolean hasPermission(UpgradeType type, int level, Player player) {
        if (!player.hasPermission(type.permission())) {
            return false;
        }
        if (type == UpgradeType.SLOTS) {
            return true;
        }
        String permission = tier(type, level).permission();
        return permission == null || player.hasPermission(permission);
    }
}
