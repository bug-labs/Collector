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

/** 3-row sub-GUI: shows stored XP and lets the owner withdraw a level / withdraw all. */
public final class XpGUI {

    public static final int SLOT_INFO       = 13;
    public static final int SLOT_WITHDRAW_1 = 11;
    public static final int SLOT_WITHDRAW_ALL = 15;
    public static final int SLOT_BACK       = 22;

    private XpGUI() {}

    public static void open(Player player, CollectorData data) {
        Collector plugin = Collector.getInstance();
        GUIHolder holder = new GUIHolder(GUIHolder.Type.XP, data);
        Component title = Utils.colorize(plugin.getPluginManager().getGui().getXpTitle());
        holder.create(27, title);
        render(holder.getInventory(), data, player);
        holder.open(player);
    }

    public static void refresh(Player player, CollectorData data) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof GUIHolder h)) return;
        if (h.getType() != GUIHolder.Type.XP) return;
        render(h.getInventory(), data, player);
    }

    public static void render(Inventory inv, CollectorData data, Player viewer) {
        Collector plugin = Collector.getInstance();
        GuiConfig gui = plugin.getPluginManager().getGui();
        ItemStack filler = MainGUI.filler(gui);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        renderXpButton(inv, gui, "xp.info",          SLOT_INFO,         data, viewer, null);
        renderXpButton(inv, gui, "xp.withdraw-level",SLOT_WITHDRAW_1,   data, viewer, "collector.xp.withdraw");
        renderXpButton(inv, gui, "xp.withdraw-all",  SLOT_WITHDRAW_ALL, data, viewer, "collector.xp.withdraw");
        renderBack(inv, gui, SLOT_BACK);
    }

    private static void renderXpButton(Inventory inv, GuiConfig gui, String key, int slot,
                                       CollectorData data, Player viewer, String permission) {
        GuiConfig.GuiButton btn = gui.getButton(key);
        if (btn == null) return;
        ItemStack item = new ItemStack(btn.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String xp = MainGUI.formatNumber(data.getXpStored());
            meta.displayName(Utils.colorize(btn.getName().replace("<xp>", xp))
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String l : btn.getLore()) {
                lore.add(Utils.colorize(l.replace("<xp>", xp))
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
