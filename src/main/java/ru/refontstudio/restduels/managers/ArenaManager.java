package ru.refontstudio.restduels.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.models.Arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ArenaManager {
    private final RestDuels plugin;
    private final List<Arena> arenas = new ArrayList<>();
    private final Map<String, Boolean> arenaStatus = new HashMap<>(); // true - занята, false - свободна
    private final Random random = new Random();

    public ArenaManager(RestDuels plugin) {
        this.plugin = plugin;
        loadArenas();
    }

    /**
     * Получить арену по её ID
     * @param id ID арены
     * @return Арена или null, если не найдена
     */
    public Arena getArena(String id) {
        for (Arena arena : arenas) {
            if (arena.getId().equals(id)) {
                return arena;
            }
        }
        return null;
    }

    /**
     * Находит арену по названию связанной области
     * @param areaName Название области
     * @return Найденная арена или null
     */
    public Arena getArenaByArea(String areaName) {
        for (Arena arena : arenas) {
            List<String> areas = plugin.getRestoreManager().getAreasForArena(arena.getId());
            if (areas.contains(areaName)) {
                return arena;
            }
        }
        return null;
    }

    /**
     * Проверяет, свободна ли арена
     * @param arenaId ID арены
     * @return true если арена свободна, false если занята
     */
    public boolean isArenaAvailable(String arenaId) {
        return !arenaStatus.getOrDefault(arenaId, false);
    }

    /**
     * Помечает арену как занятую
     * @param arenaId ID арены
     */
    public void occupyArena(String arenaId) {
        synchronized (arenaStatus) {
            arenaStatus.put(arenaId, true);
            plugin.getLogger().info("Арена " + arenaId + " помечена как занятая");
        }
    }

    /**
     * Освобождает арену
     * @param arenaId ID арены
     */
    public void releaseArena(String arenaId) {
        synchronized (arenaStatus) {
            arenaStatus.put(arenaId, false);
            plugin.getLogger().info("Арена " + arenaId + " освобождена");
        }
    }

    /**
     * Получить случайную СВОБОДНУЮ арену
     * @return Свободная арена или null, если все арены заняты
     */
    public Arena getRandomAvailableArena() {
        synchronized (arenaStatus) {
            List<Arena> availableArenas = new ArrayList<>();

            for (Arena arena : arenas) {
                if (isArenaAvailable(arena.getId())) {
                    availableArenas.add(arena);
                }
            }

            if (availableArenas.isEmpty()) {
                plugin.getLogger().warning("Все арены заняты!");
                return null;
            }

            Arena selectedArena = availableArenas.get(random.nextInt(availableArenas.size()));
            // Сразу помечаем как занятую
            occupyArena(selectedArena.getId());
            return selectedArena;
        }
    }

    /**
     * Получить конкретную арену, если она свободна
     * @param arenaId ID арены
     * @return Арена или null, если она занята или не существует
     */
    public Arena getSpecificArenaIfAvailable(String arenaId) {
        synchronized (arenaStatus) {
            Arena arena = getArena(arenaId);
            if (arena != null && isArenaAvailable(arenaId)) {
                occupyArena(arenaId);
                return arena;
            }
            return null;
        }
    }

    /**
     * Получить количество свободных арен
     * @return количество свободных арен
     */
    public int getAvailableArenaCount() {
        int count = 0;
        for (Arena arena : arenas) {
            if (isArenaAvailable(arena.getId())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Старый метод для обратной совместимости
     * @deprecated Используйте getRandomAvailableArena()
     */
    @Deprecated
    public Arena getRandomArena() {
        return getRandomAvailableArena();
    }

    private void loadArenas() {
        arenas.clear();
        arenaStatus.clear(); // Очищаем статусы

        ConfigurationSection arenasSection = plugin.getConfig().getConfigurationSection("arenas");
        if (arenasSection == null) {
            plugin.getLogger().warning("Секция арен не найдена в конфиге!");
            return;
        }

        for (String key : arenasSection.getKeys(false)) {
            ConfigurationSection arenaSection = arenasSection.getConfigurationSection(key);
            if (arenaSection == null) continue;

            String worldName = arenaSection.getString("world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Мир " + worldName + " не найден для арены " + key);

                // Попробуем загрузить мир, если он существует, но не загружен
                try {
                    plugin.getLogger().info("Пытаемся загрузить мир " + worldName + "...");
                    world = Bukkit.createWorld(new WorldCreator(worldName));
                    if (world == null) {
                        plugin.getLogger().warning("Не удалось загрузить мир " + worldName);
                        continue;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Ошибка при загрузке мира " + worldName + ": " + e.getMessage());
                    continue;
                }
            }

            double x1 = arenaSection.getDouble("x1");
            double y1 = arenaSection.getDouble("y1");
            double z1 = arenaSection.getDouble("z1");
            float yaw1 = (float) arenaSection.getDouble("yaw1", 0.0);
            float pitch1 = (float) arenaSection.getDouble("pitch1", 0.0);

            double x2 = arenaSection.getDouble("x2");
            double y2 = arenaSection.getDouble("y2");
            double z2 = arenaSection.getDouble("z2");
            float yaw2 = (float) arenaSection.getDouble("yaw2", 0.0);
            float pitch2 = (float) arenaSection.getDouble("pitch2", 0.0);

            Location spawn1 = new Location(world, x1, y1, z1, yaw1, pitch1);
            Location spawn2 = new Location(world, x2, y2, z2, yaw2, pitch2);

            Arena arena = new Arena(key, spawn1, spawn2);
            arenas.add(arena);
            arenaStatus.put(key, false); // Изначально все арены свободны
            plugin.getLogger().info("Загружена арена: " + key);
        }

        plugin.getLogger().info("Всего загружено арен: " + arenas.size());

        // Если арен нет, выводим подробную информацию для отладки
        if (arenas.isEmpty()) {
            plugin.getLogger().warning("Не удалось загрузить ни одной арены! Проверьте секцию arenas в config.yml");
            plugin.getLogger().warning("Доступные ключи в конфиге: " + String.join(", ", plugin.getConfig().getKeys(false)));

            if (arenasSection != null) {
                plugin.getLogger().warning("Ключи в секции arenas: " + String.join(", ", arenasSection.getKeys(false)));
            }
        }
    }

    public void reloadArenas() {
        loadArenas();
    }

    public List<Arena> getArenas() {
        return arenas;
    }
}