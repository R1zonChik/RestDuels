package ru.refontstudio.restduels.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.models.Duel;
import ru.refontstudio.restduels.utils.ColorUtils;

public class DuelBuildListener implements Listener {
    private final RestDuels plugin;

    public DuelBuildListener(RestDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockPlace(BlockPlaceEvent event) {
        // Проверяем, включено ли строительство в конфиге
        if (!plugin.getConfig().getBoolean("building.allow-building", true)) {
            return;
        }

        Player player = event.getPlayer();

        // Проверяем, участвует ли игрок в дуэли
        if (!plugin.getDuelManager().isPlayerInDuel(player.getUniqueId())) {
            return;
        }

        Duel duel = plugin.getDuelManager().getPlayerDuel(player.getUniqueId());

        // Проверка на лимит блоков
        int maxBlocks = plugin.getConfig().getInt("building.max-blocks-per-player", 0);
        if (maxBlocks > 0) {
            // Считаем блоки, построенные этим игроком
            long playerBlockCount = duel.getPlayerPlacedBlocks().stream()
                    .filter(loc -> loc.getWorld().equals(event.getBlock().getWorld()))
                    .count();

            if (playerBlockCount >= maxBlocks) {
                event.setCancelled(true);
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cВы достигли лимита построенных блоков (" + maxBlocks + ")!"));
                return;
            }
        }

        // Проверяем, находится ли блок в области арены
        Location blockLocation = event.getBlock().getLocation();
        boolean isInArenaArea = false;

        for (String areaName : plugin.getRestoreManager().getAreasForArena(duel.getArena().getId())) {
            if (plugin.getRestoreManager().isLocationInArea(areaName, blockLocation)) {
                isInArenaArea = true;
                break;
            }
        }

        if (isInArenaArea) {
            // Добавляем блок в список построенных игроками
            duel.addPlayerPlacedBlock(blockLocation);

            // Разрешаем строительство
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Проверяем, включено ли строительство в конфиге
        if (!plugin.getConfig().getBoolean("building.allow-building", true)) {
            return;
        }

        // Проверяем, включено ли ограничение на ломание только построенных блоков
        boolean onlyPlayerBlocks = plugin.getConfig().getBoolean("building.allow-breaking-only-player-blocks", true);

        // Проверяем, участвует ли игрок в дуэли
        if (!plugin.getDuelManager().isPlayerInDuel(player.getUniqueId())) {
            return;
        }

        Duel duel = plugin.getDuelManager().getPlayerDuel(player.getUniqueId());

        // Проверяем, находится ли блок в области арены
        Location blockLocation = event.getBlock().getLocation();
        boolean isInArenaArea = false;

        for (String areaName : plugin.getRestoreManager().getAreasForArena(duel.getArena().getId())) {
            if (plugin.getRestoreManager().isLocationInArea(areaName, blockLocation)) {
                isInArenaArea = true;
                break;
            }
        }

        if (isInArenaArea) {
            // Проверяем, является ли блок построенным игроком
            if (duel.isPlayerPlacedBlock(blockLocation)) {
                // Разрешаем ломать блоки, построенные игроками
                event.setCancelled(false);
            } else {
                // Запрещаем ломать оригинальные блоки арены
                event.setCancelled(true);
                player.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cВы можете ломать только блоки, построенные во время дуэли!"));
            }
        }
    }
}