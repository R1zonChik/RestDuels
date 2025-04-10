package ru.refontstudio.restduels.commands;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.models.RestoreArea;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.util.*;

public class ArenaWandCommand implements CommandExecutor, TabCompleter {
    private final RestDuels plugin;

    public ArenaWandCommand(RestDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Если аргументов нет, выводим справку
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String prefix = plugin.getConfig().getString("messages.prefix");

        // Проверка прав (и для игроков, и для консоли)
        if (sender instanceof Player && !sender.hasPermission("restduels.admin")) {
            sender.sendMessage(ColorUtils.colorize("&cУ вас нет прав для использования этой команды!"));
            return true;
        }

        // Команды, требующие игрока
        if (subCommand.equals("wand") || subCommand.equals("create") || subCommand.equals("capture")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtils.colorize("&cЭта команда доступна только для игроков!"));
                return true;
            }

            Player player = (Player) sender;

            if (subCommand.equals("wand")) {
                giveWand(player);
                return true;
            } else if (subCommand.equals("create")) {
                if (plugin.getSelectionManager().hasSelection(player)) {
                    promptAreaName(player);
                } else {
                    player.sendMessage(ColorUtils.colorize(prefix + "&cВы должны выбрать две точки с помощью жезла!"));
                }
                return true;
            } else if (subCommand.equals("capture")) {
                if (args.length < 2) {
                    player.sendMessage(ColorUtils.colorize(prefix + "&cУкажите название области!"));
                    return true;
                }
                captureArea(player, args[1]);
                return true;
            }
        }

        // Команды, доступные для всех (и игроков, и консоли)
        switch (subCommand) {
            case "list":
                listAreas(sender);
                break;
            case "info":
                if (args.length < 2) {
                    sender.sendMessage(ColorUtils.colorize(prefix + "&cУкажите название области!"));
                    return true;
                }
                showAreaInfo(sender, args[1]);
                break;
            case "delete":
                if (args.length < 2) {
                    sender.sendMessage(ColorUtils.colorize(prefix + "&cУкажите название области!"));
                    return true;
                }
                deleteArea(sender, args[1]);
                break;
            case "link":
                if (args.length < 3) {
                    sender.sendMessage(ColorUtils.colorize(prefix + "&cУкажите название области и ID арены!"));
                    return true;
                }
                linkAreaToArena(sender, args[1], args[2]);
                break;
            case "unlink":
                if (args.length < 3) {
                    sender.sendMessage(ColorUtils.colorize(prefix + "&cУкажите название области и ID арены!"));
                    return true;
                }
                unlinkAreaFromArena(sender, args[1], args[2]);
                break;
            case "restore":
                if (args.length < 2) {
                    sender.sendMessage(ColorUtils.colorize(prefix + "&cУкажите название области!"));
                    return true;
                }
                restoreArea(sender, args[1]);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("restduels.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = Arrays.asList("wand", "create", "list", "info", "delete", "link", "unlink", "capture", "restore");
            return filterCompletions(completions, args[0]);
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("info") || subCommand.equals("delete") ||
                    subCommand.equals("link") || subCommand.equals("unlink") ||
                    subCommand.equals("capture") || subCommand.equals("restore")) {

                // Получаем список всех областей
                List<String> areaNames = new ArrayList<>(plugin.getRestoreManager().getAllAreas().keySet());
                return filterCompletions(areaNames, args[1]);
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("link") || subCommand.equals("unlink")) {
                // Получаем список всех арен
                List<String> arenaIds = new ArrayList<>();
                plugin.getArenaManager().getArenas().forEach(arena -> arenaIds.add(arena.getId()));
                return filterCompletions(arenaIds, args[2]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterCompletions(List<String> completions, String prefix) {
        if (prefix.isEmpty()) {
            return completions;
        }

        List<String> filtered = new ArrayList<>();
        for (String completion : completions) {
            if (completion.toLowerCase().startsWith(prefix.toLowerCase())) {
                filtered.add(completion);
            }
        }
        return filtered;
    }

    // Метод для игрока и консоли
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtils.colorize("&6&l======== Команды ArenaWand ========"));
        sender.sendMessage(ColorUtils.colorize("&e/arenawand wand &7- Получить жезл выделения"));
        sender.sendMessage(ColorUtils.colorize("&e/arenawand create &7- Создать новую область"));
        sender.sendMessage(ColorUtils.colorize("&e/arenawand list &7- Список всех областей"));
        sender.sendMessage(ColorUtils.colorize("&e/arenawand info <название> &7- Информация об области"));
        sender.sendMessage(ColorUtils.colorize("&e/arenawand delete <название> &7- Удалить область"));
        sender.sendMessage(ColorUtils.colorize("&e/arenawand link <область> <арена> &7- Привязать область к арене"));
        sender.sendMessage(ColorUtils.colorize("&e/arenawand unlink <область> <арена> &7- Отвязать область от арены"));
        sender.sendMessage(ColorUtils.colorize("&e/arenawand capture <название> &7- Захватить текущее состояние области"));
        sender.sendMessage(ColorUtils.colorize("&e/arenawand restore <название> &7- Восстановить область"));
        sender.sendMessage(ColorUtils.colorize("&6&l===================================="));
    }

    private void giveWand(Player player) {
        ItemStack wand = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ColorUtils.colorize("&6&lArena Wand"));

        List<String> lore = new ArrayList<>();
        lore.add(ColorUtils.colorize("&7Правый клик - выбрать первую точку"));
        lore.add(ColorUtils.colorize("&7Левый клик - выбрать вторую точку"));
        meta.setLore(lore);

        wand.setItemMeta(meta);

        player.getInventory().addItem(wand);
        player.sendMessage(ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix") +
                        "&aВы получили жезл выделения арены!"));
    }

    private void promptAreaName(Player player) {
        player.sendMessage(ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix") +
                        "&eВведите название для новой области:"));

        // Получаем выбранные точки
        Location first = plugin.getSelectionManager().getFirstPoint(player);
        Location second = plugin.getSelectionManager().getSecondPoint(player);

        // Регистрируем временный обработчик чата
        plugin.getChatInputManager().awaitChatInput(player, input -> {
            String areaName = input.trim();

            // Проверяем валидность имени
            if (areaName.isEmpty()) {
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cНазвание не может быть пустым!"));
                return;
            }

            // Сохраняем состояние арены
            try {
                plugin.getRestoreManager().saveArenaState(areaName, first, second);
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&aОбласть успешно сохранена как '&e" + areaName + "&a'!"));

                // Связываем область с ареной, если она существует
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&eВведите ID арены для привязки или 'cancel' для отмены:"));

                plugin.getChatInputManager().awaitChatInput(player, arenaInput -> {
                    String arenaId = arenaInput.trim();

                    if (arenaId.equalsIgnoreCase("cancel")) {
                        player.sendMessage(ColorUtils.colorize(
                                plugin.getConfig().getString("messages.prefix") +
                                        "&cПривязка отменена."));
                        return;
                    }

                    // Проверяем, существует ли арена
                    if (plugin.getArenaManager().getArena(arenaId) != null) {
                        plugin.getRestoreManager().linkAreaToArena(areaName, arenaId);
                        player.sendMessage(ColorUtils.colorize(
                                plugin.getConfig().getString("messages.prefix") +
                                        "&aОбласть '&e" + areaName + "&a' привязана к арене '&e" + arenaId + "&a'!"));
                    } else {
                        player.sendMessage(ColorUtils.colorize(
                                plugin.getConfig().getString("messages.prefix") +
                                        "&cАрена с ID '&e" + arenaId + "&c' не найдена!"));
                    }
                });

            } catch (Exception e) {
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cОшибка при сохранении области: " + e.getMessage()));
                e.printStackTrace();
            }
        });
    }

    // Методы для консоли и игрока
    private void listAreas(CommandSender sender) {
        Map<String, RestoreArea> areas = plugin.getRestoreManager().getAllAreas();

        if (areas.isEmpty()) {
            sender.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cНет сохраненных областей!"));
            return;
        }

        sender.sendMessage(ColorUtils.colorize("&6&l======== Список областей ========"));
        for (RestoreArea area : areas.values()) {
            sender.sendMessage(ColorUtils.colorize("&e- &f" + area.getName() + " &7(" + area.getVolume() + " блоков)"));
        }
        sender.sendMessage(ColorUtils.colorize("&6&l================================"));
    }

    private void showAreaInfo(CommandSender sender, String areaName) {
        RestoreArea area = plugin.getRestoreManager().getArea(areaName);

        if (area == null) {
            sender.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cОбласть '&e" + areaName + "&c' не найдена!"));
            return;
        }

        sender.sendMessage(ColorUtils.colorize("&6&l======== Информация об области ========"));
        sender.sendMessage(ColorUtils.colorize("&eНазвание: &f" + area.getName()));
        sender.sendMessage(ColorUtils.colorize("&eМир: &f" + area.getWorldName()));
        sender.sendMessage(ColorUtils.colorize("&eПервая точка: &f" +
                area.getFirst().getBlockX() + ", " +
                area.getFirst().getBlockY() + ", " +
                area.getFirst().getBlockZ()));
        sender.sendMessage(ColorUtils.colorize("&eВторая точка: &f" +
                area.getSecond().getBlockX() + ", " +
                area.getSecond().getBlockY() + ", " +
                area.getSecond().getBlockZ()));
        sender.sendMessage(ColorUtils.colorize("&eРазмер: &f" + area.getVolume() + " блоков"));

        // Показываем связанные арены
        List<String> linkedArenas = plugin.getRestoreManager().getArenaLinks(areaName);

        if (!linkedArenas.isEmpty()) {
            sender.sendMessage(ColorUtils.colorize("&eСвязанные арены: &f" + String.join(", ", linkedArenas)));
        } else {
            sender.sendMessage(ColorUtils.colorize("&eСвязанные арены: &cНет"));
        }

        sender.sendMessage(ColorUtils.colorize("&6&l========================================"));
    }

    private void deleteArea(CommandSender sender, String areaName) {
        RestoreArea area = plugin.getRestoreManager().getArea(areaName);

        if (area == null) {
            sender.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cОбласть '&e" + areaName + "&c' не найдена!"));
            return;
        }

        plugin.getRestoreManager().deleteArea(areaName);
        sender.sendMessage(ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix") +
                        "&aОбласть '&e" + areaName + "&a' успешно удалена!"));
    }

    private void linkAreaToArena(CommandSender sender, String areaName, String arenaId) {
        RestoreArea area = plugin.getRestoreManager().getArea(areaName);

        if (area == null) {
            sender.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cОбласть '&e" + areaName + "&c' не найдена!"));
            return;
        }

        if (plugin.getArenaManager().getArena(arenaId) == null) {
            sender.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cАрена с ID '&e" + arenaId + "&c' не найдена!"));
            return;
        }

        plugin.getRestoreManager().linkAreaToArena(areaName, arenaId);
        sender.sendMessage(ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix") +
                        "&aОбласть '&e" + areaName + "&a' привязана к арене '&e" + arenaId + "&a'!"));
    }

    private void unlinkAreaFromArena(CommandSender sender, String areaName, String arenaId) {
        RestoreArea area = plugin.getRestoreManager().getArea(areaName);

        if (area == null) {
            sender.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cОбласть '&e" + areaName + "&c' не найдена!"));
            return;
        }

        plugin.getRestoreManager().unlinkAreaFromArena(areaName, arenaId);
        sender.sendMessage(ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix") +
                        "&aОбласть '&e" + areaName + "&a' отвязана от арены '&e" + arenaId + "&a'!"));
    }

    private void captureArea(Player player, String areaName) {
        RestoreArea area = plugin.getRestoreManager().getArea(areaName);

        if (area == null) {
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cОбласть '&e" + areaName + "&c' не найдена!"));
            return;
        }

        plugin.getRestoreManager().captureAreaState(areaName);
        player.sendMessage(ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix") +
                        "&aТекущее состояние области '&e" + areaName + "&a' успешно сохранено!"));
    }

    private void restoreArea(CommandSender sender, String areaName) {
        RestoreArea area = plugin.getRestoreManager().getArea(areaName);

        if (area == null) {
            sender.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cОбласть '&e" + areaName + "&c' не найдена!"));
            return;
        }

        plugin.getRestoreManager().restoreArea(areaName);
        sender.sendMessage(ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix") +
                        "&aНачато восстановление области '&e" + areaName + "&a'!"));
    }
}