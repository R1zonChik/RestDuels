package ru.refontstudio.restduels.listeners;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.models.Duel;
import ru.refontstudio.restduels.models.DuelType;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DuelDeathListener implements Listener {
    private final RestDuels plugin;
    private final Map<UUID, Location> pendingRespawns = new HashMap<>();
    private final Map<UUID, Long> lastRespawnTime = new HashMap<>();

    // Кеш для сохранения инвентаря умерших игроков в Ranked дуэлях
    private final Map<UUID, ItemStack[]> rankedDeathInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> rankedDeathArmor = new HashMap<>();
    private final Map<UUID, ItemStack[]> rankedDeathExtraContents = new HashMap<>();

    public DuelDeathListener(RestDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        // Проверяем, находится ли игрок в состоянии дуэли
        if (!plugin.getDuelManager().isPlayerInDuel(playerId)) {
            // Не в дуэли? Тогда игнорируем — пусть работают другие плагины (например, спавн)
            return;
        }

        // Проверяем, находится ли игрок в мире дуэлей
        if (!plugin.getConfig().getStringList("worlds.duel-worlds").contains(
                player.getWorld().getName().toLowerCase())) {
            return;
        }

        // Получаем дуэль, в которой участвует игрок
        Duel duel = plugin.getDuelManager().getPlayerDuel(playerId);
        if (duel == null) return;

        // Определяем победителя
        UUID winnerId = duel.getPlayer1Id().equals(playerId) ? duel.getPlayer2Id() : duel.getPlayer1Id();
        Player winner = Bukkit.getPlayer(winnerId);

        // КЛЮЧЕВАЯ ЛОГИКА: Обработка выпадения предметов в зависимости от типа дуэли
        if (duel.getType() == DuelType.RANKED) {
            // В Ranked дуэли предметы НЕ выпадают
            // Моментально сохраняем текущий инвентарь перед любыми изменениями
            savePlayerInventoryForRankedDuel(player);

            // Отменяем выпадение предметов
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);

            // Отправляем сообщение игроку
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cВы проиграли ранговую дуэль! Ваш инвентарь будет восстановлен после возрождения."));

            // Оповещаем победителя
            if (winner != null && winner.isOnline()) {
                winner.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&aВы победили в ранговой дуэли! Противник сохранит свой инвентарь."));
            }

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Ранкед дуэль: предметы сохранены при смерти игрока " + player.getName());
            }
        } else if (duel.getType() == DuelType.CLASSIC || duel.getType() == DuelType.NORMAL) {
            // В Classic и Normal дуэлях предметы ВЫПАДАЮТ
            event.setKeepInventory(false);
            event.setKeepLevel(false);
            // НЕ очищаем event.getDrops() - пусть предметы выпадают естественным путем!

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Классик дуэль: предметы выпали при смерти игрока " + player.getName());
            }
        }

        // Завершаем дуэль
        plugin.getDuelManager().endDuel(duel, winnerId);

        // Сохраняем исходную локацию для будущего респавна
        Location originalLocation = plugin.getDuelManager().getOriginalLocation(playerId);
        if (originalLocation != null && originalLocation.getWorld() != null) {
            pendingRespawns.put(playerId, originalLocation);

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Игрок " + player.getName() + " умер, запланирована телепортация на " +
                        originalLocation.getWorld().getName() + " после респавна");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Проверяем, есть ли запланированный респавн
        if (pendingRespawns.containsKey(playerId)) {
            Location respawnLocation = pendingRespawns.get(playerId);

            // Проверяем, что локация не в мире дуэлей
            if (!isInDuelWorld(respawnLocation)) {
                // Устанавливаем локацию респавна напрямую
                event.setRespawnLocation(respawnLocation);

                // Запоминаем время респавна
                lastRespawnTime.put(playerId, System.currentTimeMillis());

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Установлена локация респавна для игрока " + player.getName() +
                            " на " + respawnLocation.getWorld().getName());
                }
            } else {
                // Локация в мире дуэлей - используем спавн основного мира
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Локация респавна в мире дуэлей, используем стандартный респавн");
                }
            }

            // Удаляем запись, т.к. мы уже обработали респавн
            pendingRespawns.remove(playerId);

            // Проверяем, есть ли сохраненный инвентарь для Ranked дуэлей, и восстанавливаем его НЕМЕДЛЕННО
            if (rankedDeathInventories.containsKey(playerId)) {
                // Моментальное восстановление инвентаря непосредственно в этом событии
                // без создания дополнительных задержек
                restoreRankedDuelInventory(player);

                // ДОБАВЛЕНО: Восстанавливаем здоровье для ранкед дуэлей
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        plugin.getDuelManager().restorePlayerHealth(player);

                        if (plugin.getConfig().getBoolean("debug", false)) {
                            plugin.getLogger().info("Восстановлено здоровье после респавна для " + player.getName());
                        }
                    }
                }, 40L); // 2 секунды задержки
            }
        }
    }

    private void savePlayerInventoryForRankedDuel(Player player) {
        UUID playerId = player.getUniqueId();

        // Создаем глубокие копии всего содержимого инвентаря
        ItemStack[] mainInv = new ItemStack[player.getInventory().getContents().length];
        ItemStack[] armorInv = new ItemStack[player.getInventory().getArmorContents().length];

        // Копируем каждый предмет отдельно для избежания ссылочных проблем
        for (int i = 0; i < player.getInventory().getContents().length; i++) {
            ItemStack item = player.getInventory().getContents()[i];
            if (item != null) mainInv[i] = item.clone();
        }

        for (int i = 0; i < player.getInventory().getArmorContents().length; i++) {
            ItemStack item = player.getInventory().getArmorContents()[i];
            if (item != null) armorInv[i] = item.clone();
        }

        // Сохраняем копии
        rankedDeathInventories.put(playerId, mainInv);
        rankedDeathArmor.put(playerId, armorInv);

        // Для новых версий Minecraft сохраняем дополнительные слоты (оффхенд и др.)
        try {
            ItemStack[] extraInv = new ItemStack[player.getInventory().getExtraContents().length];
            for (int i = 0; i < player.getInventory().getExtraContents().length; i++) {
                ItemStack item = player.getInventory().getExtraContents()[i];
                if (item != null) extraInv[i] = item.clone();
            }
            rankedDeathExtraContents.put(playerId, extraInv);
        } catch (NoSuchMethodError e) {
            // Для совместимости со старыми версиями Bukkit
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("Невозможно сохранить дополнительные слоты инвентаря: " + e.getMessage());
            }
        }

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Сохранен инвентарь игрока " + player.getName() + " для Ranked дуэли");
        }
    }

    /**
     * Восстанавливает инвентарь игрока после смерти в Ranked дуэли
     * @param player Игрок для восстановления инвентаря
     */
    private void restoreRankedDuelInventory(Player player) {
        UUID playerId = player.getUniqueId();

        // Проверяем, есть ли сохраненный инвентарь
        if (rankedDeathInventories.containsKey(playerId)) {
            try {
                // Очищаем текущий инвентарь
                player.getInventory().clear();

                // Восстанавливаем ранее сохраненные предметы
                player.getInventory().setContents(rankedDeathInventories.get(playerId));
                player.getInventory().setArmorContents(rankedDeathArmor.get(playerId));

                // Восстанавливаем дополнительные слоты
                try {
                    if (rankedDeathExtraContents.containsKey(playerId)) {
                        player.getInventory().setExtraContents(rankedDeathExtraContents.get(playerId));
                    }
                } catch (NoSuchMethodError e) {
                    // Игнорируем ошибку для совместимости со старыми версиями
                }

                // Обновляем инвентарь игрока
                player.updateInventory();

                // Устанавливаем режим выживания, если игрок был в другом режиме
                player.setGameMode(GameMode.SURVIVAL);

                // Отправляем сообщение игроку (ТОЛЬКО ЗДЕСЬ, убрано из onPlayerRespawn)
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&aВаш инвентарь был успешно восстановлен после ранговой дуэли!"));

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Восстановлен инвентарь игрока " + player.getName() + " после Ranked дуэли");
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Ошибка при восстановлении инвентаря игрока " +
                        player.getName() + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Удаляем сохраненные данные
                rankedDeathInventories.remove(playerId);
                rankedDeathArmor.remove(playerId);
                rankedDeathExtraContents.remove(playerId);
            }
        }
    }

    /**
     * Проверяет, находится ли локация в мире дуэлей
     * @param location Локация для проверки
     * @return true, если локация в мире дуэлей
     */
    private boolean isInDuelWorld(Location location) {
        if (location == null || location.getWorld() == null) return false;

        String worldName = location.getWorld().getName().toLowerCase();
        return plugin.getConfig().getStringList("worlds.duel-worlds").contains(worldName);
    }
}