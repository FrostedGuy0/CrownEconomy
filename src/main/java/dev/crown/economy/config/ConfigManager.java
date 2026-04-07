package dev.crown.economy.config;

import dev.crown.economy.CrownEconomy;
import dev.crown.economy.auction.TransactionType;
import dev.crown.economy.gui.AuctionHouseGUI;
import dev.crown.economy.utils.GuiUtil;
import dev.crown.economy.utils.MessageUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ConfigManager {

    private final CrownEconomy plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration gui;
    private final List<ListingLimitRule> listingLimits = new ArrayList<>();
    private final List<CategoryDefinition> categories = new ArrayList<>();
    private final Map<String, WorldAuctionSettings> worldSettings = new HashMap<>();
    private AuctionWorldMode auctionWorldMode = AuctionWorldMode.GLOBAL;
    private WorldAuctionSettings defaultWorldSettings = WorldAuctionSettings.implicit(true);
    private boolean configuredWorldGroupsPresent;

    public ConfigManager(CrownEconomy plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        plugin.saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("gui.yml");
        cleanupLegacyAuctionTemplateFiles();
        cleanupLegacyAuctionDemoFiles();
        refreshLegacyAuctionDemoFile("auction-houses/per-world/demo.yml");
        refreshLegacyAuctionDemoFile("auction-houses/grouped/demo.yml");
        saveResourceIfMissing("auction-houses/global.yml");
        saveResourceIfMissing("auction-houses/per-world/demo.yml");
        saveResourceIfMissing("auction-houses/grouped/demo.yml");

        plugin.reloadConfig();
        config = plugin.getConfig();
        messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        gui = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "gui.yml"));
        loadListingLimits();
        loadCategories();
        loadWorldSettings();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public FileConfiguration getGui() {
        return gui;
    }

    public String getPrefix() {
        String prefix = config.getString("general.prefix", "");
        if (prefix == null || prefix.trim().isEmpty()) {
            return "";
        }
        return MessageUtil.color(prefix);
    }

    public String getCurrencySymbol() {
        return config.getString("general.currency-symbol", "$");
    }

    public int getDecimalPlaces() {
        return Math.max(0, config.getInt("general.decimal-places", 2));
    }

    public String formatPrice(double price) {
        return getCurrencySymbol() + MessageUtil.formatPrice(price, getDecimalPlaces());
    }

    public String getDateFormat() {
        return config.getString("general.date-format", "MM/dd/yyyy HH:mm");
    }

    public boolean isAHEnabled() {
        return config.getBoolean("auction-house.enabled", true);
    }

    public AuctionWorldMode getAuctionWorldMode() {
        return auctionWorldMode;
    }

    public WorldAuctionSettings getDefaultWorldSettings() {
        return defaultWorldSettings;
    }

    public WorldAuctionSettings getWorldSettings(String worldName) {
        String normalizedWorld = AuctionWorldScope.normalizeIdentifier(worldName);
        return worldSettings.getOrDefault(normalizedWorld, defaultWorldSettings);
    }

    public boolean isWorldConfigured(String worldName) {
        return worldSettings.containsKey(AuctionWorldScope.normalizeIdentifier(worldName));
    }

    public boolean isWorldEnabledByConfig(String worldName) {
        return getWorldSettings(worldName).enabled();
    }

    public String getConfiguredGroup(String worldName) {
        return getWorldSettings(worldName).group();
    }

    public String getConfiguredWorldDisplayName(String worldName) {
        WorldAuctionSettings settings = getWorldSettings(worldName);
        if (!settings.displayName().isBlank()) {
            return settings.displayName();
        }
        if (worldName != null && !worldName.isBlank()) {
            return worldName;
        }
        return getGuiLabel("main.labels.unknown-world", "Unknown World");
    }

    public boolean hasConfiguredWorldGroups() {
        return configuredWorldGroupsPresent;
    }

    public Set<String> getConfiguredWorldNames() {
        return Set.copyOf(worldSettings.keySet());
    }

    public Map<String, String> getAuctionScopePlaceholders(AuctionWorldScope scope) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{scope}", scope.displayName());
        placeholders.put("{scope-key}", scope.key());
        placeholders.put("{world}", scope.worldName());
        return placeholders;
    }

    public String getScopeDisplayName(String scopeKey) {
        String normalized = AuctionWorldScope.normalizeScopeKey(scopeKey);
        if (AuctionWorldScope.isGlobalScope(normalized)) {
            return getGuiLabel("main.labels.global-scope", "Global");
        }
        if (normalized.startsWith(AuctionWorldScope.GROUP_SCOPE_PREFIX)) {
            return prettifyIdentifier(normalized.substring(AuctionWorldScope.GROUP_SCOPE_PREFIX.length()));
        }
        if (normalized.startsWith(AuctionWorldScope.WORLD_SCOPE_PREFIX)) {
            String worldName = normalized.substring(AuctionWorldScope.WORLD_SCOPE_PREFIX.length());
            return getConfiguredWorldDisplayName(worldName);
        }
        return prettifyIdentifier(normalized);
    }

    public int getGlobalMaxListings() {
        return Math.max(0, config.getInt("auction-house.max-auction-house-listings", 0));
    }

    public int getTransactionHistoryLimit() {
        return Math.max(0, config.getInt("auction-house.max-transaction-history", 0));
    }

    public double getMaxPrice() {
        return config.getDouble("auction-house.max-price", 1_000_000.0);
    }

    public double getMinPrice() {
        return config.getDouble("auction-house.min-price", 1.0);
    }

    public int getDefaultDurationHours() {
        return config.getInt("auction-house.default-duration-hours", 0);
    }

    public int getMaxDurationHours() {
        return config.getInt("auction-house.max-duration-hours", 168);
    }

    public double getTaxRate() {
        return config.getDouble("auction-house.tax-rate", 5.0);
    }

    public int getExpiryCheckSeconds() {
        return Math.max(10, config.getInt("auction-house.expiry-check-seconds", 60));
    }

    public int getAutoSaveSeconds() {
        return Math.max(30, config.getInt("auction-house.auto-save-seconds", 60));
    }

    public int getReturnCheckSeconds() {
        return Math.max(10, config.getInt("auction-house.return-check-seconds", 60));
    }

    public int getMaxListings(Player player) {
        for (ListingLimitRule rule : listingLimits) {
            if (rule.matches(player)) {
                return rule.limit();
            }
        }
        return 5;
    }

    public boolean isCategoryEnabled() {
        return config.getBoolean("auction-house.categories.enabled", true);
    }

    public List<CategoryDefinition> getCategories() {
        return List.copyOf(categories);
    }

    public boolean matchesCategory(String categoryName, ItemStack item) {
        if (categoryName == null || categoryName.isBlank()) {
            return true;
        }
        for (CategoryDefinition category : categories) {
            if (category.name().equalsIgnoreCase(categoryName)) {
                return category.matches(item);
            }
        }
        return false;
    }

    public List<String> getBlacklistedMaterials() {
        return config.getStringList("auction-house.blacklisted-materials");
    }

    public List<Integer> getMainListingSlots() {
        List<Integer> slots = new ArrayList<>(gui.getIntegerList("main.listing-slots"));
        if (!slots.isEmpty()) {
            return slots;
        }
        return buildListingSlots(gui.getInt("main.listing-start-slot", 0), getMainSize());
    }

    public int getMainEmptyStateSlot() {
        return gui.getInt("main.empty-state-slot", 22);
    }

    public List<Integer> getMyListingSlots() {
        List<Integer> slots = new ArrayList<>(gui.getIntegerList("my-listings.listing-slots"));
        if (!slots.isEmpty()) {
            return slots;
        }
        return buildListingSlots(gui.getInt("my-listings.listing-start-slot", 0), getMyListingsSize());
    }

    public int getMyListingsEmptyStateSlot() {
        return gui.getInt("my-listings.empty-state-slot", 13);
    }

    public List<Integer> getCategorySlots() {
        List<Integer> slots = new ArrayList<>(gui.getIntegerList("categories.category-slots"));
        if (!slots.isEmpty()) {
            return slots;
        }
        return List.of(10, 11, 12, 13, 14, 15, 16, 19);
    }

    public String getMainTitle() {
        return MessageUtil.color(gui.getString("main.title", "&8Auction House"));
    }

    public int getMainSize() {
        return sanitizeSize(gui.getInt("main.size", 54));
    }

    public String getCategoryTitle() {
        return MessageUtil.color(gui.getString("categories.title", "&8Categories"));
    }

    public int getCategorySize() {
        return sanitizeSize(gui.getInt("categories.size", 27));
    }

    public String getConfirmTitle() {
        return MessageUtil.color(gui.getString("confirm.title", "&8Confirm Buy"));
    }

    public int getConfirmSize() {
        return sanitizeSize(gui.getInt("confirm.size", 27));
    }

    public String getMyListingsTitle() {
        return MessageUtil.color(gui.getString("my-listings.title", "&8My Listings"));
    }

    public int getMyListingsSize() {
        return sanitizeSize(gui.getInt("my-listings.size", 27));
    }

    public int getTransactionsSize() {
        return sanitizeSize(gui.getInt("transactions.size", 54));
    }

    public int getButtonSlot(String menu, String button) {
        return gui.getInt(menu + ".buttons." + button + ".slot", -1);
    }

    public String getTransactionsTitle() {
        return MessageUtil.color(gui.getString("transactions.title", "&8Transactions"));
    }

    public int getTransactionsEmptyStateSlot() {
        return gui.getInt("transactions.empty-state-slot", 13);
    }

    public List<Integer> getTransactionSlots() {
        List<Integer> slots = new ArrayList<>(gui.getIntegerList("transactions.listing-slots"));
        if (!slots.isEmpty()) {
            return slots;
        }
        return buildListingSlots(gui.getInt("transactions.listing-start-slot", 0), getTransactionsSize());
    }

    public String getTransactionLabel(TransactionType type) {
        return switch (type) {
            case LISTED -> getGuiLabel("transactions.labels.listed", "Listed");
            case PURCHASED -> getGuiLabel("transactions.labels.bought", "Bought");
            case SOLD -> getGuiLabel("transactions.labels.sold", "Sold");
            case CANCELLED -> getGuiLabel("transactions.labels.cancelled", "Cancelled");
            case EXPIRED -> getGuiLabel("transactions.labels.expired", "Expired");
        };
    }

    public String getSortLabel(AuctionHouseGUI.SortMode mode) {
        return switch (mode) {
            case PRICE_ASC -> getGuiLabel("main.sort-labels.price-asc", "Low --> High");
            case PRICE_DESC -> getGuiLabel("main.sort-labels.price-desc", "High --> Low");
            case TIME_ASC -> getGuiLabel("main.sort-labels.time-asc", "Soon --> New");
            case TIME_DESC -> getGuiLabel("main.sort-labels.time-desc", "New --> Soon");
        };
    }

    public String formatTimeLeft(long expiresAt) {
        if (expiresAt < 0L) {
            return getGuiLabel("main.labels.never", "Never");
        }

        long ms = Math.max(0L, expiresAt - System.currentTimeMillis());
        long hours = ms / 3_600_000L;
        long minutes = (ms % 3_600_000L) / 60_000L;
        String hourSuffix = getGuiLabel("main.labels.time-hours-suffix", "h");
        String minuteSuffix = getGuiLabel("main.labels.time-minutes-suffix", "m");

        if (hours > 0) {
            return hours + hourSuffix + " " + minutes + minuteSuffix;
        }
        return minutes + minuteSuffix;
    }

    public String getGuiLabel(String path, String fallback) {
        return MessageUtil.color(gui.getString(path, fallback));
    }

    public ItemStack createGuiItem(String path, Map<String, String> placeholders) {
        return GuiUtil.fromSection(gui, path, placeholders);
    }

    public String getMessage(String path) {
        String raw = messages.getString(path, "&cMessage not found: " + path);
        String prefix = getPrefix();
        if (prefix.isEmpty()) {
            return MessageUtil.color(raw);
        }
        return prefix + MessageUtil.color(raw);
    }

    public String getMessage(String path, Map<String, String> placeholders) {
        String value = getMessage(path);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                value = value.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return MessageUtil.color(value);
    }

    public String getMessageRaw(String path) {
        return MessageUtil.color(messages.getString(path, "&cMessage not found: " + path));
    }

    public String formatTimestamp(long timestamp) {
        try {
            return DateTimeFormatter.ofPattern(getDateFormat())
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(timestamp));
        } catch (Exception ignored) {
            return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime().toString();
        }
    }

    private void saveResourceIfMissing(String name) {
        File target = new File(plugin.getDataFolder(), name);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!target.exists()) {
            plugin.saveResource(name, false);
        }
    }

    private void cleanupLegacyAuctionTemplateFiles() {
        File legacyDirectory = new File(plugin.getDataFolder(), "configs");
        if (!legacyDirectory.exists() || !legacyDirectory.isDirectory()) {
            return;
        }

        deleteIfExists(new File(legacyDirectory, "README.txt"));
        deleteIfExists(new File(legacyDirectory, "global-config.yml"));
        deleteIfExists(new File(legacyDirectory, "per-world-config.disabled.yml"));
        deleteIfExists(new File(legacyDirectory, "grouped-config.disabled.yml"));

        File[] remainingFiles = legacyDirectory.listFiles();
        if (remainingFiles != null && remainingFiles.length == 0) {
            deleteIfExists(legacyDirectory);
        }
    }

    private void cleanupLegacyAuctionDemoFiles() {
        deleteIfExists(new File(plugin.getDataFolder(), "auction-houses/per-world/demo.disabled.yml"));
        deleteIfExists(new File(plugin.getDataFolder(), "auction-houses/grouped/demo.disabled.yml"));
    }

    private void refreshLegacyAuctionDemoFile(String resourcePath) {
        File target = new File(plugin.getDataFolder(), resourcePath);
        if (!target.exists()) {
            return;
        }

        YamlConfiguration existing = YamlConfiguration.loadConfiguration(target);
        if (existing.contains("options.enabled")) {
            return;
        }

        deleteIfExists(target);
    }

    private void deleteIfExists(File file) {
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Failed to delete legacy CrownEconomy file: " + file.getAbsolutePath());
        }
    }

    private void loadListingLimits() {
        listingLimits.clear();
        List<Map<?, ?>> entries = config.getMapList("auction-house.listing-limits");
        for (Map<?, ?> entry : entries) {
            String permission = String.valueOf(entry.containsKey("permission") ? entry.get("permission") : "default");
            int limit = parseInt(entry.get("limit"), 5);
            if (limit > 0) {
                listingLimits.add(new ListingLimitRule(permission, limit));
            }
        }
        if (listingLimits.isEmpty()) {
            listingLimits.add(new ListingLimitRule("default", 5));
        }
    }

    private void loadCategories() {
        categories.clear();
        List<Map<?, ?>> entries = config.getMapList("auction-house.categories.list");
        for (Map<?, ?> entry : entries) {
            String name = String.valueOf(entry.containsKey("name") ? entry.get("name") : "Unknown");
            Material icon = parseMaterial(String.valueOf(entry.containsKey("icon") ? entry.get("icon") : "COMPASS"), Material.COMPASS);
            boolean matchAll = Boolean.parseBoolean(String.valueOf(entry.containsKey("match-all") ? entry.get("match-all") : false));

            Set<Material> materials = new LinkedHashSet<>();
            Object rawMaterials = entry.get("materials");
            if (rawMaterials instanceof List<?> list) {
                for (Object value : list) {
                    materials.add(parseMaterial(String.valueOf(value), Material.AIR));
                }
            }

            Set<String> keywords = new LinkedHashSet<>();
            Object rawKeywords = entry.get("keywords");
            if (rawKeywords instanceof List<?> list) {
                for (Object value : list) {
                    String keyword = String.valueOf(value).trim();
                    if (!keyword.isEmpty()) {
                        keywords.add(keyword.toUpperCase(Locale.ROOT));
                    }
                }
            }

            categories.add(new CategoryDefinition(name, icon, matchAll, materials, keywords));
        }
        if (categories.stream().noneMatch(category -> category.matchAll() || category.name().equalsIgnoreCase("All"))) {
            categories.add(0, new CategoryDefinition("All", Material.COMPASS, true, Set.of(), Set.of()));
        }
    }

    private void loadWorldSettings() {
        worldSettings.clear();
        configuredWorldGroupsPresent = false;
        auctionWorldMode = AuctionWorldMode.from(firstNonBlank(
                config.getString("auction-house.mode"),
                config.getString("auction-house.world-scopes.mode"),
                "GLOBAL"
        ));

        boolean defaultEnabled = config.getBoolean("auction-house.default-world-settings.enabled",
                config.getBoolean("auction-house.world-scopes.default-enabled", true));
        defaultWorldSettings = WorldAuctionSettings.implicit(defaultEnabled);

        ConfigurationSection worldsSection = config.getConfigurationSection("auction-house.worlds");
        if (worldsSection == null) {
            worldsSection = config.getConfigurationSection("auction-house.world-scopes.worlds");
        }
        if (worldsSection == null) {
            return;
        }

        for (String worldKey : worldsSection.getKeys(false)) {
            String normalizedWorld = AuctionWorldScope.normalizeIdentifier(worldKey);
            if (normalizedWorld.isEmpty()) {
                continue;
            }

            boolean enabled = worldsSection.getBoolean(worldKey + ".enabled", defaultWorldSettings.enabled());
            String group = worldsSection.getString(worldKey + ".group", "");
            String displayName = worldsSection.getString(worldKey + ".display-name", worldKey);
            if (group != null && !group.isBlank()) {
                configuredWorldGroupsPresent = true;
            }
            worldSettings.put(normalizedWorld, new WorldAuctionSettings(true, enabled, group, displayName));
        }

        for (String worldName : config.getStringList("auction-house.world-scopes.disabled-worlds")) {
            String normalizedWorld = AuctionWorldScope.normalizeIdentifier(worldName);
            if (!normalizedWorld.isEmpty() && !worldSettings.containsKey(normalizedWorld)) {
                worldSettings.put(normalizedWorld, new WorldAuctionSettings(true, false, "", worldName));
            }
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private int parseInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Material parseMaterial(String name, Material fallback) {
        try {
            return Material.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String prettifyIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }

        String normalized = value.replace('-', ' ').replace('_', ' ').trim();
        if (normalized.isEmpty()) {
            return "Unknown";
        }

        String[] parts = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? "Unknown" : builder.toString();
    }

    private List<Integer> buildListingSlots(int startSlot, int size) {
        int upperBound = Math.max(0, size - 9);
        if (upperBound == 0) {
            return List.of();
        }

        int firstSlot = Math.max(0, Math.min(startSlot, upperBound - 1));
        List<Integer> slots = new ArrayList<>();
        for (int slot = firstSlot; slot < upperBound; slot++) {
            slots.add(slot);
        }
        return slots;
    }

    private int sanitizeSize(int size) {
        int clamped = Math.max(9, Math.min(54, size));
        return clamped - (clamped % 9);
    }
}
