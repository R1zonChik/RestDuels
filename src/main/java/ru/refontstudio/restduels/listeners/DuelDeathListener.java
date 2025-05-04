package ru.refontstudio.restduels.listeners;

import org.bukkit.Bukkit;
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

public class DuelDeathListener implements Listener {
    private final RestDuels plugin;
    private final Map<UUID, Location> pendingRespawns = new HashMap<>();
    private final Map<UUID, Long> lastRespawnTime = new HashMap<>();

    public DuelDeathListener(RestDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        // Проверяем, находится ли игрок в состоянии дуэли
        if (!plugin.getDuelManager().isPlayerInDuel(playerId)) {
            // Не в дуэли? Тогда игнорируем — пусть работают другие плагины (например, спавн)
            return;
        }

        if (!plugin.getConfig().getStringList("worlds.duel-worlds").contains(
                player.getWorld().getName().toLowerCase())) {
            return;
        }

        Location originalLocation = plugin.getDuelManager().getOriginalLocation(playerId);
        if (originalLocation != null && originalLocation.getWorld() != null) {
            pendingRespawns.put(playerId, originalLocation);

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Игрок " + player.getName() + " умер, запланирована телепортация на " +
                        originalLocation.getWorld().getName() + " после респавна");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Проверяем, есть ли запланированный респавн
        if (pendingRespawns.containsKey(playerId)) {
            Location respawnLocation = pendingRespawns.get(playerId);

            // Проверяем, что локация не в мире дуэлей
            if (!isInDuelWorld(respawnLocation)) {
                // Устанавливаем локацию респавна напрямую
                event.setRespawnLocation(respawnLocation);

                // Запоминаем время респавна
                lastRespawnTime.put(playerId, System.currentTimeMillis());

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Установлена локация респавна для игрока " + player.getName() +
                            " на " + respawnLocation.getWorld().getName());
                }

                // Удаляем запись, т.к. мы уже обработали респавн
                pendingRespawns.remove(playerId);
            } else {
                // Локация в мире дуэлей - используем спавн основного мира
                // Но НЕ делаем вторичную телепортацию!
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Локация респавна в мире дуэлей, используем стандартный респавн");
                }
                pendingRespawns.remove(playerId);
            }
        }
    }

    /**
     * Проверяет, находится ли локация в мире дуэлей
     * @param location Локация для проверки
     * @return true, если локация в мире дуэлей
     */
    private boolean isInDuelWorld(Location location) {
        if (location == null || location.getWorld() == null) return false;

        String worldName = location.getWorld().getName().toLowerCase();
        return plugin.getConfig().getStringList("worlds.duel-worlds").contains(worldName);
    }
}