package ru.refontstudio.restduels.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import ru.refontstudio.restduels.RestDuels;

public class FreezeListener implements Listener {
    private final RestDuels plugin;

    public FreezeListener(RestDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Проверяем, заморожен ли игрок
        if (plugin.getDuelManager().isPlayerFrozen(player.getUniqueId())) {
            // Разрешаем только вращение головы, но не движение
            if (event.getFrom().getX() != event.getTo().getX() ||
                    event.getFrom().getY() != event.getTo().getY() ||
                    event.getFrom().getZ() != event.getTo().getZ()) {

                // Отменяем движение
                event.setTo(event.getFrom());
            }
        }
    }
}