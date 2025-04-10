package ru.refontstudio.restduels.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.util.*;

public class CommandBlocker implements Listener {
    private final RestDuels plugin;
    private final Set<String> allowedCommands = new HashSet<>();
    private boolean protocolLibEnabled = false;

    public CommandBlocker(RestDuels plugin) {
        this.plugin = plugin;

        // Загружаем разрешенные команды
        loadAllowedCommands();

        // Проверяем наличие ProtocolLib
        if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            try {
                // Пытаемся инициализировать ProtocolLib
                initProtocolLib();
                protocolLibEnabled = true;
                plugin.getLogger().info("ProtocolLib найден и активирован для блокировки команд");
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при инициализации ProtocolLib: " + e.getMessage());
                plugin.getLogger().warning("Будет использована стандартная блокировка команд");
            }
        } else {
            plugin.getLogger().warning("ProtocolLib не найден! Будет использована стандартная блокировка команд.");
        }

        // Отключаем проблемные команды
        disableProblematicCommands();
    }

    /**
     * Инициализирует ProtocolLib (в отдельном методе для обработки исключений)
     */
    private void initProtocolLib() {
        // Загружаем класс ProtocolLibIntegration динамически
        try {
            Class.forName("ru.refontstudio.restduels.integrations.ProtocolLibIntegration")
                    .getConstructor(RestDuels.class, CommandBlocker.class)
                    .newInstance(plugin, this);

            plugin.getLogger().info("ProtocolLib интеграция успешно инициализирована");
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось инициализировать ProtocolLib: " + e.getMessage());
            throw new RuntimeException("Ошибка инициализации ProtocolLib", e);
        }
    }

    /**
     * Загружает список разрешенных команд из конфига
     */
    public void loadAllowedCommands() {
        allowedCommands.clear();

        // Добавляем стандартные разрешенные команды
        allowedCommands.add("/hub");
        allowedCommands.add("/duel return"); // Добавляем команду досрочного возврата

        // Загружаем дополнительные команды из конфига, если они есть
        if (plugin.getConfig().contains("commands.allowed-during-duel")) {
            for (String cmd : plugin.getConfig().getStringList("commands.allowed-during-duel")) {
                allowedCommands.add(cmd.toLowerCase());
            }
        }

        // Добавляем альтернативные варианты команд (с / и без /)
        Set<String> additionalCommands = new HashSet<>();
        for (String cmd : allowedCommands) {
            if (cmd.startsWith("/")) {
                // Для команд с аргументами, добавляем только базовую команду
                String baseCmd = cmd.split(" ")[0];
                additionalCommands.add(baseCmd.substring(1));
            } else {
                additionalCommands.add("/" + cmd);
            }
        }
        allowedCommands.addAll(additionalCommands);

        // Логируем загруженные команды
        plugin.getLogger().info("Загружено " + allowedCommands.size() + " разрешенных команд во время дуэли: " +
                String.join(", ", allowedCommands));
    }

    /**
     * Отключает команды, которые работают во время дуэли
     */
    public void disableProblematicCommands() {
        // Список команд, которые нужно отключить
        List<String> problematicCommands = Arrays.asList(
                "spawn", "tpa", "home", "tp", "teleport", "warp", "back", "rtp", "wild", "tpaccept"
        );

        for (String cmdName : problematicCommands) {
            try {
                // Получаем команду напрямую из плагина
                PluginCommand command = Bukkit.getPluginCommand(cmdName);
                if (command != null) {
                    // Сохраняем оригинальный исполнитель
                    CommandExecutor originalExecutor = command.getExecutor();

                    // Создаем ссылку на основной плагин для использования в анонимном классе
                    final RestDuels mainPlugin = this.plugin;

                    // Устанавливаем нового исполнителя, который блокирует команду во время дуэли
                    CommandExecutor blockingExecutor = (sender, cmd, label, args) -> {
                        if (sender instanceof Player) {
                            Player player = (Player) sender;
                            if (mainPlugin.getDuelManager().isPlayerInDuel(player.getUniqueId()) ||
                                    mainPlugin.getDuelManager().isPlayerFrozen(player.getUniqueId())) {

                                // Блокируем команду
                                player.sendMessage(ColorUtils.colorize(
                                        mainPlugin.getConfig().getString("messages.prefix") +
                                                "&cКоманды заблокированы во время дуэли! " +
                                                "Разрешена только команда /hub."));
                                return true;
                            }
                        }

                        // Если не в дуэли или не игрок, вызываем оригинальный исполнитель
                        if (originalExecutor != null) {
                            return originalExecutor.onCommand(sender, cmd, label, args);
                        }
                        return false;
                    };

                    // Устанавливаем нового исполнителя
                    command.setExecutor(blockingExecutor);

                    plugin.getLogger().info("Команда /" + cmdName + " переопределена для блокировки во время дуэли");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Не удалось переопределить команду /" + cmdName + ": " + e.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Если игрок в дуэли, проверяем команду
        if (plugin.getDuelManager().isPlayerInDuel(playerId) ||
                plugin.getDuelManager().isPlayerFrozen(playerId)) {

            String command = event.getMessage().toLowerCase(); // Полная команда с аргументами

            // Проверяем, разрешена ли команда
            boolean allowed = false;

            // Проверяем, разрешена ли команда
            if (isCommandAllowed(command)) {
                allowed = true;
            }

            // Проверяем, является ли это командой досрочного возврата
            if (command.startsWith("/duel return") || command.equals("/duel return")) {
                // Разрешаем команду dosrochno
                allowed = true;
            }

            // Проверяем, имеет ли игрок разрешение обходить блокировку
            if (player.hasPermission("restduels.bypass.commandblock")) {
                allowed = true;
            }

            // Если команда не разрешена, блокируем её
            if (!allowed) {
                // Отменяем событи
                event.setCancelled(true);

                // Отправляем сообщение игроку
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cКоманды заблокированы во время дуэли! " +
                                "Разрешены только команды /hub и /duel return."));

                // Логируем блокировку, если включена отладка
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Заблокирована команда " + command + " для игрока " + player.getName() + " во время дуэли");
                }
            }
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

        // Проверяем точное совпадение (для команд с аргументами)
        if (allowedCommands.contains(command)) {
            return true;
        }

        // Проверяем базовую команду (без аргументов)
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

    /**
     * Получает список разрешенных команд
     * @return Список разрешенных команд
     */
    public Set<String> getAllowedCommands() {
        return new HashSet<>(allowedCommands);
    }

    /**
     * Добавляет команду в список разрешенных
     * @param command Команда для добавления
     */
    public void addAllowedCommand(String command) {
        command = command.toLowerCase();
        if (!command.startsWith("/")) {
            command = "/" + command;
        }
        allowedCommands.add(command);
        allowedCommands.add(command.substring(1)); // Добавляем вариант без /
    }

    /**
     * Удаляет команду из списка разрешенных
     * @param command Команда для удаления
     */
    public void removeAllowedCommand(String command) {
        command = command.toLowerCase();
        if (!command.startsWith("/")) {
            command = "/" + command;
        }
        allowedCommands.remove(command);
        allowedCommands.remove(command.substring(1)); // Удаляем вариант без /
    }
}