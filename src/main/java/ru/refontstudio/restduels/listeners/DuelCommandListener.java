package ru.refontstudio.restduels.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class DuelCommandListener implements Listener {
    private final RestDuels plugin;
    private final Set<String> allowedCommands;
    private final Set<String> blockedCommands;
    private final Set<UUID> messageThrottling = ConcurrentHashMap.newKeySet(); // Для предотвращения спама сообщениями

    public DuelCommandListener(RestDuels plugin) {
        this.plugin = plugin;
        this.allowedCommands = new HashSet<>();
        this.blockedCommands = new HashSet<>();

        // Загружаем разрешенные команды из конфига
        List<String> configAllowed = plugin.getConfig().getStringList("commands.allowed-during-duel");
        if (configAllowed.isEmpty()) {
            // Если список пуст, добавляем базовые команды
            allowedCommands.addAll(Arrays.asList(
                    "/duel", "/tell", "/msg", "/r", "/w", "/whisper", "/pm"
            ));
        } else {
            allowedCommands.addAll(configAllowed);
        }

        // Загружаем блокируемые команды из конфига
        List<String> configBlocked = plugin.getConfig().getStringList("commands.blocked-during-duel");
        if (configBlocked.isEmpty()) {
            // Если список пуст, добавляем часто используемые команды телепортации
            blockedCommands.addAll(Arrays.asList(
                    "/spawn", "/warp", "/home", "/tpa", "/tp", "/tphere", "/back",
                    "/tppos", "/tpaccept", "/tpdeny", "/rtp", "/wild", "/wilderness",
                    "/lobby", "/hub", "/ewarp", "/ehome", "/etpa", "/etp", "/etphere",
                    "/eback", "/elobby", "/ehub", "/warps", "/homes"
            ));
        } else {
            blockedCommands.addAll(configBlocked);
        }

        plugin.getLogger().info("Загружено " + allowedCommands.size() + " разрешенных команд во время дуэли");
        plugin.getLogger().info("Загружено " + blockedCommands.size() + " заблокированных команд во время дуэли");

        // Запускаем очистку списка throttling каждые 2 секунды
        Bukkit.getScheduler().runTaskTimer(plugin, this::clearMessageThrottling, 40L, 40L);
    }

    private void clearMessageThrottling() {
        messageThrottling.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST) // Самый высокий приоритет для перехвата
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) {
            return; // Уже отменено другим плагином
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Проверяем, участвует ли игрок в дуэли
        if (!plugin.getDuelManager().isPlayerInDuel(playerId)) {
            return; // Игрок не в дуэли, разрешаем команду
        }

        // Игрок с правом bypass может использовать любые команды
        if (player.hasPermission("restduels.bypass.commands")) {
            return; // Разрешаем команду
        }

        // Получаем сообщение и проверяем, является ли оно командой
        String message = event.getMessage().toLowerCase();
        if (!message.startsWith("/")) {
            return; // Не команда
        }

        // Получаем команду без аргументов (первое слово после /)
        String baseCommand = message.split(" ")[0];

        // Проверяем, является ли команда разрешенной
        boolean isAllowed = false;
        for (String allowedCmd : allowedCommands) {
            String allowedCmdLower = allowedCmd.toLowerCase();
            if (baseCommand.equals(allowedCmdLower) || message.startsWith(allowedCmdLower + " ")) {
                isAllowed = true;
                break;
            }
        }

        // Проверяем, является ли команда явно заблокированной
        boolean isExplicitlyBlocked = false;
        for (String blockedCmd : blockedCommands) {
            String blockedCmdLower = blockedCmd.toLowerCase();
            if (baseCommand.equals(blockedCmdLower) || message.startsWith(blockedCmdLower + " ")) {
                isExplicitlyBlocked = true;
                break;
            }
        }

        // Если команда явно заблокирована или не в списке разрешенных, отменяем событие
        if (isExplicitlyBlocked || !isAllowed) {
            event.setCancelled(true);

            // Отправляем сообщение о блокировке, но не чаще чем раз в 2 секунды
            if (!messageThrottling.contains(playerId)) {
                messageThrottling.add(playerId);

                String blockMessage = plugin.getConfig().getString("messages.commands-blocked",
                        "&cВы не можете использовать команды во время дуэли!");

                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                blockMessage));

                // Логируем блокировку, если включен режим отладки
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Заблокирована команда для " + player.getName() + ": " + message);
                }
            }
        }
    }

    /**
     * Обрабатывает команды от консоли, которые могут быть направлены игрокам в дуэли
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        String command = event.getCommand().toLowerCase();

        // Проверяем, является ли это командой для игрока в дуэли
        if (command.startsWith("execute as ") || command.startsWith("minecraft:execute as ")) {
            // Извлекаем имя игрока из команды execute
            String[] parts = command.split(" ");
            if (parts.length >= 3) {
                String playerName = parts[2];

                // Удаляем @ если есть
                if (playerName.startsWith("@")) {
                    playerName = playerName.substring(1);
                }

                // Находим игрока
                Player player = Bukkit.getPlayer(playerName);
                if (player != null && plugin.getDuelManager().isPlayerInDuel(player.getUniqueId())) {
                    // Проверяем, является ли выполняемая команда разрешенной
                    boolean isAllowed = false;
                    for (String allowedCmd : allowedCommands) {
                        if (command.contains(allowedCmd.toLowerCase())) {
                            isAllowed = true;
                            break;
                        }
                    }

                    // Если команда не разрешена, отменяем событие
                    if (!isAllowed) {
                        event.setCancelled(true);
                        event.getSender().sendMessage("Команда не может быть выполнена для игрока " +
                                playerName + ", так как он находится в дуэли.");

                        if (plugin.getConfig().getBoolean("debug", false)) {
                            plugin.getLogger().warning("Заблокирована консольная команда для игрока в дуэли: " + command);
                        }
                    }
                }
            }
        }
    }
}