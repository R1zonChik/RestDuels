package ru.refontstudio.restduels.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.models.DuelType;
import ru.refontstudio.restduels.models.PlayerStats;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuelMenu implements InventoryHolder {
    private final RestDuels plugin;
    private final Inventory inventory;
    private final Player player;
    private final Map<Integer, String> slotActions = new HashMap<>();

    public DuelMenu(RestDuels plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        // Получаем название из конфига
        String title = ColorUtils.colorize(
                plugin.getConfig().getString("gui.title", "Меню Дуэлей"));

        // Получаем размер меню из конфига (строки * 9)
        int rows = plugin.getConfig().getInt("gui.size", 5);
        int size = rows * 9;

        this.inventory = Bukkit.createInventory(this, size, title);
        initializeItems();
    }

    private void initializeItems() {
        // Получаем информацию для плейсхолдеров
        PlayerStats stats = plugin.getStatsManager().getPlayerStats(player.getUniqueId());
        int totalArenas = plugin.getDuelManager().getTotalArenasCount();
        int occupiedArenas = plugin.getDuelManager().getOccupiedArenasCount();
        int freeArenas = totalArenas - occupiedArenas;
        int arenaQueueSize = plugin.getDuelManager().getArenaQueueSize();

        // Получаем секцию с предметами меню
        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("gui.items");
        if (itemsSection == null) return;

        // Перебираем все настроенные предметы
        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
            if (itemSection == null || !itemSection.getBoolean("enabled", true)) continue;

            // Получаем слоты для этого предмета
            String slotsStr = itemSection.getString("slots", "0");
            List<Integer> slots = parseSlots(slotsStr);

            // Получаем материал
            String materialName = itemSection.getString("material", "STONE");
            Material material = Material.getMaterial(materialName);
            if (material == null) material = Material.STONE;

            // Получаем название и свечение
            String name = ColorUtils.colorize(itemSection.getString("name", " "));
            boolean glow = itemSection.getBoolean("glow", false);

            // Получаем и обрабатываем лор
            List<String> lore = itemSection.getStringList("lore");
            List<String> processedLore = replacePlaceholders(lore, stats, freeArenas, totalArenas, arenaQueueSize);

            // Создаем предмет
            ItemStack item;

            // Особая обработка для головы игрока
            if (material == Material.PLAYER_HEAD && itemSection.getBoolean("use_player_head", false)) {
                item = new ItemStack(material);
                SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
                skullMeta.setOwningPlayer(player);
                skullMeta.setDisplayName(name);

                if (!processedLore.isEmpty()) {
                    skullMeta.setLore(processedLore);
                }

                if (glow) {
                    skullMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                    skullMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }

                item.setItemMeta(skullMeta);
            } else {
                item = createItem(material, name, processedLore, glow);
            }

            // Размещаем предмет во всех указанных слотах
            for (int slot : slots) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, item);

                    // Сохраняем действие для этого слота, если оно есть
                    String action = itemSection.getString("action", null);
                    if (action != null && !action.isEmpty()) {
                        slotActions.put(slot, action);
                    }
                }
            }
        }
    }

    // Метод для разбора строки слотов в список
    private List<Integer> parseSlots(String slotsStr) {
        List<Integer> slots = new ArrayList<>();

        // Разделяем строку по запятым
        String[] parts = slotsStr.split(",");

        for (String part : parts) {
            // Проверяем, является ли часть диапазоном
            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length == 2) {
                    try {
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());

                        // Добавляем все слоты в диапазоне
                        for (int i = start; i <= end; i++) {
                            slots.add(i);
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Неверный формат диапазона слотов: " + part);
                    }
                }
            } else {
                // Одиночный слот
                try {
                    slots.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Неверный формат слота: " + part);
                }
            }
        }

        return slots;
    }

    private List<String> replacePlaceholders(List<String> lore, PlayerStats stats, int freeArenas, int totalArenas, int arenaQueueSize) {
        List<String> result = new ArrayList<>();

        for (String line : lore) {
            // Заменяем плейсхолдеры статистики
            line = line.replace("%wins%", String.valueOf(stats.getWins()));
            line = line.replace("%deaths%", String.valueOf(stats.getDeaths()));

            // Расчет K/D и процента побед
            double kd = stats.getDeaths() > 0 ? (double) stats.getWins() / stats.getDeaths() : stats.getWins();
            int total = stats.getWins() + stats.getDeaths();
            double winRate = total > 0 ? (double) stats.getWins() / total * 100 : 0;
            DecimalFormat df = new DecimalFormat("#.##");

            line = line.replace("%kd%", df.format(kd));
            line = line.replace("%win_rate%", df.format(winRate));

            // Заменяем плейсхолдеры арен
            line = line.replace("%free_arenas%", String.valueOf(freeArenas));
            line = line.replace("%total_arenas%", String.valueOf(totalArenas));
            line = line.replace("%arena_queue_size%", String.valueOf(arenaQueueSize));

            // Заменяем плейсхолдеры очередей
            line = line.replace("%normal_queue_size%", String.valueOf(plugin.getDuelManager().getQueueSize(DuelType.NORMAL)));
            line = line.replace("%ranked_queue_size%", String.valueOf(plugin.getDuelManager().getQueueSize(DuelType.RANKED)));

            result.add(ColorUtils.colorize(line));
        }

        return result;
    }

    private ItemStack createItem(Material material, String name, List<String> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);

        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }

        if (glow) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    public void open() {
        player.openInventory(inventory);
    }

    public String getActionForSlot(int slot) {
        return slotActions.getOrDefault(slot, null);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}