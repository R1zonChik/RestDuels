package ru.refontstudio.restduels.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.InventoryHolder;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.gui.DuelMenu;
import ru.refontstudio.restduels.utils.ColorUtils;

public class MenuCloseListener implements Listener {
    private final RestDuels plugin;

    public MenuCloseListener(RestDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            // Если игрок получил урон, закрываем меню
            if (isPvPEvent(event)) {
                closeMenuIfOpen(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();

            // Если игрок нанес урон, закрываем меню
            closeMenuIfOpen(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        // Опционально: закрываем меню при приседании (часто используется для входа в PvP)
        if (event.isSneaking()) {
            closeMenuIfOpen(event.getPlayer());
        }
    }

    /**
     * Проверяет, является ли событие PvP-событием
     * @param event Событие урона
     * @return true, если событие связано с PvP
     */
    private boolean isPvPEvent(EntityDamageEvent event) {
        // Проверяем причину урона
        switch (event.getCause()) {
            case ENTITY_ATTACK:
            case PROJECTILE:
            case ENTITY_SWEEP_ATTACK:
                return true;
            default:
                return event instanceof EntityDamageByEntityEvent &&
                        ((EntityDamageByEntityEvent) event).getDamager() instanceof Player;
        }
    }

    /**
     * Закрывает меню, если оно открыто
     * @param player Игрок для проверки
     */
    private void closeMenuIfOpen(Player player) {
        if (player.getOpenInventory() != null) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();

            // Проверяем, является ли открытое меню меню дуэлей
            if (holder instanceof DuelMenu ||
                    player.getOpenInventory().getTitle().contains("Дуэли") ||
                    player.getOpenInventory().getTitle().contains("Выбор типа") ||
                    player.getOpenInventory().getTitle().contains("Статистика") ||
                    player.getOpenInventory().getTitle().contains("Топ игроков")) {

                // Закрываем меню
                player.closeInventory();

                // Опционально: отправляем сообщение
                if (plugin.getConfig().getBoolean("messages.notify-menu-close", false)) {
                    player.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    plugin.getConfig().getString("messages.menu-closed-pvp", "&cМеню дуэлей закрыто из-за входа в PvP.")));
                }
            }
        }
    }

    /**
     * Обрабатывает клик в инвентаре для определения PvP статуса
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Если игрок кликнул по оружию или броне, считаем это подготовкой к PvP
        if (event.getCurrentItem() != null) {
            String itemType = event.getCurrentItem().getType().name().toLowerCase();
            if (itemType.contains("sword") || itemType.contains("axe") ||
                    itemType.contains("bow") || itemType.contains("helmet") ||
                    itemType.contains("chestplate") || itemType.contains("leggings") ||
                    itemType.contains("boots")) {

                // Закрываем меню с небольшой задержкой
                Bukkit.getScheduler().runTaskLater(plugin, () -> closeMenuIfOpen(player), 1L);
            }
        }
    }
}