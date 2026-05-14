package xyz.bugsum.collector.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.config.GuiConfig;
import xyz.bugsum.collector.config.MessagesConfig;
import xyz.bugsum.collector.data.CollectorData;
import xyz.bugsum.collector.utils.Utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 4-row Main GUI. Layout (slot indices):
 * <pre>
 * Row 0 (0-8):    F F F F F F F F F                  - filler
 * Row 1 (9-17):   PRV F S S S S S F NXT              - storage 11..15
 * Row 2 (18-26):  F F F F F F F F F                  - filler
 * Row 3 (27-35):  F UPG TGL F CLOSE F XP INF F
 * </pre>
 */
public final class MainGUI {

    public static final int[] STORAGE_SLOTS = {11, 12, 13, 14, 15};

    public static final int SLOT_PREV      = 9;
    public static final int SLOT_NEXT      = 17;

    public static final int SLOT_UPGRADES  = 28;
    public static final int SLOT_TOGGLES   = 29;
    public static final int SLOT_CLOSE     = 31;
    public static final int SLOT_XP        = 33;
    public static final int SLOT_INFO      = 34;

    private MainGUI() {}

    public static void open(Player player, CollectorData data) {
        Collector plugin = Collector.getInstance();
        GUIHolder holder = new GUIHolder(GUIHolder.Type.MAIN, data);
        String rawTitle = plugin.getPluginManager().getGui().getMainTitle()
            .replace("<name>", displayName(data));
        Component title = Utils.colorize(rawTitle);
        holder.create(36, title);
        render(holder.getInventory(), data, 0, player);
        holder.setPage(0);
        holder.open(player);
    }

