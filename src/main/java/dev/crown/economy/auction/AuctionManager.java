package dev.crown.economy.auction;

import dev.crown.economy.CrownEconomy;
import dev.crown.economy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class AuctionManager {

    private final CrownEconomy plugin;
    private final Map<UUID, AuctionListing> activeListings = new LinkedHashMap<>();
    private final Map<UUID, List<PlayerTransaction>> transactions = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingReturns = new HashMap<>();
    private BukkitTask expiryTask;
    private BukkitTask autoSaveTask;
    private BukkitTask returnDeliveryTask;
    private File dataFile;
    private FileConfiguration dataConfig;

    public AuctionManager(CrownEconomy plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        dataFile = new File(plugin.getDataFolder(), "auction-data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Unable to create auction-data.yml: " + e.getMessage());
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadFromFile();
        startTasks();
    }

    public void reload() {
        cancelTasks();
        startTasks();
    }

    public void saveAll() {
        saveToFile();
    }

    public enum ListResult {
        SUCCESS,
        BLACKLISTED,
        GLOBAL_MAX_REACHED,
        MAX_REACHED,
        PRICE_TOO_LOW,
        PRICE_TOO_HIGH,
        NO_ITEM
    }

    public enum BuyResult {
        SUCCESS,
        NOT_FOUND,
        OWN_LISTING,
        NOT_ENOUGH_MONEY,
        ECONOMY_UNAVAILABLE
    }

    public enum CancelResult {
        SUCCESS,
        NOT_FOUND,
        NOT_OWNER
    }

    public ListResult listItem(Player player, ItemStack item, double price) {
        if (item == null || item.getType().isAir()) {
            return ListResult.NO_ITEM;
        }

        purgeExpiredListings(true);

        boolean blacklisted = plugin.getConfigManager().getBlacklistedMaterials().stream()
                .anyMatch(entry -> entry.equalsIgnoreCase(item.getType().name()));
        if (blacklisted) {
            return ListResult.BLACKLISTED;
        }

        double min = plugin.getConfigManager().getMinPrice();
        double max = plugin.getConfigManager().getMaxPrice();
        if (price < min) {
            return ListResult.PRICE_TOO_LOW;
        }
        if (price > max) {
            return ListResult.PRICE_TOO_HIGH;
        }

        int globalMaxListings = plugin.getConfigManager().getGlobalMaxListings();
        if (globalMaxListings > 0 && activeListings.size() >= globalMaxListings) {
            return ListResult.GLOBAL_MAX_REACHED;
        }

        int maxListings = plugin.getConfigManager().getMaxListings(player);
        long currentListings = activeListings.values().stream()
                .filter(listing -> listing.getSellerUUID().equals(player.getUniqueId()))
                .count();
        if (currentListings >= maxListings) {
            return ListResult.MAX_REACHED;
        }

        int configuredDuration = plugin.getConfigManager().getDefaultDurationHours();
        int maxDuration = plugin.getConfigManager().getMaxDurationHours();
        int duration = configuredDuration <= 0 ? 0
                : maxDuration > 0 ? Math.min(configuredDuration, maxDuration) : configuredDuration;
        AuctionListing listing = new AuctionListing(player.getUniqueId(), player.getName(), item, price, duration);
        activeListings.put(listing.getId(), listing);
        addTransaction(player.getUniqueId(), TransactionType.LISTED, null, null, item, price, 0.0, listing.getId());
        saveToFile();
        return ListResult.SUCCESS;
    }

    public BuyResult buyItem(Player buyer, UUID listingId) {
        purgeExpiredListings(true);
        AuctionListing listing = activeListings.get(listingId);
        if (listing == null) {
            return BuyResult.NOT_FOUND;
        }
        if (listing.isExpired()) {
            expireListing(listing);
            saveToFile();
            return BuyResult.NOT_FOUND;
        }
        if (listing.getSellerUUID().equals(buyer.getUniqueId())) {
            return BuyResult.OWN_LISTING;
        }
        if (!plugin.getVaultHook().isEnabled()) {
            return BuyResult.ECONOMY_UNAVAILABLE;
        }

        double price = listing.getPrice();
        if (!plugin.getVaultHook().has(buyer, price)) {
            return BuyResult.NOT_ENOUGH_MONEY;
        }

        if (!plugin.getVaultHook().withdraw(buyer, price)) {
            return BuyResult.NOT_ENOUGH_MONEY;
        }

        double taxRate = plugin.getConfigManager().getTaxRate();
        double taxAmount = price * (taxRate / 100.0);
        double sellerReceives = Math.max(0.0, price - taxAmount);

        boolean depositSuccess;
        Player seller = Bukkit.getPlayer(listing.getSellerUUID());
        if (seller != null) {
            depositSuccess = plugin.getVaultHook().deposit(seller, sellerReceives);
            if (depositSuccess) {
                seller.sendMessage(plugin.getConfigManager().getMessage("auction-house.item-sold")
                        .replace("{item}", MessageUtil.getItemName(listing.getItem()))
                        .replace("{price}", plugin.getConfigManager().formatPrice(sellerReceives)));
            }
        } else {
            depositSuccess = plugin.getVaultHook().deposit(Bukkit.getOfflinePlayer(listing.getSellerUUID()), sellerReceives);
        }

        if (!depositSuccess) {
            plugin.getVaultHook().deposit(buyer, price);
            return BuyResult.ECONOMY_UNAVAILABLE;
        }

        Map<Integer, ItemStack> leftover = buyer.getInventory().addItem(listing.getItem());
        if (!leftover.isEmpty()) {
            leftover.values().forEach(stack -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), stack));
        }

        addTransaction(buyer.getUniqueId(), TransactionType.PURCHASED, listing.getSellerName(),
                listing.getSellerUUID(), listing.getItem(), price, 0.0, listing.getId());
        addTransaction(listing.getSellerUUID(), TransactionType.SOLD, buyer.getName(), buyer.getUniqueId(),
                listing.getItem(), price, taxAmount, listing.getId());
        activeListings.remove(listingId);
        saveToFile();
        return BuyResult.SUCCESS;
    }

    public CancelResult cancelListing(Player actor, UUID listingId, boolean admin) {
        purgeExpiredListings(true);
        AuctionListing listing = activeListings.get(listingId);
        if (listing == null) {
            return CancelResult.NOT_FOUND;
        }
        if (!admin && !listing.getSellerUUID().equals(actor.getUniqueId())) {
            return CancelResult.NOT_OWNER;
        }

        activeListings.remove(listingId);
        String ownerMessage = listing.getSellerUUID().equals(actor.getUniqueId())
                ? null
                : "auction-house.listing-cancelled";
        String actorName = listing.getSellerUUID().equals(actor.getUniqueId()) ? null : actor.getName();
        addTransaction(listing.getSellerUUID(), TransactionType.CANCELLED, actorName, actor.getUniqueId(),
                listing.getItem(), listing.getPrice(), 0.0, listing.getId());
        returnItemToOwner(listing.getSellerUUID(), listing.getItem(), ownerMessage, null);
        saveToFile();
        return CancelResult.SUCCESS;
    }

    public void deliverPendingReturns(Player player) {
        deliverPendingReturns(player, true);
    }

    public List<AuctionListing> getActiveListings() {
        purgeExpiredListings(true);
        return new ArrayList<>(activeListings.values());
    }

    public List<AuctionListing> getPlayerListings(UUID playerUUID) {
        purgeExpiredListings(true);
        return activeListings.values().stream()
                .filter(listing -> listing.getSellerUUID().equals(playerUUID))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public List<PlayerTransaction> getPlayerTransactions(UUID playerUUID) {
        List<PlayerTransaction> history = transactions.get(playerUUID);
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(history);
    }

    public List<AuctionListing> search(String query) {
        purgeExpiredListings(true);
        String q = query.toLowerCase(Locale.ROOT);
        return activeListings.values().stream()
                .filter(listing -> {
                    String name = MessageUtil.getItemName(listing.getItem()).toLowerCase(Locale.ROOT);
                    String seller = listing.getSellerName() == null ? "" : listing.getSellerName().toLowerCase(Locale.ROOT);
                    String type = listing.getItem().getType().name().toLowerCase(Locale.ROOT);
                    return name.contains(q) || seller.contains(q) || type.contains(q);
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public AuctionListing getActiveById(UUID id) {
        purgeExpiredListings(true);
        return activeListings.get(id);
    }

    private void addTransaction(UUID playerUUID, TransactionType type, String otherName, UUID otherUUID,
                                ItemStack item, double price, double tax, UUID listingId) {
        List<PlayerTransaction> history = transactions.computeIfAbsent(playerUUID, key -> new ArrayList<>());
        history.add(0, new PlayerTransaction(UUID.randomUUID(), type, otherName, otherUUID, item, price, tax,
                System.currentTimeMillis(), listingId));
        trimTransactionHistory(history);
    }

    private void trimTransactionHistory(List<PlayerTransaction> history) {
        int maxHistory = plugin.getConfigManager().getTransactionHistoryLimit();
        if (maxHistory <= 0) {
            return;
        }

        while (history.size() > maxHistory) {
            history.remove(history.size() - 1);
        }
    }

    private void startTasks() {
        cancelTasks();
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> purgeExpiredListings(),
                20L * plugin.getConfigManager().getExpiryCheckSeconds(),
                20L * plugin.getConfigManager().getExpiryCheckSeconds());
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> saveToFile(),
                20L * plugin.getConfigManager().getAutoSaveSeconds(),
                20L * plugin.getConfigManager().getAutoSaveSeconds());
        returnDeliveryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushPendingReturns,
                20L * plugin.getConfigManager().getReturnCheckSeconds(),
                20L * plugin.getConfigManager().getReturnCheckSeconds());
    }

    private void cancelTasks() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        if (returnDeliveryTask != null) {
            returnDeliveryTask.cancel();
            returnDeliveryTask = null;
        }
    }

    private void purgeExpiredListings() {
        purgeExpiredListings(true);
    }

    private void purgeExpiredListings(boolean save) {
        boolean changed = false;
        Iterator<Map.Entry<UUID, AuctionListing>> iterator = activeListings.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, AuctionListing> entry = iterator.next();
            AuctionListing listing = entry.getValue();
            if (!listing.isExpired()) {
                continue;
            }

            iterator.remove();
            queueReturn(listing.getSellerUUID(), listing.getItem());
            addTransaction(listing.getSellerUUID(), TransactionType.EXPIRED, null, null,
                    listing.getItem(), listing.getPrice(), 0.0, listing.getId());
            Player seller = Bukkit.getPlayer(listing.getSellerUUID());
            if (seller != null) {
                seller.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-expired")
                        .replace("{item}", MessageUtil.getItemName(listing.getItem())));
            }
            changed = true;
        }
        if (changed && save) {
            saveToFile();
        }
    }

    private void expireListing(AuctionListing listing) {
        activeListings.remove(listing.getId());
        queueReturn(listing.getSellerUUID(), listing.getItem());
        addTransaction(listing.getSellerUUID(), TransactionType.EXPIRED, null, null,
                listing.getItem(), listing.getPrice(), 0.0, listing.getId());
        Player seller = Bukkit.getPlayer(listing.getSellerUUID());
        if (seller != null) {
            seller.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-expired")
                    .replace("{item}", MessageUtil.getItemName(listing.getItem())));
        }
    }

    private void returnItemToOwner(UUID owner, ItemStack item, String messagePath, String fallbackPath) {
        Player online = Bukkit.getPlayer(owner);
        if (online == null) {
            queueReturn(owner, item);
            return;
        }

        Map<Integer, ItemStack> leftover = online.getInventory().addItem(item.clone());
        if (!leftover.isEmpty()) {
            leftover.values().forEach(stack -> queueReturn(owner, stack));
            online.sendMessage(plugin.getConfigManager().getMessage("auction-house.inventory-full"));
        }

        if (messagePath != null) {
            String message = plugin.getConfigManager().getMessage(messagePath);
            if (message != null && !message.isBlank()) {
                online.sendMessage(message.replace("{item}", MessageUtil.getItemName(item)));
            }
        } else if (fallbackPath != null) {
            online.sendMessage(plugin.getConfigManager().getMessage(fallbackPath)
                    .replace("{item}", MessageUtil.getItemName(item)));
        }
    }

    private void queueReturn(UUID owner, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        pendingReturns.computeIfAbsent(owner, key -> new ArrayList<>()).add(item.clone());
    }

    private void deliverPendingReturns(Player player, boolean notify) {
        List<ItemStack> pending = pendingReturns.remove(player.getUniqueId());
        if (pending == null || pending.isEmpty()) {
            return;
        }

        List<ItemStack> remaining = new ArrayList<>();
        boolean deliveredAny = false;
        boolean inventoryFull = false;

        for (ItemStack stack : pending) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack.clone());
            if (leftover.isEmpty()) {
                deliveredAny = true;
                continue;
            }

            inventoryFull = true;
            int leftoverAmount = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            if (leftoverAmount < stack.getAmount()) {
                deliveredAny = true;
            }
            leftover.values().forEach(leftoverStack -> remaining.add(leftoverStack.clone()));
        }

        if (!remaining.isEmpty()) {
            pendingReturns.put(player.getUniqueId(), remaining);
        }

        if (deliveredAny && notify) {
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.returns-delivered"));
        }
        if (inventoryFull && notify) {
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.inventory-full"));
        }

        saveToFile();
    }

    private void flushPendingReturns() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (pendingReturns.containsKey(player.getUniqueId())) {
                deliverPendingReturns(player, false);
            }
        }
    }

    private TransactionType parseTransactionType(String raw) {
        if (raw == null || raw.isBlank()) {
            return TransactionType.LISTED;
        }
        try {
            return TransactionType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return TransactionType.LISTED;
        }
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void loadFromFile() {
        activeListings.clear();
        transactions.clear();
        pendingReturns.clear();

        ConfigurationSection active = dataConfig.getConfigurationSection("active");
        if (active != null) {
            for (String key : active.getKeys(false)) {
                String base = "active." + key + ".";
                try {
                    UUID id = UUID.fromString(key);
                    UUID seller = UUID.fromString(dataConfig.getString(base + "seller"));
                    String sellerName = dataConfig.getString(base + "sellerName", "Unknown");
                    ItemStack item = dataConfig.getItemStack(base + "item");
                    double price = dataConfig.getDouble(base + "price");
                    long listedAt = dataConfig.getLong(base + "listedAt");
                    long expiresAt = dataConfig.getLong(base + "expiresAt");
                    boolean sold = dataConfig.getBoolean(base + "sold", false);

                    if (item != null && !item.getType().isAir() && !sold) {
                        activeListings.put(id, new AuctionListing(id, seller, sellerName, item, price, listedAt, expiresAt, sold));
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load listing " + key + ": " + e.getMessage());
                }
            }
        }

        ConfigurationSection transactionsSection = dataConfig.getConfigurationSection("transactions");
        if (transactionsSection != null) {
            for (String playerKey : transactionsSection.getKeys(false)) {
                try {
                    UUID playerUuid = UUID.fromString(playerKey);
                    List<PlayerTransaction> history = new ArrayList<>();
                    ConfigurationSection playerSection = transactionsSection.getConfigurationSection(playerKey);
                    if (playerSection == null) {
                        continue;
                    }

                    playerSection.getKeys(false).stream()
                            .sorted(Comparator.comparingInt(this::parseIndex))
                            .forEach(entryKey -> {
                                String base = "transactions." + playerKey + "." + entryKey + ".";
                                try {
                                    PlayerTransaction transaction = new PlayerTransaction(
                                            UUID.fromString(dataConfig.getString(base + "id", UUID.randomUUID().toString())),
                                            parseTransactionType(dataConfig.getString(base + "type", "LISTED")),
                                            dataConfig.getString(base + "otherName", ""),
                                            parseUuid(dataConfig.getString(base + "otherUuid")),
                                            dataConfig.getItemStack(base + "item"),
                                            dataConfig.getDouble(base + "price"),
                                            dataConfig.getDouble(base + "tax"),
                                            dataConfig.getLong(base + "timestamp", System.currentTimeMillis()),
                                            parseUuid(dataConfig.getString(base + "listingId"))
                                    );
                                    history.add(transaction);
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Failed to load transaction " + entryKey + " for " + playerKey
                                            + ": " + e.getMessage());
                                }
                            });

                    if (!history.isEmpty()) {
                        trimTransactionHistory(history);
                        transactions.put(playerUuid, history);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load transactions for " + playerKey + ": " + e.getMessage());
                }
            }
        }

        ConfigurationSection returnsSection = dataConfig.getConfigurationSection("returns");
        if (returnsSection != null) {
            for (String ownerKey : returnsSection.getKeys(false)) {
                try {
                    List<ItemStack> returns = new ArrayList<>();
                    ConfigurationSection ownerSection = returnsSection.getConfigurationSection(ownerKey);
                    if (ownerSection == null) {
                        continue;
                    }
                    for (String returnKey : ownerSection.getKeys(false)) {
                        ItemStack item = ownerSection.getItemStack(returnKey + ".item");
                        if (item != null && !item.getType().isAir()) {
                            returns.add(item);
                        }
                    }
                    if (!returns.isEmpty()) {
                        pendingReturns.put(UUID.fromString(ownerKey), returns);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load returns for " + ownerKey + ": " + e.getMessage());
                }
            }
        }
    }

    private void saveToFile() {
        dataConfig.set("active", null);
        dataConfig.set("transactions", null);
        dataConfig.set("returns", null);

        for (AuctionListing listing : activeListings.values()) {
            String base = "active." + listing.getId() + ".";
            dataConfig.set(base + "seller", listing.getSellerUUID().toString());
            dataConfig.set(base + "sellerName", listing.getSellerName());
            dataConfig.set(base + "item", listing.getItem());
            dataConfig.set(base + "price", listing.getPrice());
            dataConfig.set(base + "listedAt", listing.getListedAt());
            dataConfig.set(base + "expiresAt", listing.getExpiresAt());
            dataConfig.set(base + "sold", listing.isSold());
        }

        for (Map.Entry<UUID, List<PlayerTransaction>> entry : transactions.entrySet()) {
            int index = 0;
            for (PlayerTransaction transaction : entry.getValue()) {
                String base = "transactions." + entry.getKey() + "." + index + ".";
                dataConfig.set(base + "id", transaction.id().toString());
                dataConfig.set(base + "type", transaction.type().name());
                dataConfig.set(base + "otherName", transaction.otherName());
                dataConfig.set(base + "otherUuid", transaction.otherUUID() == null ? null : transaction.otherUUID().toString());
                dataConfig.set(base + "item", transaction.item());
                dataConfig.set(base + "price", transaction.price());
                dataConfig.set(base + "tax", transaction.tax());
                dataConfig.set(base + "timestamp", transaction.timestamp());
                dataConfig.set(base + "listingId", transaction.listingId() == null ? null : transaction.listingId().toString());
                index++;
            }
        }

        for (Map.Entry<UUID, List<ItemStack>> entry : pendingReturns.entrySet()) {
            int index = 0;
            for (ItemStack item : entry.getValue()) {
                String base = "returns." + entry.getKey() + "." + index + ".";
                dataConfig.set(base + "item", item);
                index++;
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save auction data: " + e.getMessage());
        }
    }

    private int parseIndex(String input) {
        try {
            return Integer.parseInt(input);
        } catch (Exception ignored) {
            return Integer.MAX_VALUE;
        }
    }
}
