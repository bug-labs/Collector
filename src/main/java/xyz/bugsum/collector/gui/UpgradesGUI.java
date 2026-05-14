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
import xyz.bugsum.collector.managers.UpgradeManager;
import xyz.bugsum.collector.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/** 3-row sub-GUI: SPEED, RANGE, CAPACITY upgrade buttons + BACK. */
public final class UpgradesGUI {

    public static final int SLOT_SPEED    = 11;
    public static final int SLOT_RANGE    = 13;
    public static final int SLOT_CAPACITY = 15;
    public static final int SLOT_BACK     = 22;

    private UpgradesGUI() {}

    public static void open(Player player, CollectorData data) {
        Collector plugin = Collector.getInstance();
        GUIHolder holder = new GUIHolder(GUIHolder.Type.UPGRADE, data);
        Component title = Utils.colorize(plugin.getPluginManager().getGui().getUpgradesTitle());
        holder.create(27, title);
        render(holder.getInventory(), data, player);
        holder.open(player);
    }

    public static void refresh(Player player, CollectorData data) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof GUIHolder h)) return;
        if (h.getType() != GUIHolder.Type.UPGRADE) return;
        render(h.getInventory(), data, player);
    }

    public static void render(Inventory inv, CollectorData data, Player viewer) {
        Collector plugin = Collector.getInstance();
        GuiConfig gui = plugin.getPluginManager().getGui();
        ItemStack filler = MainGUI.filler(gui);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        renderTrack(inv, gui, "upgrades.speed", SLOT_SPEED, UpgradeManager.Track.SPEED, data, viewer, "collector.upgrade.speed");
        renderTrack(inv, gui, "upgrades.range", SLOT_RANGE, UpgradeManager.Track.RANGE, data, viewer, "collector.upgrade.range");
        renderTrack(inv, gui, "upgrades.capacity", SLOT_CAPACITY, UpgradeManager.Track.CAPACITY, data, viewer, "collector.upgrade.capacity");
        renderBack(inv, gui, SLOT_BACK);
    }

    private static void renderTrack(Inventory inv, GuiConfig gui, String key, int slot,
                                    UpgradeManager.Track track, CollectorData data,
                                    Player viewer, String permission) {
        GuiConfig.GuiButton btn = gui.getButton(key);
        if (btn == null) return;
        UpgradeManager upgrades = Collector.getInstance().getPluginManager().getUpgrades();

        int tier = upgrades.currentTier(data, track);
        int max = upgrades.maxTier(track);
        int value = upgrades.effectValue(track, tier);
        boolean atMax = tier >= max;
        double nextPrice = atMax ? 0 : upgrades.priceFor(track, tier + 1);

        ItemStack item = new ItemStack(btn.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Utils.colorize(replace(btn.getName(),
                    "tier", tier, "max", max, "value", value, "next_price", formatPrice(nextPrice)))
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String l : btn.getLore()) {
                lore.add(Utils.colorize(replace(l,
                    "tier", tier, "max", max, "value", value, "next_price", formatPrice(nextPrice)))
                    .decoration(TextDecoration.ITALIC, false));
            }
            if (atMax) lore.add(Utils.colorize("<gold>Maxed</gold>")
                .decoration(TextDecoration.ITALIC, false));
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

    private static String formatPrice(double d) {
        if (d == Math.floor(d)) return String.format("%,d", (long) d);
        return String.format("%,.2f", d);
    }

    private static String replace(String s, Object... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            s = s.replace("<" + pairs[i] + ">", String.valueOf(pairs[i + 1]));
        }
        return s;
    }
}
