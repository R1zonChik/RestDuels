package ru.refontstudio.restduels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import ru.refontstudio.restduels.commands.ArenaSetupCommand;
import ru.refontstudio.restduels.commands.ArenaWandCommand;
import ru.refontstudio.restduels.commands.DuelCommand;
import ru.refontstudio.restduels.config.ConfigManager;
import ru.refontstudio.restduels.integrations.ProtocolLibIntegration;
import ru.refontstudio.restduels.listeners.*;
import ru.refontstudio.restduels.managers.*;
import ru.refontstudio.restduels.placeholders.RestDuelsPlaceholder;
import ru.refontstudio.restduels.utils.ColorUtils;
import ru.refontstudio.restduels.utils.TitleManager;

import java.io.File;
import java.util.List;
import java.util.UUID;

public final class RestDuels extends JavaPlugin {

    private RestoreManager restoreManager;
    private SelectionManager selectionManager;
    private ChatInputManager chatInputManager;
    private ConfigManager configManager;
    private CommandBlocker commandBlocker;
    private DuelManager duelManager;
    private ArenaManager arenaManager;
    private StatsManager statsManager;
    private TitleManager titleManager;
    private ArenaGuardian arenaGuardian;
    private DuelWorldGuardian duelWorldGuardian;

    /**
     * Получает экземпляр DuelWorldGuardian
     * @return DuelWorldGuardian
     */
    public DuelWorldGuardian getDuelWorldGuardian() {
        return duelWorldGuardian;
    }

    // Добавим геттер для CommandBlocker
    public CommandBlocker getCommandBlocker() {
        return commandBlocker;
    }

