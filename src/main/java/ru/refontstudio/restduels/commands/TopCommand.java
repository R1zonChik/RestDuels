package ru.refontstudio.restduels.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.models.TopPlayerEntry;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.text.DecimalFormat;
import java.util.List;

public class TopCommand implements CommandExecutor {
    private final RestDuels plugin;
    private final DecimalFormat df = new DecimalFormat("#.##");

    public TopCommand(RestDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int limit = 10; // По умолчанию показываем топ-10

        // Если указан аргумент, пытаемся его распарсить как число
        if (args.length > 0) {
            try {
                limit = Integer.parseInt(args[0]);
                if (limit <= 0) {
                    limit = 10;
                } else if (limit > 100) {
                    limit = 100; // Ограничиваем максимальное значение
                }
            } catch (NumberFormatException e) {
                // Игнорируем неверный аргумент
            }
        }

        // Получаем топ игроков
        List<TopPlayerEntry> topPlayers = plugin.getStatsManager().getTopPlayers(limit);

        // Отправляем заголовок
        sender.sendMessage(ColorUtils.colorize("&6&l========== Топ-" + topPlayers.size() + " игроков в дуэлях =========="));

        // Если список пуст, отправляем сообщение
        if (topPlayers.isEmpty()) {
            sender.sendMessage(ColorUtils.colorize("&eПока никто не участвовал в дуэлях."));
        } else {
            // Отправляем информацию о каждом игроке
            int rank = 1;
            for (TopPlayerEntry entry : topPlayers) {
                // Форматируем строку с информацией о игроке
                String message = ColorUtils.colorize(
                        "&e#" + rank + " &f" + entry.getPlayerName() +
                                " &7- &aПобед: &f" + entry.getWins() +
                                " &7| &cСмертей: &f" + entry.getDeaths() +
                                " &7| &eK/D: &f" + df.format(entry.getKd()) +
                                " &7| &bWin%: &f" + df.format(entry.getWinRate()) + "%");

                sender.sendMessage(message);
                rank++;
            }
        }

        // Отправляем нижнюю часть рамки
        sender.sendMessage(ColorUtils.colorize("&6&l==========================================="));

        return true;
    }
}