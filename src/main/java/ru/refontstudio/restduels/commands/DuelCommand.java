package ru.refontstudio.restduels.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.gui.DuelMenu;
import ru.refontstudio.restduels.models.DuelType;
import ru.refontstudio.restduels.models.Duel;
import org.bukkit.Bukkit;
import ru.refontstudio.restduels.models.PlayerStats;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;

public class DuelCommand implements CommandExecutor {
    private final RestDuels plugin;
    private final TopCommand topCommand;

    public DuelCommand(RestDuels plugin) {
        this.plugin = plugin;
        this.topCommand = new TopCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эта команда доступна только для игроков!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Открываем меню дуэлей
            new DuelMenu(plugin, player).open();
            return true;
        } else if (args.length >= 1) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "cancel":
                    // Добавляем отладочный лог
                    System.out.println("[DUEL-CANCEL] Получена команда /duel cancel от игрока " + player.getName());

                    // Проверяем, находится ли игрок в очереди на поиск дуэли
                    boolean inSearch = false;
                    for (DuelType type : DuelType.values()) {
                        if (plugin.getDuelManager().isPlayerInQueue(player.getUniqueId(), type)) {
                            inSearch = true;
                            break;
                        }
                    }

                    // Отменяем дуэль
                    if (inSearch || plugin.getDuelManager().isPlayerInPreparation(player.getUniqueId())) {
                        // Если игрок в поиске или в стадии подготовки, можно отменить
                        System.out.println("[DUEL-CANCEL] Игрок " + player.getName() + " в поиске или подготовке, отменяем");
                        plugin.getDuelManager().cancelDuel(player);
                    } else if (plugin.getDuelManager().isPlayerInDuel(player.getUniqueId())) {
                        // Если дуэль уже началась, нельзя отменить
                        System.out.println("[DUEL-CANCEL] Игрок " + player.getName() + " в активной дуэли, отмена невозможна");
                        player.sendMessage(ColorUtils.colorize(
                                plugin.getConfig().getString("messages.prefix") +
                                        "&cВы не можете выйти из активной дуэли! Используйте /hub, чтобы покинуть сервер."));
                    } else {
                        // Если игрок не в дуэли, не в подготовке и не в поиске
                        System.out.println("[DUEL-CANCEL] Игрок " + player.getName() + " не в дуэли/подготовке/поиске");
                        player.sendMessage(ColorUtils.colorize(
                                plugin.getConfig().getString("messages.prefix") +
                                        "&cВы не участвуете в дуэли!"));
                    }
                    break;
                case "normal":
                    // Начинаем поиск обычной дуэли
                    plugin.getDuelManager().queuePlayer(player, DuelType.NORMAL);
                    break;
                case "ranked":
                case "noloss":
                    // Начинаем поиск дуэли без потерь
                    plugin.getDuelManager().queuePlayer(player, DuelType.RANKED);
                    break;
                case "stats":
                    // Показываем статистику
                    showStats(player);
                    break;
                case "top":
                    // Перенаправляем на команду топа
                    // Создаем новый массив аргументов без первого элемента
                    String[] topArgs = new String[args.length - 1];
                    System.arraycopy(args, 1, topArgs, 0, args.length - 1);

