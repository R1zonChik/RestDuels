package ru.refontstudio.restduels.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.models.Duel;
import ru.refontstudio.restduels.models.DuelType;
import ru.refontstudio.restduels.utils.ColorUtils;

import java.util.UUID;

public class DuelListener implements Listener {
    private final RestDuels plugin;

    public DuelListener(RestDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Проверяем, является ли атакующий игроком
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();

            // Если атакующий находится в состоянии отсчета, отменяем урон
            if (plugin.getDuelManager().isPlayerInCountdown(attacker.getUniqueId())) {
                event.setCancelled(true);
                attacker.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                "&cВы не можете атаковать во время подготовки к дуэли!"));
                return;
            }
        }

        // Проверяем, является ли цель игроком
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();

            // Если цель находится в состоянии отсчета, отменяем урон
            if (plugin.getDuelManager().isPlayerInCountdown(victim.getUniqueId())) {
                event.setCancelled(true);

                // Если атакующий - игрок, отправляем ему сообщение
                if (event.getDamager() instanceof Player) {
                    Player attacker = (Player) event.getDamager();
                    attacker.sendMessage(ColorUtils.colorize(
                            plugin.getConfig().getString("messages.prefix") +
                                    "&cВы не можете атаковать игрока во время подготовки к дуэли!"));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        if (plugin.getDuelManager().isPlayerInDuel(playerId)) {
            Duel duel = plugin.getDuelManager().getPlayerDuel(playerId);

            // Определяем победителя (тот, кто не умер)
            UUID winnerId = duel.getPlayer1Id().equals(playerId) ? duel.getPlayer2Id() : duel.getPlayer1Id();
            Player winner = Bukkit.getPlayer(winnerId);

//            // Отправляем сообщение о победе
//            if (winner != null && winner.isOnline()) {
//                winner.sendMessage(ColorUtils.colorize(
//                        plugin.getConfig().getString("messages.prefix") +
//                                "&aВы победили! Противник был убит."));
//            }

            // Для режима "Дуэль без потерь" отменяем выпадение предметов
            if (duel.getType() == DuelType.RANKED) {
                event.setKeepInventory(true);
                event.getDrops().clear();
            }

            // Завершаем дуэль с победителем
            plugin.getDuelManager().endDuel(duel, winnerId);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        // Если игрок включает полет и находится в дуэли, отменяем событие
        if (event.isFlying() && plugin.getDuelManager().isPlayerInDuel(player.getUniqueId())) {
            event.setCancelled(true);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig().getString("messages.prefix") +
                            "&cПолет запрещен во время дуэли!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Если игрок в дуэли, завершаем дуэль и объявляем победителем противника
        if (plugin.getDuelManager().isPlayerInDuel(playerId)) {
            Duel duel = plugin.getDuelManager().getPlayerDuel(playerId);

            // Определяем противника
            UUID opponentId = duel.getPlayer1Id().equals(playerId) ? duel.getPlayer2Id() : duel.getPlayer1Id();
            Player opponent = Bukkit.getPlayer(opponentId);

            // Объявляем победителем противника
            if (opponent != null && opponent.isOnline()) {
                opponent.sendMessage(ColorUtils.colorize(
                        plugin.getConfig().getString("messages.prefix") +
                                plugin.getConfig().getString("messages.opponent-quit")));
            }

            plugin.getDuelManager().endDuel(duel, opponentId);
        }
        // Если игрок заморожен (ожидает начала дуэли)
        else if (plugin.getDuelManager().isPlayerFrozen(playerId)) {
            plugin.getDuelManager().unfreezeAndCancelDuel(player);
        }
        // Если игрок в очереди
        else {
            plugin.getDuelManager().removeFromQueues(playerId);
        }
    }
}