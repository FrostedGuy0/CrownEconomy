package dev.nexus.economy.config;

import dev.nexus.economy.NexusEconomy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class ConfigManager {

    private final NexusEconomy plugin;
    private FileConfiguration config;

    public ConfigManager(NexusEconomy plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    // ── General ──────────────────────────────────────────────
    public String getPrefix() {
        return color(config.getString("general.prefix", "&8[&bNexus&3Economy&8] &r"));
    }

    public String getCurrencySymbol() {
        return config.getString("general.currency-symbol", "$");
    }

    public int getDecimalPlaces() {
        return config.getInt("general.decimal-places", 2);
    }

    // ── Auction House ────────────────────────────────────────
    public boolean isAHEnabled() {
        return config.getBoolean("auction-house.enabled", true);
    }

    public String getAHTitle() {
        return color(config.getString("auction-house.gui-title", "&8» &bAuction House &8«"));
    }

    public int getItemsPerPage() {
        return config.getInt("auction-house.items-per-page", 45);
    }

    public double getMaxPrice() {
        return config.getDouble("auction-house.listing.max-price", 1000000.0);
    }

    public double getMinPrice() {
        return config.getDouble("auction-house.listing.min-price", 1.0);
    }

    public int getDefaultDuration() {
        return config.getInt("auction-house.listing.default-duration", 48);
    }

    public double getTaxRate() {
        return config.getDouble("auction-house.listing.tax-rate", 5.0);
    }

    public boolean isReturnExpired() {
        return config.getBoolean("auction-house.listing.return-expired", true);
    }

    public int getExpiredBinDuration() {
        return config.getInt("auction-house.listing.expired-bin-duration", 72);
    }

    public int getMaxListings(String group) {
        return switch (group.toLowerCase()) {
            case "vip"   -> config.getInt("auction-house.max-listings.vip", 10);
            case "mvp"   -> config.getInt("auction-house.max-listings.mvp", 20);
            case "admin" -> config.getInt("auction-house.max-listings.admin", 100);
            default      -> config.getInt("auction-house.max-listings.default", 5);
        };
    }

    public boolean isCategoryEnabled() {
        return config.getBoolean("auction-house.categories.enabled", true);
    }

    public java.util.List<String> getBlacklistedMaterials() {
        return config.getStringList("auction-house.blacklisted-materials");
    }

    // ── GUI ──────────────────────────────────────────────────
    public String getFillerMaterial() {
        return config.getString("auction-house.gui.filler-material", "BLACK_STAINED_GLASS_PANE");
    }

    // ── Messages ─────────────────────────────────────────────
    public String getMessage(String path) {
        String raw = config.getString("messages." + path, "&cMessage not found: " + path);
        return color(getPrefix() + raw);
    }

    public String getMessageRaw(String path) {
        return color(config.getString("messages." + path, "&cMessage not found: " + path));
    }

    private String color(String s) {
        return s == null ? "" : s.replace("&", "§");
    }
}
