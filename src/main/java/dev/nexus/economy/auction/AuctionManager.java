package dev.nexus.economy.auction;

import dev.nexus.economy.NexusEconomy;
import dev.nexus.economy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class AuctionManager {

    private final NexusEconomy plugin;
    private final List<AuctionListing> activeListings = new ArrayList<>();
    // Expired listings waiting to be collected: sellerUUID -> list
    private final Map<UUID, List<AuctionListing>> expiredBin = new HashMap<>();
    private BukkitTask expiryTask;
    private File dataFile;
    private FileConfiguration dataConfig;

    public AuctionManager(NexusEconomy plugin) {
        this.plugin = plugin;
    }

    public void load() {
        dataFile = new File(plugin.getDataFolder(), "auction_data.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadFromFile();
        startExpiryTask();
    }

    public void reload() {
        if (expiryTask != null) expiryTask.cancel();
        startExpiryTask();
    }

    public void saveAll() {
        saveToFile();
    }

    // ── Listing ───────────────────────────────────────────────

    public enum ListResult {
        SUCCESS, BLACKLISTED, MAX_REACHED, PRICE_TOO_LOW, PRICE_TOO_HIGH, NO_ITEM
    }

    public ListResult listItem(Player player, ItemStack item, double price) {
        if (item == null || item.getType().isAir()) return ListResult.NO_ITEM;

        // Blacklist check
        List<String> blacklist = plugin.getConfigManager().getBlacklistedMaterials();
        if (blacklist.contains(item.getType().name())) return ListResult.BLACKLISTED;

        // Price bounds
        double min = plugin.getConfigManager().getMinPrice();
        double max = plugin.getConfigManager().getMaxPrice();
        if (price < min) return ListResult.PRICE_TOO_LOW;
        if (price > max) return ListResult.PRICE_TOO_HIGH;

        // Max listings check
        String tier = plugin.getLuckPermsHook().getListingTier(player);
        int maxListings = plugin.getConfigManager().getMaxListings(tier);
        long currentCount = activeListings.stream()
                .filter(l -> l.getSellerUUID().equals(player.getUniqueId()))
                .count();
        if (currentCount >= maxListings) return ListResult.MAX_REACHED;

        int duration = plugin.getConfigManager().getDefaultDuration();
        AuctionListing listing = new AuctionListing(
                player.getUniqueId(), player.getName(), item, price, duration);
        activeListings.add(listing);
        saveToFile();
        return ListResult.SUCCESS;
    }

    // ── Purchasing ───────────────────────────────────────────

    public enum BuyResult {
        SUCCESS, NOT_FOUND, OWN_LISTING, NOT_ENOUGH_MONEY
    }

    public BuyResult buyItem(Player buyer, UUID listingId) {
        AuctionListing listing = getActiveById(listingId);
        if (listing == null) return BuyResult.NOT_FOUND;
        if (listing.getSellerUUID().equals(buyer.getUniqueId())) return BuyResult.OWN_LISTING;

        double price = listing.getPrice();
        if (!plugin.getVaultHook().has(buyer, price)) return BuyResult.NOT_ENOUGH_MONEY;

        // Process transaction
        plugin.getVaultHook().withdraw(buyer, price);
        double taxRate = plugin.getConfigManager().getTaxRate();
        double taxAmount = price * (taxRate / 100.0);
        double sellerReceives = price - taxAmount;

        // Pay seller (if online, else store for pickup — simplified: deposit directly)
        Player seller = Bukkit.getPlayer(listing.getSellerUUID());
        if (seller != null) {
            plugin.getVaultHook().deposit(seller, sellerReceives);
            String msg = plugin.getConfigManager().getMessage("auction-house.item-sold")
                    .replace("{item}", MessageUtil.getItemName(listing.getItem()))
                    .replace("{price}", MessageUtil.formatPrice(sellerReceives))
                    .replace("{tax}", String.valueOf((int) taxRate));
            seller.sendMessage(msg);
        } else {
            // Offline seller — deposit directly via vault (works with most economy plugins)
            plugin.getVaultHook().deposit(Bukkit.getOfflinePlayer(listing.getSellerUUID()), sellerReceives);
        }

        // Give item to buyer
        Map<Integer, ItemStack> leftover = buyer.getInventory().addItem(listing.getItem());
        if (!leftover.isEmpty()) {
            // Drop at feet if inventory is full
            leftover.values().forEach(i -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), i));
        }

        listing.setSold(true);
        activeListings.remove(listing);
        saveToFile();
        return BuyResult.SUCCESS;
    }

    // ── Cancel ───────────────────────────────────────────────

    public boolean cancelListing(Player player, UUID listingId, boolean admin) {
        AuctionListing listing = getActiveById(listingId);
        if (listing == null) return false;
        if (!admin && !listing.getSellerUUID().equals(player.getUniqueId())) return false;

        activeListings.remove(listing);
        // Return item
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(listing.getItem());
        leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        saveToFile();
        return true;
    }

    // ── Expiry ───────────────────────────────────────────────

    private void startExpiryTask() {
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkExpiry, 20 * 60, 20 * 60);
    }

    private void checkExpiry() {
        Iterator<AuctionListing> it = activeListings.iterator();
        while (it.hasNext()) {
            AuctionListing listing = it.next();
            if (listing.isExpired()) {
                it.remove();
                if (plugin.getConfigManager().isReturnExpired()) {
                    expiredBin.computeIfAbsent(listing.getSellerUUID(), k -> new ArrayList<>())
                              .add(listing);
                    // Notify if online
                    Player seller = Bukkit.getPlayer(listing.getSellerUUID());
                    if (seller != null) {
                        String msg = plugin.getConfigManager()
                                .getMessage("auction-house.listing-expired")
                                .replace("{item}", MessageUtil.getItemName(listing.getItem()));
                        seller.sendMessage(msg);
                    }
                }
            }
        }
        // Purge old expired bin entries
        int maxHours = plugin.getConfigManager().getExpiredBinDuration();
        long cutoff = System.currentTimeMillis() - (maxHours * 3600000L);
        expiredBin.values().forEach(list -> list.removeIf(l -> l.getExpiresAt() < cutoff));
        saveToFile();
    }

    public int collectExpired(Player player) {
        List<AuctionListing> expired = expiredBin.getOrDefault(player.getUniqueId(), new ArrayList<>());
        if (expired.isEmpty()) return 0;
        int count = 0;
        for (AuctionListing listing : expired) {
            player.getInventory().addItem(listing.getItem());
            count++;
        }
        expiredBin.remove(player.getUniqueId());
        return count;
    }

    // ── Search / Filter ──────────────────────────────────────

    public List<AuctionListing> getActiveListings() {
        return activeListings.stream()
                .filter(l -> !l.isExpired())
                .collect(Collectors.toList());
    }

    public List<AuctionListing> getPlayerListings(UUID playerUUID) {
        return activeListings.stream()
                .filter(l -> l.getSellerUUID().equals(playerUUID) && !l.isExpired())
                .collect(Collectors.toList());
    }

    public List<AuctionListing> search(String query) {
        String q = query.toLowerCase();
        return getActiveListings().stream()
                .filter(l -> MessageUtil.getItemName(l.getItem()).toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    public List<AuctionListing> getExpiredBin(UUID playerUUID) {
        return expiredBin.getOrDefault(playerUUID, new ArrayList<>());
    }

    public AuctionListing getActiveById(UUID id) {
        return activeListings.stream().filter(l -> l.getId().equals(id)).findFirst().orElse(null);
    }

    // ── Persistence ──────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void loadFromFile() {
        activeListings.clear();
        expiredBin.clear();
        if (dataConfig.contains("listings")) {
            for (String key : dataConfig.getConfigurationSection("listings").getKeys(false)) {
                try {
                    String path = "listings." + key + ".";
                    UUID id = UUID.fromString(key);
                    UUID seller = UUID.fromString(dataConfig.getString(path + "seller"));
                    String sellerName = dataConfig.getString(path + "sellerName");
                    ItemStack item = dataConfig.getItemStack(path + "item");
                    double price = dataConfig.getDouble(path + "price");
                    long listedAt = dataConfig.getLong(path + "listedAt");
                    long expiresAt = dataConfig.getLong(path + "expiresAt");
                    boolean expired = dataConfig.getBoolean(path + "expired");
                    boolean sold = dataConfig.getBoolean(path + "sold");
                    if (!sold) {
                        AuctionListing l = new AuctionListing(id, seller, sellerName, item,
                                price, listedAt, expiresAt, expired, sold);
                        if (!l.isExpired()) {
                            activeListings.add(l);
                        } else if (plugin.getConfigManager().isReturnExpired()) {
                            expiredBin.computeIfAbsent(seller, k -> new ArrayList<>()).add(l);
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load listing: " + key + " - " + e.getMessage());
                }
            }
        }
    }

    private void saveToFile() {
        dataConfig.set("listings", null);
        for (AuctionListing listing : activeListings) {
            String path = "listings." + listing.getId() + ".";
            dataConfig.set(path + "seller", listing.getSellerUUID().toString());
            dataConfig.set(path + "sellerName", listing.getSellerName());
            dataConfig.set(path + "item", listing.getItem());
            dataConfig.set(path + "price", listing.getPrice());
            dataConfig.set(path + "listedAt", listing.getListedAt());
            dataConfig.set(path + "expiresAt", listing.getExpiresAt());
            dataConfig.set(path + "expired", listing.isExpired());
            dataConfig.set(path + "sold", listing.isSold());
        }
        // Save expired bin
        for (Map.Entry<UUID, List<AuctionListing>> entry : expiredBin.entrySet()) {
            for (AuctionListing listing : entry.getValue()) {
                String path = "listings." + listing.getId() + ".";
                dataConfig.set(path + "seller", listing.getSellerUUID().toString());
                dataConfig.set(path + "sellerName", listing.getSellerName());
                dataConfig.set(path + "item", listing.getItem());
                dataConfig.set(path + "price", listing.getPrice());
                dataConfig.set(path + "listedAt", listing.getListedAt());
                dataConfig.set(path + "expiresAt", listing.getExpiresAt());
                dataConfig.set(path + "expired", true);
                dataConfig.set(path + "sold", false);
            }
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save auction data: " + e.getMessage());
        }
    }
}
