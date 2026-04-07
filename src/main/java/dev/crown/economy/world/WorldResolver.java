package dev.crown.economy.world;

import org.bukkit.World;

public interface WorldResolver {

    String getName();

    boolean isAvailable();

    boolean isKnownWorld(World world);

    boolean isKnownWorld(String worldName);

    default String getCanonicalWorldName(World world) {
        return world == null ? "" : world.getName();
    }
}
