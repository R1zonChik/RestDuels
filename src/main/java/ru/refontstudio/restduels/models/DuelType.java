package ru.refontstudio.restduels.models;

public enum DuelType {
    NORMAL("Обычная дуэль", "Инвентарь выпадает"),
    RANKED("Дуэль без потерь", "Инвентарь сохраняется"); // Переименовал второй режим

    private final String name;
    private final String description;

    DuelType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}