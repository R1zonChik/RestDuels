package ru.refontstudio.restduels.listeners;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DuelRestrictionListener implements Listener {

    private final RestDuels plugin;
    private final Set<String> duelWorldNames = new HashSet<>();
    private final Set<Material> blockedItems = new HashSet<>();
    private final Set<InventoryType> blockedInventories = new HashSet<>();
    private boolean blockExpBottles;
    private boolean blockItemDrop;
    private boolean blockInventoryClose;
    private boolean blockItemPickup;
    private final Set<Material> blockedConsumeItems = new HashSet<>();
    private final Map<UUID, Long> lastMessageTimes = new HashMap<>();

    public DuelRestrictionListener(RestDuels plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Загружает конфигурацию из antidupe.yml
     */
    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "antidupe.yml");

        // Создаем файл, если он не существует
        if (!file.exists()) {
            plugin.saveResource("antidupe.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Загружаем имена миров дуэлей
        duelWorldNames.addAll(plugin.getConfig().getStringList("worlds.duel-worlds"));
        if (duelWorldNames.isEmpty()) {
            duelWorldNames.add("duels");
        }

        // Загружаем настройки блокировки
        blockExpBottles = config.getBoolean("block-exp-bottles", true);
        blockItemDrop = config.getBoolean("block-item-drop", false);
        blockInventoryClose = config.getBoolean("block-inventory-close-during-duel", false);
        blockItemPickup = config.getBoolean("block-item-pickup-during-preparation", true);

        // Загружаем запрещенные предметы
        List<String> blockedItemsList = config.getStringList("blocked-items");
        for (String itemName : blockedItemsList) {
            try {
                Material material = Material.valueOf(itemName.toUpperCase());
                blockedItems.add(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неверный материал в antidupe.yml: " + itemName);
            }
        }

        // Загружаем запрещенные типы инвентарей
        List<String> blockedInventoriesList = config.getStringList("blocked-inventories");
        for (String invTypeStr : blockedInventoriesList) {
            try {
                InventoryType invType = InventoryType.valueOf(invTypeStr.toUpperCase());
                blockedInventories.add(invType);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неверный тип инвентаря в antidupe.yml: " + invTypeStr);
            }
        }

        // Загружаем запрещенные для употребления предметы
        List<String> blockedConsumeItemsList = config.getStringList("blocked-consume-items");
        for (String itemName : blockedConsumeItemsList) {
            try {
                Material material = Material.valueOf(itemName.toUpperCase());
                blockedConsumeItems.add(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неверный материал для употребления в antidupe.yml: " + itemName);
            }
        }
    }

    /**
     * Проверяет, находится ли игрок в мире дуэлей
     */
    private boolean isInDuelWorld(Player player) {
        return duelWorldNames.contains(player.getWorld().getName().toLowerCase());
    }

    /**
     * Проверяет, находится ли игрок в дуэли
     */
    private boolean isInDuel(Player player) {
        return plugin.getDuelManager().isPlayerInDuel(player.getUniqueId());
    }

    /**
     * Проверяет, находится ли игрок в подготовке к дуэли
     */
    private boolean isInPreparation(Player player) {
        return plugin.getDuelManager().isPlayerInCountdown(player.getUniqueId());
    }

    /**
     * Блокирует использование предметов ТОЛЬКО во время подготовки + всегда блокирует бутылки опыта
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!isInDuelWorld(player)) return;

        // Получаем предмет в руке игрока
        ItemStack item = event.getItem();
        if (item == null) return;

        // ВСЕГДА блокируем бутылочки опыта в мире дуэлей
        if (blockExpBottles && item.getType() == Material.EXPERIENCE_BOTTLE &&
                (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            event.getPlayer().updateInventory();
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cИспользование бутылочек опыта запрещено в мире дуэлей!"));
            return;
        }

        // ТОЛЬКО во время подготовки блокируем ВСЕ взаимодействия
        if (isInPreparation(player)) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                player.updateInventory();
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cИспользование предметов запрещено во время подготовки к дуэли!"));
                return;
            }
        }

        // Остальные ограничения из конфига (работают всегда в мире дуэлей)
        if (blockedItems.contains(item.getType())) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR ||
                    event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cИспользование этого предмета запрещено в мире дуэлей!"));
            }
        }
    }

    /**
     * ВСЕГДА блокирует бросание бутылочек опыта в мире дуэлей
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof ThrownExpBottle)) return;
        if (!(event.getEntity().getShooter() instanceof Player)) return;

        Player player = (Player) event.getEntity().getShooter();

        if (isInDuelWorld(player) && blockExpBottles) {
            event.setCancelled(true);
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cИспользование бутылочек опыта запрещено в мире дуэлей!"));
        }
    }

    /**
     * ВСЕГДА блокирует разбивание бутылочек опыта в мире дуэлей
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExpBottleBreak(ExpBottleEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player)) return;

        Player player = (Player) event.getEntity().getShooter();

        if (isInDuelWorld(player) && blockExpBottles) {
            event.setCancelled(true);
            event.setExperience(0);
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cИспользование бутылочек опыта запрещено в мире дуэлей!"));
        }
    }

    /**
     * Блокирует выбрасывание предметов ТОЛЬКО во время подготовки
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (!isInDuelWorld(player)) return;

        // ТОЛЬКО во время подготовки блокируем выбрасывание
        if (isInPreparation(player)) {
            event.setCancelled(true);
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cВыбрасывание предметов запрещено во время подготовки к дуэли!"));
            return;
        }

        // Остальные ограничения из конфига (работают всегда)
        if (blockItemDrop) {
            Material dropType = event.getItemDrop().getItemStack().getType();
            if (blockedItems.contains(dropType)) {
                event.setCancelled(true);
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cВыбрасывание этого предмета запрещено в мире дуэлей!"));
            }
        }
    }

    /**
     * Блокирует закрытие инвентаря во время дуэли (не подготовки!)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        if (isInDuelWorld(player) && isInDuel(player) && blockInventoryClose) {
            if (blockedInventories.contains(event.getInventory().getType())) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.openInventory(event.getInventory());
                    player.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    "&cЗакрытие этого инвентаря запрещено во время дуэли!"));
                });
            }
        }
    }

    /**
     * Блокирует открытие определенных типов инвентарей во время дуэли (не подготовки!)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        if (isInDuelWorld(player) && isInDuel(player)) {
            if (blockedInventories.contains(event.getInventory().getType())) {
                event.setCancelled(true);
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cОткрытие этого инвентаря запрещено во время дуэли!"));
            }
        }
    }

    /**
     * Блокирует подбор предметов во время подготовки к дуэли
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        if (isInDuelWorld(player) && isInPreparation(player) && blockItemPickup) {
            event.setCancelled(true);
        }
    }

    /**
     * Блокирует употребление предметов ТОЛЬКО во время подготовки
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();

        if (!isInDuelWorld(player)) return;

        // ТОЛЬКО во время подготовки блокируем употребление
        if (isInPreparation(player)) {
            event.setCancelled(true);
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cУпотребление предметов запрещено во время подготовки к дуэли!"));
            return;
        }

        // Остальные ограничения из конфига (работают всегда)
        Material consumeType = event.getItem().getType();
        if (blockedConsumeItems.contains(consumeType)) {
            event.setCancelled(true);
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cУпотребление этого предмета запрещено в мире дуэлей!"));
        }
    }
}