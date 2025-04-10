package ru.refontstudio.restduels.models;

import java.util.UUID;

public class TopPlayerEntry {
    private final UUID playerId;
    private final String playerName;
    private final int wins;
    private final int deaths;
    private final double kd;
    private final double winRate;

    public TopPlayerEntry(UUID playerId, String playerName, int wins, int deaths, double kd, double winRate) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.wins = wins;
        this.deaths = deaths;
        this.kd = kd;
        this.winRate = winRate;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getWins() {
        return wins;
    }

    public int getDeaths() {
        return deaths;
    }

    public double getKd() {
        return kd;
    }

    public double getWinRate() {
        return winRate;
    }
}