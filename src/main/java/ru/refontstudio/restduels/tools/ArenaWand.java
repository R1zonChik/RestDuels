package ru.refontstudio.restduels.tools;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.utils.ColorUtils;

public class ArenaWand implements Listener {
    private final RestDuels plugin;

    public ArenaWand(RestDuels plugin) {
        this.plugin = plugin;
    }

    public static ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.GOLDEN_AXE);
        wand.getItemMeta().setDisplayName(ColorUtils.colorize("&6&lArena Wand"));
        return wand;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Проверяем, что игрок использует жезл арены
        if (item == null || item.getType() != Material.GOLDEN_AXE || !item.hasItemMeta() ||
                !item.getItemMeta().hasDisplayName()) {
            return;
        }

        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (!displayName.equals("Arena Wand")) {
            return;
        }

        // Проверяем права
        if (!player.hasPermission("restduels.admin")) {
            return;
        }

        // Отменяем стандартное действие
        event.setCancelled(true);

        // Обрабатываем клик
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Первая точка
            plugin.getSelectionManager().setFirstPoint(player, event.getClickedBlock().getLocation());
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&aПервая точка установлена: &e" +
                            event.getClickedBlock().getX() + ", " +
                            event.getClickedBlock().getY() + ", " +
                            event.getClickedBlock().getZ()));
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Вторая точка
            plugin.getSelectionManager().setSecondPoint(player, event.getClickedBlock().getLocation());
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&aВторая точка установлена: &e" +
                            event.getClickedBlock().getX() + ", " +
                            event.getClickedBlock().getY() + ", " +
                            event.getClickedBlock().getZ()));

            // Если обе точки выбраны, показываем информацию о размере выделения
            if (plugin.getSelectionManager().hasSelection(player)) {
                showSelectionInfo(player);
            }
        }
    }

    private void showSelectionInfo(Player player) {
        // Получаем точки выделения
        Location first = plugin.getSelectionManager().getFirstPoint(player);
        Location second = plugin.getSelectionManager().getSecondPoint(player);

        // Проверяем, что точки в одном мире
        if (first.getWorld() != second.getWorld()) {
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cТочки находятся в разных мирах!"));
            return;
        }

        // Рассчитываем размер выделения
        int width = Math.abs(second.getBlockX() - first.getBlockX()) + 1;
        int height = Math.abs(second.getBlockY() - first.getBlockY()) + 1;
        int depth = Math.abs(second.getBlockZ() - first.getBlockZ()) + 1;
        int volume = width * height * depth;

        player.sendMessage(ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix") +
                        "&aРазмер выделения: &e" + width + "x" + height + "x" + depth +
                        " &a(&e" + volume + " &aблоков)"));

        player.sendMessage(ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix") +
                        "&eИспользуйте &6/arenawand create &eдля создания области восстановления"));
    }

    /**
     * Форматирует координаты в строку для отображения
     * @param loc Локация
     * @return Отформатированная строка координат
     */
    private String formatLocation(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}