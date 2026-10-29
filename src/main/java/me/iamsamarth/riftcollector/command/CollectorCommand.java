package me.iamsamarth.riftcollector.command;

import me.iamsamarth.riftcollector.RiftCollector;
import me.iamsamarth.riftcollector.collector.CollectorItem;
import me.iamsamarth.riftcollector.lang.Lang;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CollectorCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_AMOUNT = 64;

    private final RiftCollector plugin;
    private final CollectorItem collectorItem;
    private final Lang lang;

    public CollectorCommand(RiftCollector plugin, CollectorItem collectorItem, Lang lang) {
        this.plugin = plugin;
        this.collectorItem = collectorItem;
        this.lang = lang;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("give")) {
            return give(sender, args);
        }
        if (args[0].equalsIgnoreCase("reload")) {
            return reload(sender);
        }
        lang.send(sender, "command.usage",
                Placeholder.unparsed("usage", "/" + label + " [give <player> [amount]|reload]"));
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("riftcollector.give")) {
            lang.send(sender, "command.no-permission");
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                lang.send(sender, "command.player-not-online", Placeholder.unparsed("name", args[1]));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            lang.send(sender, "command.specify-player");
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                lang.send(sender, "command.not-a-number", Placeholder.unparsed("value", args[2]));
                return true;
            }
            if (amount < 1 || amount > MAX_AMOUNT) {
                lang.send(sender, "command.amount-out-of-range",
                        Placeholder.unparsed("min", "1"),
                        Placeholder.unparsed("max", Integer.toString(MAX_AMOUNT)));
                return true;
            }
        }

        ItemStack item = collectorItem.create(amount, plugin.config().item().material());
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
        leftover.values().forEach(stack -> target.getWorld().dropItemNaturally(target.getLocation(), stack));

        lang.send(target, "command.received", Placeholder.unparsed("amount", Integer.toString(amount)));
        if (!sender.equals(target)) {
            lang.send(sender, "command.given",
                    Placeholder.unparsed("amount", Integer.toString(amount)),
                    Placeholder.unparsed("player", target.getName()));
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("riftcollector.reload")) {
            lang.send(sender, "command.no-permission");
            return true;
        }
        plugin.reload();
        lang.send(sender, "command.reloaded");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("give", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("1", "8", "16", "32", "64"), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowered)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
