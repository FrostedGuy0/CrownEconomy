package dev.crown.economy.config;

public record WorldAuctionSettings(boolean explicit, boolean enabled, String group, String displayName) {

    public WorldAuctionSettings {
        group = group == null ? "" : group.trim();
        displayName = displayName == null ? "" : displayName.trim();
    }

    public static WorldAuctionSettings implicit(boolean enabled) {
        return new WorldAuctionSettings(false, enabled, "", "");
    }
}
