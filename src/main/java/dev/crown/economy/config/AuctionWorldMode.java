package dev.crown.economy.config;

import java.util.Locale;

public enum AuctionWorldMode {
    GLOBAL,
    PER_WORLD,
    GROUPED;

    public static AuctionWorldMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return GLOBAL;
        }

        return switch (raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_')) {
            case "COMMON", "SHARED", "SHARED_GLOBAL", "GLOBAL" -> GLOBAL;
            case "PER_WORLD", "WORLD", "SEPARATE", "DIFFERENT" -> PER_WORLD;
            case "GROUPS", "GROUPED", "WORLD_GROUPS", "MULTIVERSE_GROUPS" -> GROUPED;
            default -> GLOBAL;
        };
    }
}
