package ru.refontstudio.restduels.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.refontstudio.restduels.RestDuels;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private final RestDuels plugin;
    private File statsFile;
    private FileConfiguration statsConfig;

    public ConfigManager(RestDuels plugin) {
        this.plugin = plugin;
        setupStatsConfig();
        setupDefaultConfig();
    }

    private void setupStatsConfig() {
        statsFile = new File(plugin.getDataFolder(), "stats.yml");
        if (!statsFile.exists()) {
            try {
                statsFile.getParentFile().mkdirs();
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось создать stats.yml: " + e.getMessage());
            }
        }
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
    }

    private void setupDefaultConfig() {
        FileConfiguration config = plugin.getConfig();
        boolean needsSave = false;

        // Настройки арен
        if (!config.isSet("arenas")) {
            config.createSection("arenas");
            for (int i = 1; i <= 3; i++) {
                config.set("arenas.arena" + i + ".world", "world");
                config.set("arenas.arena" + i + ".x1", 0);
                config.set("arenas.arena" + i + ".y1", 70);
                config.set("arenas.arena" + i + ".z1", 0);
                config.set("arenas.arena" + i + ".x2", 0);
                config.set("arenas.arena" + i + ".y2", 70);
                config.set("arenas.arena" + i + ".z2", 0);
            }
            needsSave = true;
        }

        // Настройки времени
        if (!config.isSet("timers")) {
            config.set("timers.search-time", 30);    // 30 секунд на поиск соперника
            config.set("timers.cancel-time", 8);     // 8 секунд на отмену дуэли
            config.set("timers.duel-time", 20);      // 20 минут максимальное время дуэли
            needsSave = true;
        }

        // Настройки сообщений
        if (!config.isSet("messages")) {
            config.set("messages.prefix", "&6[&eRestDuels&6] &r");
            config.set("messages.duel-started", "&aДуэль началась! Удачи!");
            config.set("messages.duel-ended", "&aДуэль закончилась! Победитель: &e%winner%");
            config.set("messages.duel-timeout", "&cВремя дуэли истекло! Вы были возвращены обратно.");
            config.set("messages.searching", "&eПоиск соперника... &7(%time% сек)");
            config.set("messages.no-opponent", "&cСоперник не найден! Вы были возвращены обратно.");
            config.set("messages.cancel-button", "&c[Выйти с дуэли]");
            config.set("messages.duel-cancelled", "&cДуэль отменена!");
            config.set("messages.player-in-duel", "&cИгрок уже участвует в дуэли!");
            needsSave = true;
        }

        // Настройки GUI
        if (!config.isSet("gui")) {
            ConfigurationSection guiSection = config.createSection("gui");

            // Общие настройки
            guiSection.set("title", "Сундук");

            // Настройки для обычной дуэли
            ConfigurationSection normalDuelSection = guiSection.createSection("normal_duel");
            normalDuelSection.set("slot", 11);
            normalDuelSection.set("material", "COMPASS");
            normalDuelSection.set("name", "&c[X] &6Поиск обычной дуэли");

            List<String> normalLore = new ArrayList<>();
            normalLore.add("&6→ Нажмите, чтобы начать поиск");
            normalLore.add("");
            normalLore.add("&f[ЛКМ] &6Начать поиск");
            normalLore.add("&f[ШИФТ+ЛКМ] &6Отменить поиск");
            normalLore.add("");
            normalLore.add("&c• &fПараметры");
            normalLore.add("&6▶ &fСлучайный соперник");
            normalLore.add("&6▶ &fИнвентарь выпадает");
            normalLore.add("&6▶ &fНаграды нет");
            normalDuelSection.set("lore", normalLore);

            // Настройки для рейтинговой дуэли
            ConfigurationSection rankedDuelSection = guiSection.createSection("ranked_duel");
            rankedDuelSection.set("slot", 15);
            rankedDuelSection.set("material", "COMPASS");
            rankedDuelSection.set("name", "&c[X] &6Поиск рейтинговой дуэли");

            List<String> rankedLore = new ArrayList<>();
            rankedLore.add("&6→ Нажмите, чтобы начать поиск");
            rankedLore.add("");
            rankedLore.add("&f[ЛКМ] &6Начать поиск");
            rankedLore.add("&f[ШИФТ+ЛКМ] &6Отменить поиск");
            rankedLore.add("");
            rankedLore.add("&c• &fПараметры");
            rankedLore.add("&6▶ &fРавный соперник");
            rankedLore.add("&6▶ &fИнвентарь не выпадает");
            rankedLore.add("&6▶ &fВлияет на рейтинг");
            rankedDuelSection.set("lore", rankedLore);

            needsSave = true;
        }

        // Настройки заголовков и сообщений на экране
        if (!config.isSet("titles")) {
            ConfigurationSection titlesSection = config.createSection("titles");

            // Настройки для поиска соперника
            ConfigurationSection searchingSection = titlesSection.createSection("searching");
            searchingSection.set("title", "<gradient:FCFF00:FF9900>Поиск соперника</gradient>");
            searchingSection.set("subtitle", "&eОсталось: &c%time% &eсекунд");
            searchingSection.set("fadeIn", 10);
            searchingSection.set("stay", 20);
            searchingSection.set("fadeOut", 10);

            // Настройки для отсутствия соперника
            ConfigurationSection noOpponentSection = titlesSection.createSection("no-opponent");
            noOpponentSection.set("title", "&cСоперник не найден!");
            noOpponentSection.set("subtitle", "&eВы возвращены на исходную позицию");
            noOpponentSection.set("fadeIn", 10);
            noOpponentSection.set("stay", 60);
            noOpponentSection.set("fadeOut", 20);

            // Настройки для начала дуэли
            ConfigurationSection duelStartedSection = titlesSection.createSection("duel-started");
            duelStartedSection.set("title", "<gradient:FCFF00:FF9900>Дуэль началась!</gradient>");
            duelStartedSection.set("subtitle", "&eУдачи!");
            duelStartedSection.set("fadeIn", 10);
            duelStartedSection.set("stay", 60);
            duelStartedSection.set("fadeOut", 20);

            // Настройки для окончания дуэли
            ConfigurationSection duelEndedSection = titlesSection.createSection("duel-ended");
            duelEndedSection.set("title", "<gradient:FCFF00:FF9900>Дуэль окончена!</gradient>");
            duelEndedSection.set("subtitle", "&eПобедитель: &a%winner%");
            duelEndedSection.set("fadeIn", 10);
            duelEndedSection.set("stay", 60);
            duelEndedSection.set("fadeOut", 20);

            // Настройки для истечения времени дуэли
            ConfigurationSection duelTimeoutSection = titlesSection.createSection("duel-timeout");
            duelTimeoutSection.set("title", "&cВремя истекло!");
            duelTimeoutSection.set("subtitle", "&eДуэль завершена без победителя");
            duelTimeoutSection.set("fadeIn", 10);
            duelTimeoutSection.set("stay", 60);
            duelTimeoutSection.set("fadeOut", 20);

            needsSave = true;
        }

        if (needsSave) {
            plugin.saveConfig();
        }
    }

    public FileConfiguration getStatsConfig() {
        return statsConfig;
    }

    public void saveStatsConfig() {
        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить stats.yml: " + e.getMessage());
        }
    }

    public Material getMaterialFromConfig(String path, Material defaultMaterial) {
        String materialName = plugin.getConfig().getString(path);
        if (materialName == null) {
            return defaultMaterial;
        }

        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Неверный материал в конфиге: " + materialName + ". Используется материал по умолчанию: " + defaultMaterial);
            return defaultMaterial;
        }
    }
}