                    // Вызываем команду топа
                    return topCommand.onCommand(sender, command, label, topArgs);
                case "return":
                    // Досрочный возврат в конце дуэли
                    earlyReturn(player);
                    break;
                case "commands":
                    // Управление списком разрешенных команд во время дуэли
                    if (!sender.hasPermission("restduels.admin.commands")) {
                        sender.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.prefix") +
                                "&cУ вас нет разрешения на использование этой команды!"));
                        return true;
                    }

                    if (args.length == 1) {
                        // Показать список разрешенных команд
                        sender.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.prefix") +
                                "&aРазрешенные команды во время дуэли:"));

                        for (String cmd : plugin.getCommandBlocker().getAllowedCommands()) {
                            if (cmd.startsWith("/")) {
                                sender.sendMessage(ColorUtils.colorize("&7- &f" + cmd));
                            }
                        }

                        sender.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.prefix") +
                                "&7Используйте &f/duel commands add <команда>&7 или &f/duel commands remove <команда>&7 для управления списком."));
                        return true;
                    }

                    if (args.length >= 3) {
                        if (args[1].equalsIgnoreCase("add")) {
                            String cmd = args[2].toLowerCase();
                            if (!cmd.startsWith("/")) {
                                cmd = "/" + cmd;
                            }

                            plugin.getCommandBlocker().addAllowedCommand(cmd);

                            // Сохраняем в конфиг
                            List<String> allowedCommands = plugin.getConfig().getStringList("commands.allowed-during-duel");
                            if (!allowedCommands.contains(cmd)) {
                                allowedCommands.add(cmd);
                                plugin.getConfig().set("commands.allowed-during-duel", allowedCommands);
                                plugin.saveConfig();
                            }

                            sender.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.prefix") +
                                    "&aКоманда &f" + cmd + "&a добавлена в список разрешенных!"));
                            return true;
                        }

                        if (args[1].equalsIgnoreCase("remove")) {
                            String cmd = args[2].toLowerCase();
                            if (!cmd.startsWith("/")) {
                                cmd = "/" + cmd;
                            }

                            plugin.getCommandBlocker().removeAllowedCommand(cmd);

                            // Сохраняем в конфиг
                            List<String> allowedCommands = plugin.getConfig().getStringList("commands.allowed-during-duel");
                            allowedCommands.remove(cmd);
                            plugin.getConfig().set("commands.allowed-during-duel", allowedCommands);
                            plugin.saveConfig();

                            sender.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.prefix") +
                                    "&aКоманда &f" + cmd + "&a удалена из списка разрешенных!"));
                            return true;
                        }

                        if (args[1].equalsIgnoreCase("reload")) {
                            plugin.getCommandBlocker().loadAllowedCommands();
                            sender.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.prefix") +
                                    "&aСписок разрешенных команд перезагружен!"));
                            return true;
                        }
                    }

                    // Если дошли сюда, значит аргументы неверны
                    sender.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.prefix") +
                            "&cИспользование: &f/duel commands [add/remove/reload] [команда]"));
                    break;
                default:
                    // Неизвестная команда - показываем использование
                    player.sendMessage(ChatColor.RED + "Правильное использование: /duel [cancel|normal|noloss|stats|top|return|commands]");
                    break;
            }
            return true;
        }

        return true;
    }

    /**
     * Обрабатывает запрос на досрочный возврат после дуэли
     * @param player Игрок, запросивший досрочный возврат
     */
    private void earlyReturn(Player player) {
        UUID playerId = player.getUniqueId();

        // Проверяем, есть ли отложенная задача возврата
        if (plugin.getDuelManager().hasDelayedReturnTask(playerId)) {
            // Отменяем задачу и возвращаем игрока
            plugin.getDuelManager().cancelDelayedReturnAndTeleport(player);
        } else if (plugin.getDuelManager().isPlayerInDuel(playerId)) {
            // Если игрок в активной дуэли, запрещаем досрочный возврат
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cВы не можете использовать досрочный возврат во время активной дуэли! " +
                            "Дождитесь окончания дуэли или используйте /hub."));
        } else {
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cУ вас нет активной задачи возврата."));
        }
    }

    private void showStats(Player player) {
        PlayerStats stats = plugin.getStatsManager().getPlayerStats(player.getUniqueId());

        player.sendMessage(ColorUtils.colorize("&6&l======== Статистика дуэлей ========"));
        player.sendMessage(ColorUtils.colorize("&eИгрок: &f" + player.getName()));
        player.sendMessage(ColorUtils.colorize("&eПобед: &a" + stats.getWins()));
        player.sendMessage(ColorUtils.colorize("&eСмертей: &c" + stats.getDeaths()));

        double kd = stats.getDeaths() > 0 ? (double) stats.getWins() / stats.getDeaths() : stats.getWins();
        player.sendMessage(ColorUtils.colorize("&eK/D: &f" + new DecimalFormat("#.##").format(kd)));

        int total = stats.getWins() + stats.getDeaths();
        double winRate = total > 0 ? (double) stats.getWins() / total * 100 : 0;
        player.sendMessage(ColorUtils.colorize("&eПроцент побед: &f" + new DecimalFormat("#.#").format(winRate) + "%"));
        player.sendMessage(ColorUtils.colorize("&6&l================================="));
    }
}