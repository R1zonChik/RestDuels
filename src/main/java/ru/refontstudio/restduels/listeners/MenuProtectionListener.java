package ru.refontstudio.restduels.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.utils.ColorUtils;

public class MenuProtectionListener implements Listener {
    private final RestDuels plugin;

    public MenuProtectionListener(RestDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();

        // Проверяем, что это не инвентарь игрока
        if (clickedInventory != null && !clickedInventory.equals(player.getInventory())) {
            // Проверяем, содержит ли инвентарь метаданные плагина или имеет особое название
            String title = event.getView().getTitle();
            if (title.contains("Дуэли") ||
                    title.contains("Выбор типа") ||
                    title.contains("Статистика") ||
                    title.contains("Информация") ||
                    title.contains("Меню") ||
                    title.contains("Топ игроков")) {

                // Блокируем любые действия с инвентарем, которые могут переместить предметы
                if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
                        event.getAction() == InventoryAction.COLLECT_TO_CURSOR ||
                        event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD ||
                        event.getAction() == InventoryAction.HOTBAR_SWAP ||
                        event.isShiftClick()) {

                    event.setCancelled(true);
                    return;
                }

                // Блокируем взятие предметов из меню
                event.setCancelled(true);
            }
        }

        // Блокируем нажатие цифровых клавиш для перемещения предметов
        if (event.getHotbarButton() >= 0) {
            String title = event.getView().getTitle();
            if (title.contains("Дуэли") ||
                    title.contains("Выбор типа") ||
                    title.contains("Статистика") ||
                    title.contains("Информация") ||
                    title.contains("Меню") ||
                    title.contains("Топ игроков")) {

                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        // Проверяем название инвентаря
        String title = event.getView().getTitle();
        if (title.contains("Дуэли") ||
                title.contains("Выбор типа") ||
                title.contains("Статистика") ||
                title.contains("Информация") ||
                title.contains("Меню") ||
                title.contains("Топ игроков")) {

            // Блокируем любое перетаскивание в этих инвентарях
            event.setCancelled(true);
        }
    }
}