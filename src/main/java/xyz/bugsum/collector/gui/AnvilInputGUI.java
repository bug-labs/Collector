package xyz.bugsum.collector.gui;

import org.bukkit.entity.Player;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.utils.Utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Lightweight chat-based text input. Reliable across Paper versions without NMS / packet
 * trickery. Player closes the open inventory and is prompted to type the value (or
 * "cancel" to abort) within {@link #TIMEOUT_TICKS}; the callback fires on the main thread.
 */
public final class AnvilInputGUI {

    private static final long TIMEOUT_TICKS = 30 * 20L; // 30 s

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private AnvilInputGUI() {}

    public static void prompt(Player player, String title, String defaultValue, Consumer<String> onSubmit) {
        Collector plugin = Collector.getInstance();
        UUID id = player.getUniqueId();

        // Cancel any prior prompt for this player.
        Pending prev = PENDING.remove(id);
        if (prev != null) plugin.getServer().getScheduler().cancelTask(prev.timeoutTaskId);

        player.closeInventory();
        player.sendMessage(Utils.colorize("<gray>━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(Utils.colorize("<yellow><b>" + title + "</b>"));
        if (defaultValue != null && !defaultValue.isBlank()) {
            player.sendMessage(Utils.colorize("<gray>Current: <white>" + defaultValue));
        }
        player.sendMessage(Utils.colorize("<gray>Type your input in chat, or <red>cancel</red> to abort."));
        player.sendMessage(Utils.colorize("<gray>━━━━━━━━━━━━━━━━━━━━━"));

        int taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Pending removed = PENDING.remove(id);
            if (removed != null) {
                player.sendMessage(Utils.colorize("<red>Input timed out."));
            }
        }, TIMEOUT_TICKS).getTaskId();

        PENDING.put(id, new Pending(onSubmit, taskId));
    }

    /** Called by the chat listener. Returns true if the chat message was consumed. */
    public static boolean handleInput(Player player, String message) {
        Pending pending = PENDING.remove(player.getUniqueId());
        if (pending == null) return false;
        Collector plugin = Collector.getInstance();
        plugin.getServer().getScheduler().cancelTask(pending.timeoutTaskId);

        String input = message == null ? "" : message;
        boolean cancel = input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("exit");
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (cancel) {
                player.sendMessage(Utils.colorize("<gray>Cancelled."));
            } else {
                pending.callback.accept(input);
            }
        });
        return true;
    }

    public static boolean hasPending(Player player) {
        return PENDING.containsKey(player.getUniqueId());
    }

    private record Pending(Consumer<String> callback, int timeoutTaskId) {}
}
