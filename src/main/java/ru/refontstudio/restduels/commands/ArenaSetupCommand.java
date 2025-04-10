package ru.refontstudio.restduels.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ArenaSetupCommand implements CommandExecutor, TabCompleter {
    private final RestDuels plugin;

    public ArenaSetupCommand(RestDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эта команда доступна только для игроков!");
            return true;
        }

        Player player = (Player) sender;

        // Проверяем права
        if (!player.hasPermission("restduels.admin")) {
            player.sendMessage(ColorUtils.colorize("&cУ вас нет прав для использования этой команды!"));
            return true;
        }

        // Проверяем аргументы
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // Обработка команды list - не требует аргументов
        if (subCommand.equals("list")) {
            // Показываем список арен
            ConfigurationSection arenasSection = plugin.getConfig().getConfigurationSection("arenas");
            player.sendMessage(ColorUtils.colorize("&6&l======== Список арен ========"));
            if (arenasSection == null || arenasSection.getKeys(false).isEmpty()) {
                player.sendMessage(ColorUtils.colorize("&eАрены отсутствуют."));
            } else {
                for (String key : arenasSection.getKeys(false)) {
                    player.sendMessage(ColorUtils.colorize("&e- &f" + key));
                }
            }
            player.sendMessage(ColorUtils.colorize("&6&l=========================="));
            return true;
        }

        // Обработка команды reload - не требует аргументов
        if (subCommand.equals("reload")) {
            // Перезагружаем конфиг и арены
            plugin.reloadConfig();
            plugin.getArenaManager().reloadArenas();
            player.sendMessage(ColorUtils.colorize("&aКонфигурация и арены перезагружены!"));
            return true;
        }

        // Для остальных команд нужен аргумент arenaId
        if (args.length < 2 && !subCommand.equals("list") && !subCommand.equals("reload")) {
            player.sendMessage(ColorUtils.colorize("&cНедостаточно аргументов! Используйте /arenasetup help для справки."));
            return true;
        }

        String arenaId = args.length > 1 ? args[1] : "";

        // Получаем конфиг
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection arenasSection = config.getConfigurationSection("arenas");

        if (arenasSection == null) {
            arenasSection = config.createSection("arenas");
        }

        // Обработка подкоманд
        switch (subCommand) {
            case "create":
                // Проверяем, существует ли арена
                if (arenasSection.contains(arenaId)) {
                    player.sendMessage(ColorUtils.colorize("&cАрена с ID &e" + arenaId + " &cуже существует!"));
                    return true;
                }

                // Создаем новую арену
                ConfigurationSection arenaSection = arenasSection.createSection(arenaId);
                Location loc = player.getLocation();

                arenaSection.set("world", loc.getWorld().getName());
                arenaSection.set("x1", loc.getX());
                arenaSection.set("y1", loc.getY());
                arenaSection.set("z1", loc.getZ());
                arenaSection.set("yaw1", loc.getYaw());
                arenaSection.set("pitch1", loc.getPitch());

                arenaSection.set("x2", loc.getX());
                arenaSection.set("y2", loc.getY());
                arenaSection.set("z2", loc.getZ());
                arenaSection.set("yaw2", loc.getYaw());
                arenaSection.set("pitch2", loc.getPitch());

                plugin.saveConfig();
                player.sendMessage(ColorUtils.colorize("&aАрена &e" + arenaId + " &aсоздана!"));
                player.sendMessage(ColorUtils.colorize("&aИспользуйте &e/arenasetup setspawn1 " + arenaId + " &aи &e/arenasetup setspawn2 " + arenaId + " &aдля настройки точек спавна."));
                break;

            case "delete":
                // Проверяем, существует ли арена
                if (!arenasSection.contains(arenaId)) {
                    player.sendMessage(ColorUtils.colorize("&cАрена с ID &e" + arenaId + " &cне существует!"));
                    return true;
                }

                // Удаляем арену
                arenasSection.set(arenaId, null);
                plugin.saveConfig();
                player.sendMessage(ColorUtils.colorize("&aАрена &e" + arenaId + " &aудалена!"));
                break;

            case "setspawn1":
                // Проверяем, существует ли арена
                if (!arenasSection.contains(arenaId)) {
                    player.sendMessage(ColorUtils.colorize("&cАрена с ID &e" + arenaId + " &cне существует!"));
                    return true;
                }

                // Устанавливаем первую точку спавна
                ConfigurationSection spawn1Section = arenasSection.getConfigurationSection(arenaId);
                Location spawn1Loc = player.getLocation();

                spawn1Section.set("world", spawn1Loc.getWorld().getName());
                spawn1Section.set("x1", spawn1Loc.getX());
                spawn1Section.set("y1", spawn1Loc.getY());
                spawn1Section.set("z1", spawn1Loc.getZ());
                spawn1Section.set("yaw1", spawn1Loc.getYaw());
                spawn1Section.set("pitch1", spawn1Loc.getPitch());

                plugin.saveConfig();
                player.sendMessage(ColorUtils.colorize("&aТочка спавна 1 для арены &e" + arenaId + " &aустановлена!"));
                break;

            case "setspawn2":
                // Проверяем, существует ли арена
                if (!arenasSection.contains(arenaId)) {
                    player.sendMessage(ColorUtils.colorize("&cАрена с ID &e" + arenaId + " &cне существует!"));
                    return true;
                }

                // Устанавливаем вторую точку спавна
                ConfigurationSection spawn2Section = arenasSection.getConfigurationSection(arenaId);
                Location spawn2Loc = player.getLocation();

                spawn2Section.set("x2", spawn2Loc.getX());
                spawn2Section.set("y2", spawn2Loc.getY());
                spawn2Section.set("z2", spawn2Loc.getZ());
                spawn2Section.set("yaw2", spawn2Loc.getYaw());
                spawn2Section.set("pitch2", spawn2Loc.getPitch());

                plugin.saveConfig();
                player.sendMessage(ColorUtils.colorize("&aТочка спавна 2 для арены &e" + arenaId + " &aустановлена!"));
                break;

            case "info":
                // Проверяем, существует ли арена
                if (!arenasSection.contains(arenaId)) {
                    player.sendMessage(ColorUtils.colorize("&cАрена с ID &e" + arenaId + " &cне существует!"));
                    return true;
                }

                // Показываем информацию об арене
                ConfigurationSection infoSection = arenasSection.getConfigurationSection(arenaId);
                player.sendMessage(ColorUtils.colorize("&6&l======== Информация об арене " + arenaId + " ========"));
                player.sendMessage(ColorUtils.colorize("&eМир: &f" + infoSection.getString("world")));
                player.sendMessage(ColorUtils.colorize("&eСпавн 1: &fX:" + infoSection.getDouble("x1") +
                        " Y:" + infoSection.getDouble("y1") +
                        " Z:" + infoSection.getDouble("z1") +
                        " Yaw:" + infoSection.getDouble("yaw1") +
                        " Pitch:" + infoSection.getDouble("pitch1")));
                player.sendMessage(ColorUtils.colorize("&eСпавн 2: &fX:" + infoSection.getDouble("x2") +
                        " Y:" + infoSection.getDouble("y2") +
                        " Z:" + infoSection.getDouble("z2") +
                        " Yaw:" + infoSection.getDouble("yaw2") +
                        " Pitch:" + infoSection.getDouble("pitch2")));
                player.sendMessage(ColorUtils.colorize("&6&l==========================================="));
                break;

            case "list":
                // Показываем список арен
                player.sendMessage(ColorUtils.colorize("&6&l======== Список арен ========"));
                if (arenasSection.getKeys(false).isEmpty()) {
                    player.sendMessage(ColorUtils.colorize("&eАрены отсутствуют."));
                } else {
                    for (String key : arenasSection.getKeys(false)) {
                        player.sendMessage(ColorUtils.colorize("&e- &f" + key));
                    }
                }
                player.sendMessage(ColorUtils.colorize("&6&l=========================="));
                break;

            case "tp":
                // Проверяем, существует ли арена
                if (!arenasSection.contains(arenaId)) {
                    player.sendMessage(ColorUtils.colorize("&cАрена с ID &e" + arenaId + " &cне существует!"));
                    return true;
                }

                // Проверяем, указана ли точка спавна
                if (args.length < 3) {
                    player.sendMessage(ColorUtils.colorize("&cУкажите точку спавна (1 или 2)!"));
                    return true;
                }

                String spawnPoint = args[2];
                ConfigurationSection tpSection = arenasSection.getConfigurationSection(arenaId);

                if (spawnPoint.equals("1")) {
                    // Телепортируем на первую точку спавна
                    Location tpLoc1 = new Location(
                            Bukkit.getWorld(tpSection.getString("world")),
                            tpSection.getDouble("x1"),
                            tpSection.getDouble("y1"),
                            tpSection.getDouble("z1"),
                            (float) tpSection.getDouble("yaw1"),
                            (float) tpSection.getDouble("pitch1")
                    );

                    player.teleport(tpLoc1);
                    player.sendMessage(ColorUtils.colorize("&aВы телепортированы на точку спавна 1 арены &e" + arenaId + "&a!"));
                } else if (spawnPoint.equals("2")) {
                    // Телепортируем на вторую точку спавна
                    Location tpLoc2 = new Location(
                            Bukkit.getWorld(tpSection.getString("world")),
                            tpSection.getDouble("x2"),
                            tpSection.getDouble("y2"),
                            tpSection.getDouble("z2"),
                            (float) tpSection.getDouble("yaw2"),
                            (float) tpSection.getDouble("pitch2")
                    );

                    player.teleport(tpLoc2);
                    player.sendMessage(ColorUtils.colorize("&aВы телепортированы на точку спавна 2 арены &e" + arenaId + "&a!"));
                } else {
                    player.sendMessage(ColorUtils.colorize("&cУкажите корректную точку спавна (1 или 2)!"));
                }
                break;

            case "reload":
                // Перезагружаем конфиг и арены
                plugin.reloadConfig();
                plugin.getArenaManager().reloadArenas();
                player.sendMessage(ColorUtils.colorize("&aКонфигурация и арены перезагружены!"));
                break;

            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ColorUtils.colorize("&6&l======== Команды настройки арен ========"));
        player.sendMessage(ColorUtils.colorize("&e/arenasetup create <id> &f- Создать новую арену"));
        player.sendMessage(ColorUtils.colorize("&e/arenasetup delete <id> &f- Удалить арену"));
        player.sendMessage(ColorUtils.colorize("&e/arenasetup setspawn1 <id> &f- Установить первую точку спавна"));
        player.sendMessage(ColorUtils.colorize("&e/arenasetup setspawn2 <id> &f- Установить вторую точку спавна"));
        player.sendMessage(ColorUtils.colorize("&e/arenasetup info <id> &f- Информация об арене"));
        player.sendMessage(ColorUtils.colorize("&e/arenasetup list &f- Список арен"));
        player.sendMessage(ColorUtils.colorize("&e/arenasetup tp <id> <1|2> &f- Телепортироваться на точку спавна"));
        player.sendMessage(ColorUtils.colorize("&e/arenasetup reload &f- Перезагрузить конфигурацию"));
        player.sendMessage(ColorUtils.colorize("&6&l========================================="));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("restduels.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            // Подкоманды
            String[] subCommands = {"create", "delete", "setspawn1", "setspawn2", "info", "list", "tp", "reload"};
            return Arrays.stream(subCommands)
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // ID арены
            if (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("reload")) {
                return new ArrayList<>();
            }

            if (args[0].equalsIgnoreCase("create")) {
                return Arrays.asList("arena1", "arena2", "arena3");
            }

            // Для остальных команд предлагаем существующие арены
            ConfigurationSection arenasSection = plugin.getConfig().getConfigurationSection("arenas");
            if (arenasSection == null) {
                return new ArrayList<>();
            }

            return arenasSection.getKeys(false).stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 3) {
            // Точка спавна для команды tp
            if (args[0].equalsIgnoreCase("tp")) {
                return Arrays.asList("1", "2");
            }
        }

        return new ArrayList<>();
    }
}