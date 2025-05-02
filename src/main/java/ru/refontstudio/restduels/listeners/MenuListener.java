package ru.refontstudio.restduels.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.gui.DuelMenu;
import ru.refontstudio.restduels.models.DuelType;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.util.UUID;

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
            UUID playerId = player.getUniqueId();
            DuelMenu menu = (DuelMenu) holder;
            int slot = event.getSlot();

            // Получаем действие для этого слота
            String action = menu.getActionForSlot(slot);
            if (action == null) {
                return; // Нет действия для этого слота
            }

            // ДОБАВЛЕНО: Проверяем, находится ли игрок в отсчете перед дуэлью
            if ((action.equals("normal_duel") || action.equals("ranked_duel") || action.equals("classic_duel"))
                    && !event.isShiftClick()) {

                // Проверяем, находится ли игрок в отсчете перед дуэлью
                if (plugin.getDuelManager().isPlayerInCountdown(playerId)) {
                    player.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    plugin.getConfig().getString("messages.already-in-countdown",
                                            "&cВы не можете начать новый поиск дуэли, так как уже находитесь в подготовке к дуэли!")));
                    player.closeInventory();
                    return;
                }

                // Проверяем, находится ли игрок уже в поиске дуэли
                if (plugin.getDuelManager().isPlayerInQueue(playerId)) {
                    player.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    plugin.getConfig().getString("messages.already-in-search",
                                            "&cВы уже находитесь в поиске дуэли! Отмените текущий поиск с помощью /duel cancel.")));
                    player.closeInventory();
                    return;
                }

                // Проверяем, находится ли игрок в активной дуэли
                if (plugin.getDuelManager().isPlayerInDuel(playerId)) {
                    player.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    plugin.getConfig().getString("messages.already-in-duel",
                                            "&cВы не можете начать поиск дуэли, так как уже участвуете в дуэли!")));
                    player.closeInventory();
                    return;
                }
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
            } else if (action.equals("classic_duel")) {
                player.closeInventory();

                if (event.isShiftClick()) {
                    // Отмена поиска
                    plugin.getDuelManager().cancelDuel(player);
                } else {
                    // Начало поиска
                    plugin.getDuelManager().queuePlayer(player, DuelType.CLASSIC);
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