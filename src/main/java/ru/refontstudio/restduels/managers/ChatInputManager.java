package ru.refontstudio.restduels.managers;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.refontstudio.restduels.RestDuels;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ChatInputManager implements Listener {
    private final RestDuels plugin;
    private final Map<UUID, Consumer<String>> chatCallbacks = new HashMap<>();

    public ChatInputManager(RestDuels plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void awaitChatInput(Player player, Consumer<String> callback) {
        chatCallbacks.put(player.getUniqueId(), callback);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (chatCallbacks.containsKey(playerId)) {
            event.setCancelled(true);
            String message = event.getMessage();

            // Выполняем колбэк в основном потоке
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Consumer<String> callback = chatCallbacks.remove(playerId);
                if (callback != null) {
                    callback.accept(message);
                }
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Очищаем колбэки при выходе игрока
        chatCallbacks.remove(event.getPlayer().getUniqueId());
    }
}