package ru.refontstudio.restduels.models;

import java.io.Serializable;

/**
 * Класс для хранения статистики игрока
 */
public class PlayerStats implements Serializable {
    private static final long serialVersionUID = 1L;

    private int wins;
    private int deaths;
    private int draws;
    private int totalDuels;
    private long lastDuelTime;
    private String lastOpponent;

    /**
     * Конструктор по умолчанию
     */
    public PlayerStats() {
        this.wins = 0;
        this.deaths = 0;
        this.draws = 0;
        this.totalDuels = 0;
        this.lastDuelTime = 0;
        this.lastOpponent = "";
    }

    /**
     * Получает количество побед
     * @return Количество побед
     */
    public int getWins() {
        return wins;
    }

    /**
     * Устанавливает количество побед
     * @param wins Количество побед
     */
    public void setWins(int wins) {
        this.wins = wins;
    }

    /**
     * Получает количество смертей
     * @return Количество смертей
     */
    public int getDeaths() {
        return deaths;
    }

    /**
     * Устанавливает количество смертей
     * @param deaths Количество смертей
     */
    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }

    /**
     * Получает количество ничьих
     * @return Количество ничьих
     */
    public int getDraws() {
        return draws;
    }

    /**
     * Устанавливает количество ничьих
     * @param draws Количество ничьих
     */
    public void setDraws(int draws) {
        this.draws = draws;
    }

    /**
     * Получает общее количество дуэлей
     * @return Общее количество дуэлей
     */
    public int getTotalDuels() {
        return totalDuels;
    }

    /**
     * Устанавливает общее количество дуэлей
     * @param totalDuels Общее количество дуэлей
     */
    public void setTotalDuels(int totalDuels) {
        this.totalDuels = totalDuels;
    }

    /**
     * Получает время последней дуэли
     * @return Время последней дуэли в миллисекундах
     */
    public long getLastDuelTime() {
        return lastDuelTime;
    }

    /**
     * Устанавливает время последней дуэли
     * @param lastDuelTime Время последней дуэли в миллисекундах
     */
    public void setLastDuelTime(long lastDuelTime) {
        this.lastDuelTime = lastDuelTime;
    }

    /**
     * Получает имя последнего оппонента
     * @return Имя последнего оппонента
     */
    public String getLastOpponent() {
        return lastOpponent;
    }

    /**
     * Устанавливает имя последнего оппонента
     * @param lastOpponent Имя последнего оппонента
     */
    public void setLastOpponent(String lastOpponent) {
        this.lastOpponent = lastOpponent;
    }

    /**
     * Увеличивает количество побед на 1
     */
    public void incrementWins() {
        this.wins++;
        this.totalDuels++;
    }

    /**
     * Увеличивает количество смертей на 1
     */
    public void incrementDeaths() {
        this.deaths++;
        this.totalDuels++;
    }

    /**
     * Увеличивает количество ничьих на 1
     */
    public void incrementDraws() {
        this.draws++;
        this.totalDuels++;
    }

    /**
     * Получает соотношение убийств к смертям (K/D)
     * @return Соотношение K/D
     */
    public double getKDRatio() {
        if (deaths == 0) {
            return wins;
        }
        return (double) wins / deaths;
    }

    /**
     * Получает процент побед
     * @return Процент побед
     */
    public double getWinRate() {
        if (totalDuels == 0) {
            return 0;
        }
        return (double) wins / totalDuels * 100;
    }

    /**
     * Обновляет статистику после дуэли
     * @param won true, если игрок выиграл
     * @param opponentName имя оппонента
     */
    public void updateAfterDuel(boolean won, String opponentName) {
        if (won) {
            incrementWins();
        } else {
            incrementDeaths();
        }

        this.lastDuelTime = System.currentTimeMillis();
        this.lastOpponent = opponentName;
    }

    /**
     * Обновляет статистику после ничьей
     * @param opponentName имя оппонента
     */
    public void updateAfterDraw(String opponentName) {
        incrementDraws();
        this.lastDuelTime = System.currentTimeMillis();
        this.lastOpponent = opponentName;
    }

    /**
     * Сбрасывает всю статистику
     */
    public void reset() {
        this.wins = 0;
        this.deaths = 0;
        this.draws = 0;
        this.totalDuels = 0;
        this.lastDuelTime = 0;
        this.lastOpponent = "";
    }
}