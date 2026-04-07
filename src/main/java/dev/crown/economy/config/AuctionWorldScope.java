package dev.crown.economy.config;

import java.util.Locale;

public record AuctionWorldScope(String key, String displayName, String worldName, boolean enabled) {

    public static final String GLOBAL_SCOPE_KEY = "global";
    public static final String GROUP_SCOPE_PREFIX = "group:";
    public static final String WORLD_SCOPE_PREFIX = "world:";

    public AuctionWorldScope {
        key = normalizeScopeKey(key);
        displayName = displayName == null || displayName.isBlank() ? "Global" : displayName.trim();
        worldName = worldName == null ? "" : worldName;
    }

    public boolean isGlobal() {
        return isGlobalScope(key);
    }

    public boolean isGroupScope() {
        return key.startsWith(GROUP_SCOPE_PREFIX);
    }

    public boolean isWorldScope() {
        return key.startsWith(WORLD_SCOPE_PREFIX);
    }

    public String scopeIdentifier() {
        if (isGlobal()) {
            return GLOBAL_SCOPE_KEY;
        }
        if (isGroupScope()) {
            return key.substring(GROUP_SCOPE_PREFIX.length());
        }
        if (isWorldScope()) {
            return key.substring(WORLD_SCOPE_PREFIX.length());
        }
        return key;
    }

    public String sanitizedFileComponent() {
        String base = scopeIdentifier();
        if (base.isBlank()) {
            base = GLOBAL_SCOPE_KEY;
        }
        return base.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public static boolean isGlobalScope(String scopeKey) {
        return GLOBAL_SCOPE_KEY.equals(normalizeScopeKey(scopeKey));
    }

    public static String worldScopeKey(String worldName) {
        return WORLD_SCOPE_PREFIX + normalizeIdentifier(worldName);
    }

    public static String groupScopeKey(String groupName) {
        return GROUP_SCOPE_PREFIX + normalizeIdentifier(groupName);
    }

    public static String normalizeScopeKey(String scopeKey) {
        if (scopeKey == null || scopeKey.isBlank()) {
            return GLOBAL_SCOPE_KEY;
        }

        String normalized = scopeKey.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("common") ? GLOBAL_SCOPE_KEY : normalized;
    }

    public static String normalizeIdentifier(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
