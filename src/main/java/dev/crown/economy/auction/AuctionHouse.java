package dev.crown.economy.auction;

import dev.crown.economy.CrownEconomy;
import dev.crown.economy.config.AuctionWorldScope;
import dev.crown.economy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

public final class AuctionHouse {

    private final CrownEconomy plugin;
    private final AuctionWorldScope scope;
    private final File dataFile;
    private final Map<UUID, AuctionListing> activeListings = new LinkedHashMap<>();
    private final Map<UUID, List<PlayerTransaction>> transactions = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingReturns = new HashMap<>();
    private final Object saveMonitor = new Object();
    private boolean loaded;
    private boolean dirty;
    private boolean asyncWriteRunning;
    private String pendingSerialized;

    AuctionHouse(CrownEconomy plugin, AuctionWorldScope scope, File dataFile) {
        this.plugin = plugin;
        this.scope = scope;
        this.dataFile = dataFile;
    }

    public AuctionWorldScope getScope() {
        return scope;
    }

    public void load() {
        ensureDataFileExists();
        YamlConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadFromFile(dataConfig);
        loaded = true;
        dirty = false;
    }

    public int getActiveListingCount() {
        purgeExpiredListings(false);
        return activeListings.size();
    }

    public boolean containsListing(UUID listingId) {
        purgeExpiredListings(false);
        return activeListings.containsKey(listingId);
    }

    public AuctionHouseManager.ListResult listItem(Player player, ItemStack item, double price) {
        if (item == null || item.getType().isAir()) {
            return AuctionHouseManager.ListResult.NO_ITEM;
        }
        purgeExpiredListings(false);

        boolean blacklisted = plugin.getConfigManager().getBlacklistedMaterials().stream()
                .anyMatch(entry -> entry.equalsIgnoreCase(item.getType().name()));
        if (blacklisted) {
            return AuctionHouseManager.ListResult.BLACKLISTED;
        }

        double min = plugin.getConfigManager().getMinPrice();
        double max = plugin.getConfigManager().getMaxPrice();
        if (price < min) {
            return AuctionHouseManager.ListResult.PRICE_TOO_LOW;
        }
        if (price > max) {
            return AuctionHouseManager.ListResult.PRICE_TOO_HIGH;
        }

        int maxHouseListings = plugin.getConfigManager().getGlobalMaxListings();
        if (maxHouseListings > 0 && activeListings.size() >= maxHouseListings) {
            return AuctionHouseManager.ListResult.GLOBAL_MAX_REACHED;
        }

        int maxListings = plugin.getConfigManager().getMaxListings(player);
        long currentListings = activeListings.values().stream()
                .filter(listing -> listing.getSellerUUID().equals(player.getUniqueId()))
                .count();
        if (currentListings >= maxListings) {
            return AuctionHouseManager.ListResult.MAX_REACHED;
        }

        int configuredDuration = plugin.getConfigManager().getDefaultDurationHours();
        int maxDuration = plugin.getConfigManager().getMaxDurationHours();
        int duration = configuredDuration <= 0 ? 0
                : maxDuration > 0 ? Math.min(configuredDuration, maxDuration) : configuredDuration;
        AuctionListing listing = new AuctionListing(scope.key(), player.getUniqueId(), player.getName(), item, price, duration);
        activeListings.put(listing.getId(), listing);
        addTransaction(player.getUniqueId(), TransactionType.LISTED, null, null, item, price, 0.0, listing.getId());
        saveAsync();
        return AuctionHouseManager.ListResult.SUCCESS;
    }

