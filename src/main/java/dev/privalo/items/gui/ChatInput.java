package dev.privalo.items.gui;

import dev.privalo.items.RadiantItems;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** One-shot chat prompts used by the editor ("cancelar" aborts, 2 minute timeout). */
public final class ChatInput implements Listener {

    private record Pending(Consumer<String> onInput, Runnable onCancel, long expires) {
    }

    private final RadiantItems plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInput(RadiantItems plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, String prompt, Consumer<String> onInput, Runnable onCancel) {
        pending.put(player.getUniqueId(), new Pending(onInput, onCancel, System.currentTimeMillis() + 120_000));
        plugin.messages().send(player, "chat-prompt", "prompt", prompt);
    }

    public void cancel(Player player) {
        pending.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending request = pending.remove(player.getUniqueId());
        if (request == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.originalMessage()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (System.currentTimeMillis() > request.expires()) {
                plugin.messages().send(player, "chat-timeout");
                request.onCancel().run();
                return;
            }
            if (text.equalsIgnoreCase("cancelar") || text.equalsIgnoreCase("cancel")) {
                plugin.messages().send(player, "chat-cancelled");
                request.onCancel().run();
                return;
            }
            try {
                request.onInput().accept(text);
            } catch (IllegalArgumentException ex) {
                plugin.messages().send(player, "chat-invalid", "error", ex.getMessage());
                request.onCancel().run();
            }
        });
    }
}
