package dev.crown.economy.config;

import org.bukkit.entity.Player;

public record ListingLimitRule(String permission, int limit) {

    public boolean matches(Player player) {
        return "default".equalsIgnoreCase(permission) || player.hasPermission(permission);
    }
}
