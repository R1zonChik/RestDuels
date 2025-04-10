package ru.refontstudio.restduels.models;

import org.bukkit.Location;

public class RestoreArea {
    private final String name;
    private final Location first;
    private final Location second;

    public RestoreArea(String name, Location first, Location second) {
        this.name = name;
        this.first = first;
        this.second = second;
    }

    public String getName() {
        return name;
    }

    public Location getFirst() {
        return first;
    }

    public Location getSecond() {
        return second;
    }

    public String getWorldName() {
        return first.getWorld().getName();
    }

    public int getVolume() {
        int width = Math.abs(second.getBlockX() - first.getBlockX()) + 1;
        int height = Math.abs(second.getBlockY() - first.getBlockY()) + 1;
        int depth = Math.abs(second.getBlockZ() - first.getBlockZ()) + 1;
        return width * height * depth;
    }

    @Override
    public String toString() {
        return name + " [" +
                first.getWorld().getName() + ": " +
                first.getBlockX() + "," + first.getBlockY() + "," + first.getBlockZ() + " -> " +
                second.getBlockX() + "," + second.getBlockY() + "," + second.getBlockZ() + "]";
    }
}