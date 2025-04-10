package ru.refontstudio.restduels.managers;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.refontstudio.restduels.RestDuels;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionManager {
    private final RestDuels plugin;
    private final Map<UUID, Location> firstPoints = new HashMap<>();
    private final Map<UUID, Location> secondPoints = new HashMap<>();

    public SelectionManager(RestDuels plugin) {
        this.plugin = plugin;
    }

    public void setFirstPoint(Player player, Location location) {
        firstPoints.put(player.getUniqueId(), location);
    }

    public void setSecondPoint(Player player, Location location) {
        secondPoints.put(player.getUniqueId(), location);
    }

    public Location getFirstPoint(Player player) {
        return firstPoints.get(player.getUniqueId());
    }

    public Location getSecondPoint(Player player) {
        return secondPoints.get(player.getUniqueId());
    }

    public boolean hasSelection(Player player) {
        return firstPoints.containsKey(player.getUniqueId()) &&
                secondPoints.containsKey(player.getUniqueId());
    }

    public void clearSelection(Player player) {
        firstPoints.remove(player.getUniqueId());
        secondPoints.remove(player.getUniqueId());
    }
}