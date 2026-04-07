package dev.crown.economy.world;

import org.bukkit.Bukkit;
import org.bukkit.World;

public class BukkitWorldResolver implements WorldResolver {

    @Override
    public String getName() {
        return "Bukkit";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isKnownWorld(World world) {
        return world != null;
    }

    @Override
    public boolean isKnownWorld(String worldName) {
        return worldName != null && Bukkit.getWorld(worldName) != null;
    }
}