    public AuctionHouseManager.BuyResult buyItem(Player buyer, UUID listingId) {
        purgeExpiredListings(false);
        AuctionListing listing = activeListings.get(listingId);
        if (listing == null) {
            return AuctionHouseManager.BuyResult.NOT_FOUND;
        }
        if (listing.isExpired()) {
            expireListing(listing);
            saveAsync();
            return AuctionHouseManager.BuyResult.NOT_FOUND;
        }
        if (listing.getSellerUUID().equals(buyer.getUniqueId())) {
            return AuctionHouseManager.BuyResult.OWN_LISTING;
        }
        if (!plugin.getVaultHook().isEnabled()) {
            return AuctionHouseManager.BuyResult.ECONOMY_UNAVAILABLE;
        }

        double price = listing.getPrice();
        if (!plugin.getVaultHook().has(buyer, price) || !plugin.getVaultHook().withdraw(buyer, price)) {
            return AuctionHouseManager.BuyResult.NOT_ENOUGH_MONEY;
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
            return AuctionHouseManager.BuyResult.ECONOMY_UNAVAILABLE;
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
        saveAsync();
        return AuctionHouseManager.BuyResult.SUCCESS;
    }

    public AuctionHouseManager.CancelResult cancelListing(Player actor, UUID listingId, boolean admin) {
        purgeExpiredListings(false);
        AuctionListing listing = activeListings.get(listingId);
        if (listing == null) {
            return AuctionHouseManager.CancelResult.NOT_FOUND;
        }
        if (!admin && !listing.getSellerUUID().equals(actor.getUniqueId())) {
            return AuctionHouseManager.CancelResult.NOT_OWNER;
        }

        activeListings.remove(listingId);
        String ownerMessage = listing.getSellerUUID().equals(actor.getUniqueId()) ? null : "auction-house.listing-cancelled";
        String actorName = listing.getSellerUUID().equals(actor.getUniqueId()) ? null : actor.getName();
        addTransaction(listing.getSellerUUID(), TransactionType.CANCELLED, actorName, actor.getUniqueId(),
                listing.getItem(), listing.getPrice(), 0.0, listing.getId());
        returnItemToOwner(listing.getSellerUUID(), listing.getItem(), ownerMessage, null);
        saveAsync();
        return AuctionHouseManager.CancelResult.SUCCESS;
    }

    public List<AuctionListing> getActiveListings() {
        purgeExpiredListings(false);
        return new ArrayList<>(activeListings.values());
    }

    public List<AuctionListing> getPlayerListings(UUID playerUUID) {
        purgeExpiredListings(false);
        return activeListings.values().stream()
                .filter(listing -> listing.getSellerUUID().equals(playerUUID))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public List<PlayerTransaction> getPlayerTransactions(UUID playerUUID) {
        List<PlayerTransaction> history = transactions.get(playerUUID);
        return history == null || history.isEmpty() ? List.of() : new ArrayList<>(history);
    }

    public AuctionListing getActiveById(UUID id) {
        purgeExpiredListings(false);
        return activeListings.get(id);
    }

    public void deliverPendingReturns(Player player) {
        deliverPendingReturns(player, true);
    }

    public void flushPendingReturns() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (pendingReturns.containsKey(player.getUniqueId())) {
                deliverPendingReturns(player, false);
            }
        }
    }

    public void purgeExpiredListingsAndSaveIfNeeded() {
        purgeExpiredListings(true);
    }

    public void saveNow() {
        if (!loaded) {
            return;
        }
        dirty = false;
        writeSerialized(buildSerializedSnapshot());
    }

    public void saveAsyncIfDirty() {
        if (dirty) {
            saveAsync();
        }
    }

