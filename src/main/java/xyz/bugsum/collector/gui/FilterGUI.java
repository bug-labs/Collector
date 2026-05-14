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
import xyz.bugsum.collector.data.CollectorData;
import xyz.bugsum.collector.data.FilterData;
import xyz.bugsum.collector.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public final class FilterGUI {

    /** Slots reserved for the bottom control row. */
    public static final int CONTROL_ROW_START = 45;

    private FilterGUI() {}

    public static void open(Player player, CollectorData data) {
        Collector plugin = Collector.getInstance();
        GuiConfig gui = plugin.getPluginManager().getGui();

        GUIHolder holder = new GUIHolder(GUIHolder.Type.FILTER, data);
        Component title = Utils.colorize(gui.getFilterTitle());
        holder.create(gui.getFilterRows() * 9, title);

        Inventory inv = holder.getInventory();
        int i = 0;
        for (Material m : data.getFilter().getItems()) {
            if (i >= CONTROL_ROW_START) break;
            inv.setItem(i++, new ItemStack(m));
        }
        renderControls(inv, gui, data.getFilter());
        holder.open(player);
    }

    public static void renderControls(Inventory inv, GuiConfig gui, FilterData filter) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) {
            paneMeta.displayName(Component.text(" "));
            pane.setItemMeta(paneMeta);
        }
        for (int s = CONTROL_ROW_START; s < inv.getSize(); s++) {
            inv.setItem(s, pane);
        }

        GuiConfig.GuiButton btn = gui.getFilterModeButton();
        if (btn != null) {
            ItemStack item = new ItemStack(btn.getMaterial());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Utils.colorize(btn.getName().replace("<mode>", filter.getMode().name()))
                    .decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                for (String l : btn.getLore()) {
                    lore.add(Utils.colorize(l.replace("<mode>", filter.getMode().name()))
                        .decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(btn.getSlot(), item);
        }
    }

    public static void persist(Inventory inv, FilterData filter) {
        filter.getItems().clear();
        for (int s = 0; s < CONTROL_ROW_START && s < inv.getSize(); s++) {
            ItemStack stack = inv.getItem(s);
            if (stack != null && stack.getType() != Material.AIR) {
                filter.getItems().add(stack.getType());
            }
        }
    }
}