    public static void refresh(Player player, CollectorData data) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof GUIHolder h)) return;
        if (h.getType() != GUIHolder.Type.MAIN) return;
        render(h.getInventory(), data, h.getPage(), player);
    }

    public static void render(Inventory inv, CollectorData data, int page, Player viewer) {
        Collector plugin = Collector.getInstance();
        GuiConfig gui = plugin.getPluginManager().getGui();

        ItemStack filler = filler(gui);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // Storage slots
        List<Map.Entry<Material, Long>> entries = new ArrayList<>(data.getStorage().entrySet());
        entries.sort(Comparator.comparing(e -> e.getKey().name()));
        int perPage = STORAGE_SLOTS.length;
        int totalPages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int start = safePage * perPage;

        for (int i = 0; i < perPage; i++) {
            int idx = start + i;
            if (idx >= entries.size()) {
                inv.setItem(STORAGE_SLOTS[i], emptySlotItem(plugin));
                continue;
            }
            Map.Entry<Material, Long> e = entries.get(idx);
            inv.setItem(STORAGE_SLOTS[i], buildStorageDisplay(plugin, e.getKey(), e.getValue()));
        }

        // Inline prev/next next to storage row
        renderNav(inv, gui, "main.prev", SLOT_PREV, safePage, totalPages);
        renderNav(inv, gui, "main.next", SLOT_NEXT, safePage, totalPages);

        // Bottom row icons
        renderButton(inv, gui, "main.upgrades", SLOT_UPGRADES, data, viewer, "collector.upgrade");
        renderButton(inv, gui, "main.toggles",  SLOT_TOGGLES,  data, viewer, "collector.use");
        renderButton(inv, gui, "main.close",    SLOT_CLOSE,    data, viewer, null);
        renderButton(inv, gui, "main.xp",       SLOT_XP,       data, viewer, "collector.xp");
        renderButton(inv, gui, "main.info",     SLOT_INFO,     data, viewer, null);
    }

    public static ItemStack filler(GuiConfig gui) {
        ItemStack it = new ItemStack(gui.getMainFiller());
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Utils.colorize(gui.getMainFillerName())
                .decoration(TextDecoration.ITALIC, false));
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack emptySlotItem(Collector plugin) {
        MessagesConfig msg = plugin.getPluginManager().getMessages();
        ItemStack it = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(msg.getRaw("storage.empty-slot")
                .decoration(TextDecoration.ITALIC, false));
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack buildStorageDisplay(Collector plugin, Material mat, long amount) {
        MessagesConfig msg = plugin.getPluginManager().getMessages();
        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        String prettyName = prettify(mat.name());
        meta.displayName(msg.getRaw("storage.slot-name",
                "material", prettyName, "amount", formatNumber(amount))
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        for (String line : msg.getRawStringList("storage.slot-lore",
                "material", prettyName, "amount", formatNumber(amount))) {
            lore.add(Utils.colorize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static void renderButton(Inventory inv, GuiConfig gui, String key, int slot,
                                     CollectorData data, Player viewer, String permission) {
        GuiConfig.GuiButton btn = gui.getButton(key);
        if (btn == null) return;
        ItemStack item = new ItemStack(btn.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = applyDataPlaceholders(btn.getName(), data);
            meta.displayName(Utils.colorize(name).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String l : btn.getLore()) {
                lore.add(Utils.colorize(applyDataPlaceholders(l, data))
                    .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            xyz.bugsum.collector.utils.GuiPerm.apply(meta, viewer, permission);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    private static String applyDataPlaceholders(String s, CollectorData data) {
        return s
            .replace("<owner>", ownerName(data))
            .replace("<name>", displayName(data))
            .replace("<friends>", String.valueOf(data.getFriends().size()))
            // <items> = currently stored (decreases on withdraw/sell). Use <lifetime_items>
            // for the cumulative "ever collected" counter.
            .replace("<items>", formatNumber(data.currentItems()))
            .replace("<lifetime_items>", formatNumber(data.getItemsCollected()))
            .replace("<money>", String.format("%,.0f", data.getMoneyEarned()))
            .replace("<xp>", formatNumber(data.getXpStored()))
            .replace("<tier_speed>", String.valueOf(data.getTierSpeed()))
            .replace("<tier_range>", String.valueOf(data.getTierRange()))
            .replace("<tier_capacity>", String.valueOf(data.getTierCapacity()));
    }

    public static String displayName(CollectorData data) {
        String n = data.getDisplayName();
        return (n == null || n.isBlank()) ? "Collector" : n;
    }

    private static void renderNav(Inventory inv, GuiConfig gui, String key, int slot,
                                  int currentPage, int totalPages) {
        GuiConfig.GuiButton btn = gui.getButton(key);
        if (btn == null) return;
        ItemStack item = new ItemStack(btn.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Utils.colorize(btn.getName()
                    .replace("<page>", String.valueOf(currentPage + 1))
                    .replace("<total>", String.valueOf(totalPages)))
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String l : btn.getLore()) {
                lore.add(Utils.colorize(l
                        .replace("<page>", String.valueOf(currentPage + 1))
                        .replace("<total>", String.valueOf(totalPages)))
                    .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    private static String ownerName(CollectorData d) {
        if (d.getOwner() == null) return "Unknown";
        var op = org.bukkit.Bukkit.getOfflinePlayer(d.getOwner());
        return op.getName() == null ? d.getOwner().toString() : op.getName();
    }

    private static String prettify(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        boolean cap = true;
        for (char c : name.toCharArray()) {
            if (c == '_') { sb.append(' '); cap = true; }
            else if (cap) { sb.append(Character.toUpperCase(c)); cap = false; }
            else sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    public static String formatNumber(long n) {
        if (n < 1_000)        return String.valueOf(n);
        if (n < 1_000_000)    return String.format("%.1fK", n / 1_000.0);
        if (n < 1_000_000_000)return String.format("%.1fM", n / 1_000_000.0);
        return String.format("%.1fB", n / 1_000_000_000.0);
    }

    public static int pageCount(CollectorData data) {
        int per = STORAGE_SLOTS.length;
        return Math.max(1, (data.getStorage().size() + per - 1) / per);
    }
}
