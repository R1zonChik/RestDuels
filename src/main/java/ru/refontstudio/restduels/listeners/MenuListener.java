package ru.refontstudio.restduels.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.gui.DuelMenu;
import ru.refontstudio.restduels.models.DuelType;

public class MenuListener implements Listener {
    private final RestDuels plugin;

    public MenuListener(RestDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        // Проверяем, что это наше меню
        if (holder instanceof DuelMenu) {
            event.setCancelled(true); // Отменяем клик

            if (event.getCurrentItem() == null) {
                return;
            }

            Player player = (Player) event.getWhoClicked();
            DuelMenu menu = (DuelMenu) holder;
            int slot = event.getSlot();

            // Получаем действие для этого слота
            String action = menu.getActionForSlot(slot);
            if (action == null) {
                return; // Нет действия для этого слота
            }

            // Обрабатываем действие
            if (action.equals("normal_duel")) {
                player.closeInventory();

                if (event.isShiftClick()) {
                    // Отмена поиска
                    plugin.getDuelManager().cancelDuel(player);
                } else {
                    // Начало поиска
                    plugin.getDuelManager().queuePlayer(player, DuelType.NORMAL);
                }
            } else if (action.equals("ranked_duel")) {
                player.closeInventory();

                if (event.isShiftClick()) {
                    // Отмена поиска
                    plugin.getDuelManager().cancelDuel(player);
                } else {
                    // Начало поиска
                    plugin.getDuelManager().queuePlayer(player, DuelType.RANKED);
                }
            } else if (action.equals("stats")) {
                player.closeInventory();
                player.performCommand("duel stats");
            } else if (action.equals("close")) {
                player.closeInventory();
            } else if (action.startsWith("command:")) {
                // Выполняем команду
                String command = action.substring(8); // Убираем "command:"
                player.closeInventory();
                player.performCommand(command);
            }
        }
    }
}