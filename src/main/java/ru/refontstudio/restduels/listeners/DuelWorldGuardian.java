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
import ru.refontstudio.restduels.models.DuelType;
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
            if (!canTeleportToDuelWorld(player)) {
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
        Location to = event.getTo();

        // Проверяем, телепортируется ли игрок в мир дуэлей
        if (to != null && isInDuelWorld(to.getWorld())) {
            UUID playerId = player.getUniqueId();

            // ИСПРАВЛЕНО: Проверяем разрешение на телепортацию, включая отложенную задачу возврата
            if (canTeleportToDuelWorld(player)) {
                return; // Разрешаем телепортацию
            }

            // Если телепортация запрещена
            event.setCancelled(true);
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cВы не можете телепортироваться в мир дуэлей!"));
        }
    }

    /**
     * Проверяет, может ли игрок телепортироваться в мир дуэлей
     * @param player Игрок для проверки
     * @return true, если игрок может телепортироваться
     */
    private boolean canTeleportToDuelWorld(Player player) {
        UUID playerId = player.getUniqueId();

        // Игроки с правом bypass всегда могут телепортироваться
        if (player.hasPermission("restduels.bypass.worldcheck")) {
            plugin.getLogger().info("[RestDuels] Игроку " + player.getName() +
                    " разрешена телепортация в мир дуэлей: есть право bypass");
            return true;
        }

        // Игроки в активной дуэли могут телепортироваться
        if (plugin.getDuelManager().isPlayerInDuel(playerId)) {
            plugin.getLogger().info("[RestDuels] Игроку " + player.getName() +
                    " разрешена телепортация в мир дуэлей: игрок в активной дуэли");
            return true;
        }

        // Игроки в процессе подготовки (отсчет) могут телепортироваться
        if (plugin.getDuelManager().isPlayerInCountdown(playerId)) {
            plugin.getLogger().info("[RestDuels] Игроку " + player.getName() +
                    " разрешена телепортация в мир дуэлей: игрок в процессе подготовки");
            return true;
        }

        // Игроки в очереди могут телепортироваться
        if (plugin.getDuelManager().isPlayerInQueue(playerId)) {
            plugin.getLogger().info("[RestDuels] Игроку " + player.getName() +
                    " разрешена телепортация в мир дуэлей: игрок в очереди на дуэль");
            return true;
        }

        // Проверяем, не заморожен ли игрок
        if (plugin.getDuelManager().isPlayerFrozen(playerId)) {
            plugin.getLogger().info("[RestDuels] Игроку " + player.getName() +
                    " разрешена телепортация в мир дуэлей: игрок заморожен");
            return true;
        }

        // ДОБАВЛЕНО: Проверяем, есть ли у игрока отложенная задача возврата
        // Это означает, что дуэль закончилась, но у игрока есть время на сбор предметов
        if (plugin.getDuelManager().hasDelayedReturnTask(playerId)) {
            plugin.getLogger().info("[RestDuels] Игроку " + player.getName() +
                    " разрешена телепортация в мир дуэлей: у игрока есть время на сбор предметов");
            return true;
        }

        // Если ни одно из условий не выполнено, запрещаем телепортацию
        plugin.getLogger().info("[RestDuels] Игроку " + player.getName() +
                " запрещена телепортация в мир дуэлей: не в дуэли и не в процессе начала");
        return false;
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

        // ИСПРАВЛЕНО: Проверяем все возможные состояния игрока, включая отложенную задачу возврата
        if (!canTeleportToDuelWorld(player)) {
            // Игрок не в дуэли, не в подготовке, не в очереди, не заморожен и не имеет времени на сбор предметов,
            // но находится в мире дуэлей - телепортируем на последнюю сохраненную локацию
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