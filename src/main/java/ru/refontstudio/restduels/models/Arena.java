package ru.refontstudio.restduels.models;

import org.bukkit.Location;

public class Arena {
    private final String id;
    private final Location spawn1;
    private final Location spawn2;

    public Arena(String id, Location spawn1, Location spawn2) {
        this.id = id;
        this.spawn1 = spawn1;
        this.spawn2 = spawn2;
    }

    public String getId() {
        return id;
    }

    public Location getSpawn1() {
        return spawn1;
    }

    public Location getSpawn2() {
        return spawn2;
    }
}