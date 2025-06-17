package ru.refontstudio.restduels.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DuelCommandBlocker implements Listener {

    private final RestDuels plugin;
    private final Set<String> allowedCommands = new HashSet<>();
    private final Set<String> alwaysBlockedCommands = new HashSet<>();
    private final Set<String> duelWorldNames = new HashSet<>();
    private boolean blockCommandsDuringPreparation;
    private boolean blockCommandsDuringEnd;

    public DuelCommandBlocker(RestDuels plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Загружает конфигурацию из config.yml и antidupe.yml
     */
    private void loadConfig() {
        // Загружаем основную конфигурацию плагина
        FileConfiguration config = plugin.getConfig();

        // Загружаем имена миров дуэлей
        duelWorldNames.addAll(config.getStringList("worlds.duel-worlds"));
        if (duelWorldNames.isEmpty()) {
            duelWorldNames.add("duels");
        }

        // Загружаем конфигурацию antidupe.yml
        File file = new File(plugin.getDataFolder(), "antidupe.yml");

        // Создаем файл, если он не существует
        if (!file.exists()) {
            plugin.saveResource("antidupe.yml", false);
        }

        // Загружаем конфигурацию или используем значения по умолчанию
        FileConfiguration antiDupeConfig = YamlConfiguration.loadConfiguration(file);
        blockCommandsDuringPreparation = antiDupeConfig.getBoolean("block-commands.during-preparation", true);
        blockCommandsDuringEnd = antiDupeConfig.getBoolean("block-commands.during-end", true);

        // Добавляем разрешенные команды
        List<String> configAllowedCommands = antiDupeConfig.getStringList("block-commands.allowed-commands");
        if (configAllowedCommands.isEmpty()) {
            // Значения по умолчанию если в конфиге не указано
            allowedCommands.add("/spawn");
            allowedCommands.add("/tp");
            allowedCommands.add("/тп"); // Кириллический вариант для русских серверов
        } else {
            allowedCommands.addAll(configAllowedCommands);
        }

        // Добавляем команды, которые всегда блокируются в мире дуэлей
        alwaysBlockedCommands.add("/ec");
        alwaysBlockedCommands.add("/enderchest");
        alwaysBlockedCommands.add("/echest");
        alwaysBlockedCommands.add("/eechest");
        alwaysBlockedCommands.add("/endersee");
        alwaysBlockedCommands.add("/enderview");
        alwaysBlockedCommands.add("/ender");

        // Логируем загруженную конфигурацию в режиме отладки
        if (config.getBoolean("debug", false)) {
            plugin.getLogger().info("Загружены разрешенные команды: " + allowedCommands);
            plugin.getLogger().info("Всегда блокируемые команды: " + alwaysBlockedCommands);
            plugin.getLogger().info("Блокировка команд во время подготовки: " + blockCommandsDuringPreparation);
            plugin.getLogger().info("Блокировка команд во время окончания: " + blockCommandsDuringEnd);
        }
    }

    /**
     * Проверяет, находится ли игрок в мире дуэлей
     */
    private boolean isInDuelWorld(Player player) {
        return duelWorldNames.contains(player.getWorld().getName().toLowerCase());
    }

    /**
     * Проверяет, находится ли игрок в подготовке к дуэли
     */
    private boolean isInPreparation(Player player) {
        return plugin.getDuelManager().isPlayerInCountdown(player.getUniqueId());
    }

    /**
     * Проверяет, находится ли игрок в фазе окончания дуэли
     */
    private boolean isInEndPhase(Player player) {
        return plugin.getDuelManager().isPlayerInEndPhase(player.getUniqueId());
    }

    private boolean isAllowedCommand(String command) {
        String lowerCommand = command.toLowerCase();

        // Проверяем полную команду с аргументами
        for (String allowedCommand : allowedCommands) {
            if (lowerCommand.equals(allowedCommand.toLowerCase()) ||
                    lowerCommand.startsWith(allowedCommand.toLowerCase() + " ")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проверяет, является ли команда в списке всегда блокируемых
     */
    private boolean isAlwaysBlocked(String command) {
        // Преобразуем полную команду в основную команду
        String baseCommand = command.split(" ")[0].toLowerCase();

        // Проверяем, содержится ли команда в списке всегда блокируемых
        for (String blockedCommand : alwaysBlockedCommands) {
            if (baseCommand.equals(blockedCommand.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Блокирует все команды, кроме разрешенных, во время подготовки и окончания дуэли
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        // Проверяем, находится ли игрок в мире дуэлей
        if (!isInDuelWorld(player)) {
            return;
        }

        // Сначала проверяем, является ли команда среди всегда блокируемых
        if (isAlwaysBlocked(event.getMessage())) {
            event.setCancelled(true);
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cЭта команда запрещена в мире дуэлей!"));
            return;
        }

        // Проверяем, находится ли игрок в фазе подготовки или окончания дуэли
        boolean inPreparation = isInPreparation(player);
        boolean inEndPhase = isInEndPhase(player);

        // Проверяем, нужно ли блокировать команды в текущей фазе
        if ((inPreparation && blockCommandsDuringPreparation) || (inEndPhase && blockCommandsDuringEnd)) {
            // Проверяем, разрешена ли команда
            if (!isAllowedCommand(event.getMessage()) && !player.hasPermission("restduels.bypass.commandblock")) {
                // Блокируем команду, если она не в списке разрешенных и у игрока нет права обхода
                event.setCancelled(true);
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cИспользование команд запрещено во время дуэли! Разрешены только: " +
                                String.join(", ", allowedCommands)));
            }
        }
    }
}