package xyz.bugsum.collector.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import xyz.bugsum.collector.gui.AnvilInputGUI;

public class ChatInputListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        if (!AnvilInputGUI.hasPending(p)) return;
        String msg = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (AnvilInputGUI.handleInput(p, msg)) {
            event.setCancelled(true);
        }
    }
}
