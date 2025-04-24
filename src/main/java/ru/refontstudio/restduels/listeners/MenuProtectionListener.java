package ru.refontstudio.restduels.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
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
        String title = event.getView().getTitle();

        // Проверяем, связано ли это с нашими меню
        if (title.contains("Дуэли") ||
                title.contains("Выбор типа") ||
                title.contains("Статистика") ||
                title.contains("Информация") ||
                title.contains("Меню") ||
                title.contains("Топ игроков")) {

            // Ключевое исправление: проверяем rawSlot вместо clickedInventory
            // Если rawSlot меньше размера верхнего инвентаря, значит клик был по верхнему инвентарю
            if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }

            // Дополнительно блокируем shift-клик в нижнем инвентаре
            if (event.isShiftClick()) {
                event.setCancelled(true);
                return;
            }

            // Блокируем нажатие цифровых клавиш для перемещения предметов
            if (event.getHotbarButton() >= 0) {
                event.setCancelled(true);
            }

            // Блокируем все действия, которые могут переместить предметы между инвентарями
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
                    event.getAction() == InventoryAction.COLLECT_TO_CURSOR ||
                    event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD ||
                    event.getAction() == InventoryAction.HOTBAR_SWAP ||
                    event.getAction() == InventoryAction.PICKUP_ALL ||
                    event.getAction() == InventoryAction.PICKUP_HALF ||
                    event.getAction() == InventoryAction.PICKUP_ONE ||
                    event.getAction() == InventoryAction.PICKUP_SOME ||
                    event.getAction() == InventoryAction.CLONE_STACK ||
                    event.getAction() == InventoryAction.PLACE_ALL ||
                    event.getAction() == InventoryAction.PLACE_ONE ||
                    event.getAction() == InventoryAction.PLACE_SOME ||
                    event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
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

            // Проверяем, затрагивает ли перетаскивание верхний инвентарь
            for (int slot : event.getRawSlots()) {
                if (slot < event.getView().getTopInventory().getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // Добавляем обработчик выбрасывания предметов для предотвращения подбора "фантомных" предметов
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        final Player player = event.getPlayer();

        // Проверяем, был ли игрок недавно в меню дуэлей
        // Можно также добавить проверку метаданных предмета, если вы добавляете особые метаданные к предметам меню
        if (player.getOpenInventory() != null && player.getOpenInventory().getTitle() != null) {
            String title = player.getOpenInventory().getTitle();

            if (title.contains("Дуэли") ||
                    title.contains("Выбор типа") ||
                    title.contains("Статистика") ||
                    title.contains("Информация") ||
                    title.contains("Меню") ||
                    title.contains("Топ игроков")) {

                // Отменяем выбрасывание предмета
                event.setCancelled(true);
            }
        }

        // Проверка на "фантомные" предметы из меню
        ItemStack item = event.getItemDrop().getItemStack();
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            // Здесь можно добавить более точную проверку на предметы из меню
            // Например, проверить особые названия или лор, характерные для предметов меню

            // Для дополнительной безопасности можно удалить выброшенный предмет через тик
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!event.isCancelled() && !event.getItemDrop().isDead()) {
                        event.getItemDrop().remove();
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }
}