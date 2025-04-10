package ru.refontstudio.restduels.models;

public class PlayerStats {
    private int wins;
    private int deaths;

    public PlayerStats() {
        this.wins = 0;
        this.deaths = 0;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }

    public void incrementWins() {
        this.wins++;
    }

    public void incrementDeaths() {
        this.deaths++;
    }
}