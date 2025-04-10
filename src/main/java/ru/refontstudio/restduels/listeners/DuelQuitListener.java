package ru.refontstudio.restduels.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DuelQuitListener implements Listener {
    private final RestDuels plugin;
    private final Set<String> duelWorldNames = new HashSet<>();

    public DuelQuitListener(RestDuels plugin) {
        this.plugin = plugin;

        // Загружаем имена миров дуэлей из конфига
        duelWorldNames.addAll(plugin.getConfig().getStringList("worlds.duel-worlds"));

        // Если список пуст, добавляем значение по умолчанию
        if (duelWorldNames.isEmpty()) {
            duelWorldNames.add("duels");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Если игрок в дуэли, сохраняем его текущее местоположение
        if (plugin.getDuelManager().isPlayerInDuel(playerId)) {
            // Сохраняем локацию при выходе
            plugin.getDuelManager().savePlayerLocation(player);
        }

        // Проверяем, находится ли игрок в мире дуэлей
        if (isInDuelWorld(player)) {
            // Проверяем, не находится ли игрок в защищенной зоне спавна
            if (!isInSpawnProtection(player)) {
                // Убиваем игрока перед выходом, чтобы он потерял вещи
                if (plugin.getConfig().getBoolean("duel.kill-on-quit", true)) {
                    // Устанавливаем здоровье игрока на 0, что приведет к его смерти и выпадению вещей
                    player.setHealth(0);

                    // Логируем действие
                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().info("Игрок " + player.getName() + " убит при выходе из мира дуэлей");
                    }

                    // Сообщаем всем игрокам в мире дуэлей
                    for (Player p : player.getWorld().getPlayers()) {
                        if (p != player) {
                            p.sendMessage(ColorUtils.colorize(
                                    plugin.getConfig().getString("messages.prefix") +
                                            plugin.getConfig().getString("messages.player-killed-on-quit",
                                                            "&c%player% был убит за выход во время дуэли!")
                                                    .replace("%player%", player.getName())));
                        }
                    }
                }
            }
        }

        // Удаляем игрока из очередей и отменяем задачи
        plugin.getDuelManager().removeFromQueues(player.getUniqueId());

        // Если игрок заморожен (ожидает начала дуэли), отменяем дуэль
        if (plugin.getDuelManager().isPlayerFrozen(player.getUniqueId())) {
            plugin.getDuelManager().unfreezeAndCancelDuel(player);
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

    /**
     * Проверяет, находится ли игрок в защищенной зоне спавна
     * @param player Игрок для проверки
     * @return true, если игрок в защищенной зоне
     */
    private boolean isInSpawnProtection(Player player) {
        // Проверка на основе метаданных или других признаков спавна
        // Этот метод должен быть адаптирован под конкретный сервер

        // Проверка по метаданным
        if (player.hasMetadata("inSpawn")) {
            return true;
        }

        // Проверка по названию региона (если используется WorldGuard)
        // Требуется зависимость от WorldGuard
        try {
            // Проверка наличия WorldGuard
            Class<?> worldGuardPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            if (worldGuardPluginClass != null) {
                // Получаем плагин WorldGuard
                Object worldGuardPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
                if (worldGuardPlugin != null) {
                    // Получаем RegionManager
                    Object regionContainer = worldGuardPluginClass.getMethod("getRegionContainer").invoke(worldGuardPlugin);
                    Object regionManager = regionContainer.getClass().getMethod("get", World.class).invoke(regionContainer, player.getWorld());

                    if (regionManager != null) {
                        // Получаем список регионов в точке игрока
                        Object loc = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter").getMethod("adapt", Location.class).invoke(null, player.getLocation());
                        Object regions = regionManager.getClass().getMethod("getApplicableRegions", loc.getClass()).invoke(regionManager, loc);

                        // Проверяем наличие региона "spawn"
                        boolean hasSpawnRegion = (boolean) regions.getClass().getMethod("contains", String.class).invoke(regions, "spawn");

                        return hasSpawnRegion;
                    }
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки, если WorldGuard не установлен
        }

        // Проверка по расстоянию от спавна мира
        double spawnProtectionRadius = plugin.getConfig().getDouble("duel.spawn-protection-radius", 10.0);
        Location spawnLoc = player.getWorld().getSpawnLocation();
        double distance = player.getLocation().distance(spawnLoc);

        return distance <= spawnProtectionRadius;
    }
}