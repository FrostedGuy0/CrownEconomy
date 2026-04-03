package dev.crown.economy.config;

import dev.crown.economy.CrownEconomy;
import dev.crown.economy.auction.TransactionType;
import dev.crown.economy.gui.AuctionHouseGUI;
import dev.crown.economy.utils.GuiUtil;
import dev.crown.economy.utils.MessageUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    public ConfigManager(CrownEconomy plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        plugin.saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("gui.yml");

        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        this.gui = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "gui.yml"));
        loadListingLimits();
        loadCategories();
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
        if (!target.exists()) {
            plugin.saveResource(name, false);
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
