package ru.refontstudio.restduels.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.models.DuelType;

import java.util.HashSet;
import java.util.Set;

public class DuelDamageListener implements Listener {
    private final RestDuels plugin;
    private final Set<String> duelWorldNames = new HashSet<>();

    public DuelDamageListener(RestDuels plugin) {
        this.plugin = plugin;

        // Загружаем имена миров дуэлей из конфига
        duelWorldNames.addAll(plugin.getConfig().getStringList("worlds.duel-worlds"));

        // Если список пуст, добавляем значение по умолчанию
        if (duelWorldNames.isEmpty()) {
            duelWorldNames.add("duels");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        // Проверяем, находится ли игрок в обычном мире (не в мире дуэлей)
        if (!isInDuelWorld(player)) {
            // Проверяем, находится ли игрок в очереди на дуэль
            for (DuelType type : DuelType.values()) {
                if (plugin.getDuelManager().isPlayerInQueue(player.getUniqueId(), type)) {
                    // Отменяем поиск дуэли тихо (без сообщений)
                    plugin.getDuelManager().cancelDuelSilently(player);

                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().info("Игрок " + player.getName() +
                                " получил урон в обычном мире, поиск дуэли отменен");
                    }

                    break;
                }
            }
        }
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