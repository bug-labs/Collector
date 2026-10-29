package me.iamsamarth.riftcollector.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Asks a player to type something in chat, used where a menu cannot take text. The message is
 * swallowed rather than broadcast, and the answer is handed back on the main thread.
 */
public final class ChatPrompt implements Listener {

    private static final String CANCEL = "cancel";

    private final Plugin plugin;
    private final Map<UUID, Consumer<String>> pending = new HashMap<>();

    public ChatPrompt(Plugin plugin) {
        this.plugin = plugin;
    }

    /** The handler receives the typed line, or null when the player cancels. */
    public void request(Player player, Consumer<String> handler) {
        pending.put(player.getUniqueId(), handler);
    }

    public void cancel(Player player) {
        pending.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Consumer<String> handler = pending.remove(event.getPlayer().getUniqueId());
        if (handler == null) {
            return;
        }
        event.setCancelled(true);

        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        String answer = message.equalsIgnoreCase(CANCEL) ? null : message;
        // Chat arrives off the main thread, and everything the handler touches is main-thread work.
        plugin.getServer().getScheduler().runTask(plugin, () -> handler.accept(answer));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
