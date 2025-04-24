package ru.refontstudio.restduels.models;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Duel {
    private final UUID player1Id;
    private final UUID player2Id;
    private final DuelType type;
    private final Arena arena;
    private BukkitTask duelTask;
    private BukkitTask timerTask;
    private final Set<Location> playerPlacedBlocks = new HashSet<>();

    public Duel(UUID player1Id, UUID player2Id, DuelType type, Arena arena) {
        this.player1Id = player1Id;
        this.player2Id = player2Id;
        this.type = type;
        this.arena = arena;
        // Метод setInUse был удален из класса Arena, поэтому эта строка больше не нужна
        // this.arena.setInUse(true);
    }

    /**
     * Проверяет, началась ли дуэль
     * @return true, если дуэль началась
     */
    public boolean hasStarted() {
        // Предполагаем, что дуэль началась, если есть задача дуэли
        return duelTask != null;
    }

    // Геттер и сеттер для timerTask
    public BukkitTask getTimerTask() {
        return timerTask;
    }

    public void setTimerTask(BukkitTask timerTask) {
        this.timerTask = timerTask;
    }

    public void addPlayerPlacedBlock(Location location) {
        playerPlacedBlocks.add(location);
    }

    public boolean isPlayerPlacedBlock(Location location) {
        return playerPlacedBlocks.contains(location);
    }

    public Set<Location> getPlayerPlacedBlocks() {
        return Collections.unmodifiableSet(playerPlacedBlocks);
    }

    public UUID getPlayer1Id() {
        return player1Id;
    }

    public UUID getPlayer2Id() {
        return player2Id;
    }

    public DuelType getType() {
        return type;
    }

    public Arena getArena() {
        return arena;
    }

    public BukkitTask getDuelTask() {
        return duelTask;
    }

    public void setDuelTask(BukkitTask duelTask) {
        this.duelTask = duelTask;
    }
}