    @Override
    public void onEnable() {
        // Сохраняем конфиг по умолчанию
        saveDefaultConfig();

        // Проверяем настройки миров
        List<String> duelWorlds = getConfig().getStringList("worlds.duel-worlds");
        if (duelWorlds.isEmpty()) {
            getLogger().warning("Не настроены миры дуэлей в конфиге! Добавляем мир 'duels' по умолчанию.");
            duelWorlds.add("duels");
            getConfig().set("worlds.duel-worlds", duelWorlds);
            saveConfig();
        }

        // Проверяем, существуют ли указанные миры
        for (String worldName : duelWorlds) {
            if (Bukkit.getWorld(worldName) == null) {
                getLogger().warning("Мир дуэлей '" + worldName + "' не найден! Убедитесь, что он создан.");
            }
        }

        // Создаем директорию для сохранения локаций игроков
        File playerLocationsFolder = new File(getDataFolder(), "player_locations");
        if (!playerLocationsFolder.exists()) {
            playerLocationsFolder.mkdirs();
            getLogger().info("Создана директория для сохранения локаций игроков");
        }

        // Очистка устаревших локаций
        if (getConfig().getBoolean("location_saving.clean_old_locations", true)) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                // Используем уже существующую переменную без повторного объявления
                if (playerLocationsFolder.exists() && playerLocationsFolder.isDirectory()) {
                    File[] locationFiles = playerLocationsFolder.listFiles();
                    if (locationFiles != null) {
                        int cleanedCount = 0;
                        long expirationHours = getConfig().getLong("location_saving.expiration_time", 72);
                        long expirationTime = System.currentTimeMillis() - (expirationHours * 60 * 60 * 1000);

                        for (File file : locationFiles) {
                            try {
                                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                                long timestamp = config.getLong("timestamp", 0);

                                if (timestamp < expirationTime) {
                                    file.delete();
                                    cleanedCount++;
                                }
                            } catch (Exception e) {
                                getLogger().warning("Ошибка при проверке файла локации " + file.getName() + ": " + e.getMessage());
                            }
                        }

                        if (cleanedCount > 0) {
                            getLogger().info("Очищено " + cleanedCount + " устаревших локаций игроков");
                        }
                    }
                }
            });
        }

        // Регистрация PlaceholderAPI расширения, если PlaceholderAPI установлен
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new RestDuelsPlaceholder(this).register();
            getLogger().info("PlaceholderAPI найден! Плейсхолдеры зарегистрированы.");
        } else {
            getLogger().info("PlaceholderAPI не найден! Плейсхолдеры недоступны.");
        }

        // Инициализация интеграций
        setupIntegrations();

        // В методе onEnable() добавьте:
        Bukkit.getScheduler().runTaskLater(this, () -> {
            setupIntegrations();
        }, 20L); // Задержка в 1 секунду для надежной загрузки ProtocolLib

        // Инициализируем менеджеры
        this.configManager = new ConfigManager(this);
        this.statsManager = new StatsManager(this);
        this.arenaManager = new ArenaManager(this);
        this.titleManager = new TitleManager(this);
        this.duelManager = new DuelManager(this);
        selectionManager = new SelectionManager(this);
        chatInputManager = new ChatInputManager(this);
        restoreManager = new RestoreManager(this);
        duelWorldGuardian = new DuelWorldGuardian(this);
        arenaGuardian = new ArenaGuardian(this);

        // Регистрируем защитник меню
        commandBlocker = new CommandBlocker(this);
        getServer().getPluginManager().registerEvents(new DuelRestrictionListener(this), this);
        // Регистрация слушателя блокировки команд
        getServer().getPluginManager().registerEvents(new DuelCommandBlocker(this), this);
        getServer().getPluginManager().registerEvents(commandBlocker, this);
        MenuProtectionListener menuProtectionListener = new MenuProtectionListener(this);
        getServer().getPluginManager().registerEvents(menuProtectionListener, this);
        DuelDamageListener damageListener = new DuelDamageListener(this);
        getServer().getPluginManager().registerEvents(damageListener, this);
        getServer().getPluginManager().registerEvents(new DuelDeathListener(this), this);

        DuelJoinListener joinListener = new DuelJoinListener(this);
        getServer().getPluginManager().registerEvents(joinListener, this);
        getServer().getPluginManager().registerEvents(new DuelRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new DuelQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuCloseListener(this), this);
        DuelCommandListener commandListener = new DuelCommandListener(this);
        getServer().getPluginManager().registerEvents(commandListener, this);
        getServer().getPluginManager().registerEvents(arenaGuardian, this);
        getServer().getPluginManager().registerEvents(new DuelCommandListener(this), this);
        getServer().getPluginManager().registerEvents(new DuelJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new DuelBuildListener(this), this);
        getCommand("arenawand").setExecutor(new ArenaWandCommand(this));
        getServer().getPluginManager().registerEvents(new ArenaWandListener(this), this);
        getServer().getPluginManager().registerEvents(duelWorldGuardian, this);
        getCommand("duel").setExecutor(new DuelCommand(this));

        // Регистрируем команду настройки арен
        ArenaSetupCommand arenaSetupCommand = new ArenaSetupCommand(this);
        getCommand("arenasetup").setExecutor(arenaSetupCommand);
        getCommand("arenasetup").setTabCompleter(arenaSetupCommand);

        // Регистрируем слушатели событий
        Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DuelListener(this), this);
        Bukkit.getPluginManager().registerEvents(new FreezeListener(this), this);

        getLogger().info("RestDuels успешно запущен!");
    }

    public RestoreManager getRestoreManager() {
        return restoreManager;
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    // Добавляем геттер для доступа к защитнику арены из других классов
    public ArenaGuardian getArenaGuardian() {
        return arenaGuardian;
    }

    // Добавьте этот метод в класс RestDuels
    private void setupIntegrations() {
        // Проверка наличия ProtocolLib
        if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            try {
                // Инициализация интеграции
                new ProtocolLibIntegration(this, commandBlocker);
                getLogger().info("ProtocolLib интеграция успешно загружена!");
            } catch (Exception e) {
                getLogger().warning("Не удалось инициализировать ProtocolLib: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            getLogger().warning("ProtocolLib не найден! Некоторые функции будут недоступны.");
        }
    }

    @Override
    public void onDisable() {
        // Останавливаем периодическую проверку
        if (arenaGuardian != null) {
            arenaGuardian.stopPeriodicCheck();
        }

        // Получаем список миров дуэлей из конфига
        List<String> duelWorlds = getConfig().getStringList("worlds.duel-worlds");
        for (int i = 0; i < duelWorlds.size(); i++) {
            duelWorlds.set(i, duelWorlds.get(i).toLowerCase()); // Приводим все к нижнему регистру для сравнения
        }

        // Обрабатываем всех игроков
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Удаляем метаданные блокировки команд
            if (player.hasMetadata("restduels_blocked_commands")) {
                player.removeMetadata("restduels_blocked_commands", this);
            }

            // Если есть CommandBlocker, очищаем его
            try {
                if (commandBlocker != null) {
                    commandBlocker.removePlayer(player.getUniqueId());
                }
            } catch (Exception e) {
                // Игнорируем ошибки
            }

            // НОВОЕ: Удаляем босс-бар для игрока, если он есть
            if (duelManager != null && duelManager.playerBossBars != null && duelManager.playerBossBars.containsKey(player.getUniqueId())) {
                try {
                    BossBar bossBar = duelManager.playerBossBars.get(player.getUniqueId());
                    if (bossBar != null) {
                        bossBar.removeAll();
                        getLogger().info("Удален босс-бар для игрока " + player.getName());
                    }
                    duelManager.playerBossBars.remove(player.getUniqueId());
                } catch (Exception e) {
                    getLogger().warning("Ошибка при удалении босс-бара: " + e.getMessage());
                }
            }

            // НОВОЕ: Проверяем, находится ли игрок в мире дуэлей
            if (player.getWorld() != null && duelWorlds.contains(player.getWorld().getName().toLowerCase())) {
                // Игрок в мире дуэлей, телепортируем его на спавн
                getLogger().info("Телепортация игрока " + player.getName() + " из мира дуэлей на спавн");

                // Используем команду spawn
                String spawnCommand = "spawn " + player.getName();
                try {
                    // Выполняем команду от имени консоли
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), spawnCommand);
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            getConfig().getString("messages.prefix") + "&cПлагин дуэлей перезагружается. Вы были телепортированы на спавн."));
                } catch (Exception e) {
                    getLogger().warning("Ошибка при телепортации игрока " + player.getName() + " на спавн: " + e.getMessage());
                }
            }
        }

        // Очищаем все задачи и данные
        if (duelManager != null) {
            // НОВОЕ: Удаляем все босс-бары перед очисткой данных
            try {
                if (duelManager.playerBossBars != null) {
                    for (BossBar bossBar : duelManager.playerBossBars.values()) {
                        if (bossBar != null) {
                            bossBar.removeAll();
                        }
                    }
                    duelManager.playerBossBars.clear();
                    getLogger().info("Все босс-бары успешно удалены");
                }
            } catch (Exception e) {
                getLogger().warning("Ошибка при удалении всех босс-баров: " + e.getMessage());
            }

            duelManager.clearAllDuelsWithoutTasks();
        }

        // Пытаемся очистить CommandBlocker через рефлексию
        try {
            Class<?> commandBlockerClass = Class.forName("ru.refontstudio.restduels.listeners.CommandBlocker");
            if (commandBlockerClass != null) {
                // Проверяем наличие метода clearAllPlayers
                try {
                    java.lang.reflect.Method clearMethod = commandBlockerClass.getMethod("clearAllPlayers");
                    if (clearMethod != null) {
                        java.lang.reflect.Method getInstanceMethod = commandBlockerClass.getMethod("getInstance");
                        Object commandBlocker = getInstanceMethod.invoke(null);
                        clearMethod.invoke(commandBlocker);
                        getLogger().info("CommandBlocker очищен успешно.");
                    }
                } catch (Exception e) {
                    getLogger().warning("Ошибка при очистке CommandBlocker: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки, если класс не найден
        }

        // Сохраняем статистику
        if (statsManager != null) {
            statsManager.saveAllStats();
        }

        // Сохраняем текущие состояния арен
        if (restoreManager != null) {
            for (String areaName : restoreManager.getAllAreas().keySet()) {
                if (restoreManager.hasActiveState(areaName)) {
                    restoreManager.saveAreaState(areaName, restoreManager.getSavedStates(areaName));
                }
            }
        }

        getLogger().info("RestDuels успешно выключен!");
    }

    /**
     * Телепортирует всех игроков в дуэлях на их исходные позиции
     */
    private void teleportAllPlayersToOriginalLocations() {
        if (duelManager == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();

            // Проверяем, участвует ли игрок в дуэли
            if (duelManager.isPlayerInDuel(playerId)) {
                // Получаем исходную локацию
                Location originalLocation = duelManager.getPlayerOriginalLocation(playerId);

                if (originalLocation != null && originalLocation.getWorld() != null) {
                    // Телепортируем игрока на исходную позицию
                    player.teleport(originalLocation);
                    player.sendMessage(ColorUtils.colorize(
                            getConfig().getString("messages.prefix") +
                                    "&cПлагин дуэлей был выключен. Вы были телепортированы на исходную позицию."));
                }
            }
        }

        getLogger().info("Все игроки в дуэлях телепортированы на исходные позиции");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DuelManager getDuelManager() {
        return duelManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public TitleManager getTitleManager() {
        return titleManager;
    }

    public void reloadConfig() {
        super.reloadConfig();
    }
}