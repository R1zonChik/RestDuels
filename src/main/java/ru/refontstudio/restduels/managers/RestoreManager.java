package ru.refontstudio.restduels.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.models.Arena;
import ru.refontstudio.restduels.models.RestoreArea;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RestoreManager {
    private final RestDuels plugin;
    private final Map<String, RestoreArea> restoreAreas = new HashMap<>();
    private final Set<String> restoringAreas = new HashSet<>();
    private final Map<String, List<String>> arenaToAreas = new HashMap<>();
    private final Map<String, Map<Location, BlockData>> savedStates = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> restorationTasks = new HashMap<>();
    private File areasFile;
    private File statesFolder;

    public RestoreManager(RestDuels plugin) {
        this.plugin = plugin;
        loadAreasFile();
        createStatesFolder();
        loadAreas();
    }



    private void loadAreasFile() {
        areasFile = new File(plugin.getDataFolder(), "areas.yml");
        if (!areasFile.exists()) {
            try {
                areasFile.createNewFile();
                // Создаем пустой конфиг
                YamlConfiguration config = new YamlConfiguration();
                config.createSection("areas");
                config.createSection("arena_links");
                config.save(areasFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось создать файл areas.yml: " + e.getMessage());
            }
        }
    }

    /**
     * Проверяет, восстанавливается ли область в данный момент
     * @param areaName Название области
     * @return true, если область восстанавливается
     */
    public boolean isAreaRestoring(String areaName) {
        return restoringAreas.contains(areaName);
    }

    /**
     * Сохраняет состояние области в файл
     * @param areaName Название области
     * @param blockStates Состояния блоков
     */
    public void saveAreaState(String areaName, Map<Location, BlockData> blockStates) {
        File stateFile = new File(statesFolder, areaName + ".dat");

        try {
            if (!stateFile.exists()) {
                stateFile.createNewFile();
            }

            YamlConfiguration config = new YamlConfiguration();

            // Сохраняем метаданные
            config.set("area", areaName);
            config.set("timestamp", System.currentTimeMillis());
            config.set("block_count", blockStates.size());

            // Сохраняем блоки
            ConfigurationSection blocksSection = config.createSection("blocks");
            int index = 0;

            for (Map.Entry<Location, BlockData> entry : blockStates.entrySet()) {
                Location loc = entry.getKey();
                BlockData data = entry.getValue();

                ConfigurationSection blockSection = blocksSection.createSection(String.valueOf(index));
                blockSection.set("world", loc.getWorld().getName());
                blockSection.set("x", loc.getBlockX());
                blockSection.set("y", loc.getBlockY());
                blockSection.set("z", loc.getBlockZ());
                blockSection.set("data", data.getAsString());

                index++;
            }

            config.save(stateFile);
            plugin.getLogger().info("Состояние области " + areaName + " сохранено в файл");
        } catch (IOException e) {
            plugin.getLogger().severe("Ошибка при сохранении состояния области " + areaName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Проверяет, восстанавливается ли хотя бы одна область, связанная с ареной
     * @param arenaId ID арены
     * @return true, если хотя бы одна связанная область восстанавливается
     */
    public boolean isArenaRestoring(String arenaId) {
        List<String> areas = arenaToAreas.get(arenaId);
        if (areas == null || areas.isEmpty()) {
            return false;
        }

        for (String areaName : areas) {
            if (isAreaRestoring(areaName)) {
                return true;
            }
        }

        return false;
    }

    private void createStatesFolder() {
        statesFolder = new File(plugin.getDataFolder(), "states");
        if (!statesFolder.exists()) {
            statesFolder.mkdirs();
        }
    }

    private void loadAreas() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(areasFile);
        ConfigurationSection areasSection = config.getConfigurationSection("areas");
        ConfigurationSection linksSection = config.getConfigurationSection("arena_links");

        if (areasSection != null) {
            for (String areaName : areasSection.getKeys(false)) {
                ConfigurationSection areaSection = areasSection.getConfigurationSection(areaName);
                if (areaSection == null) continue;

                String worldName = areaSection.getString("world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("Мир " + worldName + " не найден для области " + areaName);
                    continue;
                }

                int x1 = areaSection.getInt("x1");
                int y1 = areaSection.getInt("y1");
                int z1 = areaSection.getInt("z1");
                int x2 = areaSection.getInt("x2");
                int y2 = areaSection.getInt("y2");
                int z2 = areaSection.getInt("z2");

                Location first = new Location(world, x1, y1, z1);
                Location second = new Location(world, x2, y2, z2);

                RestoreArea area = new RestoreArea(areaName, first, second);
                restoreAreas.put(areaName, area);

                // Загружаем сохраненное состояние, если есть
                loadAreaState(areaName);
            }
        }

        if (linksSection != null) {
            for (String arenaId : linksSection.getKeys(false)) {
                List<String> areas = linksSection.getStringList(arenaId);
                arenaToAreas.put(arenaId, areas);
            }
        }

        plugin.getLogger().info("Загружено " + restoreAreas.size() + " областей восстановления");
    }

    public void saveAreasFile() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection areasSection = config.createSection("areas");
        ConfigurationSection linksSection = config.createSection("arena_links");

        // Сохраняем области
        for (Map.Entry<String, RestoreArea> entry : restoreAreas.entrySet()) {
            String areaName = entry.getKey();
            RestoreArea area = entry.getValue();

            ConfigurationSection areaSection = areasSection.createSection(areaName);
            areaSection.set("world", area.getFirst().getWorld().getName());
            areaSection.set("x1", area.getFirst().getBlockX());
            areaSection.set("y1", area.getFirst().getBlockY());
            areaSection.set("z1", area.getFirst().getBlockZ());
            areaSection.set("x2", area.getSecond().getBlockX());
            areaSection.set("y2", area.getSecond().getBlockY());
            areaSection.set("z2", area.getSecond().getBlockZ());
        }

        // Сохраняем связи арен с областями
        for (Map.Entry<String, List<String>> entry : arenaToAreas.entrySet()) {
            linksSection.set(entry.getKey(), entry.getValue());
        }

        try {
            config.save(areasFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить файл areas.yml: " + e.getMessage());
        }
    }

    public void saveArenaState(String areaName, Location first, Location second) {
        // Проверяем, что локации валидны
        if (first.getWorld() != second.getWorld()) {
            throw new IllegalArgumentException("Точки находятся в разных мирах!");
        }

        // Создаем новую область
        RestoreArea area = new RestoreArea(areaName, first, second);
        restoreAreas.put(areaName, area);

        // Сохраняем в файл
        saveAreasFile();

        // Сохраняем текущее состояние блоков
        captureAreaState(areaName);
    }

    public void captureAreaState(String areaName) {
        RestoreArea area = restoreAreas.get(areaName);
        if (area == null) {
            plugin.getLogger().warning("Область " + areaName + " не найдена!");
            return;
        }

        // Очищаем предыдущее состояние, если оно было
        Map<Location, BlockData> blockStates = new HashMap<>();
        savedStates.put(areaName, blockStates);

        // Получаем границы области
        Location first = area.getFirst();
        Location second = area.getSecond();
        World world = first.getWorld();

        // Минимальные и максимальные координаты
        int minX = Math.min(first.getBlockX(), second.getBlockX());
        int minY = Math.min(first.getBlockY(), second.getBlockY());
        int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
        int maxX = Math.max(first.getBlockX(), second.getBlockX());
        int maxY = Math.max(first.getBlockY(), second.getBlockY());
        int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());

        // Считаем количество блоков для логирования
        int totalBlocks = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        plugin.getLogger().info("Сохранение состояния области " + areaName + " (" + totalBlocks + " блоков)");

        // Сохраняем блоки
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR) {
                        Location loc = block.getLocation();
                        blockStates.put(loc, block.getBlockData().clone());
                    }
                }
            }
        }

        plugin.getLogger().info("Сохранено " + blockStates.size() + " блоков для области " + areaName);

        // Сохраняем состояние в файл
        saveAreaState(areaName, blockStates);
    }

    /**
     * Проверяет, находится ли локация в указанной области
     * @param areaName Название области
     * @param location Локация для проверки
     * @return true, если локация находится в области
     */
    public boolean isLocationInArea(String areaName, Location location) {
        RestoreArea area = restoreAreas.get(areaName);
        if (area == null) {
            return false;
        }

        // Проверяем, что локация в том же мире
        if (!location.getWorld().equals(area.getFirst().getWorld())) {
            return false;
        }

        // Получаем границы области
        int minX = Math.min(area.getFirst().getBlockX(), area.getSecond().getBlockX());
        int maxX = Math.max(area.getFirst().getBlockX(), area.getSecond().getBlockX());
        int minY = Math.min(area.getFirst().getBlockY(), area.getSecond().getBlockY());
        int maxY = Math.max(area.getFirst().getBlockY(), area.getSecond().getBlockY());
        int minZ = Math.min(area.getFirst().getBlockZ(), area.getSecond().getBlockZ());
        int maxZ = Math.max(area.getFirst().getBlockZ(), area.getSecond().getBlockZ());

        // Проверяем, находится ли локация в границах
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /**
     * Загружает состояние области из файла
     * @param areaName Название области
     */
    private void loadAreaState(String areaName) {
        File stateFile = new File(statesFolder, areaName + ".dat");

        if (!stateFile.exists()) {
            plugin.getLogger().info("Нет сохраненного состояния для области " + areaName);
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(stateFile);

            // Проверяем, что файл содержит данные для правильной области
            String fileArea = config.getString("area");
            if (!areaName.equals(fileArea)) {
                plugin.getLogger().warning("Файл состояния содержит данные для другой области: " + fileArea);
                return;
            }

            // Загружаем блоки
            ConfigurationSection blocksSection = config.getConfigurationSection("blocks");
            if (blocksSection == null) {
                plugin.getLogger().warning("Файл состояния не содержит данных о блоках");
                return;
            }

            Map<Location, BlockData> blockStates = new HashMap<>();

            for (String key : blocksSection.getKeys(false)) {
                ConfigurationSection blockSection = blocksSection.getConfigurationSection(key);

                String worldName = blockSection.getString("world");
                World world = Bukkit.getWorld(worldName);

                if (world == null) {
                    plugin.getLogger().warning("Мир " + worldName + " не найден для блока " + key);
                    continue;
                }

                int x = blockSection.getInt("x");
                int y = blockSection.getInt("y");
                int z = blockSection.getInt("z");
                String dataString = blockSection.getString("data");

                Location loc = new Location(world, x, y, z);
                BlockData data = Bukkit.createBlockData(dataString);

                blockStates.put(loc, data);
            }

            savedStates.put(areaName, blockStates);
            plugin.getLogger().info("Загружено " + blockStates.size() + " блоков для области " + areaName);
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка при загрузке состояния области " + areaName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void linkAreaToArena(String areaName, String arenaId) {
        // Получаем или создаем список областей для арены
        List<String> areas = arenaToAreas.computeIfAbsent(arenaId, k -> new ArrayList<>());

        // Добавляем область, если её еще нет в списке
        if (!areas.contains(areaName)) {
            areas.add(areaName);
        }

        // Сохраняем изменения
        saveAreasFile();
    }

    public void unlinkAreaFromArena(String areaName, String arenaId) {
        List<String> areas = arenaToAreas.get(arenaId);
        if (areas != null) {
            areas.remove(areaName);
            if (areas.isEmpty()) {
                arenaToAreas.remove(arenaId);
            }
            saveAreasFile();
        }
    }

    public void restoreArea(String areaName) {
        RestoreArea area = restoreAreas.get(areaName);
        if (area == null) {
            plugin.getLogger().warning("Область " + areaName + " не найдена!");
            return;
        }

        Map<Location, BlockData> blockStates = savedStates.get(areaName);
        if (blockStates == null || blockStates.isEmpty()) {
            // Пробуем загрузить состояние из файла
            loadAreaState(areaName);
            blockStates = savedStates.get(areaName);

            if (blockStates == null || blockStates.isEmpty()) {
                plugin.getLogger().warning("Нет сохраненного состояния для области " + areaName);
                return;
            }
        }

        // Если уже идет восстановление этой области, отменяем его
        cancelRestoration(areaName);

        // Получаем границы области
        Location first = area.getFirst();
        Location second = area.getSecond();
        World world = first.getWorld();

        // Минимальные и максимальные координаты
        int minX = Math.min(first.getBlockX(), second.getBlockX());
        int minY = Math.min(first.getBlockY(), second.getBlockY());
        int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
        int maxX = Math.max(first.getBlockX(), second.getBlockX());
        int maxY = Math.max(first.getBlockY(), second.getBlockY());
        int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());

        // Создаем список задач для восстановления
        List<RestoreTask> restoreTasks = new ArrayList<>();

        // Сначала сканируем текущее состояние и добавляем задачи для удаления новых блоков
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location loc = new Location(world, x, y, z);
                    Block block = world.getBlockAt(loc);

                    // Пропускаем воздух
                    if (block.getType() == Material.AIR) {
                        continue;
                    }

                    // Проверяем, был ли этот блок в исходном состоянии
                    if (!blockStates.containsKey(loc)) {
                        // Это новый блок, который нужно удалить
                        restoreTasks.add(new RestoreTask(loc, Bukkit.createBlockData(Material.AIR), true));
                    }
                }
            }
        }

        // Теперь добавляем задачи для восстановления оригинальных блоков
        for (Map.Entry<Location, BlockData> entry : blockStates.entrySet()) {
            Location loc = entry.getKey();
            BlockData data = entry.getValue();

            // Проверяем, изменился ли блок
            Block currentBlock = world.getBlockAt(loc);
            if (!currentBlock.getBlockData().equals(data)) {
                // Блок изменился, нужно восстановить
                restoreTasks.add(new RestoreTask(loc, data, false));
            }
        }

        // Получаем количество блоков для восстановления за один тик из конфига
        final int blocksPerTick = plugin.getConfig().getInt("restoration.blocks-per-tick", 50);
        plugin.getLogger().info("Восстановление области " + areaName + " с " + blocksPerTick + " блоками за тик");
        plugin.getLogger().info("Всего блоков для восстановления: " + restoreTasks.size());

        // Помечаем арену как восстанавливающуюся
        restoringAreas.add(areaName);

        // Блокируем все арены, связанные с этой областью
        List<String> linkedArenas = getArenaLinks(areaName);
        for (String arenaId : linkedArenas) {
            plugin.getDuelManager().blockArena(arenaId, "Арена восстанавливается");
        }

        // Запускаем задачу восстановления
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int currentIndex = 0;

            @Override
            public void run() {
                int processed = 0;

                // Сначала обрабатываем задачи на удаление новых блоков (приоритет)
                while (currentIndex < restoreTasks.size() && processed < blocksPerTick) {
                    RestoreTask task = restoreTasks.get(currentIndex);

                    // Восстанавливаем блок
                    Block block = task.getLocation().getBlock();
                    block.setBlockData(task.getBlockData(), false);

                    currentIndex++;
                    processed++;
                }

                // Если все блоки восстановлены, отменяем задачу
                if (currentIndex >= restoreTasks.size()) {
                    // Убираем область из списка восстанавливающихся
                    restoringAreas.remove(areaName);

                    // Разблокируем арены
                    for (String arenaId : linkedArenas) {
                        plugin.getDuelManager().unblockArena(arenaId);
                    }

                    restorationTasks.remove(areaName).cancel();
                    plugin.getLogger().info("Восстановление области " + areaName + " завершено");
                }
            }
        }, 1L, 1L);

        restorationTasks.put(areaName, task);
        plugin.getLogger().info("Запущено восстановление области " + areaName + " (" + restoreTasks.size() + " блоков)");
    }

    private void cancelRestoration(String areaName) {
        BukkitTask task = restorationTasks.remove(areaName);
        if (task != null) {
            task.cancel();
            plugin.getLogger().info("Отменено текущее восстановление области " + areaName);
        }
    }

    public void restoreArenaAreas(String arenaId) {
        List<String> areas = arenaToAreas.get(arenaId);
        if (areas == null || areas.isEmpty()) {
            return;
        }

        for (String areaName : areas) {
            restoreArea(areaName);
        }
    }

    public void captureArenaAreas(String arenaId) {
        List<String> areas = arenaToAreas.get(arenaId);
        if (areas == null || areas.isEmpty()) {
            return;
        }

        for (String areaName : areas) {
            captureAreaState(areaName);
        }
    }

    public List<String> getAreasForArena(String arenaId) {
        return arenaToAreas.getOrDefault(arenaId, Collections.emptyList());
    }

    public RestoreArea getArea(String areaName) {
        return restoreAreas.get(areaName);
    }

    public Map<String, RestoreArea> getAllAreas() {
        return Collections.unmodifiableMap(restoreAreas);
    }

    public Map<String, List<String>> getArenaLinks() {
        return Collections.unmodifiableMap(arenaToAreas);
    }

    public List<String> getArenaLinks(String areaName) {
        List<String> linkedArenas = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : arenaToAreas.entrySet()) {
            if (entry.getValue().contains(areaName)) {
                linkedArenas.add(entry.getKey());
            }
        }

        return linkedArenas;
    }

    /**
     * Проверяет, есть ли активное состояние для области
     * @param areaName Название области
     * @return true, если есть активное состояние
     */
    public boolean hasActiveState(String areaName) {
        return savedStates.containsKey(areaName) &&
                savedStates.get(areaName) != null &&
                !savedStates.get(areaName).isEmpty();
    }

    /**
     * Получает сохраненные состояния блоков для области
     * @param areaName Название области
     * @return Карта состояний блоков или null, если нет сохраненных состояний
     */
    public Map<Location, BlockData> getSavedStates(String areaName) {
        return savedStates.get(areaName);
    }

    public void deleteArea(String areaName) {
        // Удаляем область
        restoreAreas.remove(areaName);
        savedStates.remove(areaName);

        // Удаляем файл состояния, если существует
        File stateFile = new File(statesFolder, areaName + ".dat");
        if (stateFile.exists()) {
            stateFile.delete();
        }

        // Удаляем ссылки на эту область из всех арен
        for (List<String> areas : arenaToAreas.values()) {
            areas.remove(areaName);
        }

        // Удаляем пустые списки
        arenaToAreas.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // Сохраняем изменения
        saveAreasFile();
    }
    private static class RestoreTask {
        private final Location location;
        private final BlockData blockData;
        private final boolean isNewBlock; // true для новых блоков, false для оригинальных

        public RestoreTask(Location location, BlockData blockData, boolean isNewBlock) {
            this.location = location;
            this.blockData = blockData;
            this.isNewBlock = isNewBlock;
        }

        public Location getLocation() {
            return location;
        }

        public BlockData getBlockData() {
            return blockData;
        }

        public boolean isNewBlock() {
            return isNewBlock;
        }
    }
}