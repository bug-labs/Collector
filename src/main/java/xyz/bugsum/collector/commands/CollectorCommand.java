package xyz.bugsum.collector.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.config.MessagesConfig;
import xyz.bugsum.collector.data.CollectorData;
import xyz.bugsum.collector.gui.AdminGUI;
import xyz.bugsum.collector.items.CollectorItemFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class CollectorCommand implements CommandExecutor, TabCompleter {

    private final Collector plugin;

    public CollectorCommand(Collector plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        MessagesConfig msg = plugin.getPluginManager().getMessages();
        if (args.length == 0) {
            sender.sendMessage(msg.get("general.unknown-subcommand"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> handleGive(sender, args, msg);
            case "reload" -> handleReload(sender, msg);
            case "info" -> handleInfo(sender, msg);
            case "wipe" -> handleWipe(sender, msg);
            case "admin" -> handleAdmin(sender, msg);
            case "rename" -> handleRename(sender, args, msg);
            case "friend" -> handleFriend(sender, args, msg);
            default -> sender.sendMessage(msg.get("general.unknown-subcommand"));
        }
        return true;
    }

    private void handleGive(CommandSender sender, String[] args, MessagesConfig msg) {
        if (!sender.hasPermission("collector.give")) {
            sender.sendMessage(msg.get("general.no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(msg.get("general.unknown-subcommand"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(msg.get("general.player-only"));
            return;
        }
        int tier = 1;
        if (args.length >= 3) {
            try { tier = Math.max(1, Integer.parseInt(args[2])); } catch (NumberFormatException ignored) {}
        }
        ItemStack item = CollectorItemFactory.create(tier, tier, tier);
        target.getInventory().addItem(item);
        sender.sendMessage(msg.get("upgrade.success", "track", "Give", "tier", tier));
    }

    private void handleReload(CommandSender sender, MessagesConfig msg) {
        if (!sender.hasPermission("collector.admin")) {
            sender.sendMessage(msg.get("general.no-permission"));
            return;
        }
        plugin.getPluginManager().reloadConfigs();
        sender.sendMessage(msg.get("general.reload-success"));
    }

    private void handleInfo(CommandSender sender, MessagesConfig msg) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(msg.get("general.player-only"));
            return;
        }
        int count = plugin.getPluginManager().getDatabase().countCollectorsByOwner(p.getUniqueId());
        sender.sendMessage(msg.get("upgrade.success",
            "track", "Owned: " + count + " Collectors", "tier", 0));
    }

    private void handleWipe(CommandSender sender, MessagesConfig msg) {
        if (!sender.hasPermission("collector.admin")) {
            sender.sendMessage(msg.get("general.no-permission"));
            return;
        }
        sender.sendMessage(msg.get("general.reload-success"));
    }

    private void handleAdmin(CommandSender sender, MessagesConfig msg) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(msg.get("general.player-only"));
            return;
        }
        if (!p.hasPermission("collector.admin")) {
            p.sendMessage(msg.get("general.no-permission"));
            return;
        }
        AdminGUI.open(p);
    }

    private void handleRename(CommandSender sender, String[] args, MessagesConfig msg) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(msg.get("general.player-only"));
            return;
        }
        if (!p.hasPermission("collector.rename")) {
            p.sendMessage(msg.get("general.no-permission"));
            return;
        }
        Block looking = p.getTargetBlockExact(6);
        if (looking == null || looking.getType() != Material.HOPPER
                || !CollectorItemFactory.isPlacedBlock(looking.getState())) {
            p.sendMessage(msg.get("rename.no-target"));
            return;
        }
        CollectorData data = plugin.getPluginManager().getCollector().getCollector(looking.getLocation());
        if (data == null) return;
        if (!isOwnerOrAdmin(p, data)) { p.sendMessage(msg.get("break.not-owner")); return; }

        if (args.length < 2) { p.sendMessage(msg.get("rename.usage")); return; }
        String newName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (newName.isEmpty() || newName.equalsIgnoreCase("reset") || newName.equalsIgnoreCase("Collector")) {
            data.setDisplayName(null);
        } else {
            data.setDisplayName(newName.length() > 32 ? newName.substring(0, 32) : newName);
        }
        data.markDirty();
        plugin.getPluginManager().getDatabase().enqueueSave(data);
        plugin.getPluginManager().getHolograms().updateText(data);
        p.sendMessage(msg.get("rename.success",
            "name", data.getDisplayName() == null ? "Collector" : data.getDisplayName()));
    }

    private void handleFriend(CommandSender sender, String[] args, MessagesConfig msg) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(msg.get("general.player-only"));
            return;
        }
        if (!p.hasPermission("collector.friend.manage")) {
            p.sendMessage(msg.get("general.no-permission"));
            return;
        }
        Block looking = p.getTargetBlockExact(6);
        if (looking == null || looking.getType() != Material.HOPPER
                || !CollectorItemFactory.isPlacedBlock(looking.getState())) {
            p.sendMessage(msg.get("friend.no-target"));
            return;
        }
        CollectorData data = plugin.getPluginManager().getCollector().getCollector(looking.getLocation());
        if (data == null) return;
        if (!isOwnerOrAdmin(p, data)) { p.sendMessage(msg.get("break.not-owner")); return; }
        if (args.length < 2) { p.sendMessage(msg.get("friend.usage")); return; }

        String action = args[1].toLowerCase();
        if (action.equals("list")) {
            if (data.getFriends().isEmpty()) { p.sendMessage(msg.get("friend.empty")); return; }
            p.sendMessage(msg.get("friend.list-header", "count", data.getFriends().size()));
            for (UUID id : data.getFriends()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                p.sendMessage(msg.get("friend.list-entry",
                    "name", op.getName() == null ? id.toString() : op.getName()));
            }
            return;
        }
        if (args.length < 3) { p.sendMessage(msg.get("friend.usage")); return; }
        String targetName = args[2];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getUniqueId() == null) {
            p.sendMessage(msg.get("friend.unknown", "name", targetName));
            return;
        }
        switch (action) {
            case "add" -> {
                if (plugin.getPluginManager().getFriends().add(data, target.getUniqueId())) {
                    p.sendMessage(msg.get("friend.added", "name", targetName));
                } else {
                    p.sendMessage(msg.get("friend.already", "name", targetName));
                }
            }
            case "remove" -> {
                if (plugin.getPluginManager().getFriends().remove(data, target.getUniqueId())) {
                    p.sendMessage(msg.get("friend.removed", "name", targetName));
                } else {
                    p.sendMessage(msg.get("friend.not-friend", "name", targetName));
                }
            }
            default -> p.sendMessage(msg.get("friend.usage"));
        }
    }

    private boolean isOwnerOrAdmin(Player p, CollectorData data) {
        return (data.getOwner() != null && data.getOwner().equals(p.getUniqueId()))
            || p.hasPermission("collector.admin");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("give", "reload", "info", "wipe",
                "admin", "rename", "friend"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(Arrays.asList("1", "2", "3", "4", "5"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("friend")) {
            return filter(Arrays.asList("add", "remove", "list"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("friend")
                && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream()
            .filter(o -> o.toLowerCase().startsWith(prefix.toLowerCase()))
            .collect(Collectors.toList());
    }
}
