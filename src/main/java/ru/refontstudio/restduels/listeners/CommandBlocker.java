package ru.refontstudio.restduels.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.scheduler.BukkitTask;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CommandBlocker implements Listener {
    private final RestDuels plugin;
    private final Set<String> allowedCommands = new HashSet<>();
    private final Set<String> alwaysBlockedCommands = new HashSet<>();
    private final List<String> duelWorlds;
    private boolean protocolLibEnabled = false;

    // Статический экземпляр для доступа из других классов
    private static CommandBlocker instance;

    // Хранит UUID игроков, которым сейчас показывается ActionBar
    private final Set<UUID> actionBarActive = ConcurrentHashMap.newKeySet();

    // Кеш задач ActionBar для отмены
    private final Map<UUID, BukkitTask> actionBarTasks = new ConcurrentHashMap<>();

    // Множество игроков, для которых команды заблокированы
    private final Set<UUID> blockedPlayers = ConcurrentHashMap.newKeySet();

    public CommandBlocker(RestDuels plugin) {
        this.plugin = plugin;
        instance = this;

        // Загружаем список миров дуэлей
        duelWorlds = plugin.getConfig().getStringList("worlds.duel-worlds");
        if (duelWorlds.isEmpty()) {
            duelWorlds.add("duels");
        }

        // Загружаем разрешенные команды
        loadAllowedCommands();

        // Добавляем команды, всегда блокируемые в мире дуэлей
        alwaysBlockedCommands.add("/ec");
        alwaysBlockedCommands.add("/enderchest");
        alwaysBlockedCommands.add("/echest");
        alwaysBlockedCommands.add("/eechest");
        alwaysBlockedCommands.add("/endersee");
        alwaysBlockedCommands.add("/enderview");
        alwaysBlockedCommands.add("/ender");

        // Проверяем наличие ProtocolLib
        if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            try {
                initProtocolLib();
                protocolLibEnabled = true;
                plugin.getLogger().info("ProtocolLib найден и активирован для блокировки команд");
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при инициализации ProtocolLib: " + e.getMessage());
            }
        } else {
            plugin.getLogger().warning("ProtocolLib не найден! Будет использована стандартная блокировка команд.");
        }

        // Отключаем проблемные команды
        disableProblematicCommands();

        // Отключаем другие обработчики команд для избежания дублирования
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                // Находим другие слушатели и отключаем их
                Bukkit.getPluginManager().registerEvents(this, plugin);
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при настройке слушателей: " + e.getMessage());
            }
        }, 5L);
    }

    /**
     * Проверяет, находится ли игрок в мире дуэлей
     */
    private boolean isInDuelWorld(Player player) {
        return duelWorlds.contains(player.getWorld().getName().toLowerCase());
    }

    /**
     * Получает экземпляр CommandBlocker
     * @return Экземпляр CommandBlocker
     */
    public static CommandBlocker getInstance() {
        return instance;
    }

    /**
     * Добавляет игрока в список блокированных
     * @param playerId UUID игрока
     */
    public void addPlayer(UUID playerId) {
        blockedPlayers.add(playerId);
    }

    /**
     * Удаляет игрока из списка блокированных
     * @param playerId UUID игрока
     */
    public void removePlayer(UUID playerId) {
        blockedPlayers.remove(playerId);
    }

    /**
     * Проверяет, заблокированы ли команды для игрока
     * @param playerId UUID игрока
     * @return true, если команды заблокированы
     */
    public boolean isBlocked(UUID playerId) {
        return blockedPlayers.contains(playerId);
    }

    /**
     * Очищает всех игроков из списка заблокированных команд
     */
    public void clearAllPlayers() {
        blockedPlayers.clear();
    }

    /**
     * Показывает сообщение в ActionBar вместо обычного чата
     * Это предотвращает спам в чате при повторных командах
     * @param player Игрок для отображения
     * @param message Сообщение для отображения
     */
    private void showActionBarMessage(Player player, String message) {
        UUID playerId = player.getUniqueId();

        // Если у игрока уже активно сообщение, отменяем его
        BukkitTask existingTask = actionBarTasks.get(playerId);
        if (existingTask != null) {
            existingTask.cancel();
        }

        // Если сообщение уже отображается, не показываем новое
        if (actionBarActive.contains(playerId)) {
            return;
        }

        // Добавляем игрока в список активных ActionBar
        actionBarActive.add(playerId);

        // Отправляем ActionBar сообщение
        try {
            // Используем методы NMS или другой API для отображения ActionBar
            // В зависимости от версии сервера
            if (Bukkit.getVersion().contains("1.16") || Bukkit.getVersion().contains("1.17") ||
                    Bukkit.getVersion().contains("1.18") || Bukkit.getVersion().contains("1.19")) {
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(ColorUtils.colorize(message)));
            } else {
                // Для более старых версий - обычное сообщение
                player.sendMessage(ColorUtils.colorize(message));
            }
        } catch (Exception e) {
            // Если не удалось отправить через ActionBar, отправляем обычное сообщение
            player.sendMessage(ColorUtils.colorize(message));
        }

        // Планируем задачу для удаления игрока из списка активных
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            actionBarActive.remove(playerId);
            actionBarTasks.remove(playerId);
        }, 40L); // 2 секунды

        // Сохраняем задачу для возможной отмены
        actionBarTasks.put(playerId, task);
    }

    /**
     * Инициализирует ProtocolLib
     */
    private void initProtocolLib() {
        try {
            Class.forName("ru.refontstudio.restduels.integrations.ProtocolLibIntegration")
                    .getConstructor(RestDuels.class, CommandBlocker.class)
                    .newInstance(plugin, this);
            plugin.getLogger().info("ProtocolLib интеграция успешно инициализирована");
        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации ProtocolLib", e);
        }
    }

    /**
     * Загружает список разрешенных команд из конфига
     */
    public void loadAllowedCommands() {
        allowedCommands.clear();
        allowedCommands.add("/hub");
        allowedCommands.add("/duel return");

        if (plugin.getConfig().contains("commands.allowed-during-duel")) {
            for (String cmd : plugin.getConfig().getStringList("commands.allowed-during-duel")) {
                allowedCommands.add(cmd.toLowerCase());
            }
        }

        Set<String> additionalCommands = new HashSet<>();
        for (String cmd : allowedCommands) {
            if (cmd.startsWith("/")) {
                String baseCmd = cmd.split(" ")[0];
                additionalCommands.add(baseCmd.substring(1));
            } else {
                additionalCommands.add("/" + cmd);
            }
        }
        allowedCommands.addAll(additionalCommands);

        plugin.getLogger().info("Загружено " + allowedCommands.size() + " разрешенных команд во время дуэли");
    }

    /**
     * Отключает команды, которые работают во время дуэли
     */
    public void disableProblematicCommands() {
        List<String> problematicCommands = Arrays.asList(
                "spawn", "tpa", "home", "tp", "teleport", "warp", "back", "rtp", "wild", "tpaccept",
                "enderchest", "ec", "echest", "endersee"
        );

        for (String cmdName : problematicCommands) {
            try {
                PluginCommand command = Bukkit.getPluginCommand(cmdName);
                if (command != null) {
                    CommandExecutor originalExecutor = command.getExecutor();
                    final RestDuels mainPlugin = this.plugin;

                    CommandExecutor blockingExecutor = (sender, cmd, label, args) -> {
                        if (sender instanceof Player) {
                            Player player = (Player) sender;

                            // Блокируем /ec всегда в мире дуэлей
                            if ((cmdName.equals("enderchest") || cmdName.equals("ec") ||
                                    cmdName.equals("echest") || cmdName.equals("endersee")) &&
                                    isInDuelWorld(player)) {

                                showActionBarMessage(player,
                                        mainPlugin.getConfig().getString("messages.prefix") +
                                                "&cЭта команда запрещена в мире дуэлей!");
                                return true;
                            }

                            // В дуэли блокируем все остальные команды
                            if (mainPlugin.getDuelManager().isPlayerInDuel(player.getUniqueId()) ||
                                    mainPlugin.getDuelManager().isPlayerFrozen(player.getUniqueId())) {

                                showActionBarMessage(player,
                                        mainPlugin.getConfig().getString("messages.prefix") +
                                                "&cКоманды заблокированы во время дуэли!");
                                return true;
                            }
                        }

                        if (originalExecutor != null) {
                            return originalExecutor.onCommand(sender, cmd, label, args);
                        }
                        return false;
                    };

                    command.setExecutor(blockingExecutor);
                    plugin.getLogger().info("Команда /" + cmdName + " переопределена для блокировки");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Не удалось переопределить команду /" + cmdName + ": " + e.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();
        String baseCmd = command.split(" ")[0];

        // Всегда блокируем EC в мире дуэлей
        if (isInDuelWorld(player)) {
            for (String blockedCmd : alwaysBlockedCommands) {
                if (baseCmd.equalsIgnoreCase(blockedCmd)) {
                    // Блокируем команду
                    event.setCancelled(true);
                    showActionBarMessage(player,
                            plugin.getConfig().getString("messages.prefix") +
                                    "&cЭта команда запрещена в мире дуэлей!");
                    return;
                }
            }
        }

        // Если игрок находится в списке блокированных
        if (blockedPlayers.contains(player.getUniqueId())) {
            // Проверяем, находится ли игрок в поиске дуэли
            if (plugin.getDuelManager().isPlayerSearchingDuel(player.getUniqueId())) {
                return; // Если в поиске, разрешаем команды
            }

            // Если команда разрешена, пропускаем
            if (isCommandAllowed(command)) {
                return;
            }

            // Отменяем выполнение команды
            event.setCancelled(true);
            showActionBarMessage(player,
                    plugin.getConfig().getString("messages.prefix") +
                            "&cВы не можете использовать команды во время дуэли!");
        }
    }

    /**
     * Проверяет, разрешена ли команда во время дуэли
     * @param command Полная команда для проверки
     * @return true, если команда разрешена
     */
    public boolean isCommandAllowed(String command) {
        command = command.toLowerCase();
        if (!command.startsWith("/")) {
            command = "/" + command;
        }

        // Проверяем точное совпадение
        if (allowedCommands.contains(command)) {
            return true;
        }

        // Проверяем базовую команду
        String baseCmd = command.split(" ")[0];
        if (allowedCommands.contains(baseCmd)) {
            return true;
        }

        // Проверяем, начинается ли команда с разрешенного префикса
        for (String allowed : allowedCommands) {
            if (allowed.contains(" ") && command.startsWith(allowed.split(" ")[0])) {
                return true;
            }
        }

        return false;
    }

    public Set<String> getAllowedCommands() {
        return new HashSet<>(allowedCommands);
    }

    public void addAllowedCommand(String command) {
        command = command.toLowerCase();
        if (!command.startsWith("/")) {
            command = "/" + command;
        }
        allowedCommands.add(command);
        allowedCommands.add(command.substring(1));
    }

    public void removeAllowedCommand(String command) {
        command = command.toLowerCase();
        if (!command.startsWith("/")) {
            command = "/" + command;
        }
        allowedCommands.remove(command);
        allowedCommands.remove(command.substring(1));
    }
}