package ru.refontstudio.restduels.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DuelJoinListener implements Listener {
    private final RestDuels plugin;
    private final Set<String> duelWorldNames = new HashSet<>();

    public DuelJoinListener(RestDuels plugin) {
        this.plugin = plugin;

        // Загружаем имена миров дуэлей из конфига
        duelWorldNames.addAll(plugin.getConfig().getStringList("worlds.duel-worlds"));

        // Если список пуст, добавляем значение по умолчанию
        if (duelWorldNames.isEmpty()) {
            duelWorldNames.add("duels");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // ДОБАВЛЕНО: Проверяем, не был ли игрок в дуэли при выходе
        String previousStatus = plugin.getDuelManager().loadPlayerDuelStatusFromFile(playerId);
        if (previousStatus != null) {
            // Если игрок вышел во время отсчета или дуэли, убеждаемся, что все данные сброшены
            if (previousStatus.equals("CANCELLED_BY_QUIT")) {
                // Принудительно очищаем все данные дуэли для игрока
                plugin.getDuelManager().cleanupPlayerState(playerId);

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Очищено состояние дуэли для игрока " +
                            player.getName() + " после предыдущего выхода");
                }
            }
        }

        // Обрабатываем телепортацию из мира дуэлей
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getDuelManager().handlePlayerReturnFromDuel(player);
            }
        }, 10L);

        // Проверяем, не осталось ли арен, занятых этим игроком
        plugin.getDuelManager().cleanupPlayerArenas(player.getUniqueId());
    }

    /**
     * Проверяет, находится ли игрок в мире дуэлей
     * @param player Игрок для проверки
     * @return true, если игрок в мире дуэлей
     */
    private boolean isInDuelWorld(Player player) {
        return duelWorldNames.contains(player.getWorld().getName().toLowerCase());
    }
}