package ru.refontstudio.restduels.models;

import ru.refontstudio.restduels.RestDuels;

public enum DuelType {
    NORMAL("Обычная дуэль", "Инвентарь выпадает"),
    RANKED("Дуэль без потерь", "Инвентарь сохраняется"),
    CLASSIC("Классическая дуэль", "Инвентарь выпадает"); // Добавлен новый тип

    private final String name;
    private final String description;

    DuelType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    /**
     * Получает название типа дуэли из конфига
     * @param plugin Экземпляр плагина для доступа к конфигу
     * @return Название типа дуэли из конфига или стандартное название
     */
    public String getConfigName(RestDuels plugin) {
        return plugin.getConfig().getString("duel_types." + this.name().toLowerCase() + ".name", this.getName());
    }

    public String getDescription() {
        return description;
    }
}