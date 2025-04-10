package ru.refontstudio.restduels.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.scheduler.BukkitRunnable;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.util.*;

public class DuelWorldGuardian implements Listener {
    private final Map<UUID, Long> recentDeaths = new HashMap<>();
    private final RestDuels plugin;
    private final Set<UUID> pendingTeleports = new HashSet<>();
    private final Set<String> duelWorldNames = new HashSet<>();

    public DuelWorldGuardian(RestDuels plugin) {
        this.plugin = plugin;

        // Загружаем имена миров дуэлей из конфига
        duelWorldNames.addAll(plugin.getConfig().getStringList("worlds.duel-worlds"));

        // Если список пуст, добавляем значение по умолчанию
        if (duelWorldNames.isEmpty()) {
            duelWorldNames.add("duels");
        }

        // Запускаем задачу для периодической проверки игроков в мирах дуэлей
        new BukkitRunnable() {
            @Override
            public void run() {
                checkPlayersInDuelWorlds();
            }
        }.runTaskTimer(plugin, 100L, 100L); // Проверка каждые 5 секунд
    }

    // Добавь метод для регистрации смерти
    public void registerDeath(UUID playerId) {
        recentDeaths.put(playerId, System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Проверяем, находится ли игрок в мире дуэлей
        if (isInDuelWorld(player)) {
            // Проверяем, участвует ли игрок в дуэли или в процессе начала дуэли
            if (!plugin.getDuelManager().isPlayerInDuel(playerId) &&
                    !plugin.getDuelManager().isPlayerFrozen(playerId) &&
                    !plugin.getDuelManager().isPlayerInQueue(playerId)) {

                // Игрок не в дуэли, не заморожен и не в очереди, но находится в мире дуэлей
                // Телепортируем на последнюю сохраненную локацию
                if (plugin.getDuelManager().hasSavedLocation(playerId)) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        plugin.getDuelManager().teleportToSavedLocation(player);
                    }, 10L); // 0.5 секунды задержка
                } else {
                    // Если нет сохраненной локации, просто телепортируем на спавн сервера
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                    }, 10L);
                }
            }
        }

        // Проверяем, есть ли у игрока сохраненная локация после выхода с дуэли
        if (plugin.getDuelManager().hasSavedLocation(playerId)) {
            // Возвращаем игрока на сохраненную локацию с задержкой
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getDuelManager().teleportToSavedLocation(player);
            }, 10L); // 0.5 секунды задержка
        }

        // Проверяем, не осталось ли арен, занятых этим игроком
        plugin.getDuelManager().cleanupPlayerArenas(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        // Если игрок телепортировался в мир дуэлей
        if (isInDuelWorld(player)) {
            checkPlayerInDuelWorld(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Если игрок телепортируется в мир дуэлей
        if (isInDuelWorld(event.getTo().getWorld())) {
            // Проверяем, умер ли игрок недавно (в течение 5 секунд)
            boolean isRecentDeath = recentDeaths.containsKey(playerId) &&
                    System.currentTimeMillis() - recentDeaths.get(playerId) < 5000;

            // Если игрок умер недавно или помечен для телепортации
            if (isRecentDeath || plugin.getDuelManager().isMarkedForTeleport(playerId)) {
                // Это респавн, разрешаем телепортацию
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Разрешена телепортация в мир дуэлей для игрока " +
                            player.getName() + " (после смерти)");
                }
                return;
            }

            // Проверяем, участвует ли игрок в дуэли или в процессе начала дуэли
            if (!plugin.getDuelManager().isPlayerInDuel(playerId) &&
                    !plugin.getDuelManager().isPlayerFrozen(playerId) &&
                    !plugin.getDuelManager().isPlayerInQueue(playerId) &&
                    !pendingTeleports.contains(playerId) &&
                    !player.hasPermission("restduels.bypass.worldcheck")) {

                // Отменяем телепортацию
                event.setCancelled(true);

                plugin.getLogger().info("Игроку " + player.getName() + " запрещена телепортация в мир дуэлей: не в дуэли и не в процессе начала");
            } else {
                // Разрешаем телепортацию, так как игрок в дуэли или в процессе начала
                if (plugin.getConfig().getBoolean("debug", false)) {
                    String reason = "";
                    if (plugin.getDuelManager().isPlayerInDuel(playerId)) {
                        reason = "игрок в активной дуэли";
                    } else if (plugin.getDuelManager().isPlayerFrozen(playerId)) {
                        reason = "игрок заморожен перед дуэлью";
                    } else if (plugin.getDuelManager().isPlayerInQueue(playerId)) {
                        reason = "игрок в очереди на дуэль";
                    } else if (pendingTeleports.contains(playerId)) {
                        reason = "в процессе телепортации";
                    } else if (player.hasPermission("restduels.bypass.worldcheck")) {
                        reason = "есть право bypass";
                    }

                    plugin.getLogger().info("Игроку " + player.getName() + " разрешена телепортация в мир дуэлей: " + reason);
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
        return isInDuelWorld(player.getWorld());
    }

    /**
     * Проверяет, является ли мир миром дуэлей
     * @param world Мир для проверки
     * @return true, если мир является миром дуэлей
     */
    private boolean isInDuelWorld(World world) {
        return duelWorldNames.contains(world.getName().toLowerCase());
    }

    /**
     * Проверяет всех игроков в мирах дуэлей
     */
    private void checkPlayersInDuelWorlds() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isInDuelWorld(player)) {
                checkPlayerInDuelWorld(player);
            }
        }
    }

    /**
     * Проверяет игрока в мире дуэлей
     * @param player Игрок для проверки
     */
    private void checkPlayerInDuelWorld(Player player) {
        UUID playerId = player.getUniqueId();

        // Пропускаем игроков с правами администратора
        if (player.hasPermission("restduels.bypass.worldcheck")) {
            return;
        }

        // Проверяем, участвует ли игрок в дуэли или в процессе начала дуэли
        if (!plugin.getDuelManager().isPlayerInDuel(playerId) &&
                !plugin.getDuelManager().isPlayerFrozen(playerId) &&
                !plugin.getDuelManager().isPlayerInQueue(playerId)) {

            // Игрок не в дуэли, не заморожен и не в очереди, но находится в мире дуэлей
            // Телепортируем на последнюю сохраненную локацию
            if (plugin.getDuelManager().hasSavedLocation(playerId)) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getDuelManager().teleportToSavedLocation(player);
                }, 10L); // 0.5 секунды задержка
            } else {
                // Если нет сохраненной локации, просто телепортируем на спавн сервера
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                }, 10L);
            }
        }
    }

    /**
     * Добавляет мир в список миров дуэлей
     * @param worldName Название мира
     */
    public void addDuelWorld(String worldName) {
        duelWorldNames.add(worldName.toLowerCase());
    }

    /**
     * Удаляет мир из списка миров дуэлей
     * @param worldName Название мира
     */
    public void removeDuelWorld(String worldName) {
        duelWorldNames.remove(worldName.toLowerCase());
    }
}