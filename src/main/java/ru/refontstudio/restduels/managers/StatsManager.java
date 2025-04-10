package ru.refontstudio.restduels.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.models.PlayerStats;
import ru.refontstudio.restduels.models.TopPlayerEntry;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class StatsManager {
    private final RestDuels plugin;
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();
    private final File dataFile;
    private final Gson gson;

    public StatsManager(RestDuels plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadStats();
    }

    private void loadStats() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
                saveAllStats(); // Сохраняем пустой файл
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось создать data.json: " + e.getMessage());
            }
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, PlayerStats>>() {}.getType();
            Map<String, PlayerStats> loadedStats = gson.fromJson(reader, type);

            if (loadedStats != null) {
                loadedStats.forEach((uuidStr, stats) -> {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        playerStats.put(uuid, stats);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Некорректный UUID в статистике: " + uuidStr);
                    }
                });
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Ошибка при загрузке data.json: " + e.getMessage());
        }
    }

    public void saveAllStats() {
        Map<String, PlayerStats> saveMap = new HashMap<>();
        playerStats.forEach((uuid, stats) -> saveMap.put(uuid.toString(), stats));

        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(saveMap, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Ошибка при сохранении data.json: " + e.getMessage());
        }
    }

    public PlayerStats getPlayerStats(UUID playerId) {
        return playerStats.computeIfAbsent(playerId, id -> new PlayerStats());
    }

    public void incrementWins(UUID playerId) {
        PlayerStats stats = getPlayerStats(playerId);
        stats.incrementWins();
        saveAllStats();
    }

    public void incrementDeaths(UUID playerId) {
        PlayerStats stats = getPlayerStats(playerId);
        stats.incrementDeaths();
        saveAllStats();
    }

    public String getFormattedStats(UUID playerId) {
        PlayerStats stats = getPlayerStats(playerId);
        return "§e§lПобед: §f" + stats.getWins() + "\n§c§lСмертей: §f" + stats.getDeaths();
    }

    // Новый метод для получения топ игроков
    public List<TopPlayerEntry> getTopPlayers(int limit) {
        List<TopPlayerEntry> topPlayers = new ArrayList<>();

        // Перебираем всех игроков с их статистикой
        for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerStats stats = entry.getValue();

            // Пропускаем игроков с нулевой статистикой
            if (stats.getWins() == 0 && stats.getDeaths() == 0) {
                continue;
            }

            // Получаем имя игрока
            String playerName = getPlayerName(playerId);

            // Вычисляем K/D и процент побед
            double kd = stats.getDeaths() > 0 ? (double) stats.getWins() / stats.getDeaths() : stats.getWins();
            int total = stats.getWins() + stats.getDeaths();
            double winRate = total > 0 ? (double) stats.getWins() / total * 100 : 0;

            // Создаем запись для топа
            TopPlayerEntry topEntry = new TopPlayerEntry(
                    playerId,
                    playerName,
                    stats.getWins(),
                    stats.getDeaths(),
                    kd,
                    winRate
            );

            topPlayers.add(topEntry);
        }

        // Сортируем по количеству побед (в порядке убывания)
        topPlayers.sort(Comparator.comparingInt(TopPlayerEntry::getWins).reversed());

        // Ограничиваем количество записей
        if (topPlayers.size() > limit) {
            topPlayers = topPlayers.subList(0, limit);
        }

        return topPlayers;
    }

    // Метод для получения имени игрока по UUID
    private String getPlayerName(UUID playerId) {
        // Сначала проверяем онлайн-игроков
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }

        // Затем проверяем оффлайн-игроков
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        String name = offlinePlayer.getName();

        // Если имя не найдено, возвращаем UUID как строку
        return name != null ? name : playerId.toString().substring(0, 8);
    }
}