    private void addTransaction(UUID playerUUID, TransactionType type, String otherName, UUID otherUUID,
                                ItemStack item, double price, double tax, UUID listingId) {
        List<PlayerTransaction> history = transactions.computeIfAbsent(playerUUID, key -> new ArrayList<>());
        history.add(0, new PlayerTransaction(UUID.randomUUID(), type, otherName, otherUUID, item, price, tax,
                System.currentTimeMillis(), listingId));
        trimTransactionHistory(history);
        dirty = true;
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

    private void purgeExpiredListings(boolean saveAfter) {
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
        if (changed && saveAfter) {
            saveAsync();
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
        dirty = true;
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

        dirty = true;
        saveAsync();
    }

    private void saveAsync() {
        if (!loaded) {
            return;
        }
        dirty = false;
        String serialized = buildSerializedSnapshot();
        synchronized (saveMonitor) {
            pendingSerialized = serialized;
            if (asyncWriteRunning) {
                return;
            }
            asyncWriteRunning = true;
        }

        // Serialize writes so a stale snapshot cannot overwrite a newer one later.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::flushQueuedWrites);
    }

    private void flushQueuedWrites() {
        while (true) {
            String serialized;
            synchronized (saveMonitor) {
                serialized = pendingSerialized;
                pendingSerialized = null;
                if (serialized == null) {
                    asyncWriteRunning = false;
                    return;
                }
            }
            writeSerialized(serialized);
        }
    }

    private void writeSerialized(String serialized) {
        try {
            File parent = dataFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.writeString(dataFile.toPath(), serialized, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save auction data for " + scope.key() + ": " + e.getMessage());
        }
    }

    private String buildSerializedSnapshot() {
        YamlConfiguration snapshot = new YamlConfiguration();
        snapshot.set("scope.key", scope.key());
        snapshot.set("scope.display-name", scope.displayName());
        snapshot.set("scope.world-name", scope.worldName());
        snapshot.set("active", null);
        snapshot.set("transactions", null);
        snapshot.set("returns", null);

        for (AuctionListing listing : activeListings.values()) {
            String base = "active." + listing.getId() + ".";
            snapshot.set(base + "scope", listing.getScopeKey());
            snapshot.set(base + "seller", listing.getSellerUUID().toString());
            snapshot.set(base + "sellerName", listing.getSellerName());
            snapshot.set(base + "item", listing.getItem());
            snapshot.set(base + "price", listing.getPrice());
            snapshot.set(base + "listedAt", listing.getListedAt());
            snapshot.set(base + "expiresAt", listing.getExpiresAt());
            snapshot.set(base + "sold", listing.isSold());
        }

        for (Map.Entry<UUID, List<PlayerTransaction>> entry : transactions.entrySet()) {
            int index = 0;
            for (PlayerTransaction transaction : entry.getValue()) {
                String base = "transactions." + entry.getKey() + "." + index + ".";
                snapshot.set(base + "id", transaction.id().toString());
                snapshot.set(base + "type", transaction.type().name());
                snapshot.set(base + "otherName", transaction.otherName());
                snapshot.set(base + "otherUuid", transaction.otherUUID() == null ? null : transaction.otherUUID().toString());
                snapshot.set(base + "item", transaction.item());
                snapshot.set(base + "price", transaction.price());
                snapshot.set(base + "tax", transaction.tax());
                snapshot.set(base + "timestamp", transaction.timestamp());
                snapshot.set(base + "listingId", transaction.listingId() == null ? null : transaction.listingId().toString());
                index++;
            }
        }

        for (Map.Entry<UUID, List<ItemStack>> entry : pendingReturns.entrySet()) {
            int index = 0;
            for (ItemStack item : entry.getValue()) {
                String base = "returns." + entry.getKey() + "." + index + ".";
                snapshot.set(base + "item", item);
                index++;
            }
        }

        return snapshot.saveToString();
    }

    private void ensureDataFileExists() {
        try {
            File parent = dataFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            if (!dataFile.exists()) {
                dataFile.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Unable to create auction file " + dataFile.getName() + ": " + e.getMessage());
        }
    }

    private void loadFromFile(YamlConfiguration dataConfig) {
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
                    String scopeKey = dataConfig.getString(base + "scope", scope.key());
                    String sellerName = dataConfig.getString(base + "sellerName", "Unknown");
                    ItemStack item = dataConfig.getItemStack(base + "item");
                    double price = dataConfig.getDouble(base + "price");
                    long listedAt = dataConfig.getLong(base + "listedAt");
                    long expiresAt = dataConfig.getLong(base + "expiresAt");
                    boolean sold = dataConfig.getBoolean(base + "sold", false);
                    if (item != null && !item.getType().isAir() && !sold) {
                        activeListings.put(id, new AuctionListing(id, scopeKey, seller, sellerName, item, price, listedAt, expiresAt, sold));
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load listing " + key + " for " + scope.key() + ": " + e.getMessage());
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
                                    plugin.getLogger().warning("Failed to load transaction " + entryKey + " for "
                                            + playerKey + " in " + scope.key() + ": " + e.getMessage());
                                }
                            });

                    if (!history.isEmpty()) {
                        trimTransactionHistory(history);
                        transactions.put(playerUuid, history);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load transactions for " + playerKey + " in " + scope.key()
                            + ": " + e.getMessage());
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
                    plugin.getLogger().warning("Failed to load returns for " + ownerKey + " in " + scope.key()
                            + ": " + e.getMessage());
                }
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

    private int parseIndex(String input) {
        try {
            return Integer.parseInt(input);
        } catch (Exception ignored) {
            return Integer.MAX_VALUE;
        }
    }
}
