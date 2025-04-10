package ru.refontstudio.restduels.listeners;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import ru.refontstudio.restduels.RestDuels;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DuelRespawnListener implements Listener {
    private final RestDuels plugin;
    private final Map<UUID, Location> deathLocations = new HashMap<>();

    public DuelRespawnListener(RestDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        // Сохраняем локацию смерти
        deathLocations.put(playerId, player.getLocation().clone());

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Сохранена локация смерти игрока " + player.getName() +
                    " в мире " + player.getWorld().getName());
        }

        // Если игрок умер в мире дуэлей, отмечаем его для последующей телепортации
        if (isInDuelWorld(player)) {
            plugin.getDuelManager().markPlayerForTeleport(playerId);

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Игрок " + player.getName() + " отмечен для телепортации после респавна");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Проверяем, отмечен ли игрок для телепортации
        if (plugin.getDuelManager().isMarkedForTeleport(playerId)) {
            // Получаем оригинальную локацию
            Location originalLocation = plugin.getDuelManager().getOriginalLocation(playerId);

            if (originalLocation != null && originalLocation.getWorld() != null) {
                // Устанавливаем локацию респавна напрямую
                event.setRespawnLocation(originalLocation);

                // Снимаем отметку
                plugin.getDuelManager().unmarkPlayerForTeleport(playerId);
            }
        }
    }

    /**
     * Проверяет, находится ли игрок в мире дуэлей
     * @param player Игрок для проверки
     * @return true, если игрок в мире дуэлей
     */
    private boolean isInDuelWorld(Player player) {
        String worldName = player.getWorld().getName().toLowerCase();
        return plugin.getConfig().getStringList("worlds.duel-worlds").contains(worldName);
    }
}