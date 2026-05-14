package xyz.bugsum.collector.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.config.GuiConfig;
import xyz.bugsum.collector.data.CollectorData;
import xyz.bugsum.collector.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * 3-row Settings sub-GUI. Layout:
 * <pre>
 * Row 0: F F F F F F F F F
 * Row 1: AS ASm XP HOL REN FRD FLT . F      (slots 9..16)
 * Row 2: F F F F BACK F F F F               (slot 22)
 * </pre>
 * Despite the file name, this is the consolidated Settings menu — all per-Collector
 * configuration lives here.
 */
public final class TogglesGUI {

    public static final int SLOT_AUTOSELL  = 10;
    public static final int SLOT_AUTOSMELT = 11;
    public static final int SLOT_XP        = 12;
    public static final int SLOT_HOLOGRAM  = 13;
    public static final int SLOT_RENAME    = 14;
    public static final int SLOT_FRIENDS   = 15;
    public static final int SLOT_FILTER    = 16;
    public static final int SLOT_BACK      = 22;

    private TogglesGUI() {}

    public static void open(Player player, CollectorData data) {
        Collector plugin = Collector.getInstance();
        GUIHolder holder = new GUIHolder(GUIHolder.Type.TOGGLE, data);
        Component title = Utils.colorize(plugin.getPluginManager().getGui().getTogglesTitle());
        holder.create(27, title);
        render(holder.getInventory(), data, player);
        holder.open(player);
    }

    public static void refresh(Player player, CollectorData data) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof GUIHolder h)) return;
        if (h.getType() != GUIHolder.Type.TOGGLE) return;
        render(h.getInventory(), data, player);
    }

    public static void render(Inventory inv, CollectorData data, Player viewer) {
        Collector plugin = Collector.getInstance();
        GuiConfig gui = plugin.getPluginManager().getGui();
        ItemStack filler = MainGUI.filler(gui);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        renderToggle(inv, gui, "settings.autosell",  SLOT_AUTOSELL,  data.isAutosell(),       viewer, "collector.toggle.autosell");
        renderToggle(inv, gui, "settings.autosmelt", SLOT_AUTOSMELT, data.isAutosmelt(),      viewer, "collector.toggle.autosmelt");
        renderToggle(inv, gui, "settings.xp",        SLOT_XP,        data.isXpCollect(),      viewer, "collector.toggle.xp");
        renderToggle(inv, gui, "settings.hologram",  SLOT_HOLOGRAM,  data.isHologramVisible(),viewer, "collector.toggle.hologram");
        renderInfo  (inv, gui, "settings.rename",    SLOT_RENAME,    data,                    viewer, "collector.rename");
        renderInfo  (inv, gui, "settings.friends",   SLOT_FRIENDS,   data,                    viewer, "collector.friend.manage");
        renderInfo  (inv, gui, "settings.filter",    SLOT_FILTER,    data,                    viewer, "collector.filter");
        renderBack(inv, gui, SLOT_BACK);
    }

    private static void renderToggle(Inventory inv, GuiConfig gui, String key, int slot,
                                     boolean enabled, Player viewer, String permission) {
        GuiConfig.GuiButton btn = gui.getButton(key);
        if (btn == null) return;
        ItemStack item = new ItemStack(btn.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String enabledText = enabled ? "<green>ᴏɴ</green>" : "<red>ᴏғғ</red>";
            meta.displayName(Utils.colorize(btn.getName().replace("<enabled>", enabledText))
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String l : btn.getLore()) {
                lore.add(Utils.colorize(l.replace("<enabled>", enabledText))
                    .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            xyz.bugsum.collector.utils.GuiPerm.apply(meta, viewer, permission);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    private static void renderInfo(Inventory inv, GuiConfig gui, String key, int slot,
                                   CollectorData data, Player viewer, String permission) {
        GuiConfig.GuiButton btn = gui.getButton(key);
        if (btn == null) return;
        ItemStack item = new ItemStack(btn.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String currentName = data.getDisplayName() == null || data.getDisplayName().isBlank()
                ? "Collector" : data.getDisplayName();
            String mode = data.getFilter().getMode().name();
            meta.displayName(Utils.colorize(btn.getName()
                    .replace("<friends>", String.valueOf(data.getFriends().size()))
                    .replace("<name>", currentName)
                    .replace("<mode>", mode))
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String l : btn.getLore()) {
                lore.add(Utils.colorize(l
                        .replace("<friends>", String.valueOf(data.getFriends().size()))
                        .replace("<name>", currentName)
                        .replace("<mode>", mode))
                    .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            xyz.bugsum.collector.utils.GuiPerm.apply(meta, viewer, permission);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    private static void renderBack(Inventory inv, GuiConfig gui, int slot) {
        GuiConfig.GuiButton btn = gui.getButton("common.back");
        if (btn == null) return;
        ItemStack item = new ItemStack(btn.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Utils.colorize(btn.getName()).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String l : btn.getLore()) {
                lore.add(Utils.colorize(l).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }
}
