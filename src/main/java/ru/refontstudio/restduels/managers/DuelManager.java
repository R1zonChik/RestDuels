package ru.refontstudio.restduels.managers;

import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.inventory.InventoryView;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.boss.BarStyle;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.listeners.CommandBlocker;
import ru.refontstudio.restduels.models.Arena;
import ru.refontstudio.restduels.models.Duel;
import ru.refontstudio.restduels.models.DuelType;
import ru.refontstudio.restduels.utils.ColorUtils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import me.clip.placeholderapi.PlaceholderAPI;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class DuelManager {
    // Добавим новое поле для хранения оригинальных локаций (до входа в мир дуэлей)
    private final Map<UUID, Location> originalWorldLocations = new HashMap<>();
    private final RestDuels plugin;
    private final Set<UUID> duelCountdownPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Float> originalWalkSpeed = new HashMap<>();
    private final Map<UUID, Boolean> playerFlightStatus = new HashMap<>();
    private final Map<String, String> blockedArenas = new HashMap<>();
    private final Map<UUID, ItemStack[]> originalInventories = new HashMap<>(); // Инвентарь до начала дуэли
    private final Map<UUID, ItemStack[]> originalArmor = new HashMap<>(); // Броня до начала дуэли
    private final Map<UUID, BukkitTask> flightCheckTasks = new HashMap<>();
    private Set<UUID> countdownPlayers = new HashSet<>();
    private final Map<UUID, Duel> playerDuels = new HashMap<>();
    private final Set<UUID> playersMarkedForTeleport = new HashSet<>();
    private final Map<UUID, Location> playerLocations = new HashMap<>();
    private final Map<UUID, ItemStack[]> playerInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> playerArmor = new HashMap<>();
    private Map<UUID, Float> playerOriginalSpeeds = new HashMap<>();
    private final Map<DuelType, List<UUID>> queuedPlayers = new HashMap<>();
    public final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private final Map<UUID, BukkitTask> searchTasks = new HashMap<>();
    // Добавьте эти поля в класс DuelManager
    private final Map<UUID, Double> originalPlayerHealth = new HashMap<>(); // Сохраненное здоровье
    private final Map<UUID, Integer> originalPlayerFood = new HashMap<>(); // Сохраненная сытость (бонус)
    private final Map<UUID, Float> originalPlayerSaturation = new HashMap<>(); // Сохраненная насыщенность (бонус)
    private final Map<UUID, Long> postDuelBlockTime = new HashMap<>();
    private final long POST_DUEL_BLOCK_DURATION = 10000; // 10 секунд блокировки после дуэли
    private final Set<UUID> playersInEndPhase = new HashSet<>();
    private final Map<UUID, Long> lastTeleportTimes = new HashMap<>();
    private final Map<UUID, BukkitTask> delayedReturnTasks = new HashMap<>();
    private final Map<UUID, Arena> playerArenas = new HashMap<>();
    private final Set<UUID> frozenPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>()); // Потокобезопасная коллекция
    private final Set<String> occupiedArenas = Collections.newSetFromMap(new ConcurrentHashMap<>()); // Потокобезопасная коллекция
    private final Queue<QueueEntry> arenaQueue = new LinkedList<>(); // Очередь игроков, ожидающих арену
    private final Map<UUID, Long> lastTeleportTime = new HashMap<>();
    private final Map<UUID, Long> lastTeleportMessageTime = new HashMap<>();
    private final Set<UUID> pendingTeleports = new HashSet<>();
    private final Map<String, Long> arenaLastUsedTime = new HashMap<>(); // Для отслеживания времени последнего использования арены
    private final Map<String, Integer> worldPlayerCount = new HashMap<>(); // Для отслеживания количества игроков в мирах
    private final Set<UUID> entitiesMarkedForRemoval = new HashSet<>(); // Для отслеживания сущностей, которые нужно удалить
    private final List<String> recentlyUsedArenas = new ArrayList<>(); // Список недавно использованных арен
    private final int MAX_RECENT_ARENAS = 3; // Максимальное количество запоминаемых арен

    // Класс для хранения данных о парах игроков в очереди
    private static class QueueEntry {
        UUID player1Id;
        UUID player2Id;
        DuelType type;

        public QueueEntry(UUID player1Id, UUID player2Id, DuelType type) {
            this.player1Id = player1Id;
            this.player2Id = player2Id;
            this.type = type;
        }
    }

    /**
     * Сохраняет текущее здоровье игрока для ранкед дуэлей
     * @param player Игрок
     */
    private void savePlayerHealth(Player player) {
        UUID playerId = player.getUniqueId();

        // Сохраняем здоровье
        originalPlayerHealth.put(playerId, player.getHealth());

        // Бонус: сохраняем еду и насыщенность
        originalPlayerFood.put(playerId, player.getFoodLevel());
        originalPlayerSaturation.put(playerId, player.getSaturation());

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Сохранено здоровье игрока " + player.getName() + ": " +
                    player.getHealth() + " HP, " + player.getFoodLevel() + " food");
        }
    }

    /**
     * Восстанавливает сохраненное здоровье игрока после ранкед дуэли
     * @param player Игрок
     */
    public void restorePlayerHealth(Player player) {
        UUID playerId = player.getUniqueId();

        // Восстанавливаем здоровье
        if (originalPlayerHealth.containsKey(playerId)) {
            double savedHealth = originalPlayerHealth.get(playerId);
            player.setHealth(Math.min(savedHealth, player.getMaxHealth())); // Не больше максимума
            originalPlayerHealth.remove(playerId);

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Восстановлено здоровье игрока " + player.getName() + ": " + savedHealth + " HP");
            }
        }

        // Восстанавливаем еду
        if (originalPlayerFood.containsKey(playerId)) {
            int savedFood = originalPlayerFood.get(playerId);
            player.setFoodLevel(savedFood);
            originalPlayerFood.remove(playerId);
        }

        // Восстанавливаем насыщенность
        if (originalPlayerSaturation.containsKey(playerId)) {
            float savedSaturation = originalPlayerSaturation.get(playerId);
            player.setSaturation(savedSaturation);
            originalPlayerSaturation.remove(playerId);
        }
    }

    /**
     * Очищает сохраненные данные здоровья игрока
     * @param playerId UUID игрока
     */
    private void clearSavedPlayerHealth(UUID playerId) {
        originalPlayerHealth.remove(playerId);
        originalPlayerFood.remove(playerId);
        originalPlayerSaturation.remove(playerId);
    }

    /**
     * Отмечает игрока как находящегося в фазе после дуэли
     * @param playerId UUID игрока
     */
    public void markPostDuel(UUID playerId) {
        postDuelBlockTime.put(playerId, System.currentTimeMillis() + POST_DUEL_BLOCK_DURATION);
    }

    /**
     * Проверяет, находится ли игрок в фазе после дуэли
     * @param playerId UUID игрока
     * @return true, если игрок в фазе после дуэли
     */
    public boolean isPlayerInPostDuelPhase(UUID playerId) {
        Long blockEndTime = postDuelBlockTime.get(playerId);
        if (blockEndTime == null) {
            return false;
        }

        // Если время блокировки истекло, удаляем запись
        if (System.currentTimeMillis() > blockEndTime) {
            postDuelBlockTime.remove(playerId);
            return false;
        }

        return true;
    }

    /**
     * Сбрасывает фазу после дуэли для игрока
     * @param playerId UUID игрока
     */
    public void clearPostDuelPhase(UUID playerId) {
        postDuelBlockTime.remove(playerId);
    }

    /**
     * Проверяет, можно ли телепортировать игрока (защита от двойной телепортации)
     * @param playerId UUID игрока
     * @return true, если телепортация разрешена
     */
    private boolean canTeleport(UUID playerId) {
        long now = System.currentTimeMillis();
        if (lastTeleportTime.containsKey(playerId)) {
            // Если прошло меньше 2 секунд с последней телепортации, запрещаем
            if (now - lastTeleportTime.get(playerId) < 2000) {
                return false;
            }
        }

        // Если игрок уже в процессе телепортации, запрещаем
        if (pendingTeleports.contains(playerId)) {
            return false;
        }

        return true;
    }

    /**
     * Запускает таймер с отображением времени в BossBar
     * @param player1 Первый игрок
     * @param player2 Второй игрок
     * @param duel Дуэль
     * @param duelTimeSeconds Время дуэли в секундах
     */
    private void startBossBarTimer(Player player1, Player player2, Duel duel, int duelTimeSeconds) {
        // Загружаем настройки из конфига
        String titleTemplate = plugin.getConfig().getString("bossbar.title", "&6Осталось времени: &e%time%");

        // Определяем стиль BossBar
        String styleStr = plugin.getConfig().getString("bossbar.style", "SOLID");
        BarStyle style;
        try {
            style = BarStyle.valueOf(styleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            style = BarStyle.SOLID;
            plugin.getLogger().warning("Неверный стиль BossBar в конфиге: " + styleStr + ". Используется SOLID.");
        }

        // Определяем цвета BossBar
        String defaultColorStr = plugin.getConfig().getString("bossbar.colors.default", "GREEN");
        String warningColorStr = plugin.getConfig().getString("bossbar.colors.warning", "YELLOW");
        String dangerColorStr = plugin.getConfig().getString("bossbar.colors.danger", "RED");

        BarColor defaultColor, warningColor, dangerColor;
        try {
            defaultColor = BarColor.valueOf(defaultColorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            defaultColor = BarColor.GREEN;
            plugin.getLogger().warning("Неверный цвет BossBar в конфиге: " + defaultColorStr + ". Используется GREEN.");
        }

        try {
            warningColor = BarColor.valueOf(warningColorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            warningColor = BarColor.YELLOW;
            plugin.getLogger().warning("Неверный цвет BossBar в конфиге: " + warningColorStr + ". Используется YELLOW.");
        }

        try {
            dangerColor = BarColor.valueOf(dangerColorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            dangerColor = BarColor.RED;
            plugin.getLogger().warning("Неверный цвет BossBar в конфиге: " + dangerColorStr + ". Используется RED.");
        }

        // Получаем пороги для смены цвета
        int warningThreshold = plugin.getConfig().getInt("bossbar.color_change.warning", 60);
        int dangerThreshold = plugin.getConfig().getInt("bossbar.color_change.danger", 30);

        // Форматируем начальный текст
        String initialTitle = titleTemplate.replace("%time%", formatTime(duelTimeSeconds));

        // Создаем BossBar с начальным текстом и цветом
        BossBar bossBar = Bukkit.createBossBar(
                ColorUtils.colorize(initialTitle),
                defaultColor,
                style);

        // Добавляем игроков к BossBar
        bossBar.addPlayer(player1);
        bossBar.addPlayer(player2);

        // Сохраняем BossBar для обоих игроков
        playerBossBars.put(player1.getUniqueId(), bossBar);
        playerBossBars.put(player2.getUniqueId(), bossBar);

        // Сохраняем исходное время для расчета прогресса
        final int startTime = duelTimeSeconds;
        final int[] timeLeft = {duelTimeSeconds};

        // Сохраняем необходимые переменные как final для использования в лямбде
        final UUID player1Id = player1.getUniqueId();
        final UUID player2Id = player2.getUniqueId();
        final String titleTemplateFinal = titleTemplate;
        final BarColor warningColorFinal = warningColor;
        final BarColor dangerColorFinal = dangerColor;
        final BarColor defaultColorFinal = defaultColor;

        // Запускаем таймер
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                // Проверяем, активна ли еще дуэль
                if (!isPlayerInDuel(player1Id) || !isPlayerInDuel(player2Id)) {
                    bossBar.removeAll();
                    playerBossBars.remove(player1Id);
                    playerBossBars.remove(player2Id);
                    if (duel.getTimerTask() != null) {
                        duel.getTimerTask().cancel();
                    }
                    return;
                }

                // Обновляем оставшееся время
                timeLeft[0]--;

                if (timeLeft[0] <= 0) {
                    // Время вышло, завершаем дуэль
                    bossBar.removeAll();
                    playerBossBars.remove(player1Id);
                    playerBossBars.remove(player2Id);

                    if (duel.getTimerTask() != null) {
                        duel.getTimerTask().cancel();
                    }

                    // Объявляем ничью
                    endDuelAsDraw(duel);
                    return;
                }

                // Обновляем заголовок BossBar
                String updatedTitle = titleTemplateFinal.replace("%time%", formatTime(timeLeft[0]));
                bossBar.setTitle(ColorUtils.colorize(updatedTitle));

                // Обновляем прогресс BossBar
                double progress = (double) timeLeft[0] / startTime;
                bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

                // Меняем цвет в зависимости от оставшегося времени
                if (timeLeft[0] <= dangerThreshold) {
                    bossBar.setColor(dangerColorFinal);
                } else if (timeLeft[0] <= warningThreshold) {
                    bossBar.setColor(warningColorFinal);
                } else {
                    bossBar.setColor(defaultColorFinal);
                }
            }
        }, 0L, 20L); // Обновление каждую секунду

        // Сохраняем задачу таймера в объекте дуэли
        duel.setTimerTask(task);
    }



    /**
     * Форматирует время в формате минуты:секунды
     * @param seconds Время в секундах
     * @return Отформатированное время
     */
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }

    /**
     * Отмечает начало телепортации игрока
     * @param playerId UUID игрока
     */
    private void markTeleportStart(UUID playerId) {
        pendingTeleports.add(playerId);
    }

    /**
     * Отмечает завершение телепортации игрока
     * @param playerId UUID игрока
     */
    private void markTeleportEnd(UUID playerId) {
        pendingTeleports.remove(playerId);
        lastTeleportTime.put(playerId, System.currentTimeMillis());
    }

    /**
     * Отмечает игрока для последующей телепортации
     * @param playerId UUID игрока
     */
    public void markPlayerForTeleport(UUID playerId) {
        playersMarkedForTeleport.add(playerId);
    }

    /**
     * Снимает отметку с игрока для телепортации
     * @param playerId UUID игрока
     */
    public void unmarkPlayerForTeleport(UUID playerId) {
        playersMarkedForTeleport.remove(playerId);
    }

    /**
     * Проверяет, отмечен ли игрок для телепортации
     * @param playerId UUID игрока
     * @return true, если игрок отмечен для телепортации
     */
    public boolean isMarkedForTeleport(UUID playerId) {
        return playersMarkedForTeleport.contains(playerId);
    }

    /**
     * Проверяет и отключает режим бога у игрока, если он включен
     * @param player Игрок для проверки
     * @return true, если режим бога был отключен
     */
    public boolean disableGodModeIfEnabled(Player player) {
        // Проверяем, установлен ли PlaceholderAPI
        Plugin papiPlugin = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papiPlugin != null && papiPlugin.isEnabled()) {
            try {
                // Проверяем режим бога через PlaceholderAPI
                String godMode = PlaceholderAPI.setPlaceholders(player, "%essentials_godmode%").toLowerCase();

                if (godMode.equals("yes") || godMode.equals("true") || godMode.equals("enabled")) {
                    // Сохраняем текущее состояние
                    player.setMetadata("restduels_godmode", new FixedMetadataValue(plugin, true));

                    // Выполняем команду от имени консоли для отключения режима бога
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "god " + player.getName() + " off");

                    player.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    plugin.getConfig().getString("messages.godmode-disabled", "&cРежим бога был временно отключен для дуэли!")));

                    return true;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при проверке режима бога через PlaceholderAPI: " + e.getMessage());
            }
        } else {
            plugin.getLogger().info("PlaceholderAPI не найден, проверка режима бога отключена");
        }

        return false;
    }

    /**
     * Восстанавливает режим бога у игрока, если он был отключен
     * @param player Игрок для восстановления
     */
    public void restoreGodModeIfDisabled(Player player) {
        // Проверяем, был ли отключен режим бога
        if (player.hasMetadata("restduels_godmode")) {
            player.removeMetadata("restduels_godmode", plugin);

            // Восстанавливаем режим бога через консольную команду
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "god " + player.getName() + " on");

            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            plugin.getConfig().getString("messages.godmode-restored", "&aРежим бога был восстановлен!")));
        }
    }

    /**
     * Блокирует арену для использования в дуэлях
     * @param arenaId ID арены
     * @param reason Причина блокировки
     */
    public void blockArena(String arenaId, String reason) {
        blockedArenas.put(arenaId, reason);
        plugin.getLogger().info("Арена " + arenaId + " заблокирована: " + reason);
    }

    /**
     * Разблокирует арену
     * @param arenaId ID арены
     */
    public void unblockArena(String arenaId) {
        blockedArenas.remove(arenaId);
        plugin.getLogger().info("Арена " + arenaId + " разблокирована");
    }

    /**
     * Проверяет, заблокирована ли арена
     * @param arenaId ID арены
     * @return true, если арена заблокирована
     */
    public boolean isArenaBlocked(String arenaId) {
        return blockedArenas.containsKey(arenaId);
    }

    /**
     * Получает причину блокировки арены
     * @param arenaId ID арены
     * @return Причина блокировки или null, если арена не заблокирована
     */
    public String getArenaBlockReason(String arenaId) {
        return blockedArenas.get(arenaId);
    }

    // Метод для начала проверки полета
    private void startFlightCheck(Player player) {
        UUID playerId = player.getUniqueId();

        // Отменяем существующую задачу, если есть
        if (flightCheckTasks.containsKey(playerId)) {
            flightCheckTasks.get(playerId).cancel();
        }

        // Создаем новую задачу проверки полета
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Проверяем, что игрок онлайн и в дуэли
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && isPlayerInDuel(playerId)) {
                // Если игрок летает или имеет разрешение на полет, отключаем
                if (p.isFlying() || p.getAllowFlight()) {
                    p.setAllowFlight(false);
                    p.setFlying(false);
                    p.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    "&cПолет запрещен во время дуэли!"));
                }
            } else {
                // Если игрок вышел или дуэль закончилась, отменяем задачу
                if (flightCheckTasks.containsKey(playerId)) {
                    flightCheckTasks.get(playerId).cancel();
                    flightCheckTasks.remove(playerId);
                }
            }
        }, 10L, 10L); // Проверка каждые полсекунды

        flightCheckTasks.put(playerId, task);
    }

    // Метод для остановки проверки полета
    private void stopFlightCheck(UUID playerId) {
        if (flightCheckTasks.containsKey(playerId)) {
            flightCheckTasks.get(playerId).cancel();
            flightCheckTasks.remove(playerId);
        }
    }

    public DuelManager(RestDuels plugin) {
        this.plugin = plugin;

        // Инициализируем очереди для каждого типа дуэли
        for (DuelType type : DuelType.values()) {
            queuedPlayers.put(type, new ArrayList<>());
        }

        // Запускаем таймер для проверки очереди арен
        Bukkit.getScheduler().runTaskTimer(plugin, () -> checkArenaQueue(), 20L, 20L); // Каждую секунду

        // Запускаем таймер для периодической проверки очереди арен и освобождения арен
        startArenaQueueChecker();

        // Запускаем таймер для очистки сущностей на аренах
        if (plugin.getConfig().getBoolean("optimization.clean-arena-entities", true)) {
            int cleanupInterval = plugin.getConfig().getInt("optimization.entity-cleanup-interval", 60);
            Bukkit.getScheduler().runTaskTimer(plugin, () -> cleanupArenaEntities(), 20L * cleanupInterval, 20L * cleanupInterval);
        }

        // Запускаем таймер для отслеживания нагрузки миров
        Bukkit.getScheduler().runTaskTimer(plugin, () -> updateWorldPlayerCount(), 20L * 10, 20L * 10); // Каждые 10 секунд
    }

    // Метод для обновления счетчика игроков в мирах
    private void updateWorldPlayerCount() {
        // Очищаем текущие данные
        worldPlayerCount.clear();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasMetadata("restduels_blocked_commands")) {
                player.removeMetadata("restduels_blocked_commands", plugin);
            }

            // Если есть CommandBlocker, разблокируем команды
            try {
                Class<?> commandBlockerClass = Class.forName("ru.refontstudio.restduels.listeners.CommandBlocker");
                if (commandBlockerClass != null) {
                    java.lang.reflect.Method getInstanceMethod = commandBlockerClass.getMethod("getInstance");
                    if (getInstanceMethod != null) {
                        Object commandBlocker = getInstanceMethod.invoke(null);
                        java.lang.reflect.Method removePlayerMethod = commandBlocker.getClass().getMethod("removePlayer", UUID.class);
                        if (removePlayerMethod != null) {
                            removePlayerMethod.invoke(commandBlocker, player.getUniqueId());
                        }
                    }
                }
            } catch (Exception e) {
                // Игнорируем ошибки
            }
        }
    }

    private void cleanupArenaEntities() {
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            World world = arena.getSpawn1().getWorld();
            if (world == null) continue;

            // СКАНИРУЕМ только ПУСТЫЕ арены!
            boolean arenaIsBusy = occupiedArenas.contains(arena.getId());

            // ДОПОЛНИТЕЛЬНО: проверяем — нет ли на этой арене игроков, у которых еще идет таймер сбора ресурсов:
            boolean hasDelayedPlayer = false;
            for (UUID playerId : delayedReturnTasks.keySet()) {
                Player p = Bukkit.getPlayer(playerId);
                if (p != null && p.isOnline()) {
                    Location pl = p.getLocation();
                    // Координаты в пределах арены?
                    double minX = Math.min(arena.getSpawn1().getX(), arena.getSpawn2().getX()) - 30;
                    double maxX = Math.max(arena.getSpawn1().getX(), arena.getSpawn2().getX()) + 30;
                    double minZ = Math.min(arena.getSpawn1().getZ(), arena.getSpawn2().getZ()) - 30;
                    double maxZ = Math.max(arena.getSpawn1().getZ(), arena.getSpawn2().getZ()) + 30;
                    if (pl.getWorld().getName().equals(world.getName()) &&
                            pl.getX() >= minX && pl.getX() <= maxX &&
                            pl.getZ() >= minZ && pl.getZ() <= maxZ) {
                        hasDelayedPlayer = true;
                        break;
                    }
                }
            }

            if (arenaIsBusy || hasDelayedPlayer) {
                // На арене идет лутосбор победителем - ничего не трогаем!
                continue;
            }

            // Теперь можно чистить только если никого не осталось на арене!
            List<Entity> entities = world.getEntities();

            double minX = Math.min(arena.getSpawn1().getX(), arena.getSpawn2().getX()) - 30;
            double maxX = Math.max(arena.getSpawn1().getX(), arena.getSpawn2().getX()) + 30;
            double minZ = Math.min(arena.getSpawn1().getZ(), arena.getSpawn2().getZ()) - 30;
            double maxZ = Math.max(arena.getSpawn1().getZ(), arena.getSpawn2().getZ()) + 30;

            for (Entity entity : entities) {
                if (entity instanceof Player) continue;
                if (entitiesMarkedForRemoval.contains(entity.getUniqueId())) continue;

                Location loc = entity.getLocation();

                if (loc.getX() >= minX && loc.getX() <= maxX &&
                        loc.getZ() >= minZ && loc.getZ() <= maxZ) {

                    entitiesMarkedForRemoval.add(entity.getUniqueId());
                    Bukkit.getScheduler().runTask(plugin, () -> entity.remove());
                }
            }
        }
        entitiesMarkedForRemoval.clear();
    }

    // Метод для проверки очереди арен
    private void checkArenaQueue() {
        if (arenaQueue.isEmpty()) {
            return; // Очередь пуста
        }

        // Проверяем доступность арен
        Arena freeArena = getNextFreeArena();
        if (freeArena == null) {
            return; // Нет свободных арен
        }

        // Берем первую пару игроков из очереди
        QueueEntry entry = arenaQueue.poll();

        // Получаем игроков
        Player player1 = Bukkit.getPlayer(entry.player1Id);
        Player player2 = Bukkit.getPlayer(entry.player2Id);

        if (player1 != null && player2 != null && player1.isOnline() && player2.isOnline()) {
            // Помечаем арену как занятую
            occupiedArenas.add(freeArena.getId());

            // Обновляем время последнего использования арены
            arenaLastUsedTime.put(freeArena.getId(), System.currentTimeMillis());

            // Добавляем арену в список недавно использованных
            addToRecentlyUsedArenas(freeArena.getId());

            // Предварительно загружаем арену
            preloadArena(freeArena);

            // Замораживаем игроков и начинаем отсчет
            freezePlayersBeforeDuel(player1, player2, entry.type, freeArena);

            // Оповещаем игроков
            String message = ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&aНашлась свободная арена! Подготовка к дуэли...");
            player1.sendMessage(message);
            player2.sendMessage(message);
        } else {
            // Если один из игроков оффлайн, возвращаем того, кто онлайн
            if (player1 != null && player1.isOnline()) {
                returnPlayer(player1, true);
                player1.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cВаш противник вышел с сервера. Дуэль отменена."));
            }

            if (player2 != null && player2.isOnline()) {
                returnPlayer(player2, true);
                player2.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cВаш противник вышел с сервера. Дуэль отменена."));
            }
        }
    }

    // Метод для добавления арены в список недавно использованных
    private void addToRecentlyUsedArenas(String arenaId) {
        // Добавляем арену в начало списка
        recentlyUsedArenas.remove(arenaId); // Удаляем, если уже есть
        recentlyUsedArenas.add(0, arenaId); // Добавляем в начало

        // Ограничиваем размер списка
        while (recentlyUsedArenas.size() > MAX_RECENT_ARENAS) {
            recentlyUsedArenas.remove(recentlyUsedArenas.size() - 1);
        }
    }

    /**
     * Получает следующую свободную арену с учетом балансировки нагрузки
     * @return Свободная арена или null, если нет доступных арен
     */
    private Arena getNextFreeArena() {
        List<Arena> allArenas = plugin.getArenaManager().getArenas();
        if (allArenas.isEmpty()) {
            plugin.getLogger().warning("Нет настроенных арен!");
            return null;
        }

        // Выводим отладочную информацию
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Поиск свободной арены. Всего арен: " + allArenas.size());
            plugin.getLogger().info("Занятые арены: " + occupiedArenas.size() + " - " + String.join(", ", occupiedArenas));

            // Проверяем заблокированные арены
            List<String> blockedArenaIds = new ArrayList<>();
            for (Arena arena : allArenas) {
                if (isArenaBlocked(arena.getId())) {
                    blockedArenaIds.add(arena.getId() + "(" + getArenaBlockReason(arena.getId()) + ")");
                }
            }

            if (!blockedArenaIds.isEmpty()) {
                plugin.getLogger().info("Заблокированные арены: " + String.join(", ", blockedArenaIds));
            }
        }

        // Фильтруем только свободные и не заблокированные арены
        List<Arena> freeArenas = new ArrayList<>();
        for (Arena arena : allArenas) {
            String arenaId = arena.getId();

            // Проверяем, не занята ли арена
            boolean isOccupied = occupiedArenas.contains(arenaId);

            // Проверяем, не заблокирована ли арена
            boolean isBlocked = isArenaBlocked(arenaId);

            // Проверяем, не восстанавливается ли арена
            boolean isRestoring = false;
            if (plugin.getRestoreManager() != null) {
                isRestoring = plugin.getRestoreManager().isArenaRestoring(arenaId);
            }

            // Если арена свободна, не заблокирована и не восстанавливается - добавляем в список
            if (!isOccupied && !isBlocked && !isRestoring) {
                freeArenas.add(arena);

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Арена " + arenaId + " доступна для использования");
                }
            } else if (plugin.getConfig().getBoolean("debug", false)) {
                String reason = "";
                if (isOccupied) reason += "занята, ";
                if (isBlocked) reason += "заблокирована (" + getArenaBlockReason(arenaId) + "), ";
                if (isRestoring) reason += "восстанавливается, ";

                // Удаляем последнюю запятую и пробел
                if (reason.endsWith(", ")) {
                    reason = reason.substring(0, reason.length() - 2);
                }

                plugin.getLogger().info("Арена " + arenaId + " недоступна: " + reason);
            }
        }

        if (freeArenas.isEmpty()) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Нет доступных арен");
            }
            return null;
        }

        // Исключаем недавно использованные арены, если есть другие варианты
        List<Arena> preferredArenas = freeArenas.stream()
                .filter(arena -> !recentlyUsedArenas.contains(arena.getId()))
                .collect(Collectors.toList());

        // Если после фильтрации список пуст, используем все свободные арены
        if (preferredArenas.isEmpty() && freeArenas.size() > 1) {
            // Если есть только одна недавно использованная арена в списке, удаляем ее
            if (recentlyUsedArenas.size() == 1) {
                String oldestArena = recentlyUsedArenas.remove(recentlyUsedArenas.size() - 1);
                preferredArenas = freeArenas.stream()
                        .filter(arena -> !arena.getId().equals(oldestArena))
                        .collect(Collectors.toList());
            }

            // Если все еще пусто, используем все арены
            if (preferredArenas.isEmpty()) {
                preferredArenas = new ArrayList<>(freeArenas);
            }
        } else if (preferredArenas.isEmpty()) {
            preferredArenas = new ArrayList<>(freeArenas);
        }

        // Проверяем, что у нас есть хотя бы одна арена после всех фильтраций
        if (preferredArenas.isEmpty()) {
            // Если после всех проверок список пуст, используем все арены
            preferredArenas = new ArrayList<>(allArenas);

            // Удаляем занятые арены
            preferredArenas.removeIf(arena -> occupiedArenas.contains(arena.getId()));

            // Если все еще пусто, возвращаем null
            if (preferredArenas.isEmpty()) {
                return null;
            }
        }

        // Сортируем арены по количеству игроков в мире (предпочтительнее миры с меньшим количеством игроков)
        preferredArenas.sort((a1, a2) -> {
            String world1 = a1.getSpawn1().getWorld().getName();
            String world2 = a2.getSpawn1().getWorld().getName();
            int count1 = worldPlayerCount.getOrDefault(world1, 0);
            int count2 = worldPlayerCount.getOrDefault(world2, 0);
            return Integer.compare(count1, count2);
        });

        // Случайный выбор из первых N арен (если арен больше N)
        int selectFromTop = Math.min(3, preferredArenas.size());
        int randomIndex = ThreadLocalRandom.current().nextInt(selectFromTop);

        Arena selectedArena = preferredArenas.get(randomIndex);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Выбрана арена: " + selectedArena.getId());
        }

        return selectedArena;
    }

    // Метод для предварительной загрузки арены с оптимизациями
    private void preloadArena(Arena arena) {
        if (arena == null || arena.getSpawn1() == null || arena.getSpawn2() == null) {
            return;
        }

        World world = arena.getSpawn1().getWorld();
        if (world == null) {
            return;
        }

        // Получаем координаты чанков
        int chunkX1 = arena.getSpawn1().getBlockX() >> 4;
        int chunkZ1 = arena.getSpawn1().getBlockZ() >> 4;
        int chunkX2 = arena.getSpawn2().getBlockX() >> 4;
        int chunkZ2 = arena.getSpawn2().getBlockZ() >> 4;

        // Определяем границы области чанков
        int minX = Math.min(chunkX1, chunkX2) - 1;
        int maxX = Math.max(chunkX1, chunkX2) + 1;
        int minZ = Math.min(chunkZ1, chunkZ2) - 1;
        int maxZ = Math.max(chunkZ1, chunkZ2) + 1;

        // Ограничиваем размер загружаемой области
        int maxChunksToLoad = plugin.getConfig().getInt("optimization.max-chunks-to-load", 9); // Не более 3x3 чанков по умолчанию
        int chunksToLoadCount = (maxX - minX + 1) * (maxZ - minZ + 1);
        if (chunksToLoadCount > maxChunksToLoad) {
            // Если область слишком большая, загружаем только ближайшие чанки
            minX = chunkX1 - 1;
            maxX = chunkX1 + 1;
            minZ = chunkZ1 - 1;
            maxZ = chunkZ1 + 1;
        }

        // Загружаем все чанки в области асинхронно
        final int finalMinX = minX;
        final int finalMaxX = maxX;
        final int finalMinZ = minZ;
        final int finalMaxZ = maxZ;

        // Проверяем, поддерживает ли мир асинхронную загрузку чанков
        boolean supportsAsync = true;
        try {
            supportsAsync = world.getClass().getMethod("isChunkLoaded", int.class, int.class) != null;
        } catch (NoSuchMethodException e) {
            supportsAsync = false;
        }

        if (supportsAsync) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                // Приоритетно загружаем чанки со спавнами
                loadChunkSafely(world, chunkX1, chunkZ1);
                loadChunkSafely(world, chunkX2, chunkZ2);

                // Затем загружаем остальные чанки
                for (int x = finalMinX; x <= finalMaxX; x++) {
                    for (int z = finalMinZ; z <= finalMaxZ; z++) {
                        // Пропускаем уже загруженные чанки спавнов
                        if ((x == chunkX1 && z == chunkZ1) || (x == chunkX2 && z == chunkZ2)) {
                            continue;
                        }

                        loadChunkSafely(world, x, z);

                        // Небольшая пауза между загрузками чанков
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            });
        } else {
            // Если асинхронная загрузка не поддерживается, загружаем синхронно
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Приоритетно загружаем чанки со спавнами
                loadChunkSafely(world, chunkX1, chunkZ1);
                loadChunkSafely(world, chunkX2, chunkZ2);

                // Затем загружаем остальные чанки
                for (int x = finalMinX; x <= finalMaxX; x++) {
                    for (int z = finalMinZ; z <= finalMaxZ; z++) {
                        // Пропускаем уже загруженные чанки спавнов
                        if ((x == chunkX1 && z == chunkZ1) || (x == chunkX2 && z == chunkZ2)) {
                            continue;
                        }

                        loadChunkSafely(world, x, z);
                    }
                }
            });
        }
    }

    // Безопасная загрузка чанка
    private void loadChunkSafely(World world, int x, int z) {
        try {
            // Проверяем, загружен ли чанк
            if (!world.isChunkLoaded(x, z)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        world.loadChunk(x, z, true);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Ошибка при загрузке чанка: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при проверке чанка: " + e.getMessage());
        }
    }

    /**
     * Безопасно телепортирует игрока с проверками
     * @param player Игрок
     * @param location Локация
     */
    private void safeTeleport(Player player, Location location) {
        if (location == null || location.getWorld() == null) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("Попытка телепортации игрока " + player.getName() + " в null локацию или мир");
            }
            return;
        }

        // Проверяем, загружен ли мир
        String worldName = location.getWorld().getName();
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("Мир " + worldName + " не загружен! Отмена телепортации для " + player.getName());
            }
            return;
        }

        // Проверяем, не находится ли игрок уже в этом месте
        Location playerLoc = player.getLocation();
        if (playerLoc.getWorld().getName().equals(worldName) &&
                playerLoc.distanceSquared(location) < 1.0) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Игрок " + player.getName() + " уже находится в этой локации. Пропускаем телепортацию.");
            }
            return;
        }

        // Запоминаем время телепортации для предотвращения двойной телепортации
        UUID playerId = player.getUniqueId();
        lastTeleportTime.put(playerId, System.currentTimeMillis());

        // Телепортируем игрока
        try {
            boolean success = player.teleport(location);
            if (success) {
                // Отмечаем время телепортации для анти-дюп защиты
                markTeleported(player.getUniqueId());
            }

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Телепортация игрока " + player.getName() + " в " +
                        formatLocation(location) + ": " + (success ? "успешно" : "неудачно"));
            }
        } catch (Exception e) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().severe("Ошибка при телепортации игрока " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    // Метод для добавления игрока в фазу окончания (вызывать, когда дуэль заканчивается)
    public void addPlayerToEndPhase(UUID playerId) {
        playersInEndPhase.add(playerId);
    }

    // Метод для удаления игрока из фазы окончания (вызывать, когда игрок полностью выходит из дуэли)
    public void removePlayerFromEndPhase(UUID playerId) {
        playersInEndPhase.remove(playerId);
    }

    // Проверка, находится ли игрок в фазе окончания
    public boolean isPlayerInEndPhase(UUID playerId) {
        return playersInEndPhase.contains(playerId);
    }

    // Метод для сохранения оригинального инвентаря
    private void saveOriginalInventory(Player player) {
        UUID playerId = player.getUniqueId();

        // Делаем глубокие копии для безопасности
        ItemStack[] inventoryCopy = new ItemStack[player.getInventory().getContents().length];
        ItemStack[] armorCopy = new ItemStack[player.getInventory().getArmorContents().length];

        // Копируем каждый предмет отдельно
        for (int i = 0; i < player.getInventory().getContents().length; i++) {
            ItemStack item = player.getInventory().getContents()[i];
            if (item != null) {
                inventoryCopy[i] = item.clone();
            }
        }

        for (int i = 0; i < player.getInventory().getArmorContents().length; i++) {
            ItemStack item = player.getInventory().getArmorContents()[i];
            if (item != null) {
                armorCopy[i] = item.clone();
            }
        }

        // Сохраняем копии
        originalInventories.put(playerId, inventoryCopy);
        originalArmor.put(playerId, armorCopy);

        // Сохраняем статус полета
        playerFlightStatus.put(playerId, player.getAllowFlight());
    }

    /**
     * Отменяет поиск дуэли без отправки сообщений
     * @param player Игрок для отмены
     */
    public void cancelDuelSilently(Player player) {
        UUID playerId = player.getUniqueId();

        // Отменяем таймер поиска
        if (searchTasks.containsKey(playerId)) {
            searchTasks.get(playerId).cancel();
            searchTasks.remove(playerId);
        }

        // Удаляем из очереди всех типов
        for (DuelType type : DuelType.values()) {
            queuedPlayers.get(type).remove(playerId);
        }

        // Возвращаем игрока с восстановлением инвентаря
        returnPlayer(player, true);
    }

    /**
     * Проверяет, находится ли игрок в очереди на дуэль определенного типа
     * @param playerId UUID игрока
     * @param type Тип дуэли
     * @return true, если игрок в очереди
     */
    public boolean isPlayerInQueue(UUID playerId, DuelType type) {
        return queuedPlayers.get(type).contains(playerId);
    }

    /**
     * Добавляет игрока в очередь на дуэль
     * @param player Игрок
     * @param type Тип дуэли
     */
    public void queuePlayer(Player player, DuelType type) {
        UUID playerId = player.getUniqueId();

        // ДОБАВЛЕНО: Проверка на открытые инвентари - защита от дюпа
        InventoryView openInventory = player.getOpenInventory();
        if (openInventory != null && openInventory.getTopInventory() != null) {
            // Проверяем, не открыт ли у игрока сундук, шалкер и т.д.
            if (openInventory.getTopInventory().getType() != InventoryType.CRAFTING &&
                    openInventory.getTopInventory().getType() != InventoryType.CREATIVE &&
                    openInventory.getTopInventory().getType() != InventoryType.PLAYER) {

                // Сообщение с матами для дюперов
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&4&lЗакрой нахрен сундуки и прочую хрень перед дуэлью!"));
                return;
            }
        }

        // ДОБАВЛЕНО: Проверяем, находится ли игрок в отсчете перед дуэлью
        if (isPlayerInCountdown(playerId)) {
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            plugin.getConfig().getString("messages.already-in-countdown",
                                    "&cВы не можете начать новый поиск дуэли, так как уже находитесь в подготовке к дуэли!")));
            return;
        }

        // ДОБАВЛЕНО: Проверяем, находится ли игрок уже в поиске дуэли
        if (isPlayerInQueue(playerId)) {
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            plugin.getConfig().getString("messages.already-in-search",
                                    "&cВы уже находитесь в поиске дуэли! Отмените текущий поиск с помощью /duel cancel.")));
            return;
        }

        // ДОБАВЛЕНО: Проверяем, находится ли игрок в активной дуэли
        if (isPlayerInDuel(playerId)) {
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            plugin.getConfig().getString("messages.already-in-duel",
                                    "&cВы не можете начать поиск дуэли, так как уже участвуете в дуэли!")));
            return;
        }

        // Сохраняем текущий инвентарь и локацию
