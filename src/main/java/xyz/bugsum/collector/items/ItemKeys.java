package xyz.bugsum.collector.items;

import lombok.Getter;
import org.bukkit.NamespacedKey;
import xyz.bugsum.collector.Collector;

public final class ItemKeys {

    @Getter private static NamespacedKey isCollector;
    @Getter private static NamespacedKey isCollectorChest;
    @Getter private static NamespacedKey owner;
    @Getter private static NamespacedKey tierSpeed;
    @Getter private static NamespacedKey tierRange;
    @Getter private static NamespacedKey tierCapacity;
    @Getter private static NamespacedKey autosellEnabled;
    @Getter private static NamespacedKey autosmeltEnabled;
    @Getter private static NamespacedKey filterMode;
    @Getter private static NamespacedKey filterItems;
    @Getter private static NamespacedKey itemsCollected;
    @Getter private static NamespacedKey moneyEarned;
    @Getter private static NamespacedKey linkedCollector;
    @Getter private static NamespacedKey guiType;
    @Getter private static NamespacedKey displayName;
    /** Identifies a TextDisplay we own. Value = serialized Collector location it belongs to. */
    @Getter private static NamespacedKey hologramOwner;

    private ItemKeys() {}

    public static void init(Collector plugin) {
        isCollector       = new NamespacedKey(plugin, "is_collector");
        isCollectorChest  = new NamespacedKey(plugin, "is_collector_chest");
        owner             = new NamespacedKey(plugin, "owner");
        tierSpeed         = new NamespacedKey(plugin, "tier_speed");
        tierRange         = new NamespacedKey(plugin, "tier_range");
        tierCapacity      = new NamespacedKey(plugin, "tier_capacity");
        autosellEnabled   = new NamespacedKey(plugin, "autosell");
        autosmeltEnabled  = new NamespacedKey(plugin, "autosmelt");
        filterMode        = new NamespacedKey(plugin, "filter_mode");
        filterItems       = new NamespacedKey(plugin, "filter_items");
        itemsCollected    = new NamespacedKey(plugin, "items_collected");
        moneyEarned       = new NamespacedKey(plugin, "money_earned");
        linkedCollector   = new NamespacedKey(plugin, "linked_collector");
        guiType           = new NamespacedKey(plugin, "gui_type");
        displayName       = new NamespacedKey(plugin, "display_name");
        hologramOwner     = new NamespacedKey(plugin, "hologram_owner");
    }
}
