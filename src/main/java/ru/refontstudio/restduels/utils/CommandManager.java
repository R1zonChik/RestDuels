package ru.refontstudio.restduels.utils;

import ru.refontstudio.restduels.RestDuels;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CommandManager {
    private static CommandManager instance;
    private final RestDuels plugin;
    private final Set<UUID> blockedPlayers = ConcurrentHashMap.newKeySet();

    private CommandManager(RestDuels plugin) {
        this.plugin = plugin;
    }

    public static CommandManager getInstance() {
        return instance;
    }

    public static void initialize(RestDuels plugin) {
        if (instance == null) {
            instance = new CommandManager(plugin);
        }
    }

    /**
     * Блокирует команды для игрока
     * @param playerId UUID игрока
     */
    public void blockCommands(UUID playerId) {
        blockedPlayers.add(playerId);
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Команды заблокированы для игрока " + playerId);
        }
    }

    /**
     * Разблокирует команды для игрока
     * @param playerId UUID игрока
     */
    public void unblockCommands(UUID playerId) {
        blockedPlayers.remove(playerId);
        if (plugin.getConfig().getBoolean("debug", false)) {
        }
    }

    /**
     * Проверяет, заблокированы ли команды для игрока
     * @param playerId UUID игрока
     * @return true, если команды заблокированы
     */
    public boolean areCommandsBlocked(UUID playerId) {
        return blockedPlayers.contains(playerId);
    }

    /**
     * Разблокирует команды для всех игроков
     */
    public void unblockAllCommands() {
        blockedPlayers.clear();
    }
}