//        savePlayerInventory(player);
//        saveOriginalLocation(player);

        // Инициализируем список, если его еще нет
        if (!queuedPlayers.containsKey(type)) {
            queuedPlayers.put(type, new ArrayList<>());
        }

        // Добавляем игрока в очередь
        queuedPlayers.get(type).add(playerId);

        // Отображаем сообщение о начале поиска
        String typeName = type.getConfigName(plugin);
        player.sendMessage(ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix") +
                        plugin.getConfig().getString("messages.search-started",
                                        "&aНачат поиск {type}! &7({time} сек)")
                                .replace("{type}", typeName)
                                .replace("{time}", String.valueOf(plugin.getConfig().getInt("timers.search-time", 30)))));

        // Отправляем кнопку отмены
        sendCancelButton(player);

        // Если в очереди есть другой игрок, подбираем арену или ставим в очередь
        if (queuedPlayers.get(type).size() >= 2) {
            UUID player1Id = queuedPlayers.get(type).get(0);
            UUID player2Id = queuedPlayers.get(type).get(1);

            // Удаляем из очереди
            queuedPlayers.get(type).remove(player1Id);
            queuedPlayers.get(type).remove(player2Id);

            // Отменяем поисковые таймеры
            if (searchTasks.containsKey(player1Id)) {
                searchTasks.get(player1Id).cancel();
                searchTasks.remove(player1Id);
            }
            if (searchTasks.containsKey(player2Id)) {
                searchTasks.get(player2Id).cancel();
                searchTasks.remove(player2Id);
            }

            // Получаем игроков
            Player player1 = Bukkit.getPlayer(player1Id);
            Player player2 = Bukkit.getPlayer(player2Id);

            if (player1 != null && player2 != null) {
                // Проверяем, есть ли свободная арена
                Arena freeArena = getNextFreeArena();

                if (freeArena != null) {
                    // Есть свободная арена, помечаем ее как занятую
                    occupiedArenas.add(freeArena.getId());

                    // Обновляем время последнего использования арены
                    arenaLastUsedTime.put(freeArena.getId(), System.currentTimeMillis());

                    // Добавляем арену в список недавно использованных
                    addToRecentlyUsedArenas(freeArena.getId());

                    // Предварительно загружаем арену
                    preloadArena(freeArena);

                    // Замораживаем игроков и начинаем отсчет
                    freezePlayersBeforeDuel(player1, player2, type, freeArena);

                    // Оповещаем игроков
                    // String message = ColorUtils.colorize(
                    //        plugin.getConfig().getString("messages.prefix") +
                    //                "&aНайден соперник! Подготовка к дуэли...");
                    // player1.sendMessage(message);
                    // player2.sendMessage(message);
                } else {
                    // Проверяем, есть ли арены в процессе восстановления
                    boolean hasRestoringArenas = false;
                    if (plugin.getRestoreManager() != null) {
                        for (Arena arena : plugin.getArenaManager().getArenas()) {
                            if (plugin.getRestoreManager().isArenaRestoring(arena.getId())) {
                                hasRestoringArenas = true;
                                break;
                            }
                        }
                    }

                    // Проверяем заблокированные арены
                    boolean hasBlockedArenas = false;
                    for (Arena arena : plugin.getArenaManager().getArenas()) {
                        if (isArenaBlocked(arena.getId())) {
                            hasBlockedArenas = true;
                            break;
                        }
                    }

                    // Выводим отладочную информацию
                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().info("Нет свободных арен. Всего арен: " +
                                plugin.getArenaManager().getArenas().size());
                        plugin.getLogger().info("Занятые арены: " + occupiedArenas.size());
                        plugin.getLogger().info("Есть восстанавливающиеся арены: " + hasRestoringArenas);
                        plugin.getLogger().info("Есть заблокированные арены: " + hasBlockedArenas);
                    }

                    // Формируем сообщение в зависимости от ситуации
                    String message;
                    if (hasRestoringArenas) {
                        message = ColorUtils.colorize(
                                plugin.getConfig().getString("messages.prefix") +
                                        "&eВсе арены заняты или восстанавливаются. Вы добавлены в очередь на арену.");
                    } else if (hasBlockedArenas) {
                        message = ColorUtils.colorize(
                                plugin.getConfig().getString("messages.prefix") +
                                        "&eВсе арены заняты или заблокированы. Вы добавлены в очередь на арену.");
                    } else {
                        message = ColorUtils.colorize(
                                plugin.getConfig().getString("messages.prefix") +
                                        "&eВсе арены заняты. Вы добавлены в очередь на арену.");
                    }

                    // Нет свободных арен, добавляем в очередь на арену
                    arenaQueue.add(new QueueEntry(player1Id, player2Id, type));

                    // Оповещаем игроков
                    player1.sendMessage(message);
                    player2.sendMessage(message);

                    // Показываем позицию в очереди
                    int position = arenaQueue.size();
                    String queueMessage = ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    "&eВаша позиция в очереди: &6" + position);
                    player1.sendMessage(queueMessage);
                    player2.sendMessage(queueMessage);
                }
            }
        } else {
            // Запускаем таймер поиска
            int searchTime = plugin.getConfig().getInt("timers.search-time", 30);

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                private int timeLeft = searchTime;

                @Override
                public void run() {
                    if (timeLeft <= 0) {
                        // Время истекло, соперник не найден
                        Player p = Bukkit.getPlayer(player.getUniqueId());
                        if (p != null) {
                            // Отправляем заголовок вместо сообщения в чат
                            plugin.getTitleManager().sendTitle(p, "no-opponent");

                            // Звук неудачи
                            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);

                            // Возвращаем игрока с восстановлением инвентаря - ВОТ ЭТУ СТРОКУ МЕНЯЕМ
                            returnPlayer(p, false);
                        }

                        // Удаляем из очереди
                        queuedPlayers.get(type).remove(player.getUniqueId());
                        playerArenas.remove(player.getUniqueId());

                        // Отменяем задачу
                        if (searchTasks.containsKey(player.getUniqueId())) {
                            searchTasks.get(player.getUniqueId()).cancel();
                            searchTasks.remove(player.getUniqueId());
                        }
                    } else {
                        // Обновляем заголовок о поиске
                        Player p = Bukkit.getPlayer(player.getUniqueId());
                        if (p != null) {
                            plugin.getTitleManager().sendTitle(p, "searching",
                                    "%time%", String.valueOf(timeLeft));

                            // Воспроизводим звук пиликанья каждую секунду
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
                        }
                        timeLeft--;
                    }
                }
            }, 0L, 20L); // Каждую секунду

            searchTasks.put(player.getUniqueId(), task);
        }
    }

    /**
     * Периодически проверяет очередь арен и освобождает арены
     */
    public void startArenaQueueChecker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Проверяем, есть ли игроки в очереди
            if (!arenaQueue.isEmpty()) {
                // Проверяем, есть ли свободные арены
                Arena freeArena = getNextFreeArena();

                if (freeArena != null) {
                    // Есть свободная арена, берем первую пару из очереди
                    QueueEntry entry = arenaQueue.poll();

                    // Проверяем, что игроки все еще онлайн
                    Player player1 = Bukkit.getPlayer(entry.player1Id);
                    Player player2 = Bukkit.getPlayer(entry.player2Id);

                    if (player1 != null && player2 != null && player1.isOnline() && player2.isOnline()) {
                        // Помечаем арену как занятую
                        occupiedArenas.add(freeArena.getId());

                        // Обновляем время последнего использования арены
                        arenaLastUsedTime.put(freeArena.getId(), System.currentTimeMillis());

                        // Добавляем арену в список недавно использованных
                        addToRecentlyUsedArenas(freeArena.getId());

                        // Предварительно загружаем арену
                        preloadArena(freeArena);

                        // Замораживаем игроков и начинаем отсчет
                        freezePlayersBeforeDuel(player1, player2, entry.type, freeArena);

                        // Оповещаем игроков
                        String message = ColorUtils.colorize(
                                plugin.getConfig().getString("messages.prefix") +
                                        "&aНайдена свободная арена! Подготовка к дуэли...");
                        player1.sendMessage(message);
                        player2.sendMessage(message);

                        if (plugin.getConfig().getBoolean("debug", false)) {
                            plugin.getLogger().info("Пара игроков из очереди перемещена на арену " + freeArena.getId());
                        }
                    } else {
                        // Один из игроков оффлайн, возвращаем того, кто онлайн
                        if (player1 != null && player1.isOnline()) {
                            returnPlayer(player1, true);
                            player1.sendMessage(ColorUtils.colorize(
                                    plugin.getConfig().getString("messages.prefix") +
                                            "&cВаш противник вышел с сервера. Дуэль отменена."));
                        }

                        if (player2 != null && player2.isOnline()) {
                            returnPlayer(player2, true);
                            player2.sendMessage(ColorUtils.colorize(
                                    plugin.getConfig().getString("messages.prefix") +
                                            "&cВаш противник вышел с сервера. Дуэль отменена."));
                        }
                    }
                }
            }

            // Проверяем, нет ли "зависших" арен
            long currentTime = System.currentTimeMillis();
            Set<String> arenasToFree = new HashSet<>();

            for (Map.Entry<String, Long> entry : arenaLastUsedTime.entrySet()) {
                String arenaId = entry.getKey();
                long lastUsed = entry.getValue();

                // Если арена была занята более 30 минут, считаем ее "зависшей"
                if (occupiedArenas.contains(arenaId) && currentTime - lastUsed > 30 * 60 * 1000) {
                    arenasToFree.add(arenaId);
                    plugin.getLogger().warning("Арена " + arenaId + " была занята более 30 минут. Принудительное освобождение.");
                }
            }

            // Освобождаем "зависшие" арены
            for (String arenaId : arenasToFree) {
                occupiedArenas.remove(arenaId);
            }
        }, 20L * 5, 20L * 5); // Проверка каждые 5 секунд
    }

    // Новый метод для сохранения полного инвентаря
    /**
     * Сохраняет инвентарь игрока перед дуэлью
     */
