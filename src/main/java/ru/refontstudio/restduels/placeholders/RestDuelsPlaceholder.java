package ru.refontstudio.restduels.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.models.Duel;
import ru.refontstudio.restduels.models.DuelType;

/**
 * PlaceholderAPI расширение для плагина RestDuels
 */
public class RestDuelsPlaceholder extends PlaceholderExpansion {

    private final RestDuels plugin;

    public RestDuelsPlaceholder(RestDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "restduels";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Это заставит PlaceholderAPI не выгружать/перезагружать этот экспандер при перезагрузке
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        if (player == null || !player.isOnline()) {
            return "";
        }

        Player p = player.getPlayer();

        // %restduels_in_duel% - проверяет, находится ли игрок в дуэли
        if (identifier.equals("in_duel")) {
            return plugin.getDuelManager().isPlayerInDuel(player.getUniqueId()) ? "true" : "false";
        }

        // %restduels_in_queue% - проверяет, находится ли игрок в очереди на дуэль
        if (identifier.equals("in_queue")) {
            return plugin.getDuelManager().isPlayerInQueue(player.getUniqueId()) ? "true" : "false";
        }

        // %restduels_in_countdown% - проверяет, находится ли игрок в отсчете перед дуэлью
        if (identifier.equals("in_countdown")) {
            return plugin.getDuelManager().isPlayerInCountdown(player.getUniqueId()) ? "true" : "false";
        }

        // %restduels_duel_type% - возвращает тип текущей дуэли игрока
        if (identifier.equals("duel_type")) {
            if (plugin.getDuelManager().isPlayerInDuel(player.getUniqueId())) {
                Duel duel = plugin.getDuelManager().getPlayerDuel(player.getUniqueId());
                if (duel != null) {
                    DuelType type = duel.getType();
                    return type.getName();
                }
            }
            return "Нет";
        }

        // %restduels_opponent% - возвращает имя оппонента в текущей дуэли
        if (identifier.equals("opponent")) {
            if (plugin.getDuelManager().isPlayerInDuel(player.getUniqueId())) {
                Duel duel = plugin.getDuelManager().getPlayerDuel(player.getUniqueId());
                if (duel != null) {
                    if (duel.getPlayer1Id().equals(player.getUniqueId())) {
                        Player opponent = plugin.getServer().getPlayer(duel.getPlayer2Id());
                        return opponent != null ? opponent.getName() : "Неизвестно";
                    } else {
                        Player opponent = plugin.getServer().getPlayer(duel.getPlayer1Id());
                        return opponent != null ? opponent.getName() : "Неизвестно";
                    }
                }
            }
            return "Нет";
        }

        // %restduels_arena% - возвращает название текущей арены
        if (identifier.equals("arena")) {
            if (plugin.getDuelManager().isPlayerInDuel(player.getUniqueId())) {
                Duel duel = plugin.getDuelManager().getPlayerDuel(player.getUniqueId());
                if (duel != null && duel.getArena() != null) {
                    return duel.getArena().getId();
                }
            }
            return "Нет";
        }

        // %restduels_wins% - возвращает количество побед игрока
        if (identifier.equals("wins")) {
            // ИСПРАВЛЕНО: Используем правильный метод для получения побед
            return String.valueOf(plugin.getStatsManager().getPlayerStats(player.getUniqueId()).getWins());
        }

        // %restduels_deaths% - возвращает количество смертей игрока
        if (identifier.equals("deaths")) {
            // ИСПРАВЛЕНО: Используем правильный метод для получения смертей
            return String.valueOf(plugin.getStatsManager().getPlayerStats(player.getUniqueId()).getDeaths());
        }

        // %restduels_kd% - возвращает K/D соотношение игрока
        if (identifier.equals("kd")) {
            // ИСПРАВЛЕНО: Используем правильные методы для получения статистики
            int wins = plugin.getStatsManager().getPlayerStats(player.getUniqueId()).getWins();
            int deaths = plugin.getStatsManager().getPlayerStats(player.getUniqueId()).getDeaths();

            if (deaths == 0) {
                return String.valueOf(wins);
            }

            double kd = (double) wins / deaths;
            return String.format("%.2f", kd);
        }

        // %restduels_win_rate% - возвращает процент побед игрока
        if (identifier.equals("win_rate")) {
            // ИСПРАВЛЕНО: Используем правильные методы для получения статистики
            int wins = plugin.getStatsManager().getPlayerStats(player.getUniqueId()).getWins();
            int deaths = plugin.getStatsManager().getPlayerStats(player.getUniqueId()).getDeaths();

            if (wins == 0 && deaths == 0) {
                return "0";
            }

            double winRate = (double) wins / (wins + deaths) * 100;
            return String.format("%.1f", winRate);
        }

        // %restduels_free_arenas% - возвращает количество свободных арен
        if (identifier.equals("free_arenas")) {
            int total = plugin.getArenaManager().getArenas().size();
            int occupied = plugin.getDuelManager().getOccupiedArenasCount();
            return String.valueOf(total - occupied);
        }

        // %restduels_total_arenas% - возвращает общее количество арен
        if (identifier.equals("total_arenas")) {
            return String.valueOf(plugin.getArenaManager().getArenas().size());
        }

        // %restduels_queue_size_normal% - возвращает количество игроков в очереди на обычную дуэль
        if (identifier.equals("queue_size_normal")) {
            return String.valueOf(plugin.getDuelManager().getQueueSize(DuelType.NORMAL));
        }

        // %restduels_queue_size_ranked% - возвращает количество игроков в очереди на рейтинговую дуэль
        if (identifier.equals("queue_size_ranked")) {
            return String.valueOf(plugin.getDuelManager().getQueueSize(DuelType.RANKED));
        }

        // %restduels_queue_size_classic% - возвращает количество игроков в очереди на классическую дуэль
        if (identifier.equals("queue_size_classic")) {
            return String.valueOf(plugin.getDuelManager().getQueueSize(DuelType.CLASSIC));
        }

        // %restduels_arena_queue_size% - возв��ащает количество игроков в очереди на арену
        if (identifier.equals("arena_queue_size")) {
            return String.valueOf(plugin.getDuelManager().getArenaQueueSize());
        }

        return null; // Неизвестный плейсхолдер
    }
}