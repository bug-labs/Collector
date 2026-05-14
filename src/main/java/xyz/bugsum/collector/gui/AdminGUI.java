package xyz.bugsum.collector.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game config editor for owners with {@code collector.admin}. Each leaf button cycles
 * through valid values on left-click, or opens an Anvil text-input on shift-click.
 * Changes are persisted via {@link org.bukkit.plugin.Plugin#saveConfig()} and
 * {@link xyz.bugsum.collector.managers.PluginManager#reloadConfigs()}.
 */
public final class AdminGUI {

    private AdminGUI() {}

    private static final NamespacedKey PATH_KEY =
        new NamespacedKey("collector", "admin_path");
    private static final NamespacedKey KIND_KEY =
        new NamespacedKey("collector", "admin_kind");

    public enum Kind { BOOLEAN, INTEGER, DOUBLE, STRING, ENUM_LANGUAGE }

    public static void open(Player player) {
        Holder holder = new Holder();
        Component title = Utils.colorize("<gold><b>⚙ Collector Admin</b>");
        holder.inv = Bukkit.createInventory(holder, 36, title);

        ItemStack filler = MainGUI.filler(
            Collector.getInstance().getPluginManager().getGui());
        for (int i = 0; i < 36; i++) holder.inv.setItem(i, filler);

        // Row 1: General
        holder.inv.setItem(10, button(Material.PAPER, "<yellow>Language",
            "language", Kind.ENUM_LANGUAGE));
        holder.inv.setItem(11, button(Material.IRON_INGOT, "<yellow>Max Collectors / Player",
            "limits.max-collectors-per-player", Kind.INTEGER));
        holder.inv.setItem(12, button(Material.OAK_DOOR, "<yellow>One Per Chunk",
            "limits.one-per-chunk", Kind.BOOLEAN));
        holder.inv.setItem(13, button(Material.EXPERIENCE_BOTTLE, "<yellow>XP Collection",
            "collection.xp-enabled", Kind.BOOLEAN));
        holder.inv.setItem(14, button(Material.ARMOR_STAND, "<yellow>Holograms Enabled",
            "hologram.enabled", Kind.BOOLEAN));
        holder.inv.setItem(15, button(Material.CLOCK, "<yellow>Hologram Refresh Ticks",
            "hologram.refresh-interval-ticks", Kind.INTEGER));
        holder.inv.setItem(16, button(Material.COMPARATOR, "<yellow>Debug Logging",
            "debug", Kind.BOOLEAN));

        // Close
        holder.inv.setItem(31, closeBtn());

        player.openInventory(holder.inv);
    }

    private static ItemStack button(Material mat, String name, String path, Kind kind) {
        Object current = Collector.getInstance().getConfig().get(path);
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Utils.colorize(name).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Utils.colorize("<dark_gray>━━━━━━━━━━━━━━━━━━").decoration(TextDecoration.ITALIC, false));
            lore.add(Utils.colorize("<gray>Path:    <white>" + path).decoration(TextDecoration.ITALIC, false));
            lore.add(Utils.colorize("<gray>Current: <green>" + String.valueOf(current))
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Utils.colorize("<gold>▶ <yellow>Left-click <gray>to cycle")
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Utils.colorize("<gold>▶ <yellow>Shift-click <gray>to type a value")
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Utils.colorize("<dark_gray>━━━━━━━━━━━━━━━━━━").decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(PATH_KEY, PersistentDataType.STRING, path);
            meta.getPersistentDataContainer().set(KIND_KEY, PersistentDataType.STRING, kind.name());
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack closeBtn() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Utils.colorize("<red><b>Close").decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(PATH_KEY, PersistentDataType.STRING, "__close__");
            it.setItemMeta(meta);
        }
        return it;
    }

    public static void handleClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        ItemStack it = event.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String path = it.getItemMeta().getPersistentDataContainer()
            .get(PATH_KEY, PersistentDataType.STRING);
        if (path == null) return;
        if ("__close__".equals(path)) { p.closeInventory(); return; }
        String kindStr = it.getItemMeta().getPersistentDataContainer()
            .get(KIND_KEY, PersistentDataType.STRING);
        Kind kind = kindStr == null ? Kind.STRING : Kind.valueOf(kindStr);

        boolean shift = event.isShiftClick();
        Collector plugin = Collector.getInstance();
        if (shift) {
            String current = String.valueOf(plugin.getConfig().get(path));
            AnvilInputGUI.prompt(p, "Set " + path, current, input -> {
                applyValue(path, kind, input);
                Bukkit.getScheduler().runTask(plugin, () -> open(p));
            });
            return;
        }
        // Cycle current value
        Object current = plugin.getConfig().get(path);
        Object next = cycle(current, kind);
        plugin.getConfig().set(path, next);
        plugin.saveConfig();
        plugin.getPluginManager().applyRuntimeChanges();
        open(p); // re-render with updated value
    }

    private static void applyValue(String path, Kind kind, String input) {
        Collector plugin = Collector.getInstance();
        try {
            Object parsed = switch (kind) {
                case BOOLEAN -> Boolean.parseBoolean(input);
                case INTEGER -> Integer.parseInt(input.trim());
                case DOUBLE  -> Double.parseDouble(input.trim());
                case ENUM_LANGUAGE, STRING -> input.trim();
            };
            plugin.getConfig().set(path, parsed);
            plugin.saveConfig();
            plugin.getPluginManager().applyRuntimeChanges();
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Admin GUI: invalid input '" + input + "' for " + path);
        }
    }

    private static Object cycle(Object current, Kind kind) {
        return switch (kind) {
            case BOOLEAN -> !(current instanceof Boolean b ? b : false);
            case INTEGER -> {
                int v = current instanceof Number n ? n.intValue() : 0;
                // Cycle 0 → 5 → 10 → 25 → 50 → 100 → 0
                int[] steps = {0, 5, 10, 25, 50, 100};
                int idx = 0;
                for (int i = 0; i < steps.length; i++) if (steps[i] == v) idx = i;
                yield steps[(idx + 1) % steps.length];
            }
            case DOUBLE -> {
                double v = current instanceof Number n ? n.doubleValue() : 0d;
                double[] steps = {0.5, 1.0, 2.0, 4.0, 6.0, 8.0, 12.0, 16.0, 18.5, 19.5};
                int idx = 0;
                for (int i = 0; i < steps.length; i++) if (Math.abs(steps[i] - v) < 0.01) idx = i;
                yield steps[(idx + 1) % steps.length];
            }
            case ENUM_LANGUAGE -> {
                String[] langs = {"en", "de", "es", "fr"};
                String v = String.valueOf(current);
                int idx = 0;
                for (int i = 0; i < langs.length; i++) if (langs[i].equalsIgnoreCase(v)) idx = i;
                yield langs[(idx + 1) % langs.length];
            }
            case STRING -> current;
        };
    }

    /** Tag inventory holder so the listener can match by type. */
    public static class Holder implements InventoryHolder {
        Inventory inv;
        @Override public @NotNull Inventory getInventory() { return inv; }
    }
}