// Найдите метод, который сохраняет инвентарь игрока
    /**
     * Сохраняет инвентарь игрока перед дуэлью
     */
    private void savePlayerInventory(Player player) {
        UUID playerId = player.getUniqueId();

        // Сохраняем инвентарь только если еще не сохранили
        if (!originalInventories.containsKey(playerId)) {
            // Создаем копии предметов
            ItemStack[] inventoryCopy = new ItemStack[player.getInventory().getContents().length];
            ItemStack[] armorCopy = new ItemStack[player.getInventory().getArmorContents().length];

            // Копируем каждый предмет
            for (int i = 0; i < player.getInventory().getContents().length; i++) {
                ItemStack item = player.getInventory().getContents()[i];
                if (item != null) {
                    inventoryCopy[i] = item.clone();
                }
            }

            for (int i = 0; i < player.getInventory().getArmorContents().length; i++) {
                ItemStack item = player.getInventory().getArmorContents()[i];
                if (item != null) {
                    armorCopy[i] = item.clone();
                }
            }

            // Сохраняем копии
            originalInventories.put(playerId, inventoryCopy);
            originalArmor.put(playerId, armorCopy);
        }
    }

    private void freezePlayersBeforeDuel(Player player1, Player player2, DuelType type, Arena arena) {
        // Сохраняем локации игроков
        saveOriginalLocation(player1);
        saveOriginalLocation(player2);

        // Добавляем игроков в список подготовки ПЕРЕД телепортацией
        duelCountdownPlayers.add(player1.getUniqueId());
        duelCountdownPlayers.add(player2.getUniqueId());

        // Создаем временную "дуэль" для обоих игроков, чтобы они считались "в дуэли"
        // Это нужно только для проверки телепортации, реальная дуэль будет создана позже
        Duel tempDuel = new Duel(player1.getUniqueId(), player2.getUniqueId(), type, arena);
        playerDuels.put(player1.getUniqueId(), tempDuel);
        playerDuels.put(player2.getUniqueId(), tempDuel);

        // Сохраняем статус полета и отключаем его
        playerFlightStatus.put(player1.getUniqueId(), player1.getAllowFlight());
        playerFlightStatus.put(player2.getUniqueId(), player2.getAllowFlight());

        // Отключаем полет
        player1.setAllowFlight(false);
        player1.setFlying(false);
        player2.setAllowFlight(false);
        player2.setFlying(false);

        // Сохраняем оригинальную скорость
        float base1 = player1.getWalkSpeed();
        float base2 = player2.getWalkSpeed();
        originalWalkSpeed.put(player1.getUniqueId(), base1);
        originalWalkSpeed.put(player2.getUniqueId(), base2);

        // ОТЛАДКА: Логи для отслеживания скорости
        plugin.getLogger().info("[DEBUG] Сохранена скорость для игрока " + player1.getName() + ": " + base1);
        plugin.getLogger().info("[DEBUG] Сохранена скорость для игрока " + player2.getName() + ": " + base2);

        // Устанавливаем скорость в 2 раза больше текущей, но не более 1.0
        float newSpeed1 = Math.min(base1 * 2, 1.0f);
        float newSpeed2 = Math.min(base2 * 2, 1.0f);

        // ИСПРАВЛЕНО: Проверяем, что новая скорость не меньше минимальной скорости
        if (newSpeed1 < 0.2f) newSpeed1 = 0.2f;
        if (newSpeed2 < 0.2f) newSpeed2 = 0.2f;

        // ОТЛАДКА: Логи для отслеживания новой скорости
        plugin.getLogger().info("[DEBUG] Новая скорость для игрока " + player1.getName() + ": " + newSpeed1);
        plugin.getLogger().info("[DEBUG] Новая скорость для игрока " + player2.getName() + ": " + newSpeed2);

        // Устанавливаем новую скорость для обоих игроков
        player1.setWalkSpeed(newSpeed1);
        player2.setWalkSpeed(newSpeed2);

        // Телепортируем игроков на арену
        plugin.getLogger().info("[DEBUG] Телепортация игрока " + player1.getName() + " на спавн 1");
        safeTeleport(player1, arena.getSpawn1());

        plugin.getLogger().info("[DEBUG] Телепортация игрока " + player2.getName() + " на спавн 2");
        safeTeleport(player2, arena.getSpawn2());

        // Удаляем временную дуэль после телепортации
        // Реальная дуэль будет создана после отсчета
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            playerDuels.remove(player1.getUniqueId());
            playerDuels.remove(player2.getUniqueId());
        }, 20L); // 1 секунда

        // Оптимизация: очистка инвентаря перед дуэлью для уменьшения объема данных
        if (plugin.getConfig().getBoolean("optimization.clean-inventory-before-duel", false)) {
            // Для дуэли без потерь мы сохраняем инвентарь, поэтому очищать не нужно
            if (type != DuelType.RANKED) {
                // Очищаем ненужные предметы из инвентаря
                cleanInventory(player1);
                cleanInventory(player2);
            }
        }

        // Отправляем сообщение о начале дуэли через 8 секунд
        String confirmMessage = ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix") +
                        "&eНайден соперник! Начало дуэли через &c8 секунд&e. Вы можете бегать по арене!");

        player1.sendMessage(confirmMessage);
        player2.sendMessage(confirmMessage);

        // Отправляем кнопку отмены
        sendCancelButton(player1);
        sendCancelButton(player2);

        // Запускаем отсчет с титлами
        startCountdown(player1, player2, type, arena);
    }

    /**
     * Восстанавливает исходное состояние игрока после дуэли или отмены дуэли.
     * @param player игрок, которого нужно разморозить
     */
    private void unfreezePlayer(Player player) {
        UUID uuid = player.getUniqueId();

        // ОТЛАДКА: Логируем начало разморозки
        plugin.getLogger().info("[DEBUG] Разморозка игрока " + player.getName());

        // Восстанавливаем исходную скорость ходьбы
        if (originalWalkSpeed.containsKey(uuid)) {
            float originalSpeed = originalWalkSpeed.get(uuid);
            player.setWalkSpeed(originalSpeed);
            plugin.getLogger().info("[DEBUG] Восстановлена скорость " + originalSpeed +
                    " для игрока " + player.getName());
            originalWalkSpeed.remove(uuid);
        } else {
            // Если сохраненная скорость не найдена, устанавливаем стандартную
            player.setWalkSpeed(0.2f);
            plugin.getLogger().info("[DEBUG] Установлена стандартная скорость 0.2 для игрока " +
                    player.getName() + " (сохраненная не найдена)");
        }

        // Восстанавливаем статус полёта
        if (playerFlightStatus.containsKey(uuid)) {
            boolean allowFlight = playerFlightStatus.get(uuid);
            player.setAllowFlight(allowFlight);
            // Если полет был разрешен, включаем его
            if (allowFlight) {
                player.setFlying(allowFlight);
                plugin.getLogger().info("[DEBUG] Восстановлен полет для игрока " + player.getName());
            }
            playerFlightStatus.remove(uuid);
        } else {
            plugin.getLogger().info("[DEBUG] Статус полета не был сохранен для игрока " + player.getName());
        }

        // Удаляем игрока из списка подготовки
        boolean wasInCountdown = duelCountdownPlayers.remove(uuid);
        plugin.getLogger().info("[DEBUG] Игрок " + player.getName() +
                (wasInCountdown ? " удален из списка подготовки" : " не был в списке подготовки"));
    }

    public void unfreezeAndCancelDuel(Player player) {
        UUID playerId = player.getUniqueId();

        // Удаляем игрока из списка отсчета
        duelCountdownPlayers.remove(playerId);

        // Восстанавливаем скорость
        if (originalWalkSpeed.containsKey(playerId)) {
            player.setWalkSpeed(originalWalkSpeed.get(playerId));
            originalWalkSpeed.remove(playerId);
        }

        // Удаляем игрока из списка замороженных (если он там есть)
        frozenPlayers.remove(playerId);

        // Находим второго игрока, который был в отсчете с этим игроком
        Player otherPlayer = null;
        UUID otherPlayerId = null;
        Arena playerArena = null;

        // Ищем другого игрока в списке отсчета
        for (UUID id : new HashSet<>(duelCountdownPlayers)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && player.getWorld().equals(p.getWorld()) &&
                    player.getLocation().distance(p.getLocation()) < 50) {
                otherPlayer = p;
                otherPlayerId = id;
                break;
            }
        }

        // Ищем арену, на которой был игрок
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            if (occupiedArenas.contains(arena.getId()) &&
                    (player.getWorld().equals(arena.getSpawn1().getWorld()) ||
                            player.getWorld().equals(arena.getSpawn2().getWorld()))) {
                playerArena = arena;
                break;
            }
        }

        // Если нашли другого игрока, отменяем подготовку и возвращаем его
        if (otherPlayer != null) {
            duelCountdownPlayers.remove(otherPlayerId);
            frozenPlayers.remove(otherPlayerId);

            // Восстанавливаем скорость для другого игрока
            if (originalWalkSpeed.containsKey(otherPlayerId)) {
                otherPlayer.setWalkSpeed(originalWalkSpeed.get(otherPlayerId));
                originalWalkSpeed.remove(otherPlayerId);
            }

            returnPlayer(otherPlayer, true);
            otherPlayer.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cПротивник вышел с сервера. Дуэль отменена."));

            // Звук отмены для другого игрока
            otherPlayer.playSound(otherPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }

        // Если нашли арену, освобождаем ее
        if (playerArena != null) {
            occupiedArenas.remove(playerArena.getId());
        }

        // ВАЖНОЕ ДОПОЛНЕНИЕ: Освобождаем arenas ВСЕГДА при вызове этого метода
        playerArenas.remove(playerId);

        // ВАЖНОЕ ДОПОЛНЕНИЕ: Удаляем дуэль из активных, если она есть
        playerDuels.remove(playerId);

        // ВАЖНОЕ ДОПОЛНЕНИЕ: Сохраняем этот статус выхода в файл, чтобы при входе можно было проверить
        savePlayerDuelStatusToFile(playerId, "CANCELLED_BY_QUIT");
    }

    /**
     * Сохраняет статус дуэли игрока в файл при выходе
     * @param playerId UUID игрока
     * @param status Статус дуэли ("CANCELLED_BY_QUIT" и т.д.)
     */
    private void savePlayerDuelStatusToFile(UUID playerId, String status) {
        try {
            File statusFolder = new File(plugin.getDataFolder(), "player_duel_status");
            if (!statusFolder.exists()) {
                statusFolder.mkdirs();
            }

            File statusFile = new File(statusFolder, playerId.toString() + ".dat");
            YamlConfiguration config = new YamlConfiguration();
            config.set("status", status);
            config.set("timestamp", System.currentTimeMillis());
            config.save(statusFile);

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Сохранен статус дуэли игрока " + playerId + ": " + status);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось сохранить статус дуэли игрока: " + e.getMessage());
        }
    }

    /**
     * Загружает статус дуэли игрока из файла
     * @param playerId UUID игрока
     * @return Последний сохраненный статус или null
     */
    public String loadPlayerDuelStatusFromFile(UUID playerId) {
        try {
            File statusFile = new File(plugin.getDataFolder() + "/player_duel_status", playerId.toString() + ".dat");
            if (!statusFile.exists()) {
                return null;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(statusFile);
            String status = config.getString("status");
            long timestamp = config.getLong("timestamp", 0);

            // Проверяем, не устарел ли статус (30 минут по умолчанию)
            long expirationMinutes = plugin.getConfig().getLong("status_saving.expiration_time", 30);
            if (System.currentTimeMillis() - timestamp > expirationMinutes * 60 * 1000) {
                statusFile.delete();
                return null;
            }

            // Удаляем файл после прочтения
            statusFile.delete();

            return status;
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось загрузить статус дуэли игрока: " + e.getMessage());
            return null;
        }
    }

    /**
     * Полностью очищает состояние игрока, связанное с дуэлями
     * @param playerId UUID игрока
     */
    public void cleanupPlayerState(UUID playerId) {
        // Удаляем из списка отсчёта
        duelCountdownPlayers.remove(playerId);

        // Удаляем из списка замороженных
        frozenPlayers.remove(playerId);

        // Удаляем из списка дуэлей
        playerDuels.remove(playerId);

        // Удаляем арену, связанную с игроком
        playerArenas.remove(playerId);

        // Удаляем оригинальную скорость ходьбы
        originalWalkSpeed.remove(playerId);

        // Удаляем статус полёта
        playerFlightStatus.remove(playerId);

        // Отменяем задачи возврата, если они есть
        if (delayedReturnTasks.containsKey(playerId)) {
            delayedReturnTasks.get(playerId).cancel();
            delayedReturnTasks.remove(playerId);
        }

        // Отменяем проверку полёта
        stopFlightCheck(playerId);

        // Удаляем из всех очередей
        for (DuelType type : DuelType.values()) {
            queuedPlayers.get(type).remove(playerId);
        }
    }

    /**
     * Отмечает время последней телепортации игрока
     * @param playerId UUID игрока
     */
    public void markTeleported(UUID playerId) {
        lastTeleportTimes.put(playerId, System.currentTimeMillis());
    }

    /**
     * Проверяет, прошло ли достаточно времени с последней телепортации
     * @param playerId UUID игрока
     * @param cooldownMs Время задержки в миллисекундах
     * @return true, если задержка после телепортации прошла
     */
    public boolean isTeleportCooldownOver(UUID playerId, long cooldownMs) {
        if (!lastTeleportTimes.containsKey(playerId)) {
            return true;
        }

        long timeSinceTeleport = System.currentTimeMillis() - lastTeleportTimes.get(playerId);
        return timeSinceTeleport > cooldownMs;
    }

    // Добавляем новый метод для телепортации без восстановления инвентаря
    private void teleportPlayerToSavedLocation(Player player) {
        UUID playerId = player.getUniqueId();
        Location location = null;

        // Сначала проверяем originalWorldLocations - там хранится локация ДО входа в мир дуэли
        if (originalWorldLocations.containsKey(playerId)) {
            location = originalWorldLocations.get(playerId);
            originalWorldLocations.remove(playerId); // Удаляем запись
        } else if (playerLocations.containsKey(playerId)) {
            location = playerLocations.get(playerId);
            playerLocations.remove(playerId); // Удаляем запись
        }

        // Проверяем, что нашли локацию и она валидна
        if (location != null && location.getWorld() != null) {
            safeTeleport(player, location);

            // Удаляем сохраненные данные инвентаря БЕЗ восстановления
            originalInventories.remove(playerId);
            originalArmor.remove(playerId);

            // Разблокируем команды
            if (player.hasMetadata("restduels_blocked_commands")) {
                player.removeMetadata("restduels_blocked_commands", plugin);
            }

            // Если есть CommandBlocker, разблокируем команды
            try {
                CommandBlocker commandBlocker = plugin.getCommandBlocker();
                if (commandBlocker != null) {
                    commandBlocker.removePlayer(playerId);
                }
            } catch (Exception e) {
                // Игнорируем ошибки, если класс или методы не найдены
            }
        } else {
            // Если не нашли локацию, телепортируем на спавн
            World world = Bukkit.getWorlds().get(0);
            player.teleport(world.getSpawnLocation());

            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cНе удалось найти исходную позицию. Вы были телепортированы на спавн."));
        }
    }

    /**
     * Проверяет, находится ли игрок в процессе отсчета перед дуэлью
     * @param playerId UUID игрока
     * @return true, если игрок в процессе отсчета
     */
    public boolean isPlayerInCountdown(UUID playerId) {
        return duelCountdownPlayers.contains(playerId);
    }



    public void returnPlayer(Player player, boolean restoreInventory) {
        UUID playerId = player.getUniqueId();

        // Логируем действие, если включен режим отладки
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Начинаем возврат игрока " + player.getName() +
                    " (UUID: " + playerId + "), restoreInventory=" + restoreInventory);
        }

        // ДОБАВЛЕНО: Восстанавливаем полное здоровье ВСЕГДА при возвращении
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20.0f);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Восстановлено полное здоровье для " + player.getName());
        }

        // Удаляем из списков
        frozenPlayers.remove(playerId);
        playerArenas.remove(playerId);

        // Отменяем проверку полета
        stopFlightCheck(playerId);

        // Восстанавливаем статус полета
        if (playerFlightStatus.containsKey(playerId)) {
            boolean allowFlight = playerFlightStatus.get(playerId);
            player.setAllowFlight(allowFlight);
            if (allowFlight) player.setFlying(allowFlight);
            playerFlightStatus.remove(playerId);

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Восстановлен статус полета для " + player.getName() + ": " + allowFlight);
            }
        }

        // Телепортируем на исходную позицию
        Location teleportLocation = null;
        boolean locationFound = false;

        // ИЗМЕНЕНО: Сначала проверяем originalWorldLocations (сохраненная локация ДО входа в мир дуэли)
        if (originalWorldLocations.containsKey(playerId)) {
            teleportLocation = originalWorldLocations.get(playerId);
            if (teleportLocation != null && teleportLocation.getWorld() != null) {
                locationFound = true;

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Найдена оригинальная локация для " + player.getName() + ": " +
                            formatLocation(teleportLocation));
                }
            }
        }

        // Если не нашли в originalWorldLocations, проверяем playerLocations
        if (!locationFound && playerLocations.containsKey(playerId)) {
            teleportLocation = playerLocations.get(playerId);
            if (teleportLocation != null && teleportLocation.getWorld() != null) {
                locationFound = true;

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Найдена локация в playerLocations для " + player.getName() + ": " +
                            formatLocation(teleportLocation));
                }
            }
        }

        // ДОБАВЛЕНО: Если не нашли локацию или мир null, используем спавн основного мира
        if (!locationFound || teleportLocation == null || teleportLocation.getWorld() == null) {
            World defaultWorld = Bukkit.getWorlds().get(0); // Получаем первый (обычно основной) мир
            teleportLocation = defaultWorld.getSpawnLocation();

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("Не удалось найти сохраненную локацию для " + player.getName() +
                        ", используем спавн мира: " + formatLocation(teleportLocation));
            }
        }

        // ИЗМЕНЕНО: Безопасно телепортируем игрока с проверками
        if (teleportLocation != null && teleportLocation.getWorld() != null) {
            // Проверяем, загружен ли мир
            String worldName = teleportLocation.getWorld().getName();
            World world = Bukkit.getWorld(worldName);

            if (world == null) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("Мир " + worldName + " не загружен! Используем спавн основного мира.");
                }
                world = Bukkit.getWorlds().get(0);
                teleportLocation = world.getSpawnLocation();
            }

            // Проверяем, находится ли игрок в мире дуэли
            if (isInDuelWorld(player.getWorld().getName())) {
                // Если игрок в мире дуэли, телепортируем его обратно
                safeTeleport(player, teleportLocation);
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&aВы были телепортированы на исходную позицию."));

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Игрок " + player.getName() + " телепортирован из мира дуэли на: " +
                            formatLocation(teleportLocation));
                }
            }
        } else {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().severe("Критическая ошибка: teleportLocation == null или мир == null для " + player.getName());
            }

            // В случае ошибки, телепортируем на спавн основного мира
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cПроизошла ошибка при телепортации. Вы были телепортированы на спавн."));
        }

        // ИСПРАВЛЕН ДЮП: Убираем восстановление инвентаря, просто чистим записи
        if (restoreInventory) {
            // Проверяем, не пытается ли игрок дюпать
            if (System.currentTimeMillis() - lastTeleportTime.getOrDefault(playerId, 0L) < 60000) {
            }

            // Стандартное восстановление инвентаря и брони
            if (originalInventories.containsKey(playerId)) {
                player.getInventory().setContents(originalInventories.get(playerId));
                originalInventories.remove(playerId);
            }
            if (originalArmor.containsKey(playerId)) {
                player.getInventory().setArmorContents(originalArmor.get(playerId));
                originalArmor.remove(playerId);
            }
        } else {
            // Если не надо возвращать вещи - просто очищаем записи
            originalInventories.remove(playerId);
            originalArmor.remove(playerId);
            // НЕ ДЕЛАЕМ .clear() и setArmorContents! Просто оставляем инвентарь как есть!
        }

        // Очищаем сохраненные локации
        originalWorldLocations.remove(playerId);
        playerLocations.remove(playerId);

        // Обновляем инвентарь
        player.updateInventory();

        // Разблокируем команды
        if (player.hasMetadata("restduels_blocked_commands")) {
            player.removeMetadata("restduels_blocked_commands", plugin);
        }

        // Если есть CommandBlocker, разблокируем команды
        try {
            CommandBlocker commandBlocker = plugin.getCommandBlocker();
            if (commandBlocker != null) {
                commandBlocker.removePlayer(playerId);
            }
        } catch (Exception e) {
            // Игнорируем ошибки, если класс или методы не найдены
        }
    }

    /**
     * Форматирует локацию для вывода в лог
     * @param location Локация
     * @return Отформатированная строка
     */
    private String formatLocation(Location location) {
        if (location == null) return "null";
        if (location.getWorld() == null) return "world=null, x=" + location.getX() + ", y=" + location.getY() + ", z=" + location.getZ();
        return "world=" + location.getWorld().getName() + ", x=" + location.getX() + ", y=" + location.getY() + ", z=" + location.getZ();
    }

    /**
     * Получает мир, в котором игрок был до дуэли
     * @param playerId UUID игрока
     * @return Имя мира или null, если не найдено
     */
    public String getPlayerOriginalWorld(UUID playerId) {
        Location loc = getOriginalDuelLocation(playerId);
        if (loc != null && loc.getWorld() != null) {
            return loc.getWorld().getName();
        }
        return null;
    }

    // В метод clearPlayerData добавить:
    private void clearPlayerData(UUID playerId) {
        originalInventories.remove(playerId);
        originalArmor.remove(playerId);
        playerFlightStatus.remove(playerId);
        // ДОБАВЛЕНО: Очищаем сохраненное здоровье
        clearSavedPlayerHealth(playerId);
    }

    /**
     * Проверяет, находится ли игрок в очереди на дуэль
     * @param playerId UUID игрока
     * @return true, если игрок в очереди
     */
    public boolean isPlayerInQueue(UUID playerId) {
        // Проверяем все типы очередей
        for (DuelType type : DuelType.values()) {
            if (queuedPlayers.get(type).contains(playerId)) {
                return true;
            }
        }

        // Проверяем, находится ли игрок в очереди на арену
        for (QueueEntry entry : arenaQueue) {
            if (entry.player1Id.equals(playerId) || entry.player2Id.equals(playerId)) {
                return true;
            }
        }

        return false;
    }

    // Метод для очистки инвентаря от ненужных предметов
    private void cleanInventory(Player player) {
        // Получаем список материалов, которые следует удалить
        List<String> materialsToClean = plugin.getConfig().getStringList("optimization.materials-to-clean");

        // Если список пуст, ничего не делаем
        if (materialsToClean.isEmpty()) {
            return;
        }

        // Преобразуем строки в материалы
        List<Material> materials = new ArrayList<>();
        for (String materialName : materialsToClean) {
            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                materials.add(material);
            } catch (IllegalArgumentException e) {
                // Игнорируем неверные материалы
            }
        }

        // Очищаем инвентарь от указанных предметов
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && materials.contains(item.getType())) {
                contents[i] = null;
            }
        }
        player.getInventory().setContents(contents);
    }

    // Новый метод для восстановления инвентаря
    private void restorePlayerInventory(Player player) {
        UUID playerId = player.getUniqueId();

        // Восстанавливаем основное содержимое инвентаря
        if (playerInventories.containsKey(playerId)) {
            ItemStack[] contents = playerInventories.get(playerId);
            if (contents != null) {
                player.getInventory().setContents(contents);
            }
        }

        // Восстанавливаем броню
        if (playerArmor.containsKey(playerId)) {
            ItemStack[] armor = playerArmor.get(playerId);
            if (armor != null) {
                player.getInventory().setArmorContents(armor);
            }
        }

        // Восстанавливаем статус полета
        if (playerFlightStatus.containsKey(playerId)) {
            boolean allowFlight = playerFlightStatus.get(playerId);
            player.setAllowFlight(allowFlight);
            if (allowFlight) player.setFlying(allowFlight);
        }

        // Очищаем сохраненные данные
        clearSavedPlayerData(playerId);
    }

    // Новый метод для очистки сохраненных данных игрока
    private void clearSavedPlayerData(UUID playerId) {
        playerInventories.remove(playerId);
        playerArmor.remove(playerId);
        playerFlightStatus.remove(playerId);
        // Удалить другие сохраненные атрибуты при необходимости
    }

    private void startCountdown(Player player1, Player player2, DuelType type, Arena arena) {
        final int[] countdown = {8}; // Начальное значение отсчета

        final int[] taskId = {-1}; // Сохраняем ID задачи для отмены

        BukkitTask countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                // Проверяем, что оба игрока все еще онлайн перед каждым тиком отсчета
                if (!player1.isOnline() || !player2.isOnline() ||
                        !duelCountdownPlayers.contains(player1.getUniqueId()) ||
                        !duelCountdownPlayers.contains(player2.getUniqueId())) {

                    // Один из игроков вышел или был удален из списка - отменяем отсчет
                    if (taskId[0] != -1) {
                        Bukkit.getScheduler().cancelTask(taskId[0]);
                        plugin.getLogger().info("[DEBUG] Отсчет отменен: игрок вышел или удален из списка");
                    }

                    // Определяем, кто остался онлайн
                    Player remainingPlayer = null;

                    if (player1.isOnline() && duelCountdownPlayers.contains(player1.getUniqueId())) {
                        remainingPlayer = player1;
                        duelCountdownPlayers.remove(player1.getUniqueId());
                        // Восстанавливаем скорость
                        if (originalWalkSpeed.containsKey(player1.getUniqueId())) {
                            float origSpeed = originalWalkSpeed.get(player1.getUniqueId());
                            player1.setWalkSpeed(origSpeed);
                            plugin.getLogger().info("[DEBUG] Восстановлена скорость игрока " + player1.getName() + " к " + origSpeed);
                            originalWalkSpeed.remove(player1.getUniqueId());
                        }
                    } else if (player2.isOnline() && duelCountdownPlayers.contains(player2.getUniqueId())) {
                        remainingPlayer = player2;
                        duelCountdownPlayers.remove(player2.getUniqueId());
                        // Восстанавливаем скорость
                        if (originalWalkSpeed.containsKey(player2.getUniqueId())) {
                            float origSpeed = originalWalkSpeed.get(player2.getUniqueId());
                            player2.setWalkSpeed(origSpeed);
                            plugin.getLogger().info("[DEBUG] Восстановлена скорость игрока " + player2.getName() + " к " + origSpeed);
                            originalWalkSpeed.remove(player2.getUniqueId());
                        }
                    }

                    // Если кто-то остался, возвращаем его и отправляем сообщение
                    if (remainingPlayer != null) {
                        remainingPlayer.sendMessage(ColorUtils.colorize(
                                plugin.getConfig().getString("messages.prefix") +
                                        "&cПротивник вышел с сервера. Дуэль отменена."));

                        // Звук отмены
                        remainingPlayer.playSound(remainingPlayer.getLocation(),
                                Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

                        // Возвращаем игрока
                        returnPlayer(remainingPlayer, true);
                    }

                    // Освобождаем арену
                    if (arena != null) {
                        occupiedArenas.remove(arena.getId());
                        plugin.getLogger().info("[DEBUG] Арена " + arena.getId() + " освобождена из-за отмены отсчета");
                    }

                    return;
                }

                if (countdown[0] <= 0) {
                    // Отсчет завершен, еще раз проверяем, что оба игрока онлайн
                    if (player1.isOnline() && player2.isOnline() &&
                            duelCountdownPlayers.contains(player1.getUniqueId()) &&
                            duelCountdownPlayers.contains(player2.getUniqueId())) {

                        // Отменяем текущую задачу отсчета
                        if (taskId[0] != -1) {
                            Bukkit.getScheduler().cancelTask(taskId[0]);
                            plugin.getLogger().info("[DEBUG] Отсчет завершен, начинаем дуэль");
                        }

                        // ИСПРАВЛЕНО: Восстанавливаем нормальную скорость для обоих игроков
                        UUID player1Id = player1.getUniqueId();
                        UUID player2Id = player2.getUniqueId();

                        if (originalWalkSpeed.containsKey(player1Id)) {
                            float origSpeed1 = originalWalkSpeed.get(player1Id);
                            player1.setWalkSpeed(origSpeed1);
                            plugin.getLogger().info("[DEBUG] Восстановлена скорость игрока " +
                                    player1.getName() + " к " + origSpeed1);
                            originalWalkSpeed.remove(player1Id);
                        } else {
                            // Если не найдена сохраненная скорость, устанавливаем стандартную
                            player1.setWalkSpeed(0.2f);
                            plugin.getLogger().info("[DEBUG] Установлена стандартная скорость для игрока " +
                                    player1.getName() + " (0.2)");
                        }

                        if (originalWalkSpeed.containsKey(player2Id)) {
                            float origSpeed2 = originalWalkSpeed.get(player2Id);
                            player2.setWalkSpeed(origSpeed2);
                            plugin.getLogger().info("[DEBUG] Восстановлена скорость игрока " +
                                    player2.getName() + " к " + origSpeed2);
                            originalWalkSpeed.remove(player2Id);
                        } else {
                            // Если не найдена сохраненная скорость, устанавливаем стандартную
                            player2.setWalkSpeed(0.2f);
                            plugin.getLogger().info("[DEBUG] Установлена стандартная скорость для игрока " +
                                    player2.getName() + " (0.2)");
                        }

                        // ИСПРАВЛЕНО: Телепортируем игроков обратно на их начальные позиции
                        safeTeleport(player1, arena.getSpawn1());
                        safeTeleport(player2, arena.getSpawn2());
                        plugin.getLogger().info("[DEBUG] Игроки телепортированы на начальные позиции арены");

                        // Удаляем игроков из списка отсчета
                        duelCountdownPlayers.remove(player1.getUniqueId());
                        duelCountdownPlayers.remove(player2.getUniqueId());

                        // Начинаем дуэль
                        startDuel(player1, player2, type, arena);
                    } else {
                        // Один из игроков вышел в последний момент
                        // Отменяем текущую задачу отсчета
                        if (taskId[0] != -1) {
                            Bukkit.getScheduler().cancelTask(taskId[0]);
                            plugin.getLogger().info("[DEBUG] Игрок вышел в последний момент отсчета");
                        }

                        // Определяем, кто остался онлайн
                        Player remainingPlayer = null;

                        if (player1.isOnline() && duelCountdownPlayers.contains(player1.getUniqueId())) {
                            remainingPlayer = player1;
                            duelCountdownPlayers.remove(player1.getUniqueId());
                            // Восстанавливаем скорость
                            if (originalWalkSpeed.containsKey(player1.getUniqueId())) {
                                float origSpeed = originalWalkSpeed.get(player1.getUniqueId());
                                player1.setWalkSpeed(origSpeed);
                                plugin.getLogger().info("[DEBUG] Восстановлена скорость игрока " + player1.getName() + " к " + origSpeed);
                                originalWalkSpeed.remove(player1.getUniqueId());
                            }
                        } else if (player2.isOnline() && duelCountdownPlayers.contains(player2.getUniqueId())) {
                            remainingPlayer = player2;
                            duelCountdownPlayers.remove(player2.getUniqueId());
                            // Восстанавливаем скорость
                            if (originalWalkSpeed.containsKey(player2.getUniqueId())) {
                                float origSpeed = originalWalkSpeed.get(player2.getUniqueId());
                                player2.setWalkSpeed(origSpeed);
                                plugin.getLogger().info("[DEBUG] Восстановлена скорость игрока " + player2.getName() + " к " + origSpeed);
                                originalWalkSpeed.remove(player2.getUniqueId());
                            }
                        }

                        // Если кто-то остался, возвращаем его и отправляем сообщение
                        if (remainingPlayer != null) {
                            remainingPlayer.sendMessage(ColorUtils.colorize(
                                    plugin.getConfig().getString("messages.prefix") +
                                            "&cПротивник вышел с сервера. Дуэль отменена."));

                            // Звук отмены
                            remainingPlayer.playSound(remainingPlayer.getLocation(),
                                    Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

                            // Возвращаем игрока
                            returnPlayer(remainingPlayer, true);
                        }

                        // Освобождаем арену
                        if (arena != null) {
                            occupiedArenas.remove(arena.getId());
                            plugin.getLogger().info("[DEBUG] Арена " + arena.getId() + " освобождена из-за выхода игрока");
                        }
                    }

                    return;
                }

                // Определяем цвет в зависимости от оставшегося времени
                String color;
                if (countdown[0] > 6) {
                    color = "&a"; // Зеленый для 8-7
                } else if (countdown[0] > 3) {
                    color = "&e"; // Желтый для 6-4
                } else {
                    color = "&c"; // Красный для 3-1
                }

                // Отправляем титл с отсчетом
                String countTitle = color + countdown[0];
                String countSubtitle = "&eПодготовьтесь к дуэли!";

                if (player1.isOnline() && duelCountdownPlayers.contains(player1.getUniqueId())) {
                    player1.sendTitle(
                            ChatColor.translateAlternateColorCodes('&', countTitle),
                            ChatColor.translateAlternateColorCodes('&', countSubtitle),
                            5, 10, 5
                    );

                    // Звук отсчета
                    Sound countSound = (countdown[0] > 1) ? Sound.BLOCK_NOTE_BLOCK_HAT : Sound.BLOCK_NOTE_BLOCK_BASS;
                    float pitch = (countdown[0] > 1) ? 1.0f : 1.5f;
                    player1.playSound(player1.getLocation(), countSound, 1.0f, pitch);
                }

                if (player2.isOnline() && duelCountdownPlayers.contains(player2.getUniqueId())) {
                    player2.sendTitle(
                            ChatColor.translateAlternateColorCodes('&', countTitle),
                            ChatColor.translateAlternateColorCodes('&', countSubtitle),
                            5, 10, 5
                    );

                    // Звук отсчета
                    Sound countSound = (countdown[0] > 1) ? Sound.BLOCK_NOTE_BLOCK_HAT : Sound.BLOCK_NOTE_BLOCK_BASS;
                    float pitch = (countdown[0] > 1) ? 1.0f : 1.5f;
                    player2.playSound(player2.getLocation(), countSound, 1.0f, pitch);
                }

                // ОТЛАДКА: Логируем текущее состояние отсчета
                plugin.getLogger().info("[DEBUG] Отсчет: " + countdown[0]);

                // Уменьшаем счетчик
                countdown[0]--;
            }
        }, 0L, 20L); // Каждую секунду

        // Сохраняем ID задачи
        taskId[0] = countdownTask.getTaskId();
        plugin.getLogger().info("[DEBUG] Запущен отсчет с ID задачи: " + taskId[0]);
    }

    // Добавь этот метод в класс DuelManager
    public void sendPreparationMessage(Player player) {
        sendCancelButton(player);
    }

    /**
     * Проверяет, находится ли игрок в стадии подготовки к дуэли
     * @param playerId UUID игрока
     * @return true, если игрок в стадии подготовки
     */
    public boolean isPlayerInPreparation(UUID playerId) {
        return duelCountdownPlayers.contains(playerId);
    }

    /**
     * Метод для начала дуэли с автоматическим восстановлением арены
     */
    public void startDuel(Player player1, Player player2, DuelType type, Arena arena) {
        if (arena == null) {
            player1.sendMessage(ChatColor.RED + "Нет доступных арен для дуэли!");
            player2.sendMessage(ChatColor.RED + "Нет доступных арен для дуэли!");
            returnPlayer(player1, true);
            returnPlayer(player2, true);
            return;
        }

        // ИЗМЕНЕНО: Сохраняем инвентарь и локацию ЗДЕСЬ, когда дуэль реально началась
        savePlayerInventory(player1);
        savePlayerInventory(player2);
        saveOriginalLocation(player1);
        saveOriginalLocation(player2);

        // ДОБАВЛЕНО: Для ранкед дуэлей сохраняем здоровье
        if (type == DuelType.RANKED) {
            savePlayerHealth(player1);
            savePlayerHealth(player2);

            // Восстанавливаем полное здоровье в начале дуэли
            player1.setHealth(player1.getMaxHealth());
            player2.setHealth(player2.getMaxHealth());
            player1.setFoodLevel(20);
            player2.setFoodLevel(20);
            player1.setSaturation(20.0f);
            player2.setSaturation(20.0f);

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Ранкед дуэль: здоровье игроков восстановлено до максимума");
            }
        }

        // Восстанавливаем арену при входе игроков
        restoreArenaWhenPlayersEnter(arena);

        // Проверяем, включена ли опция отключения режима бога
        if (plugin.getConfig().getBoolean("godmode.disable-during-duel", true)) {
            // Отключаем режим бога у игроков, если он включен
            disableGodModeIfEnabled(player1);
            disableGodModeIfEnabled(player2);
        }

        // Создаем дуэль
        Duel duel = new Duel(player1.getUniqueId(), player2.getUniqueId(), type, arena);

        // Регистрируем дуэль
        playerDuels.put(player1.getUniqueId(), duel);
        playerDuels.put(player2.getUniqueId(), duel);

        // УДАЛЕНЫ СЛЕДУЮЩИЕ СТРОКИ:
        // sendCancelButton(player1);
        // sendCancelButton(player2);

        // Запускаем проверку полета для обоих игроков
        startFlightCheck(player1);
        startFlightCheck(player2);

        // Захватываем текущее состояние арены, если включено
        if (plugin.getConfig().getBoolean("restoration.auto-capture", true)) {
            plugin.getRestoreManager().captureArenaAreas(arena.getId());
        }

        // Отправляем заголовок о начале дуэли ОДИН РАЗ
        plugin.getTitleManager().sendTitle(player1, "duel-started");
        plugin.getTitleManager().sendTitle(player2, "duel-started");

        // Воспроизводим звук начала дуэли
        player1.playSound(player1.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.2f);
        player2.playSound(player2.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.2f);

        // Отправляем сообщение о начале дуэли
        String startMessage = plugin.getConfig().getString("messages.duel-started", "&aДуэль началась! Удачи!");
        player1.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.prefix") + startMessage));
        player2.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.prefix") + startMessage));

        // Запускаем таймер дуэли
        int duelTimeMinutes = plugin.getConfig().getInt("timers.duel-time", 20);
        int duelTimeSeconds = duelTimeMinutes * 60; // в секундах

        // Вместо ActionBar используем BossBar для отображения таймера
        startBossBarTimer(player1, player2, duel, duelTimeSeconds);

        // Основная задача для завершения дуэли по времени
        BukkitTask duelTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Время дуэли истекло - объявляем ничью
            if (isPlayerInDuel(player1.getUniqueId()) && isPlayerInDuel(player2.getUniqueId())) {
                // Отменяем таймер отображения
                if (duel.getTimerTask() != null) {
                    duel.getTimerTask().cancel();
                }

                if (player1.isOnline()) {
                    plugin.getTitleManager().sendTitle(player1, "duel-draw");
                    player1.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    "&eВремя дуэли истекло! &6Объявлена ничья."));
                }

                if (player2.isOnline()) {
                    plugin.getTitleManager().sendTitle(player2, "duel-draw");
                    player2.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    "&eВремя дуэли истекло! &6Объявлена ничья."));
                }

                // Удаляем дуэль и возвращаем игроков с задержкой
                endDuel(duel, null, true);
            }
        }, duelTimeSeconds * 20L);

        duel.setDuelTask(duelTask);
    }

    /**
     * Планирует отложенный возврат игрока
     * @param player Игрок для возврата
     * @param delaySeconds Задержка в секундах
     * @param restoreInventory Восстанавливать ли инвентарь
     */
    private void scheduleDelayedReturn(Player player, int delaySeconds, boolean restoreInventory) {
        if (player == null || !player.isOnline()) return;

        UUID playerId = player.getUniqueId();

        // Отменяем существующую задачу, если она есть
        if (delayedReturnTasks.containsKey(playerId)) {
            delayedReturnTasks.get(playerId).cancel();
        }

        // Создаем новую задачу
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                returnPlayer(player, restoreInventory);
            }
            delayedReturnTasks.remove(playerId);
        }, delaySeconds * 20L); // Преобразуем секунды в тики (20 тиков = 1 секунда)

        // Сохраняем задачу
        delayedReturnTasks.put(playerId, task);
    }

    /**
     * Планирует отложенный возврат игрока с восстановлением инвентаря
     * @param player Игрок для возврата
     * @param delaySeconds Задержка в секундах
     */
    private void scheduleDelayedReturn(Player player, int delaySeconds) {
        scheduleDelayedReturn(player, delaySeconds, true);
    }

    /**
     * Очищает все активные эффекты зелий у игрока
     * @param player Игрок для очистки эффектов
     */
    private void clearEffects(Player player) {
        if (player == null || !player.isOnline()) return;

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Очищены все эффекты у игрока " + player.getName());
        }
    }

    /**
     * Завершает дуэль с указанием победителя
     * @param duel Дуэль
     * @param winnerId UUID победителя
     */
    public void endDuel(Duel duel, UUID winnerId) {
        // Получаем ID игроков
        UUID player1Id = duel.getPlayer1Id();
        UUID player2Id = duel.getPlayer2Id();

        // Получаем игроков (если они онлайн)
        Player player1 = Bukkit.getPlayer(player1Id);
        Player player2 = Bukkit.getPlayer(player2Id);

        // ДОБАВЛЕНО: Очищаем все эффекты у игроков при окончании дуэли
        clearEffects(player1);
        clearEffects(player2);

        // Определяем победителя и проигравшего
        UUID loserId = winnerId.equals(player1Id) ? player2Id : player1Id;
        Player winner = winnerId.equals(player1Id) ? player1 : player2;
        Player loser = winnerId.equals(player1Id) ? player2 : player1;

        // Обновляем статистику
        plugin.getStatsManager().incrementWins(winnerId);
        plugin.getStatsManager().incrementDeaths(loserId);

        // Отправляем сообщение о результате дуэли
        if (winner != null && winner.isOnline()) {
            String winMessage = plugin.getConfig().getString("messages.duel-win");
            if (winMessage == null) {
                winMessage = "&aПоздравляем! Вы победили в дуэли!";
            }
            winner.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") + winMessage));
            winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            plugin.getTitleManager().sendTitle(winner, "duel-win");
        }

        if (loser != null && loser.isOnline()) {
            String loseMessage = plugin.getConfig().getString("messages.duel-lose");
            if (loseMessage == null) {
                loseMessage = "&cВы проиграли в дуэли!";
            }
            loser.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") + loseMessage));
            loser.playSound(loser.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
            plugin.getTitleManager().sendTitle(loser, "duel-lose");
        }

        // ДОБАВЛЕНО: Удаляем BossBar
        if (playerBossBars.containsKey(player1Id)) {
            BossBar bossBar = playerBossBars.get(player1Id);
            bossBar.removeAll();
            playerBossBars.remove(player1Id);
            playerBossBars.remove(player2Id);
        }

        // Отменяем таймер дуэли
        if (duel.getTimerTask() != null) {
            duel.getTimerTask().cancel();
        }

        // Удаляем дуэль из списка активных дуэлей
        playerDuels.remove(player1Id);
        playerDuels.remove(player2Id);

        // Освобождаем арену
        occupiedArenas.remove(duel.getArena().getId());

        // Проверяем очередь на арену
        checkArenaQueue();

        // ИЗМЕНЕНО: Даем время на сбор предметов в классических дуэлях
        if (duel.getType() == DuelType.NORMAL || duel.getType() == DuelType.CLASSIC) {
            // Проигравшего возвращаем сразу
            if (loser != null && loser.isOnline()) {
                // Для CLASSIC не используем отложенный возврат, а сразу очищаем инвентарь
                returnPlayer(loser, false);
                // НЕ вызываем scheduleDelayedReturn для проигравшего в CLASSIC
            }

// Победителю даем время собрать вещи
            if (winner != null && winner.isOnline()) {
                int collectTime = plugin.getConfig().getInt("duel.resource-collection-time", 60);
                winner.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                plugin.getConfig().getString("messages.winner-collect",
                                        "&aУ вас есть &c" + collectTime + " секунд &aдля сбора вещей.")));

                // Отправляем кнопку досрочного возврата
                sendEarlyReturnButton(winner);

                // Планируем отложенный возврат
                BukkitTask winnerTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (winner.isOnline() && isInDuelWorld(winner)) {
                        winner.sendMessage(ColorUtils.colorize(
                                plugin.getConfig().getString("messages.prefix") +
                                        "&aВремя сбора ресурсов истекло. Вы были телепортированы на исходную позицию с собранными вещами."));
                        returnPlayer(winner, false);
                    }

                    // Восстанавливаем арену
                    if (duel.getArena() != null) {
                        plugin.getRestoreManager().restoreArenaAreas(duel.getArena().getId());
                        forceRestoreArena(duel.getArena().getId());
                    }
                }, collectTime * 20L);

                delayedReturnTasks.put(winner.getUniqueId(), winnerTask);
            } else {
                // Если победитель оффлайн, просто восстанавливаем арену
                if (duel.getArena() != null) {
                    plugin.getRestoreManager().restoreArenaAreas(duel.getArena().getId());
                    forceRestoreArena(duel.getArena().getId());
                }
            }
        } else {
            // Для других типов дуэлей (RANKED) - сразу телепортируем обоих игроков без задержки
            if (player1 != null && player1.isOnline()) {
                returnPlayer(player1, true);
            }
            if (player2 != null && player2.isOnline()) {
                returnPlayer(player2, true);
            }
        }
    }

    /**
     * Завершает дуэль как ничью
     * @param duel Дуэль
     */
    private void endDuelAsDraw(Duel duel) {
        // Получаем ID игроков
        final UUID player1Id = duel.getPlayer1Id();
        final UUID player2Id = duel.getPlayer2Id();

        // Получаем игроков (если они онлайн)
        final Player player1 = Bukkit.getPlayer(player1Id);
        final Player player2 = Bukkit.getPlayer(player2Id);

        // ДОБАВЛЕНО: Очищаем все эффекты у игроков при ничьей
        clearEffects(player1);
        clearEffects(player2);

        // Отправляем сообщение о ничьей
        String drawMessage = ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix") +
                        plugin.getConfig().getString("messages.duel-draw", "&eВремя вышло! Дуэль завершилась ничьей."));

        if (player1 != null && player1.isOnline()) {
            player1.sendMessage(drawMessage);
            player1.playSound(player1.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 0.8f);
            plugin.getTitleManager().sendTitle(player1, "duel-draw");
        }

        if (player2 != null && player2.isOnline()) {
            player2.sendMessage(drawMessage);
            player2.playSound(player2.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 0.8f);
            plugin.getTitleManager().sendTitle(player2, "duel-draw");
        }

        // Удаляем BossBar
        if (playerBossBars.containsKey(player1Id)) {
            BossBar bossBar = playerBossBars.get(player1Id);
            bossBar.removeAll();
            playerBossBars.remove(player1Id);
            playerBossBars.remove(player2Id);
        }

        // Отменяем таймер дуэли
        if (duel.getTimerTask() != null) {
            duel.getTimerTask().cancel();
        }

        // Удаляем дуэль из списка активных дуэлей
        playerDuels.remove(player1Id);
        playerDuels.remove(player2Id);

        // Получаем арену (сохраняем как final)
        final Arena arena = duel.getArena();

        // Освобождаем арену
        if (arena != null) {
            occupiedArenas.remove(arena.getId());
        }

        // Проверяем очередь на арену
        checkArenaQueue();

        // Разное поведение в зависимости от типа дуэли
        if (duel.getType() == DuelType.RANKED) {
            // В режиме RANKED (без потерь) сразу возвращаем обоих игроков с восстановлением инвентаря
            if (player1 != null && player1.isOnline()) {
                // Мгновенное возвращение игрока и восстановление инвентаря
                returnPlayer(player1, true);

                // Специальное сообщение для ранкед-режима в случае ничьей
                player1.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                plugin.getConfig().getString("messages.ranked-duel-draw",
                                        "&eРанговая дуэль завершилась ничьей! Ваш инвентарь восстановлен.")));
            }

            if (player2 != null && player2.isOnline()) {
                // Мгновенное возвращение игрока и восстановление инвентаря
                returnPlayer(player2, true);

                // Специальное сообщение для ранкед-режима
                player2.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                plugin.getConfig().getString("messages.ranked-duel-draw",
                                        "&aРанговая дуэль завершилась ничьей! Ваш инвентарь восстановлен.")));
            }

            // Восстанавливаем арену
            restoreArenaAfterDuel(duel);
            return; // Важно добавить return, чтобы не выполнять логику для других типов дуэлей
        } else {
            // Для CLASSIC и NORMAL даем время на сбор предметов
            // При ничьей даем время собрать вещи обоим
            String delayMessage = ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            plugin.getConfig().getString("messages.delay-collect"));

            // Получаем время сбора ресурсов из конфига
            int collectTime = plugin.getConfig().getInt("duel.resource-collection-time", 60);

            // Создаем задачу для отложенного возврата игроков
            createDelayedReturnTask(duel, player1Id, player2Id, collectTime);

            // Отправляем сообщения и кнопки
            if (player1 != null && player1.isOnline()) {
                player1.sendMessage(delayMessage);
                sendEarlyReturnButton(player1);
            }

            if (player2 != null && player2.isOnline()) {
                player2.sendMessage(delayMessage);
                sendEarlyReturnButton(player2);
            }
        }
    }

    /**
     * Создает задачу для отложенного возврата игроков
     * @param duel Дуэль
     * @param player1Id UUID первого игрока
     * @param player2Id UUID второго игрока
     * @param delaySeconds Задержка в секундах
     */
    private void createDelayedReturnTask(Duel duel, UUID player1Id, UUID player2Id, int delaySeconds) {
        // Создаем новую задачу
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                // Получаем игроков (они могут быть оффлайн)
                Player player1 = Bukkit.getPlayer(player1Id);
                Player player2 = Bukkit.getPlayer(player2Id);

                // Возвращаем первого игрока, если он не в новой дуэли
                if (player1 != null && player1.isOnline()) {
                    // ДОБАВЛЕНО: Проверяем, не находится ли игрок в новой дуэли
                    if (!isPlayerInDuel(player1Id) && isInDuelWorld(player1)) {
                        returnPlayer(player1, false);
                        player1.sendMessage(ColorUtils.colorize(
                                plugin.getConfig().getString("messages.prefix") +
                                        "&aВремя сбора ресурсов истекло. Вы были телепортированы на исходную позицию."));
                    }
                }

                // Возвращаем второго игрока, если он не в новой дуэли
                if (player2 != null && player2.isOnline()) {
                    // ДОБАВЛЕНО: Проверяем, не находится ли игрок в новой дуэли
                    if (!isPlayerInDuel(player2Id) && isInDuelWorld(player2)) {
                        returnPlayer(player2, false);
                        player2.sendMessage(ColorUtils.colorize(
                                plugin.getConfig().getString("messages.prefix") +
                                        "&aВремя сбора ресурсов истекло. Вы были телепортированы на исходную позицию."));
                    }
                }

                // Восстанавливаем арену
                restoreArenaAfterDuel(duel);

                // Удаляем задачи из списка
                delayedReturnTasks.remove(player1Id);
                delayedReturnTasks.remove(player2Id);
            }
        }, delaySeconds * 20L);

        // Сохраняем задачу для обоих игроков
        delayedReturnTasks.put(player1Id, task);
        delayedReturnTasks.put(player2Id, task);
    }

    /**
     * Восстанавливает арену после дуэли
     * @param duel Дуэль
     */
    private void restoreArenaAfterDuel(Duel duel) {
        // Логируем количество построенных блоков для статистики
        int placedBlocksCount = duel.getPlayerPlacedBlocks().size();
        if (placedBlocksCount > 0) {
            plugin.getLogger().info("Дуэль завершена с " + placedBlocksCount + " построенными блоками. Восстанавливаем арену...");
        }

        // Очищаем арену от стрел и других ресурсов
        if (duel.getArena() != null) {
            // Автоматическое восстановление арены
            plugin.getRestoreManager().restoreArenaAreas(duel.getArena().getId());

            // Принудительное восстановление через команду
            forceRestoreArena(duel.getArena().getId());
        }
    }

    /**
     * Сохраняет исходную локацию игрока перед дуэлью
     * @param player Игрок
     */
    public void saveOriginalLocation(Player player) {
        UUID playerId = player.getUniqueId();
        Location location = player.getLocation().clone();
        if (!isInDuelWorld(player.getWorld().getName())) {
            // СЕЙЧАС: Записывает только если НЕ в мире дуэлей
            originalWorldLocations.put(playerId, location);

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Сохранена исходная локация для " + player.getName() + ": " +
                        formatLocation(location));
            }
        } else {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Игрок " + player.getName() + " уже в мире дуэли, не сохраняем локацию");
            }
        }
    }

    /**
     * Проверяет, находится ли игрок в мире дуэлей
     * @param player Игрок
     * @return true, если игрок в мире дуэлей
     */
    private boolean isInDuelWorld(Player player) {
        String worldName = player.getWorld().getName().toLowerCase();
        return plugin.getConfig().getStringList("worlds.duel-worlds").contains(worldName);
    }

    /**
     * Получает оригинальную локацию игрока (до входа в мир дуэлей)
     * @param playerId UUID игрока
     * @return Оригинальная локация или null, если не найдена
     */
    public Location getOriginalLocation(UUID playerId) {
        // Сначала проверяем в памяти
        if (originalWorldLocations.containsKey(playerId)) {
            return originalWorldLocations.get(playerId);
        }

        // Если нет в памяти, пробуем загрузить из файла
        Location location = loadPlayerLocationFromFile(playerId);

        // Проверяем, что загруженная локация не в мире дуэлей
        if (location != null && location.getWorld() != null) {
            String worldName = location.getWorld().getName().toLowerCase();
            if (isInDuelWorld(worldName)) {
                // Локация в мире дуэлей, не используем её
                return null;
            }
        }

        return location;
    }

    /**
     * Проверяет, является ли мир миром дуэлей
     * @param worldName Название мира
     * @return true, если мир является миром дуэлей
     */
    private boolean isInDuelWorld(String worldName) {
        return plugin.getConfig().getStringList("worlds.duel-worlds").contains(worldName.toLowerCase());
    }

    /**
     * Телепортирует игрока на его оригинальную локацию (до входа в мир дуэлей)
     * @param player Игрок
     */
    public void teleportToOriginalLocation(Player player) {
        UUID playerId = player.getUniqueId();

        // Получаем оригинальную локацию
        Location location = getOriginalLocation(playerId);

        if (location != null && location.getWorld() != null && !isInDuelWorld(location.getWorld().getName())) {
            // Проверяем безопасность локации
            if (!isSafeLocation(location)) {
                // Если локация небезопасна, находим безопасную локацию рядом
                Location safeLocation = findSafeLocation(location);
                if (safeLocation != null) {
                    location = safeLocation;
                }
            }

            // Финальная телепортация
            final Location finalLoc = location;

            // Телепортация с задержкой для плавности
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.teleport(finalLoc);
                }
            }, 5L); // 5 тиков = 0.25 секунды
        } else {
            // Если локация не найдена или в мире дуэлей, НЕ телепортируем
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Не найдена подходящая оригинальная локация для игрока " +
                        player.getName() + ", телепортация отменена");
            }
        }
    }

    /**
     * Обрабатывает телепортацию игрока из мира дуэлей в его оригинальный мир
     * @param player Игрок для телепортации
     */
    public void handlePlayerReturnFromDuel(Player player) {
        if (player == null || !player.isOnline()) return;

        UUID playerId = player.getUniqueId();

        // Проверяем, находится ли игрок в мире дуэлей
        if (isInDuelWorld(player.getWorld().getName())) {
            // Получаем оригинальную локацию
            Location originalLocation = getOriginalLocation(playerId);

            if (originalLocation != null && originalLocation.getWorld() != null &&
                    !isInDuelWorld(originalLocation.getWorld().getName())) {

                // Проверяем безопасность локации
                if (!isSafeLocation(originalLocation)) {
                    // Если локация небезопасна, находим безопасную локацию рядом
                    Location safeLocation = findSafeLocation(originalLocation);
                    if (safeLocation != null) {
                        originalLocation = safeLocation;
                    }
                }

                // Финальная телепортация
                final Location finalLoc = originalLocation;

                // Задержка для плавности
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.teleport(finalLoc);
                    }
                }, 5L);
            }
        }
    }

    /**
     * Проверяет, безопасна ли локация для телепортации
     * @param location Локация для проверки
     * @return true, если локация безопасна
     */
    private boolean isSafeLocation(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Проверяем, что блок под ногами твердый
        Block blockBelow = world.getBlockAt(x, y - 1, z);
        if (!blockBelow.getType().isSolid()) {
            return false;
        }

        // Проверяем, что блоки для тела и головы пустые
        Block blockAt = world.getBlockAt(x, y, z);
        Block blockAbove = world.getBlockAt(x, y + 1, z);

        return blockAt.getType().isAir() && blockAbove.getType().isAir();
    }

    /**
     * Находит безопасную локацию рядом с указанной
     * @param location Исходная локация
     * @return Безопасная локация или null, если не найдена
     */
    private Location findSafeLocation(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Проверяем текущую локацию
        if (isSafeLocation(location)) {
            return location;
        }

        // Проверяем локации выше (до 20 блоков вверх)
        for (int dy = 1; dy <= 20; dy++) {
            Location loc = new Location(world, x, y + dy, z, location.getYaw(), location.getPitch());
            if (isSafeLocation(loc)) {
                return loc;
            }
        }

        // Проверяем локации ниже (до 20 блоков вниз)
        for (int dy = 1; dy <= 20; dy++) {
            if (y - dy > 0) {
                Location loc = new Location(world, x, y - dy, z, location.getYaw(), location.getPitch());
                if (isSafeLocation(loc)) {
                    return loc;
                }
            }
        }

        // Проверяем локации вокруг (в радиусе 10 блоков)
        for (int radius = 1; radius <= 10; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) continue;

                    for (int dy = -5; dy <= 5; dy++) {
                        if (y + dy > 0 && y + dy < world.getMaxHeight()) {
                            Location loc = new Location(world, x + dx, y + dy, z + dz, location.getYaw(), location.getPitch());
                            if (isSafeLocation(loc)) {
                                return loc;
                            }
                        }
                    }
                }
            }
        }

        // Не нашли безопасную локацию
        return null;
    }

    /**
     * Разблокирует команды для игрока через CommandBlocker
     * @param playerId UUID игрока
     */
    private void unblockCommandsForPlayer(UUID playerId) {
        try {
            // Получаем экземпляр CommandBlocker через RestDuels
            CommandBlocker commandBlocker = plugin.getCommandBlocker();
            if (commandBlocker != null) {
                // Пробуем найти метод removePlayer через рефлексию
                java.lang.reflect.Method removePlayerMethod = commandBlocker.getClass().getMethod("removePlayer", UUID.class);
                if (removePlayerMethod != null) {
                    removePlayerMethod.invoke(commandBlocker, playerId);

                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().info("Команды разблокированы для игрока " + playerId);
                    }
                }
            }
        } catch (Exception e) {
            // Если что-то пошло не так, пробуем через рефлексию
            try {
                Class<?> commandBlockerClass = Class.forName("ru.refontstudio.restduels.listeners.CommandBlocker");
                if (commandBlockerClass != null) {
                    java.lang.reflect.Method getInstanceMethod = commandBlockerClass.getMethod("getInstance");
                    if (getInstanceMethod != null) {
                        Object commandBlocker = getInstanceMethod.invoke(null);
                        java.lang.reflect.Method removePlayerMethod = commandBlocker.getClass().getMethod("removePlayer", UUID.class);
                        if (removePlayerMethod != null) {
                            removePlayerMethod.invoke(commandBlocker, playerId);
                        }
                    }
                }
            } catch (Exception ex) {
                // Игнорируем ошибки, если класс или методы не найдены
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("Не удалось разблокировать команды для игрока " + playerId + ": " + ex.getMessage());
                }
            }
        }
    }

    /**
     * Завершает дуэль с указанием победителя
     * @param duel Дуэль
     * @param winnerId UUID победителя
     * @param isDraw флаг, является ли дуэль ничьей
     */
    public void endDuel(Duel duel, UUID winnerId, boolean isDraw) {
        // Отменяем таймер дуэли
        if (duel.getDuelTask() != null) {
            duel.getDuelTask().cancel();
        }

        // Отменяем таймер отображения
        if (duel.getTimerTask() != null) {
            duel.getTimerTask().cancel();
        }

        // Освобождаем арену
        Arena arena = duel.getArena();
        if (arena != null) {
            occupiedArenas.remove(arena.getId());
        }

        // Обрабатываем игроков и статистику
        Player player1 = Bukkit.getPlayer(duel.getPlayer1Id());
        Player player2 = Bukkit.getPlayer(duel.getPlayer2Id());

        // ДОБАВЛЕНО: Очищаем все эффекты у игроков при завершении дуэли
        clearEffects(player1);
        clearEffects(player2);

        // Сразу удаляем дуэль из списка активных для обоих игроков
        playerDuels.remove(duel.getPlayer1Id());
        playerDuels.remove(duel.getPlayer2Id());

        // Определяем победителя и проигравшего
        final Player winner;
        final Player loser;
        final UUID loserId;

        if (winnerId != null && !isDraw) {
            // Обновляем статистику
            plugin.getStatsManager().incrementWins(winnerId);

            // Определяем проигравшего и увеличиваем счетчик смертей
            loserId = winnerId.equals(duel.getPlayer1Id()) ? duel.getPlayer2Id() : duel.getPlayer1Id();
            plugin.getStatsManager().incrementDeaths(loserId);

            // Определяем игроков
            winner = winnerId.equals(duel.getPlayer1Id()) ? player1 : player2;
            loser = winnerId.equals(duel.getPlayer1Id()) ? player2 : player1;

            // Отправляем заголовки о победителе/проигравшем
            String winnerName = winner != null ? winner.getName() : "Неизвестный игрок";

            if (player1 != null && player1.isOnline()) {
                plugin.getTitleManager().sendTitle(player1, "duel-ended", "%winner%", winnerName);

                // Звук победы/поражения
                if (player1 == winner) {
                    player1.playSound(player1.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                } else {
                    player1.playSound(player1.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 0.8f);
                }

                // Разблокируем команды для обоих игроков сразу
                if (player1.hasMetadata("restduels_blocked_commands")) {
                    player1.removeMetadata("restduels_blocked_commands", plugin);
                }

                // Если есть CommandBlocker, разблокируем команды
                try {
                    CommandBlocker commandBlocker = plugin.getCommandBlocker();
                    if (commandBlocker != null) {
                        commandBlocker.removePlayer(player1.getUniqueId());
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки, если класс или методы не найдены
                }
            }

            if (player2 != null && player2.isOnline()) {
                plugin.getTitleManager().sendTitle(player2, "duel-ended", "%winner%", winnerName);

                // Звук победы/поражения
                if (player2 == winner) {
                    player2.playSound(player2.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                } else {
                    player2.playSound(player2.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 0.8f);
                }

                // Разблокируем команды для обоих игроков сразу
                if (player2.hasMetadata("restduels_blocked_commands")) {
                    player2.removeMetadata("restduels_blocked_commands", plugin);
                }

                // Если есть CommandBlocker, разблокируем команды
                try {
                    CommandBlocker commandBlocker = plugin.getCommandBlocker();
                    if (commandBlocker != null) {
                        commandBlocker.removePlayer(player2.getUniqueId());
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки, если класс или методы не найдены
                }
            }
        } else if (isDraw) {
            // Ничья - инициализируем переменные как null
            winner = null;
            loser = null;
            loserId = null;

            // Звук ничьей для обоих игроков
            if (player1 != null && player1.isOnline()) {
                player1.playSound(player1.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.0f);
                plugin.getTitleManager().sendTitle(player1, "duel-draw");
                player1.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&eВремя дуэли истекло! &6Объявлена ничья."));

                // Разблокируем команды сразу
                if (player1.hasMetadata("restduels_blocked_commands")) {
                    player1.removeMetadata("restduels_blocked_commands", plugin);
                }

                // Если есть CommandBlocker, разблокируем команды
                try {
                    CommandBlocker commandBlocker = plugin.getCommandBlocker();
                    if (commandBlocker != null) {
                        commandBlocker.removePlayer(player1.getUniqueId());
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки, если класс или методы не найдены
                }
            }

            if (player2 != null && player2.isOnline()) {
                player2.playSound(player2.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.0f);
                plugin.getTitleManager().sendTitle(player2, "duel-draw");
                player2.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&eВремя дуэли истекло! &6Объявлена ничья."));

                // Разблокируем команды сразу
                if (player2.hasMetadata("restduels_blocked_commands")) {
                    player2.removeMetadata("restduels_blocked_commands", plugin);
                }

                // Если есть CommandBlocker, разблокируем команды
                try {
                    CommandBlocker commandBlocker = plugin.getCommandBlocker();
                    if (commandBlocker != null) {
                        commandBlocker.removePlayer(player2.getUniqueId());
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки, если класс или методы не найдены
                }
            }
        } else {
            // На случай, если и не победа, и не ничья
            winner = null;
            loser = null;
            loserId = null;
        }

// Разное поведение в зависимости от типа дуэли
        if (duel.getType() == DuelType.RANKED) {
            // В режиме RANKED (без потерь) сразу возвращаем обоих игроков с восстановлением инвентаря
            if (player1 != null && player1.isOnline()) {
                returnPlayer(player1, true);

                // ДОБАВЛЕНО: Восстанавливаем здоровье для ранкед дуэлей
                restorePlayerHealth(player1);

                // Специальное сообщение для ранкед-режима
                if (winner == player1) {
                    player1.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    plugin.getConfig().getString("messages.ranked-duel-win",
                                            "&aВы победили в ранговой дуэли! Ваш инвентарь и здоровье восстановлены.")));
                } else if (winner == player2) {
                    player1.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    plugin.getConfig().getString("messages.ranked-duel-lose",
                                            "&cВы проиграли в ранговой дуэли! Ваш инвентарь и здоровье восстановлены.")));
                } else if (isDraw) {
                    player1.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    plugin.getConfig().getString("messages.ranked-duel-draw",
                                            "&eРанговая дуэль завершилась вничью! Ваш инвентарь и здоровье восстановлены.")));
                }
            }

            if (player2 != null && player2.isOnline()) {
                returnPlayer(player2, true);

                // ДОБАВЛЕНО: Восстанавливаем здоровье для ранкед дуэлей
                restorePlayerHealth(player2);

                // Специальное сообщение для ранкед-режима
                if (winner == player2) {
                    player2.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    plugin.getConfig().getString("messages.ranked-duel-win",
                                            "&aВы победили в ранговой дуэли! Ваш инвентарь и здоровье восстановлены.")));
                } else if (winner == player1) {
                    player2.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    plugin.getConfig().getString("messages.ranked-duel-lose",
                                            "&cВы проиграли в ранговой дуэли! Ваш инвентарь и здоровье восстановлены.")));
                } else if (isDraw) {
                    player2.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    plugin.getConfig().getString("messages.ranked-duel-draw",
                                            "&eРанговая дуэль завершилась вничью! Ваш инвентарь и здоровье восстановлены.")));
                }
            }

            // Обновляем статистику ранговых дуэлей, если есть специальный менеджер
            // if (plugin.getRankedStatsManager() != null) {
            //     if (isDraw) {
            //         plugin.getRankedStatsManager().recordDraw(duel.getPlayer1Id(), duel.getPlayer2Id());
            //     } else if (winnerId != null) {
            //         plugin.getRankedStatsManager().recordWin(winnerId, loserId);
            //     }
            // }

            // Логируем количество построенных блоков для статистики
            int placedBlocksCount = duel.getPlayerPlacedBlocks().size();
            if (placedBlocksCount > 0) {
                plugin.getLogger().info("Ранговая дуэль завершена с " + placedBlocksCount + " построенными блоками. Восстанавливаем арену...");
            }

            // Очищаем арену от стрел и других ресурсов
            if (arena != null) {
                // Автоматическое восстановление арены
                plugin.getRestoreManager().restoreArenaAreas(arena.getId());

                // Принудительное восстановление через команду
                forceRestoreArena(arena.getId());
            }

            // ВАЖНО: Добавляем return, чтобы не выполнять код для CLASSIC и NORMAL типов дуэлей
            return;
        } else if (duel.getType() == DuelType.CLASSIC) {
            // ДОБАВЛЕНО: Обработка CLASSIC дуэлей так же, как NORMAL
            if (isDraw) {
                // При ничьей даем время собрать вещи обоим
                String delayMessage = ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                plugin.getConfig().getString("messages.delay-collect"));

                // Получаем время сбора ресурсов из конфига
                int collectTime = plugin.getConfig().getInt("duel.resource-collection-time", 60);

                // Создаем отложенную задачу для возврата игроков через collectTime секунд
                BukkitTask delayedTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player1 != null && player1.isOnline()) {
                        // Проверяем, находится ли игрок все еще в мире дуэлей
                        if (isInDuelWorld(player1)) {
                            returnPlayer(player1, false);
                            player1.sendMessage(ColorUtils.colorize(
                                    plugin.getConfig().getString("messages.prefix") +
                                            "&aВремя сбора ресурсов истекло. Вы были телепортированы на исходную позицию."));
                        }
                    }

                    if (player2 != null && player2.isOnline()) {
                        // Проверяем, находится ли игрок все еще в мире дуэлей
                        if (isInDuelWorld(player2)) {
                            returnPlayer(player2, false);
                            player2.sendMessage(ColorUtils.colorize(
                                    plugin.getConfig().getString("messages.prefix") +
                                            "&aВремя сбора ресурсов истекло. Вы были телепортированы на исходную позицию."));
                        }
                    }

                    // Логируем количество построенных блоков для статистики
                    int placedBlocksCount = duel.getPlayerPlacedBlocks().size();
                    if (placedBlocksCount > 0) {
                        plugin.getLogger().info("Дуэль завершена с " + placedBlocksCount + " построенными блоками. Восстанавливаем арену...");
                    }

                    // Очищаем арену от стрел и других ресурсов
                    if (arena != null) {
                        // Автоматическое восстановление арены
                        plugin.getRestoreManager().restoreArenaAreas(arena.getId());

                        // Принудительное восстановление через команду
                        forceRestoreArena(arena.getId());
                    }

                    // Удаляем задачи из списка
                    delayedReturnTasks.remove(duel.getPlayer1Id());
                    delayedReturnTasks.remove(duel.getPlayer2Id());
                }, collectTime * 20L); // collectTime секунд

                // Сохраняем задачу для обоих игроков
                delayedReturnTasks.put(duel.getPlayer1Id(), delayedTask);
                delayedReturnTasks.put(duel.getPlayer2Id(), delayedTask);

                // Отправляем сообщения и кнопки
                if (player1 != null && player1.isOnline()) {
                    player1.sendMessage(delayMessage);
                    sendEarlyReturnButton(player1);
                }

                if (player2 != null && player2.isOnline()) {
                    player2.sendMessage(delayMessage);
                    sendEarlyReturnButton(player2);
                }
            } else {
                // При победе: проигравшего возвращаем сразу, победителю даем время собрать вещи
                if (loser != null && loser.isOnline()) {
                    // Сразу возвращаем проигравшего
                    returnPlayer(loser, false);
                }

                if (winner != null && winner.isOnline()) {
                    // Запоминаем UUID победителя, чтобы использовать в лямбде
                    final UUID winnerUUID = winner.getUniqueId();

                    // Получаем время сбора ресурсов из конфига
                    int collectTime = plugin.getConfig().getInt("duel.resource-collection-time", 60);

                    // Создаем отложенную задачу для возврата победителя через collectTime секунд
                    BukkitTask winnerTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Player winnerPlayer = Bukkit.getPlayer(winnerUUID);
                        if (winnerPlayer != null && winnerPlayer.isOnline()) {
                            // Проверяем, находится ли победитель все еще в мире дуэлей
                            if (isInDuelWorld(winnerPlayer)) {
                                // Возвращаем победителя, НО сохраняем текущий инвентарь (не восстанавливаем)
                                // Очищаем только сохраненные данные и телепортируем обратно

                                // Удаляем из списков
                                frozenPlayers.remove(winnerUUID);
                                playerArenas.remove(winnerUUID);

                                // Отменяем проверку полета
                                stopFlightCheck(winnerUUID);

                                // Восстанавливаем статус полета
                                if (playerFlightStatus.containsKey(winnerUUID)) {
                                    boolean allowFlight = playerFlightStatus.get(winnerUUID);
                                    winnerPlayer.setAllowFlight(allowFlight);
                                    if (allowFlight) winnerPlayer.setFlying(allowFlight);
                                    playerFlightStatus.remove(winnerUUID);
                                }

                                // Телепортируем на исходную позицию
                                Location teleportLocation = null;
                                if (originalWorldLocations.containsKey(winnerUUID)) {
                                    teleportLocation = originalWorldLocations.get(winnerUUID);
                                    if (teleportLocation != null && teleportLocation.getWorld() != null &&
                                            !isInDuelWorld(teleportLocation.getWorld().getName())) {
                                        safeTeleport(winnerPlayer, teleportLocation);
                                    }
                                } else if (playerLocations.containsKey(winnerUUID)) {
                                    teleportLocation = playerLocations.get(winnerUUID);
                                    if (teleportLocation != null && teleportLocation.getWorld() != null &&
                                            !isInDuelWorld(teleportLocation.getWorld().getName())) {
                                        safeTeleport(winnerPlayer, teleportLocation);
                                        playerLocations.remove(winnerUUID);
                                    }
                                }

                                // Удаляем сохраненные данные инвентаря (не восстанавливаем)
                                originalInventories.remove(winnerUUID);
                                originalArmor.remove(winnerUUID);

                                winnerPlayer.sendMessage(ColorUtils.colorize(
                                        plugin.getConfig().getString("messages.prefix") +
                                                "&aВремя сбора ресурсов истекло. Вы были телепортированы на исходную позицию с собранными вещами."));
                            }
                        }

                        // Логируем количество построенных блоков для статистики
                        int placedBlocksCount = duel.getPlayerPlacedBlocks().size();
                        if (placedBlocksCount > 0) {
                            plugin.getLogger().info("Дуэль завершена с " + placedBlocksCount + " построенными блоками. Восстанавливаем арену...");
                        }

                        // Очищаем арену от стрел и других ресурсов
                        if (arena != null) {
                            // Автоматическое восстановление арены
                            plugin.getRestoreManager().restoreArenaAreas(arena.getId());

                            // Принудительное восстановление через команду
                            forceRestoreArena(arena.getId());
                        }

                        // Удаляем задачу
                        delayedReturnTasks.remove(winnerUUID);
                    }, collectTime * 20L); // collectTime секунд

                    // Сохраняем задачу для победителя
                    delayedReturnTasks.put(winnerUUID, winnerTask);

                    // Отправляем кнопку и сообщение
                    sendEarlyReturnButton(winner);

                    // Даем победителю время собрать вещи
                    winner.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    plugin.getConfig().getString("messages.winner-collect",
                                            "&aУ вас есть &c" + collectTime + " секунд &aдля сбора вещей.")));
                } else {
                    // Если победитель оффлайн, просто очищаем арену
                    // Логируем количество построенных блоков для статистики
                    int placedBlocksCount = duel.getPlayerPlacedBlocks().size();
                    if (placedBlocksCount > 0) {
                        plugin.getLogger().info("Дуэль завершена с " + placedBlocksCount + " построенными блоками. Восстанавливаем арену...");
                    }

                    // Очищаем арену
                    if (arena != null) {
                        // Автоматическое восстановление арены
                        plugin.getRestoreManager().restoreArenaAreas(arena.getId());

                        // Принудительное восстановление через команду
                        forceRestoreArena(arena.getId());
                    }
                }
            }
        } else {
            // В обычном режиме NORMAL
            if (isDraw) {
                // При ничьей даем время собрать вещи обоим
                String delayMessage = ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                plugin.getConfig().getString("messages.delay-collect"));

                // Получаем время сбора ресурсов из конфига
                int collectTime = plugin.getConfig().getInt("duel.resource-collection-time", 60);

                // Создаем отложенную задачу для возврата игроков через collectTime секунд
                BukkitTask delayedTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player1 != null && player1.isOnline()) {
                        // Проверяем, находится ли игрок все еще в мире дуэлей
                        if (isInDuelWorld(player1)) {
                            returnPlayer(player1, false);
                            player1.sendMessage(ColorUtils.colorize(
                                    plugin.getConfig().getString("messages.prefix") +
                                            "&aВремя сбора ресурсов истекло. Вы были телепортированы на исходную позицию."));
                        }
                    }

                    if (player2 != null && player2.isOnline()) {
                        // Проверяем, находится ли игрок все еще в мире дуэлей
                        if (isInDuelWorld(player2)) {
                            returnPlayer(player2, false);
                            player2.sendMessage(ColorUtils.colorize(
                                    plugin.getConfig().getString("messages.prefix") +
                                            "&aВремя сбора ресурсов истекло. Вы были телепортированы на исходную позицию."));
                        }
                    }

                    // Логируем количество построенных блоков для статистики
                    int placedBlocksCount = duel.getPlayerPlacedBlocks().size();
                    if (placedBlocksCount > 0) {
                        plugin.getLogger().info("Дуэль завершена с " + placedBlocksCount + " построенными блоками. Восстанавливаем арену...");
                    }

                    // Очищаем арену от стрел и других ресурсов
                    if (arena != null) {
                        // Автоматическое восстановление арены
                        plugin.getRestoreManager().restoreArenaAreas(arena.getId());

                        // Принудительное восстановление через команду
                        forceRestoreArena(arena.getId());
                    }

                    // Удаляем задачи из списка
                    delayedReturnTasks.remove(duel.getPlayer1Id());
                    delayedReturnTasks.remove(duel.getPlayer2Id());
                }, collectTime * 20L); // collectTime секунд

                // Сохраняем задачу для обоих игроков
                delayedReturnTasks.put(duel.getPlayer1Id(), delayedTask);
                delayedReturnTasks.put(duel.getPlayer2Id(), delayedTask);

                // СНАЧАЛА добавили задачи в delayedReturnTasks, ПОТОМ отправляем кнопки
                if (player1 != null && player1.isOnline()) {
                    player1.sendMessage(delayMessage);
                    sendEarlyReturnButton(player1);
                }

                if (player2 != null && player2.isOnline()) {
                    player2.sendMessage(delayMessage);
                    sendEarlyReturnButton(player2);
                }
            } else {
                // При победе: проигравшего возвращаем сразу, победителю даем время собрать вещи
                if (loser != null && loser.isOnline()) {
                    // Сразу возвращаем проигравшего
                    returnPlayer(loser, false);
                }

                if (winner != null && winner.isOnline()) {
                    // Запоминаем UUID победителя, чтобы использовать в лямбде
                    final UUID winnerUUID = winner.getUniqueId();

                    // Получаем время сбора ресурсов из конфига
                    int collectTime = plugin.getConfig().getInt("duel.resource-collection-time", 60);

                    // Сначала создаем отложенную задачу для возврата победителя через collectTime секунд
                    BukkitTask winnerTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Player winnerPlayer = Bukkit.getPlayer(winnerUUID);
                        if (winnerPlayer != null && winnerPlayer.isOnline()) {
                            // Проверяем, находится ли победитель все еще в мире дуэлей
                            if (isInDuelWorld(winnerPlayer)) {
                                // Возвращаем победителя, НО сохраняем текущий инвентарь (не восстанавливаем)
                                // Очищаем только сохраненные данные и телепортируем обратно

                                // Удаляем из списков
                                frozenPlayers.remove(winnerUUID);
                                playerArenas.remove(winnerUUID);

                                // Отменяем проверку полета
                                stopFlightCheck(winnerUUID);

                                // Восстанавливаем статус полета
                                if (playerFlightStatus.containsKey(winnerUUID)) {
                                    boolean allowFlight = playerFlightStatus.get(winnerUUID);
                                    winnerPlayer.setAllowFlight(allowFlight);
                                    if (allowFlight) winnerPlayer.setFlying(allowFlight);
                                    playerFlightStatus.remove(winnerUUID);
                                }

                                // Телепортируем на исходную позицию
                                Location teleportLocation = null;
                                if (originalWorldLocations.containsKey(winnerUUID)) {
                                    teleportLocation = originalWorldLocations.get(winnerUUID);
                                    if (teleportLocation != null && teleportLocation.getWorld() != null &&
                                            !isInDuelWorld(teleportLocation.getWorld().getName())) {
                                        safeTeleport(winnerPlayer, teleportLocation);
                                    }
                                } else if (playerLocations.containsKey(winnerUUID)) {
                                    teleportLocation = playerLocations.get(winnerUUID);
                                    if (teleportLocation != null && teleportLocation.getWorld() != null &&
                                            !isInDuelWorld(teleportLocation.getWorld().getName())) {
                                        safeTeleport(winnerPlayer, teleportLocation);
                                        playerLocations.remove(winnerUUID);
                                    }
                                }

                                // Удаляем сохраненные данные инвентаря (не восстанавливаем)
                                originalInventories.remove(winnerUUID);
                                originalArmor.remove(winnerUUID);

                                winnerPlayer.sendMessage(ColorUtils.colorize(
                                        plugin.getConfig().getString("messages.prefix") +
                                                "&aВремя сбора ресурсов истекло. Вы были телепортированы на исходную позицию с собранными вещами."));
                            }
                        }

                        // Логируем количество построенных блоков для статистики
                        int placedBlocksCount = duel.getPlayerPlacedBlocks().size();
                        if (placedBlocksCount > 0) {
                            plugin.getLogger().info("Дуэль завершена с " + placedBlocksCount + " построенными блоками. Восстанавливаем арену...");
                        }

                        // Очищаем арену от стрел и других ресурсов
                        if (arena != null) {
                            // Автоматическое восстановление арены
                            plugin.getRestoreManager().restoreArenaAreas(arena.getId());

                            // Принудительное восстановление через команду
                            forceRestoreArena(arena.getId());
                        }

                        // Удаляем задачу
                        delayedReturnTasks.remove(winnerUUID);
                    }, collectTime * 20L); // collectTime секунд

                    // Затем сохраняем задачу для победителя
                    delayedReturnTasks.put(winnerUUID, winnerTask);

                    // И только ПОТОМ отправляем кнопку и сообщение
                    sendEarlyReturnButton(winner);

                    // Даем победителю время собрать вещи
                    winner.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    plugin.getConfig().getString("messages.winner-collect",
                                            "&aУ вас есть &c" + collectTime + " секунд &aдля сбора вещей.")));
                } else {
                    // Если победитель оффлайн, просто очищаем арену
                    // Логируем количество построенных блоков для статистики
                    int placedBlocksCount = duel.getPlayerPlacedBlocks().size();
                    if (placedBlocksCount > 0) {
                        plugin.getLogger().info("Дуэль завершена с " + placedBlocksCount + " построенными блоками. Восстанавливаем арену...");
                    }

                    // Очищаем арену
                    if (arena != null) {
                        // Автоматическое восстановление арены
                        plugin.getRestoreManager().restoreArenaAreas(arena.getId());

                        // Принудительное восстановление через команду
                        forceRestoreArena(arena.getId());
                    }
                }
            }
        }
    }

    /**
     * Проверяет, есть ли у игрока отложенная задача возврата
     * @param playerId UUID игрока
     * @return true, если у игрока есть отложенная задача возврата
     */
    public boolean hasDelayedReturnTask(UUID playerId) {
        return delayedReturnTasks.containsKey(playerId) &&
                delayedReturnTasks.get(playerId) != null &&
                !delayedReturnTasks.get(playerId).isCancelled();
    }

    /**
     * Отменяет отложенную задачу возврата и телепортирует игрока
     * @param player Игрок для телепортации
     */
    public void cancelDelayedReturnAndTeleport(Player player) {
        UUID playerId = player.getUniqueId();

        // ИСПРАВЛЕНО: Сначала проверяем, является ли игрок победителем
        // до отмены и удаления задачи
        boolean isWinner = delayedReturnTasks.containsKey(playerId) &&
                !delayedReturnTasks.get(playerId).isCancelled();

        // Отменяем отложенную задачу, если есть
        if (delayedReturnTasks.containsKey(playerId)) {
            delayedReturnTasks.get(playerId).cancel();
            delayedReturnTasks.remove(playerId);
        }

        frozenPlayers.remove(playerId);
        playerArenas.remove(playerId);
        stopFlightCheck(playerId);

        if (playerFlightStatus.containsKey(playerId)) {
            boolean allowFlight = playerFlightStatus.get(playerId);
            player.setAllowFlight(allowFlight);
            if (allowFlight) player.setFlying(allowFlight);
            playerFlightStatus.remove(playerId);
        }

        // Сначала пытаемся найти оригинальную локацию
        Location teleportLocation = null;
        boolean teleportSuccessful = false;

        if (originalWorldLocations.containsKey(playerId)) {
            teleportLocation = originalWorldLocations.get(playerId);
            if (teleportLocation != null && teleportLocation.getWorld() != null && !isInDuelWorld(teleportLocation.getWorld().getName())) {
                safeTeleport(player, teleportLocation);
                teleportSuccessful = true;
            }
        } else if (playerLocations.containsKey(playerId)) {
            teleportLocation = playerLocations.get(playerId);
            if (teleportLocation != null && teleportLocation.getWorld() != null && !isInDuelWorld(teleportLocation.getWorld().getName())) {
                safeTeleport(player, teleportLocation);
                playerLocations.remove(playerId);
                teleportSuccessful = true;
            }
        }

        // Сообщение о телепорте
        if (teleportSuccessful) {
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "§aВы были досрочно телепортированы на исходную позицию."));
        } else {
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "§cНе удалось найти безопасную локацию для телепортации. Пожалуйста, используйте /spawn."));
        }

        // ИСПРАВЛЕНО: Теперь используем сохраненное значение isWinner
        // для определения, нужно ли восстанавливать инвентарь
        if (isWinner) {
            // Для победителя НЕ восстанавливаем инвентарь, чтобы сохранить подобранные предметы
            originalInventories.remove(playerId);
            originalArmor.remove(playerId);
        } else {
            // Для проигравшего восстанавливаем исходный инвентарь
            if (originalInventories.containsKey(playerId)) {
                player.getInventory().setContents(originalInventories.get(playerId));
                originalInventories.remove(playerId);
            }
            if (originalArmor.containsKey(playerId)) {
                player.getInventory().setArmorContents(originalArmor.get(playerId));
                originalArmor.remove(playerId);
            }
        }
        player.updateInventory();

        // Разрешить команды (если есть CommandBlocker)
        if (player.hasMetadata("restduels_blocked_commands")) {
            player.removeMetadata("restduels_blocked_commands", plugin);
        }
        try {
            CommandBlocker commandBlocker = plugin.getCommandBlocker();
            if (commandBlocker != null) {
                commandBlocker.removePlayer(playerId);
            }
        } catch (Exception ignored) {}

        // Удаляем дуэль для игрока
        if (playerDuels.containsKey(playerId)) {
            playerDuels.remove(playerId);
        }
    }

    /**
     * Возвращает игрока в мир дуэлей
     * @param player Игрок
     */
    public void returnPlayerToDuelWorld(Player player) {
        UUID playerId = player.getUniqueId();

        if (playerArenas.containsKey(playerId)) {
            Arena arena = playerArenas.get(playerId);

            // Проверяем, в какой команде находится игрок
            Duel duel = playerDuels.get(playerId);
            if (duel != null) {
                boolean isPlayer1 = duel.getPlayer1Id().equals(playerId);
                Location spawnLocation = isPlayer1 ? arena.getSpawn1() : arena.getSpawn2();

                player.teleport(spawnLocation);
//                player.sendMessage(ColorUtils.colorize(
//                        plugin.getConfig().getString("messages.prefix") +
//                                "&aВы были возвращены на арену дуэли."));
            }
        }
    }

    /**
     * Получает оригинальную локацию игрока (где он начал поиск дуэли)
     * @param playerId UUID игрока
     * @return Оригинальная локация или null, если не найдена
     */
    public Location getOriginalDuelLocation(UUID playerId) {
        // Сначала проверяем в памяти
        if (playerLocations.containsKey(playerId)) {
            return playerLocations.get(playerId);
        }

        // Если нет в памяти, пробуем загрузить из файла
        return loadPlayerLocationFromFile(playerId);
    }

    /**
     * Отправляет игроку кнопку для досрочного возврата
     * @param player Игрок для отправки
     */
    private void sendEarlyReturnButton(Player player) {
        UUID playerId = player.getUniqueId();

        // ВАЖНО: Удаляем эту проверку, которая блокировала отправку кнопки
        // Кнопка должна отправляться всегда, независимо от наличия отложенной задачи

        // Отправляем сообщение для досрочного возврата
        player.sendMessage(ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix") +
                        "&eНажмите на кнопку ниже для досрочного возврата:"));

        // Получаем текст кнопки из конфига и форматируем его
        String returnText = plugin.getConfig().getString("messages.early-return-button", "&a[Телепортироваться досрочно]");
        returnText = ChatColor.translateAlternateColorCodes('&', returnText);

        // Экранируем кавычки для JSON
        returnText = returnText.replace("\"", "\\\"");

        // Создаем JSON для кликабельного текста
        String json = "{\"text\":\"" + returnText + "\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/duel return\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Нажмите, чтобы вернуться досрочно\"}}";

        // Отправляем команду tellraw для создания кликабельного текста
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + json);

        // Отладочное сообщение
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Отправлена кнопка досрочного возврата игроку " + player.getName());
        }
    }

    /**
     * Принудительно восстанавливает арену через команду
     * @param arenaId ID арены для восстановления
     */
    private void forceRestoreArena(String arenaId) {
        // Проверяем, включено ли принудительное восстановление
        if (!plugin.getConfig().getBoolean("restoration.force-command-restore", true)) {
            return;
        }

        // Получаем задержку из конфига
        int delay = plugin.getConfig().getInt("restoration.force-restore-delay", 40);

        // Запускаем с задержкой после автоматического восстановления
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Выполняем команду от имени консоли
            String command = "arenawand restore " + arenaId;

            // Логируем выполнение команды
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Выполняется принудительное восстановление арены: " + command);
            }

            // Выполняем команду
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

            // Логируем результат
            if (!success) {
                plugin.getLogger().warning("Ошибка при выполнении команды восстановления арены: " + command);

                // Пробуем альтернативный вариант команды
                String altCommand = "arenawand restore arena" + arenaId;
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Пробуем альтернативную команду: " + altCommand);
                }

                boolean altSuccess = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), altCommand);

                if (!altSuccess) {
                    plugin.getLogger().warning("Ошибка при выполнении альтернативной команды восстановления арены");

                    // Пробуем третий вариант команды
                    String thirdCommand = "arenawand restore arena_" + arenaId;
                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().info("Пробуем третий вариант команды: " + thirdCommand);
                    }

                    boolean thirdSuccess = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), thirdCommand);

                    if (!thirdSuccess && plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().warning("Ошибка при выполнении третьего варианта команды восстановления арены");
                    }
                }
            } else if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Команда восстановления арены выполнена успешно");
            }
        }, delay); // Задержка из конфига
    }

    /**
     * Проверяет, есть ли у игрока сохраненная локация
     * @param playerId UUID игрока
     * @return true, если есть сохраненная локация
     */
    public boolean hasSavedLocation(UUID playerId) {
        return playerLocations.containsKey(playerId);
    }

    /**
     * Телепортирует игрока на сохраненную локацию и очищает данные
     * @param player Игрок для телепортации
     */
    public void teleportToSavedLocation(Player player) {
        UUID playerId = player.getUniqueId();
        Location location = null;

        // Сначала пробуем получить из памяти
        if (playerLocations.containsKey(playerId)) {
            location = playerLocations.get(playerId);
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Найдена локация в памяти для игрока " + player.getName());
            }
        }

        // Если нет в памяти или локация невалидна, пробуем загрузить из файла
        if (location == null || location.getWorld() == null) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Локация в памяти не найдена, пробуем загрузить из файла для игрока " + player.getName());
            }
            location = loadPlayerLocationFromFile(playerId);
        }

        if (location != null && location.getWorld() != null) {
            final Location finalLoc = location;

            // Получаем задержку из конфига или используем значение по умолчанию (10 тиков = 0.5 секунды)
            int delay = plugin.getConfig().getInt("location_saving.teleport_delay", 10);

            // Телепортируем с указанной задержкой
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Проверяем, что игрок все еще онлайн
                if (player.isOnline()) {
                    // Безопасная телепортация
                    player.teleport(finalLoc);

                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().info("Игрок " + player.getName() + " телепортирован на сохраненную локацию");
                    }
                }
            }, delay);

            // Не удаляем локацию после телепортации!
            // Сохраняем её на случай, если игрок умрёт и будет респавнен
        } else {
            // Если локация не найдена, НЕ телепортируем на спавн!
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Не удалось найти сохраненную локацию для игрока " + player.getName());
            }
        }
    }

    /**
     * Сохраняет текущую локацию игрока
     * @param player Игрок
     */
    public void savePlayerLocation(Player player) {
        UUID playerId = player.getUniqueId();
        Location location = player.getLocation().clone();

        // Сохраняем в памяти
        playerLocations.put(playerId, location);

        // Сохраняем в файл
        if (plugin.getConfig().getBoolean("location_saving.save_to_file", true)) {
            savePlayerLocationToFile(playerId, location);
        }

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Сохранена текущая локация игрока " + player.getName() +
                    ": " + location.getWorld().getName() + ", " +
                    location.getX() + ", " + location.getY() + ", " + location.getZ());
        }
    }

    /**
     * Получает исходную локацию игрока
     * @param playerId UUID игрока
     * @return Исходная локация или null, если не найдена
     */
    public Location getPlayerOriginalLocation(UUID playerId) {
        return playerLocations.get(playerId);
    }

    /**
     * Телепортирует игрока на спавн
     * @param player Игрок для телепортации
     */
    private void teleportToSpawn(Player player) {
        // Получаем спавн из конфига или используем стандартный
        String spawnCommand = plugin.getConfig().getString("teleport.spawn-command", "spawn %player%")
                .replace("%player%", player.getName());

        // Более точная проверка состояния экрана смерти
        if (player.isDead()) {
            // Игрок мертв и находится на экране смерти, не телепортируем
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Игрок " + player.getName() + " на экране смерти. Телепортация отложена.");
            }
            return;
        }

        // Дополнительная проверка здоровья
        if (player.getHealth() <= 0) {
            // Игрок без здоровья, но возможно еще не полностью мертв
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Игрок " + player.getName() + " с нулевым здоровьем. Телепортация отложена.");
            }
            return;
        }

        // Проверка на возможные состояния игрока, связанные со смертью
        try {
            // Попытка проверить, находится ли игрок в процессе возрождения
            // через рефлексию (для совместимости с разными версиями)
            Object playerHandle = player.getClass().getMethod("getHandle").invoke(player);
            boolean isDeathScreenActive = false;

            try {
                // Пытаемся получить поле, которое может указывать на состояние смерти
                // Имя поля может отличаться в разных версиях
                Field deathField = playerHandle.getClass().getDeclaredField("deathTicks");
                deathField.setAccessible(true);
                int deathTicks = (int) deathField.get(playerHandle);
                isDeathScreenActive = deathTicks > 0;
            } catch (NoSuchFieldException e1) {
                try {
                    // Альтернативное поле в некоторых версиях
                    Field respawnField = playerHandle.getClass().getDeclaredField("respawnForced");
                    respawnField.setAccessible(true);
                    boolean respawnForced = (boolean) respawnField.get(playerHandle);
                    isDeathScreenActive = !respawnForced;
                } catch (NoSuchFieldException e2) {
                    // Не удалось найти подходящее поле, используем базовую проверку
                    isDeathScreenActive = player.isDead();
                }
            }

            if (isDeathScreenActive) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Игрок " + player.getName() + " на экране смерти (проверка через рефлексию). Телепортация отложена.");
                }
                return;
            }
        } catch (Exception e) {
            // Если произошла ошибка при использовании рефлексии,
            // используем стандартную проверку
            if (player.isDead()) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Игрок " + player.getName() + " мертв (стандартная проверка). Телепортация отложена.");
                }
                return;
            }
        }

        // Проверяем дополнительные метаданные, которые могут быть установлены другими плагинами
        if (player.hasMetadata("respawning") || player.hasMetadata("deathscreen")) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Игрок " + player.getName() + " имеет метаданные экрана смерти. Телепортация отложена.");
            }
            return;
        }

        // Если все проверки пройдены, выполняем команду телепортации
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), spawnCommand);
    }

    /**
     * Автоматически восстанавливает арену, когда игроки входят на нее
     * @param arena Арена для восстановления
     */
    public void restoreArenaWhenPlayersEnter(Arena arena) {
        if (arena == null) return;

        // Проверяем, включена ли функция автоматического восстановления при входе игроков
        if (!plugin.getConfig().getBoolean("restoration.restore-on-enter", true)) {
            return;
        }

        // Выполняем команду восстановления арены
        String arenaId = arena.getId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Логируем действие
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Автоматическое восстановление арены " + arenaId + " при входе игроков");
            }

            // Выполняем команду восстановления
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "arenawand restore " + arenaId);
        });
    }

    /**
     * Очищает занятые арены, связанные с игроком
     * @param playerId UUID игрока
     */
    public void cleanupPlayerArenas(UUID playerId) {
        // Проверяем, есть ли дуэль с этим игроком
        for (Duel duel : new ArrayList<>(playerDuels.values())) {
            if (duel.getPlayer1Id().equals(playerId) || duel.getPlayer2Id().equals(playerId)) {
                // Освобождаем арену
                if (duel.getArena() != null) {
                    occupiedArenas.remove(duel.getArena().getId());
                    plugin.getLogger().info("Освобождена арена " + duel.getArena().getId() +
                            " после выхода игрока с сервера");
                }

                // Удаляем дуэль
                playerDuels.remove(duel.getPlayer1Id());
                playerDuels.remove(duel.getPlayer2Id());

                // Восстанавливаем арену, если нужно
                if (plugin.getConfig().getBoolean("restoration.auto-restore", true)) {
                    plugin.getRestoreManager().restoreArenaAreas(duel.getArena().getId());
                }
            }
        }

        // Проверяем, есть ли игрок в списке арен
        if (playerArenas.containsKey(playerId)) {
            String arenaId = playerArenas.get(playerId).getId();
            occupiedArenas.remove(arenaId);
            playerArenas.remove(playerId);
            plugin.getLogger().info("Освобождена арена " + arenaId +
                    " после выхода игрока с сервера");
        }
    }

    // Добавляем метод для очистки арены
    private void scheduleArenaCleanup(Arena arena) {
        if (arena == null || arena.getSpawn1() == null || arena.getSpawn1().getWorld() == null) {
            return;
        }

        // Запускаем очистку через 1 секунду после телепортации игроков
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World world = arena.getSpawn1().getWorld();

            // Определяем границы арены
            double minX = Math.min(arena.getSpawn1().getX(), arena.getSpawn2().getX()) - 30;
            double maxX = Math.max(arena.getSpawn1().getX(), arena.getSpawn2().getX()) + 30;
            double minY = Math.min(arena.getSpawn1().getY(), arena.getSpawn2().getY()) - 10;
            double maxY = Math.max(arena.getSpawn1().getY(), arena.getSpawn2().getY()) + 10;
            double minZ = Math.min(arena.getSpawn1().getZ(), arena.getSpawn2().getZ()) - 30;
            double maxZ = Math.max(arena.getSpawn1().getZ(), arena.getSpawn2().getZ()) + 30;

            // Получаем все сущности в пределах арены
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) continue; // Пропускаем игроков

                Location loc = entity.getLocation();

                // Проверяем, находится ли сущность в пределах арены
                if (loc.getX() >= minX && loc.getX() <= maxX &&
                        loc.getY() >= minY && loc.getY() <= maxY &&
                        loc.getZ() >= minZ && loc.getZ() <= maxZ) {

                    // Удаляем сущность, если это стрела, выпавший предмет или другой мусор
                    if (entity instanceof Arrow ||
                            entity instanceof Item ||
                            entity instanceof ExperienceOrb) {
                        entity.remove();
                    }
                }
            }

            plugin.getLogger().info("Арена " + arena.getId() + " очищена от мусора.");
        }, 20L); // Через 1 секунду
    }



    /**
     * Удаляет игрока из всех очередей при выходе с сервера
     */
    public void removeFromQueues(UUID playerId) {
        // Удаляем из очереди поиска
        for (DuelType type : DuelType.values()) {
            queuedPlayers.get(type).remove(playerId);
        }

        // Отменяем таймер поиска
        if (searchTasks.containsKey(playerId)) {
            searchTasks.get(playerId).cancel();
            searchTasks.remove(playerId);
        }

        // Удаляем из очереди на арену
        Iterator<QueueEntry> iterator = arenaQueue.iterator();
        while (iterator.hasNext()) {
            QueueEntry entry = iterator.next();
            if (entry.player1Id.equals(playerId) || entry.player2Id.equals(playerId)) {
                // Нашли пару с этим игроком
                UUID otherPlayerId = entry.player1Id.equals(playerId) ? entry.player2Id : entry.player1Id;
                Player otherPlayer = Bukkit.getPlayer(otherPlayerId);

                // Удаляем пару из очереди
                iterator.remove();

                // Возвращаем другого игрока
                if (otherPlayer != null && otherPlayer.isOnline()) {
                    returnPlayer(otherPlayer, true);
                    otherPlayer.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    "&cПротивник вышел с сервера. Дуэль отменена."));
                }

                break;
            }
        }
    }

    public void cancelDuel(Player player) {
        UUID playerId = player.getUniqueId();

        // Логируем для отладки
        System.out.println("[DUEL-CANCEL] Запуск cancelDuel для игрока " + player.getName());

        // 1. Проверяем, находится ли игрок в очереди на дуэль
        boolean inSearch = false;
        for (DuelType type : DuelType.values()) {
            if (queuedPlayers.get(type).contains(playerId)) {
                System.out.println("[DUEL-CANCEL] Игрок " + player.getName() + " найден в очереди типа " + type);
                inSearch = true;

                // Удаляем из очереди
                queuedPlayers.get(type).remove(playerId);

                // Отменяем таймер поиска
                if (searchTasks.containsKey(playerId)) {
                    searchTasks.get(playerId).cancel();
                    searchTasks.remove(playerId);
                    System.out.println("[DUEL-CANCEL] Отменен таймер поиска");
                }

                // ВОТ ЗДЕСЬ КЛЮЧЕВОЕ ИЗМЕНЕНИЕ:
                // Вместо того чтобы восстанавливать инвентарь, мы просто удаляем сохраненные данные
                // Это предотвратит дюп предметов, так как игрок сохранит только те предметы,
                // которые у него в текущем инвентаре (включая предметы в сундуке)

                // Удаляем сохраненные данные инвентаря без восстановления
                originalInventories.remove(playerId);
                originalArmor.remove(playerId);
                originalWorldLocations.remove(playerId);

                // Разблокируем команды, если они были заблокированы
                if (player.hasMetadata("restduels_blocked_commands")) {
                    player.removeMetadata("restduels_blocked_commands", plugin);
                }

                // Если есть CommandBlocker, разблокируем команды
                try {
                    CommandBlocker commandBlocker = plugin.getCommandBlocker();
                    if (commandBlocker != null) {
                        commandBlocker.removePlayer(playerId);
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки, если класс или методы не найдены
                }

                // Сообщение об отмене
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&aПоиск дуэли успешно отменен!"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                System.out.println("[DUEL-CANCEL] Поиск успешно отменен для игрока " + player.getName());
                return;
            }
        }

        // 2. ВТОРАЯ ПРОВЕРКА: Находится ли игрок в подготовке к дуэли
        if (duelCountdownPlayers.contains(playerId)) {
            System.out.println("[DUEL-CANCEL] Игрок " + player.getName() + " найден в списке подготовки");

            // Удаляем из списка подготовки
            duelCountdownPlayers.remove(playerId);

            // Восстанавливаем скорость
            if (originalWalkSpeed.containsKey(playerId)) {
                player.setWalkSpeed(originalWalkSpeed.get(playerId));
                originalWalkSpeed.remove(playerId);
                System.out.println("[DUEL-CANCEL] Восстановлена скорость ходьбы");
            }

            // Находим второго игрока в подготовке
            Player otherPlayer = null;
            UUID otherPlayerId = null;

            for (UUID id : new HashSet<>(duelCountdownPlayers)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline() && player.getWorld().equals(p.getWorld()) &&
                        player.getLocation().distance(p.getLocation()) < 50) {
                    otherPlayer = p;
                    otherPlayerId = id;
                    System.out.println("[DUEL-CANCEL] Найден второй игрок: " + p.getName());
                    break;
                }
            }

            // Если нашли второго игрока, отменяем его подготовку
            if (otherPlayer != null) {
                duelCountdownPlayers.remove(otherPlayerId);

                // Восстанавливаем скорость для второго игрока
                if (originalWalkSpeed.containsKey(otherPlayerId)) {
                    otherPlayer.setWalkSpeed(originalWalkSpeed.get(otherPlayerId));
                    originalWalkSpeed.remove(otherPlayerId);
                }

                returnPlayer(otherPlayer, true);
                otherPlayer.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cПротивник отменил дуэль!"));
                otherPlayer.playSound(otherPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }

            // Освобождаем арену
            for (Arena arena : plugin.getArenaManager().getArenas()) {
                if (occupiedArenas.contains(arena.getId()) &&
                        (player.getWorld().equals(arena.getSpawn1().getWorld()) ||
                                player.getWorld().equals(arena.getSpawn2().getWorld()))) {
                    occupiedArenas.remove(arena.getId());
                    System.out.println("[DUEL-CANCEL] Освобождена арена: " + arena.getId());
                    break;
                }
            }

            // Возвращаем игрока
            returnPlayer(player, true);
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&aПодготовка к дуэли отменена!"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

            System.out.println("[DUEL-CANCEL] Подготовка успешно отменена для игрока " + player.getName());
            return;
        }

        // 3. ТРЕТЬЯ ПРОВЕРКА: Находится ли игрок в списке замороженных
        if (frozenPlayers.contains(playerId)) {
            System.out.println("[DUEL-CANCEL] Игрок " + player.getName() + " найден в списке замороженных");

            // Удаляем из списка замороженных
            frozenPlayers.remove(playerId);

            // Возвращаем игрока
            returnPlayer(player, true);
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&aДуэль успешно отменена!"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

            System.out.println("[DUEL-CANCEL] Заморозка успешно отменена для игрока " + player.getName());
            return;
        }

        // 4. ЧЕТВЕРТАЯ ПРОВЕРКА: Находится ли игрок в активной дуэли
        if (playerDuels.containsKey(playerId)) {
            System.out.println("[DUEL-CANCEL] Игрок " + player.getName() + " находится в активной дуэли");

            // Проверяем, закончилась ли дуэль и игрок ожидает возврата
            if (hasDelayedReturnTask(playerId)) {
                System.out.println("[DUEL-CANCEL] Игрок имеет отложенную задачу возврата");
                cancelDelayedReturnAndTeleport(player);
                return;
            } else {
                // Игрок в активной дуэли - не разрешаем отмену
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cВы не можете выйти из активной дуэли! Используйте /hub, чтобы покинуть сервер."));
                System.out.println("[DUEL-CANCEL] Отмена активной дуэли запрещена");
                return;
            }
        }

        // 5. ПЯТАЯ ПРОВЕРКА: Находится ли игрок в очереди на арену
        boolean inArenaQueue = isPlayerInArenaQueue(playerId);
        System.out.println("[DUEL-CANCEL] Игрок " + player.getName() + " в очереди на арену: " + inArenaQueue);

        if (inArenaQueue) {
            Iterator<QueueEntry> iterator = arenaQueue.iterator();
            while (iterator.hasNext()) {
                QueueEntry entry = iterator.next();
                if (entry.player1Id.equals(playerId) || entry.player2Id.equals(playerId)) {
                    System.out.println("[DUEL-CANCEL] Игрок " + player.getName() + " найден в очереди на арену");

                    // Нашли пару с этим игроком
                    UUID otherPlayerId = entry.player1Id.equals(playerId) ? entry.player2Id : entry.player1Id;
                    Player otherPlayer = Bukkit.getPlayer(otherPlayerId);

                    // Удаляем пару из очереди
                    iterator.remove();

                    // Возвращаем игроков
                    returnPlayer(player, false);
                    if (otherPlayer != null && otherPlayer.isOnline()) {
                        returnPlayer(otherPlayer, true);
                        otherPlayer.sendMessage(ColorUtils.colorize(
                                plugin.getConfig().getString("messages.prefix") +
                                        "&cПротивник отменил дуэль!"));
                        otherPlayer.playSound(otherPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    }

                    // Сообщение об отмене
                    player.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    "&aОжидание арены отменено!"));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

                    System.out.println("[DUEL-CANCEL] Ожидание арены успешно отменено");
                    return;
                }
            }
        }

        // Если игрок не найден ни в одной из проверок
        System.out.println("[DUEL-CANCEL] Игрок " + player.getName() + " не найден ни в одном состоянии дуэли");
        player.sendMessage(ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix") +
                        "&cВы не участвуете в дуэли!"));
    }

    /**
     * Проверяет, находится ли игрок в очереди на арену
     */
    private boolean isPlayerInArenaQueue(UUID playerId) {
        for (QueueEntry entry : arenaQueue) {
            if (entry.player1Id.equals(playerId) || entry.player2Id.equals(playerId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проверяет, находится ли игрок в очереди на дуэль
     */
    public boolean isPlayerSearchingDuel(UUID playerId) {
        for (DuelType type : DuelType.values()) {
            if (queuedPlayers.get(type).contains(playerId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Завершает дуэль и телепортирует игрока досрочно
     * @param duel Дуэль для завершения
     * @param winnerId UUID победителя
     */
    public void endDuelAndTeleport(Duel duel, UUID winnerId) {
        // Если у игрока есть отложенная задача возврата, отменяем её
        if (delayedReturnTasks.containsKey(winnerId)) {
            delayedReturnTasks.get(winnerId).cancel();
            delayedReturnTasks.remove(winnerId);
        }

        // Получаем игрока
        Player player = Bukkit.getPlayer(winnerId);
        if (player != null && player.isOnline()) {
            // Телепортируем игрока на исходное местоположение
            returnPlayer(player, true);
        }

        // Если дуэль еще активна, завершаем её
        if (playerDuels.containsKey(winnerId)) {
            // Определяем проигравшего
            UUID loserId = duel.getPlayer1Id().equals(winnerId) ? duel.getPlayer2Id() : duel.getPlayer1Id();

            // Обновляем статистику
            plugin.getStatsManager().incrementWins(winnerId);
            plugin.getStatsManager().incrementDeaths(loserId);

            // Освобождаем арену
            Arena arena = duel.getArena();
            // Удаляем строку с ArenaStatus, так как enum отсутствует
            // if (arena != null) {
            //     arena.setStatus(ArenaStatus.FREE);
            // }

            // Удаляем дуэль из списка активных
            playerDuels.remove(duel.getPlayer1Id());
            playerDuels.remove(duel.getPlayer2Id());

            // Удаляем связь игроков с ареной
            playerArenas.remove(winnerId);
            playerArenas.remove(loserId);
        }
    }

    /**
     * Останавливает все активные дуэли и возвращает игроков
     * Используется при перезагрузке/выключении сервера
     */
    public void stopAllDuels() {
        // Завершаем все активные дуэли и восстанавливаем инвентарь
        for (Duel duel : new ArrayList<>(playerDuels.values())) {
            // Получаем игроков
            Player player1 = Bukkit.getPlayer(duel.getPlayer1Id());
            Player player2 = Bukkit.getPlayer(duel.getPlayer2Id());

            // ДОБАВЛЕНО: Очищаем эффекты при принудительном завершении дуэлей
            clearEffects(player1);
            clearEffects(player2);

            // Освобождаем арену
            if (duel.getArena() != null) {
                occupiedArenas.remove(duel.getArena().getId());
            }

            // Отменяем таймер дуэли
            if (duel.getDuelTask() != null) {
                duel.getDuelTask().cancel();
            }

            // Отменяем таймер отображения
            if (duel.getTimerTask() != null) {
                duel.getTimerTask().cancel();
            }

            // Восстанавливаем инвентарь и возвращаем игроков
            if (player1 != null && player1.isOnline()) {
                // ДОБАВЛЕНО: Разблокируем команды для игрока 1
                if (player1.hasMetadata("restduels_blocked_commands")) {
                    player1.removeMetadata("restduels_blocked_commands", plugin);
                }

                // Если есть CommandBlocker, разблокируем команды
                try {
                    Class<?> commandBlockerClass = Class.forName("ru.refontstudio.restduels.listeners.CommandBlocker");
                    if (commandBlockerClass != null) {
                        java.lang.reflect.Method getInstanceMethod = commandBlockerClass.getMethod("getInstance");
                        if (getInstanceMethod != null) {
                            Object commandBlocker = getInstanceMethod.invoke(null);
                            java.lang.reflect.Method removePlayerMethod = commandBlocker.getClass().getMethod("removePlayer", UUID.class);
                            if (removePlayerMethod != null) {
                                removePlayerMethod.invoke(commandBlocker, player1.getUniqueId());
                            }
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки, если класс или методы не найдены
                }

                restorePlayerInventory(player1);
                if (playerLocations.containsKey(player1.getUniqueId())) {
                    safeTeleport(player1, playerLocations.get(player1.getUniqueId()));
                    playerLocations.remove(player1.getUniqueId());
                }
                player1.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cДуэль была отменена из-за перезагрузки плагина."));
            }

            if (player2 != null && player2.isOnline()) {
                // ДОБАВЛЕНО: Разблокируем команды для игрока 2
                if (player2.hasMetadata("restduels_blocked_commands")) {
                    player2.removeMetadata("restduels_blocked_commands", plugin);
                }

                // Если есть CommandBlocker, разблокируем команды
                try {
                    Class<?> commandBlockerClass = Class.forName("ru.refontstudio.restduels.listeners.CommandBlocker");
                    if (commandBlockerClass != null) {
                        java.lang.reflect.Method getInstanceMethod = commandBlockerClass.getMethod("getInstance");
                        if (getInstanceMethod != null) {
                            Object commandBlocker = getInstanceMethod.invoke(null);
                            java.lang.reflect.Method removePlayerMethod = commandBlocker.getClass().getMethod("removePlayer", UUID.class);
                            if (removePlayerMethod != null) {
                                removePlayerMethod.invoke(commandBlocker, player2.getUniqueId());
                            }
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки, если класс или методы не найдены
                }

                restorePlayerInventory(player2);
                if (playerLocations.containsKey(player2.getUniqueId())) {
                    safeTeleport(player2, playerLocations.get(player2.getUniqueId()));
                    playerLocations.remove(player2.getUniqueId());
                }
                player2.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cДуэль была отменена из-за перезагрузки плагина."));
            }
        }

        // Отменяем все таймеры поиска
        for (BukkitTask task : searchTasks.values()) {
            task.cancel();
        }
        searchTasks.clear();

        // Отменяем все отложенные задачи возврата
        for (BukkitTask task : delayedReturnTasks.values()) {
            task.cancel();
        }
        delayedReturnTasks.clear();

        // Отменяем все задачи проверки полета
        for (BukkitTask task : flightCheckTasks.values()) {
            task.cancel();
        }
        flightCheckTasks.clear();

        // Восстанавливаем игроков из очередей и разблокируем команды
        for (DuelType type : DuelType.values()) {
            for (UUID playerId : queuedPlayers.get(type)) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    // ДОБАВЛЕНО: Разблокируем команды
                    if (player.hasMetadata("restduels_blocked_commands")) {
                        player.removeMetadata("restduels_blocked_commands", plugin);
                    }

                    // Если есть CommandBlocker, разблокируем команды
                    try {
                        Class<?> commandBlockerClass = Class.forName("ru.refontstudio.restduels.listeners.CommandBlocker");
                        if (commandBlockerClass != null) {
                            java.lang.reflect.Method getInstanceMethod = commandBlockerClass.getMethod("getInstance");
                            if (getInstanceMethod != null) {
                                Object commandBlocker = getInstanceMethod.invoke(null);
                                java.lang.reflect.Method removePlayerMethod = commandBlocker.getClass().getMethod("removePlayer", UUID.class);
                                if (removePlayerMethod != null) {
                                    removePlayerMethod.invoke(commandBlocker, playerId);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Игнорируем ош��бки, если класс или методы не найдены
                    }

                    restorePlayerInventory(player);
                    if (playerLocations.containsKey(playerId)) {
                        safeTeleport(player, playerLocations.get(playerId));
                        playerLocations.remove(playerId);
                    }
                    player.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    "&cПоиск дуэли отменен из-за перезагрузки плагина."));
                }
            }
            queuedPlayers.get(type).clear();
        }

        // Восстанавливаем игроков из очереди на арену
        for (QueueEntry entry : arenaQueue) {
            // Игрок 1
            Player player1 = Bukkit.getPlayer(entry.player1Id);
            if (player1 != null && player1.isOnline()) {
                // ДОБАВЛЕНО: Разблокируем команды
                if (player1.hasMetadata("restduels_blocked_commands")) {
                    player1.removeMetadata("restduels_blocked_commands", plugin);
                }

                // Если есть CommandBlocker, разблокируем команды
                try {
                    Class<?> commandBlockerClass = Class.forName("ru.refontstudio.restduels.listeners.CommandBlocker");
                    if (commandBlockerClass != null) {
                        java.lang.reflect.Method getInstanceMethod = commandBlockerClass.getMethod("getInstance");
                        if (getInstanceMethod != null) {
                            Object commandBlocker = getInstanceMethod.invoke(null);
                            java.lang.reflect.Method removePlayerMethod = commandBlocker.getClass().getMethod("removePlayer", UUID.class);
                            if (removePlayerMethod != null) {
                                removePlayerMethod.invoke(commandBlocker, entry.player1Id);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки, если класс или методы не найдены
                }

                restorePlayerInventory(player1);
                if (playerLocations.containsKey(entry.player1Id)) {
                    safeTeleport(player1, playerLocations.get(entry.player1Id));
                    playerLocations.remove(entry.player1Id);
                }
                player1.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cОжидание арены отменено из-за перезагрузки плагина."));
            }

            // Игрок 2
            Player player2 = Bukkit.getPlayer(entry.player2Id);
            if (player2 != null && player2.isOnline()) {
                // ДОБАВЛЕНО: Разблокируем команды
                if (player2.hasMetadata("restduels_blocked_commands")) {
                    player2.removeMetadata("restduels_blocked_commands", plugin);
                }

                // Если есть CommandBlocker, разблокируем команды
                try {
                    Class<?> commandBlockerClass = Class.forName("ru.refontstudio.restduels.listeners.CommandBlocker");
                    if (commandBlockerClass != null) {
                        java.lang.reflect.Method getInstanceMethod = commandBlockerClass.getMethod("getInstance");
                        if (getInstanceMethod != null) {
                            Object commandBlocker = getInstanceMethod.invoke(null);
                            java.lang.reflect.Method removePlayerMethod = commandBlocker.getClass().getMethod("removePlayer", UUID.class);
                            if (removePlayerMethod != null) {
                                removePlayerMethod.invoke(commandBlocker, entry.player2Id);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки, если класс или методы не найдены
                }

                restorePlayerInventory(player2);
                if (playerLocations.containsKey(entry.player2Id)) {
                    safeTeleport(player2, playerLocations.get(entry.player2Id));
                    playerLocations.remove(entry.player2Id);
                }
                player2.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cОжидание арены отменено из-за перезагрузки плагина."));
            }
        }
        arenaQueue.clear();

        // Размораживаем всех игроков
        for (UUID playerId : new HashSet<>(frozenPlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                // ДОБАВЛЕНО: Разблокируем команды
                if (player.hasMetadata("restduels_blocked_commands")) {
                    player.removeMetadata("restduels_blocked_commands", plugin);
                }

                // Если есть CommandBlocker, разблокируем команды
                try {
                    Class<?> commandBlockerClass = Class.forName("ru.refontstudio.restduels.listeners.CommandBlocker");
                    if (commandBlockerClass != null) {
                        java.lang.reflect.Method getInstanceMethod = commandBlockerClass.getMethod("getInstance");
                        if (getInstanceMethod != null) {
                            Object commandBlocker = getInstanceMethod.invoke(null);
                            java.lang.reflect.Method removePlayerMethod = commandBlocker.getClass().getMethod("removePlayer", UUID.class);
                            if (removePlayerMethod != null) {
                                removePlayerMethod.invoke(commandBlocker, playerId);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки, если класс или методы не найдены
                }

                restorePlayerInventory(player);
                if (playerLocations.containsKey(playerId)) {
                    safeTeleport(player, playerLocations.get(playerId));
                    playerLocations.remove(playerId);
                }
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cПодготовка к дуэли отменена из-за перезагрузки плагина."));
            }
        }
        frozenPlayers.clear();

        // Очищаем все остальные коллекции
        playerDuels.clear();
        playerArenas.clear();
        occupiedArenas.clear();
        lastTeleportTime.clear();
        arenaLastUsedTime.clear();
        recentlyUsedArenas.clear();
        worldPlayerCount.clear();
        entitiesMarkedForRemoval.clear();
        playerFlightStatus.clear();
        originalInventories.clear();
        originalArmor.clear();
        // Очищаем сохраненное здоровье
        originalPlayerHealth.clear();
        originalPlayerFood.clear();
        originalPlayerSaturation.clear();

        // ДОБАВЛЕНО: Попытка очистить CommandBlocker
        try {
            Class<?> commandBlockerClass = Class.forName("ru.refontstudio.restduels.listeners.CommandBlocker");
            if (commandBlockerClass != null) {
                // Проверяем наличие метода clearAllPlayers
                try {
                    java.lang.reflect.Method clearMethod = commandBlockerClass.getMethod("clearAllPlayers");
                    if (clearMethod != null) {
                        java.lang.reflect.Method getInstanceMethod = commandBlockerClass.getMethod("getInstance");
                        Object commandBlocker = getInstanceMethod.invoke(null);
                        clearMethod.invoke(commandBlocker);
                    }
                } catch (NoSuchMethodException e) {
                    // Если метода нет, просто пропускаем
                    plugin.getLogger().info("Метод clearAllPlayers не найден в CommandBlocker");
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки, если класс не найден
        }
    }

    /**
     * Проверяет, находится ли игрок в активной дуэли
     * @param playerId UUID игрока
     * @return true, если игрок в активной дуэли
     */
    public boolean isPlayerInDuel(UUID playerId) {
        // Игрок в дуэли, только если он в активной дуэли (не в поиске и не в подготовке)
        return playerDuels.containsKey(playerId) && !delayedReturnTasks.containsKey(playerId);
    }

    public boolean isPlayerFrozen(UUID playerId) {
        return frozenPlayers.contains(playerId);
    }

    public Duel getPlayerDuel(UUID playerId) {
        return playerDuels.get(playerId);
    }

    /**
     * Получает количество игроков в очереди на дуэль указанного типа
     * @param type Тип дуэли
     * @return Количество игроков в очереди
     */
    public int getQueueSize(DuelType type) {
        if (!queuedPlayers.containsKey(type)) {
            return 0;
        }
        return queuedPlayers.get(type).size();
    }

    /**
     * Получает количество игроков в очереди на арену
     * @return Количество игроков в очереди
     */
    public int getArenaQueueSize() {
        return arenaQueue.size() * 2; // Каждая запись в очереди содержит пару игроков
    }

    /**
     * Получает количество занятых арен
     * @return Количество занятых арен
     */
    public int getOccupiedArenasCount() {
        return occupiedArenas.size();
    }

    /**
     * Очищает все дуэли без запуска новых задач
     * Безопасный метод для использования при выключении плагина
     */
    public void clearAllDuelsWithoutTasks() {
        // Отменяем все таймеры
        for (BukkitTask task : searchTasks.values()) {
            try {
                task.cancel();
            } catch (Exception e) {
                // Игнорируем ошибки при отмене задач
            }
        }
        searchTasks.clear();

        for (BukkitTask task : delayedReturnTasks.values()) {
            try {
                task.cancel();
            } catch (Exception e) {
                // Игнорируем ошибки при отмене задач
            }
        }
        delayedReturnTasks.clear();

        for (BukkitTask task : flightCheckTasks.values()) {
            try {
                task.cancel();
            } catch (Exception e) {
                // Игнорируем ошибки при отмене задач
            }
        }
        flightCheckTasks.clear();

        // Очищаем все коллекции без запуска новых задач
        playerDuels.clear();
        playerArenas.clear();
        occupiedArenas.clear();
        frozenPlayers.clear();
        lastTeleportTime.clear();
        arenaLastUsedTime.clear();
        recentlyUsedArenas.clear();
        worldPlayerCount.clear();
        entitiesMarkedForRemoval.clear();
        playerFlightStatus.clear();
        originalInventories.clear();
        originalArmor.clear();
        // Очищаем сохраненное здоровье
        originalPlayerHealth.clear();
        originalPlayerFood.clear();
        originalPlayerSaturation.clear();

        // Очищаем очереди
        for (DuelType type : DuelType.values()) {
            queuedPlayers.get(type).clear();
        }
        arenaQueue.clear();

        plugin.getLogger().info("Все дуэли и очереди успешно очищены");
    }

    // Сохраняем локацию игрока в файл
    private void savePlayerLocationToFile(UUID playerId, Location location) {
        try {
            // Проверяем, что локация не в мире дуэлей
            if (plugin.getConfig().getStringList("worlds.duel-worlds").contains(
                    location.getWorld().getName().toLowerCase())) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Не сохраняем локацию в мире дуэлей для игрока " + playerId);
                }
                return;
            }

            File locationsFolder = new File(plugin.getDataFolder(), "player_locations");
            if (!locationsFolder.exists()) {
                locationsFolder.mkdirs();
            }

            File locationFile = new File(locationsFolder, playerId.toString() + ".dat");
            YamlConfiguration config = new YamlConfiguration();

            config.set("world", location.getWorld().getName());
            config.set("x", location.getX());
            config.set("y", location.getY());
            config.set("z", location.getZ());
            config.set("yaw", location.getYaw());
            config.set("pitch", location.getPitch());
            config.set("timestamp", System.currentTimeMillis());

            config.save(locationFile);

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Сохранена локация игрока " + playerId + ": " +
                        location.getWorld().getName() + ", " +
                        location.getX() + ", " +
                        location.getY() + ", " +
                        location.getZ());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось сохранить локацию игрока: " + e.getMessage());
        }
    }

    // Загружаем локацию игрока из файла
    private Location loadPlayerLocationFromFile(UUID playerId) {
        try {
            File locationFile = new File(plugin.getDataFolder() + "/player_locations", playerId.toString() + ".dat");
            if (!locationFile.exists()) {
                return null;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(locationFile);
            String worldName = config.getString("world");
            World world = Bukkit.getWorld(worldName);

            if (world == null) {
                plugin.getLogger().warning("Не удалось найти мир " + worldName + " для игрока " + playerId);
                return null;
            }

            double x = config.getDouble("x");
            double y = config.getDouble("y");
            double z = config.getDouble("z");
            float yaw = (float) config.getDouble("yaw");
            float pitch = (float) config.getDouble("pitch");

            // Проверяем, не устарела ли локация (72 часа по умолчанию)
            long expirationHours = plugin.getConfig().getLong("location_saving.expiration_time", 72);
            long timestamp = config.getLong("timestamp", 0);
            if (System.currentTimeMillis() - timestamp > expirationHours * 60 * 60 * 1000) {
                plugin.getLogger().info("Локация игрока " + playerId + " устарела и будет удалена");
                locationFile.delete();
                return null;
            }

            Location location = new Location(world, x, y, z, yaw, pitch);

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Загружена локация игрока " + playerId + ": " +
                        world.getName() + ", " + x + ", " + y + ", " + z);
            }

            return location;
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось загрузить локацию игрока: " + e.getMessage());
            return null;
        }
    }

    // Получить общее количество арен
    public int getTotalArenasCount() {
        int count = plugin.getArenaManager().getArenas().size();
        // Если арен нет, проверяем конфиг
        if (count == 0) {
            ConfigurationSection arenasSection = plugin.getConfig().getConfigurationSection("arenas");
            if (arenasSection != null) {
                count = arenasSection.getKeys(false).size();
            }
        }
        return count;
    }

    private void sendCancelButton(Player player) {
        UUID playerId = player.getUniqueId();

        // Определяем, находится ли игрок в режиме поиска или уже на арене
        boolean isOnArena = duelCountdownPlayers.contains(playerId);

        // Используем разные сообщения и кнопки в зависимости от ситуации
        String messageKey;
        String buttonKey;
        String message;

        if (isOnArena) {
            // Игрок на арене, ожидает начала дуэли (8 секунд)
            buttonKey = "messages.cancel-button";

            // Для арены используем сообщение без упоминания времени
            message = plugin.getConfig().getString("messages.cancel-duel-message-no-time",
                    "&eДля отмены дуэли нажмите на кнопку ниже.");
        } else {
            // Игрок в режиме поиска дуэли (30 секунд)
            buttonKey = "messages.cancel-search-button";

            // Для поиска оставляем сообщение с временем
            int searchTime = plugin.getConfig().getInt("timers.search-time", 30);
            message = plugin.getConfig().getString("messages.cancel-search-message",
                    "&eДля отмены поиска дуэли нажмите на кнопку ниже в течение &c%time% &eсекунд.");

            // Заменяем %time% на фактическое время
            message = message.replace("%time%", String.valueOf(searchTime));
        }

        // Отправляем сообщение
        player.sendMessage(ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix") + message));

        // Получаем текст кнопки из конфига или используем значение по умолчанию
        String cancelText = plugin.getConfig().getString(buttonKey,
                isOnArena ? "&c[Выйти с дуэли]" : "&c[Отменить поиск дуэли]");

        // Форматируем текст кнопки
        cancelText = ChatColor.translateAlternateColorCodes('&', cancelText);

        // Экранируем кавычки для JSON
        cancelText = cancelText.replace("\"", "\\\"");

        // Создаем JSON для кликабельного текста
        String json = "{\"text\":\"" + cancelText + "\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/duel cancel\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Нажмите, чтобы отменить " + (isOnArena ? "дуэль" : "поиск") + "\"}}";

        // Отправляем команду tellraw для создания кликабельного текста
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + json);
    }
}