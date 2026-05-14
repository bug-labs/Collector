package xyz.bugsum.collector.items;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.config.MessagesConfig;
import xyz.bugsum.collector.data.CollectorData;
import xyz.bugsum.collector.data.FilterData;
import xyz.bugsum.collector.utils.PdcUtil;
import xyz.bugsum.collector.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public final class CollectorItemFactory {

    private CollectorItemFactory() {}

    /** Build a fresh Collector ItemStack at given tiers (used by /collector give and crates). */
    public static ItemStack create(int tierSpeed, int tierRange, int tierCapacity) {
        return create(tierSpeed, tierRange, tierCapacity, null);
    }

    public static ItemStack create(int tierSpeed, int tierRange, int tierCapacity, String displayName) {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        PdcUtil.setBool(pdc, ItemKeys.getIsCollector(), true);
        PdcUtil.setInt(pdc, ItemKeys.getTierSpeed(), tierSpeed);
        PdcUtil.setInt(pdc, ItemKeys.getTierRange(), tierRange);
        PdcUtil.setInt(pdc, ItemKeys.getTierCapacity(), tierCapacity);
        if (displayName != null && !displayName.isBlank()) {
            PdcUtil.setString(pdc, ItemKeys.getDisplayName(), displayName);
        }

        applyDisplay(meta, tierSpeed, tierRange, tierCapacity, displayName);
        item.setItemMeta(meta);
        return item;
    }

    /** Build an ItemStack from existing CollectorData (used when block is broken). */
    public static ItemStack fromData(CollectorData data) {
        ItemStack item = create(data.getTierSpeed(), data.getTierRange(), data.getTierCapacity(),
            data.getDisplayName());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        PdcUtil.setBool(pdc, ItemKeys.getAutosellEnabled(), data.isAutosell());
        PdcUtil.setBool(pdc, ItemKeys.getAutosmeltEnabled(), data.isAutosmelt());
        PdcUtil.setString(pdc, ItemKeys.getFilterMode(), data.getFilter().getMode().name());
        PdcUtil.setString(pdc, ItemKeys.getFilterItems(), data.getFilter().serializeItems());
        PdcUtil.setLong(pdc, ItemKeys.getItemsCollected(), data.getItemsCollected());
        PdcUtil.setDouble(pdc, ItemKeys.getMoneyEarned(), data.getMoneyEarned());
        if (data.getOwner() != null) {
            PdcUtil.setString(pdc, ItemKeys.getOwner(), data.getOwner().toString());
        }
        item.setItemMeta(meta);
        return item;
    }

    public static boolean is(ItemStack item) {
        if (item == null || item.getType() != Material.HOPPER || !item.hasItemMeta()) return false;
        return PdcUtil.has(item.getItemMeta().getPersistentDataContainer(), ItemKeys.getIsCollector());
    }

    public static boolean isPlacedBlock(BlockState state) {
        if (!(state instanceof org.bukkit.block.Hopper hopper)) return false;
        return PdcUtil.has(hopper.getPersistentDataContainer(), ItemKeys.getIsCollector());
    }

    /** Hydrate a CollectorData from an item's PDC (used when placed). */
    public static void copyFromItem(ItemStack item, CollectorData out) {
        if (!is(item)) return;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        out.setTierSpeed(Math.max(1, PdcUtil.getInt(pdc, ItemKeys.getTierSpeed(), 1)));
        out.setTierRange(Math.max(1, PdcUtil.getInt(pdc, ItemKeys.getTierRange(), 1)));
        out.setTierCapacity(Math.max(1, PdcUtil.getInt(pdc, ItemKeys.getTierCapacity(), 1)));
        out.setAutosell(PdcUtil.getBool(pdc, ItemKeys.getAutosellEnabled(), false));
        out.setAutosmelt(PdcUtil.getBool(pdc, ItemKeys.getAutosmeltEnabled(), false));
        String dn = PdcUtil.getString(pdc, ItemKeys.getDisplayName(), "");
        out.setDisplayName(dn.isBlank() ? null : dn);
        try {
            out.getFilter().setMode(FilterData.Mode.valueOf(
                PdcUtil.getString(pdc, ItemKeys.getFilterMode(), "NONE")));
        } catch (IllegalArgumentException ignored) {}
        out.getFilter().deserializeItems(PdcUtil.getString(pdc, ItemKeys.getFilterItems(), ""));
        out.setItemsCollected(PdcUtil.getLong(pdc, ItemKeys.getItemsCollected(), 0));
        out.setMoneyEarned(PdcUtil.getDouble(pdc, ItemKeys.getMoneyEarned(), 0));
    }

    /** Refresh the item's display name + lore from messages.yml (called after upgrades). */
    public static void applyDisplay(ItemMeta meta, int tierSpeed, int tierRange, int tierCapacity,
                                    String displayName) {
        MessagesConfig msg = Collector.getInstance().getPluginManager().getMessages();
        String name = (displayName == null || displayName.isBlank()) ? "Collector" : displayName;
        meta.displayName(msg.getRaw("items.collector.name",
            "name", name,
            "tier_speed", tierSpeed, "tier_range", tierRange, "tier_capacity", tierCapacity)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : msg.getRawStringList("items.collector.lore",
                "name", name,
                "tier_speed", tierSpeed, "tier_range", tierRange, "tier_capacity", tierCapacity)) {
            lore.add(Utils.colorize(line)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
    }
}
