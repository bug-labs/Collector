package xyz.bugsum.collector.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.config.MessagesConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Appends a "no permission" footer to a GUI button's lore when the viewer lacks the
 * given permission. The lore content is fully customizable via {@code lang/<x>.yml} key
 * {@code gui.no-permission-lore}.
 */
public final class GuiPerm {

    private GuiPerm() {}

    public static void apply(ItemMeta meta, Player viewer, String permission) {
        if (meta == null || viewer == null || permission == null || permission.isEmpty()) return;
        if (viewer.hasPermission(permission)) return;
        MessagesConfig msg = Collector.getInstance().getPluginManager().getMessages();
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        for (String line : msg.getRawStringList("gui.no-permission-lore")) {
            lore.add(Utils.colorize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
    }
}
