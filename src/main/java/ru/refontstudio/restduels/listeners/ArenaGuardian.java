package ru.refontstudio.restduels.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.models.Arena;
import ru.refontstudio.restduels.models.Duel;
import ru.refontstudio.restduels.models.RestoreArea;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class ArenaGuardian implements Listener {
    private final RestDuels plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> lastWarnTime = new HashMap<>();
    private final Map<UUID, Long> lastTeleportTime = new HashMap<>();
    private final int CHECK_INTERVAL = 5; // Проверка каждые 5 тиков
    private int taskId = -1;

    public ArenaGuardian(RestDuels plugin) {
        this.plugin = plugin;
        startPeriodicCheck();
    }

    /**
     * Запускает периодическую проверку игроков на аренах
     */
    private void startPeriodicCheck() {
        // Отменяем предыдущую задачу, если она существует
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        // Запускаем новую задачу проверки
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                checkPlayerLocation(player);
            }
        }, CHECK_INTERVAL, CHECK_INTERVAL);
    }

    /**
     * Останавливает периодическую проверку
     */
    public void stopPeriodicCheck() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    /**
     * Проверяет местоположение игрока
     * @param player Игрок для проверки
     */
    private void checkPlayerLocation(Player player) {
        UUID playerId = player.getUniqueId();

        // Проверяем, участвует ли игрок в дуэли
        if (!plugin.getDuelManager().isPlayerInDuel(playerId)) {
            return;
        }

        // Получаем дуэль и арену
        Duel duel = plugin.getDuelManager().getPlayerDuel(playerId);
        if (duel == null || duel.getArena() == null) {
            return;
        }

        // Проверяем, находится ли игрок в пределах арены
        if (!isPlayerInArenaArea(player, duel.getArena())) {
            // Проверяем время последнего предупреждения
            long now = System.currentTimeMillis();
            long lastWarn = lastWarnTime.getOrDefault(playerId, 0L);

            // Проверяем, прошло ли достаточно времени с последнего телепорта
            long lastTp = lastTeleportTime.getOrDefault(playerId, 0L);

            if (now - lastWarn > 1000) { // Предупреждение раз в секунду
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                plugin.getConfig().getString("messages.arena-boundary-warning",
                                        "&cВы выходите за пределы арены! Вернитесь обратно!")));
                lastWarnTime.put(playerId, now);
            }

            if (now - lastTp > 3000) { // Телепортация раз в 3 секунды
                teleportToRandomArenaLocation(player, duel.getArena());
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                plugin.getConfig().getString("messages.arena-boundary-teleport",
                                        "&cВы вышли за пределы арены! Вы были телепортированы обратно.")));
                lastTeleportTime.put(playerId, now);
            }
        }
    }

    /**
     * Проверяет, находится ли игрок в пределах арены
     * @param player Игрок для проверки
     * @param arena Арена для проверки
     * @return true, если игрок находится в пределах арены
     */
    private boolean isPlayerInArenaArea(Player player, Arena arena) {
        // Проверяем, есть ли у арены связанные области восстановления
        for (String areaName : plugin.getRestoreManager().getAreasForArena(arena.getId())) {
            RestoreArea area = plugin.getRestoreManager().getArea(areaName);

            if (area != null) {
                // Проверяем, находится ли игрок в пределах этой области
                if (isPlayerInArea(player, area)) {
                    return true;
                }
            }
        }

        // Если у арены нет областей восстановления, проверяем по точкам спавна
        // Расстояние от точек спавна, в пределах которого игрок считается на арене
        int maxDistance = plugin.getConfig().getInt("arena.boundary-distance", 30);

        // Проверяем расстояние от точек спавна
        Location spawn1 = arena.getSpawn1();
        Location spawn2 = arena.getSpawn2();

        if (player.getWorld().equals(spawn1.getWorld())) {
            double dist1 = player.getLocation().distance(spawn1);
            double dist2 = player.getLocation().distance(spawn2);

            return dist1 <= maxDistance || dist2 <= maxDistance;
        }

        return false;
    }

    /**
     * Проверяет, находится ли игрок в пределах указанной области
     * @param player Игрок для проверки
     * @param area Область для проверки
     * @return true, если игрок находится в пределах области
     */
    private boolean isPlayerInArea(Player player, RestoreArea area) {
        // Проверяем, что игрок в том же мире
        if (!player.getWorld().equals(area.getFirst().getWorld())) {
            return false;
        }

        // Получаем координаты игрока
        Location loc = player.getLocation();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        // Получаем границы области
        int minX = Math.min(area.getFirst().getBlockX(), area.getSecond().getBlockX());
        int maxX = Math.max(area.getFirst().getBlockX(), area.getSecond().getBlockX());
        int minY = Math.min(area.getFirst().getBlockY(), area.getSecond().getBlockY());
        int maxY = Math.max(area.getFirst().getBlockY(), area.getSecond().getBlockY());
        int minZ = Math.min(area.getFirst().getBlockZ(), area.getSecond().getBlockZ());
        int maxZ = Math.max(area.getFirst().getBlockZ(), area.getSecond().getBlockZ());

        // Проверяем, находится ли игрок в этих границах
        boolean inBounds = x >= minX && x <= maxX &&
                y >= minY && y <= maxY &&
                z >= minZ && z <= maxZ;

        // Если включено расширение границ по вертикали, проверяем только X и Z
        if (!inBounds && plugin.getConfig().getBoolean("arena.ignore-y-boundaries", true)) {
            inBounds = x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        return inBounds;
    }

    /**
     * Телепортирует игрока в случайное место на арене
     * @param player Игрок для телепортации
     * @param arena Арена для телепортации
     */
    private void teleportToRandomArenaLocation(Player player, Arena arena) {
        // Проверяем, есть ли у арены связанные области восстановления
        for (String areaName : plugin.getRestoreManager().getAreasForArena(arena.getId())) {
            RestoreArea area = plugin.getRestoreManager().getArea(areaName);

            if (area != null) {
                // Телепортируем в случайное место в области
                teleportToRandomLocationInArea(player, area);
                return;
            }
        }

        // Если у арены нет областей восстановления, телепортируем к случайной точке спавна
        if (random.nextBoolean()) {
            player.teleport(arena.getSpawn1());
        } else {
            player.teleport(arena.getSpawn2());
        }
    }

    /**
     * Телепортирует игрока в случайное место в указанной области
     * @param player Игрок для телепортации
     * @param area Область для телепортации
     */
    private void teleportToRandomLocationInArea(Player player, RestoreArea area) {
        // Получаем границы области
        int minX = Math.min(area.getFirst().getBlockX(), area.getSecond().getBlockX());
        int maxX = Math.max(area.getFirst().getBlockX(), area.getSecond().getBlockX());
        int minY = Math.min(area.getFirst().getBlockY(), area.getSecond().getBlockY());
        int maxY = Math.max(area.getFirst().getBlockY(), area.getSecond().getBlockY());
        int minZ = Math.min(area.getFirst().getBlockZ(), area.getSecond().getBlockZ());
        int maxZ = Math.max(area.getFirst().getBlockZ(), area.getSecond().getBlockZ());

        // Генерируем случайные координаты
        int x = minX + random.nextInt(maxX - minX + 1);
        int y = minY + random.nextInt(maxY - minY + 1);
        int z = minZ + random.nextInt(maxZ - minZ + 1);

        // Если включена безопасная телепортация, ищем безопасный блок
        if (plugin.getConfig().getBoolean("arena.safe-teleport", true)) {
            // Поиск безопасного блока (не воздух и не лава)
            World world = area.getFirst().getWorld();
            int attempts = 0;
            boolean safe = false;

            while (!safe && attempts < 10) {
                x = minX + random.nextInt(maxX - minX + 1);
                y = minY + random.nextInt(maxY - minY + 1);
                z = minZ + random.nextInt(maxZ - minZ + 1);

                // Проверяем, безопасен ли блок для телепортации
                if (world.getBlockAt(x, y-1, z).getType().isSolid() &&
                        world.getBlockAt(x, y, z).isEmpty() &&
                        world.getBlockAt(x, y+1, z).isEmpty()) {
                    safe = true;
                }

                attempts++;
            }

            // Если не нашли безопасное место, используем точку спавна арены
            if (!safe) {
                Arena arena = plugin.getArenaManager().getArenaByArea(area.getName());
                if (arena != null) {
                    player.teleport(random.nextBoolean() ? arena.getSpawn1() : arena.getSpawn2());
                    return;
                }
            }
        }

        // Телепортируем игрока
        Location teleportLocation = new Location(area.getFirst().getWorld(), x + 0.5, y, z + 0.5);
        player.teleport(teleportLocation);
    }

    /**
     * Обработчик события движения игрока (опционально, для мгновенной проверки)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Оптимизация: проверяем только если игрок переместился в другой блок
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // Проверяем только если включена мгновенная проверка
        if (plugin.getConfig().getBoolean("arena.instant-boundary-check", false)) {
            checkPlayerLocation(event.getPlayer());
        }
    }
}