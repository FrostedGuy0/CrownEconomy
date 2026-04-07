package dev.crown.economy.auction;

import dev.crown.economy.CrownEconomy;
import dev.crown.economy.config.AuctionWorldMode;
import dev.crown.economy.config.AuctionWorldScope;
import dev.crown.economy.config.WorldAuctionSettings;
import dev.crown.economy.world.BukkitWorldResolver;
import dev.crown.economy.world.MultiverseWorldResolver;
import dev.crown.economy.world.WorldResolver;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AuctionHouseManager {

    public enum ListResult {
        SUCCESS,
        WORLD_DISABLED,
        BLACKLISTED,
        GLOBAL_MAX_REACHED,
        MAX_REACHED,
        PRICE_TOO_LOW,
        PRICE_TOO_HIGH,
        NO_ITEM
    }

    public enum BuyResult {
        SUCCESS,
        WORLD_DISABLED,
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

    private final CrownEconomy plugin;
    private final Map<String, AuctionHouse> houseCache = new ConcurrentHashMap<>();
    private final WorldResolver multiverseResolver = new MultiverseWorldResolver();
    private final WorldResolver bukkitResolver = new BukkitWorldResolver();
    private BukkitTask expiryTask;
    private BukkitTask autoSaveTask;
    private BukkitTask returnDeliveryTask;

    public AuctionHouseManager(CrownEconomy plugin) {
        this.plugin = plugin;
    }

    public void load() {
        ensureStorageDirectories();
        migrateGroupedStorageIfNeeded();
        loadCachedHousesForCurrentMode();
        startTasks();
    }

    public void reload() {
        saveAll();
        cancelTasks();
        houseCache.clear();
        load();
    }

    public void saveAll() {
        houseCache.values().forEach(AuctionHouse::saveNow);
    }

    public AuctionHouse getAuctionHouse(Player player) {
        AuctionWorldScope scope = resolveScope(player);
        if (!scope.enabled()) {
            return null;
        }
        return getOrCreateHouse(scope);
    }

    public boolean isEnabled(World world) {
        return resolveScope(world).enabled();
    }

    public String getGroup(World world) {
        AuctionWorldScope scope = resolveScope(world);
        return scope.isGroupScope() ? scope.scopeIdentifier() : "";
    }

    public String getWorldProviderName() {
        return multiverseResolver.isAvailable() ? multiverseResolver.getName() : bukkitResolver.getName();
    }

    public AuctionWorldScope resolveScope(Player player) {
        return resolveScope(player == null ? null : player.getWorld());
    }

    public AuctionWorldScope resolveScope(World world) {
        String worldName = resolveWorldName(world);
        WorldAuctionSettings settings = plugin.getConfigManager().getWorldSettings(worldName);
        return resolveScope(worldName, settings);
    }

    private AuctionWorldScope resolveScope(String worldName, WorldAuctionSettings settings) {
        boolean enabled = settings.enabled();
        AuctionWorldMode mode = plugin.getConfigManager().getAuctionWorldMode();

        if (mode == AuctionWorldMode.GLOBAL) {
            return new AuctionWorldScope(
                    AuctionWorldScope.GLOBAL_SCOPE_KEY,
                    plugin.getConfigManager().getGuiLabel("main.labels.global-scope", "Global"),
                    worldName,
                    enabled
            );
        }

        if (mode == AuctionWorldMode.PER_WORLD) {
            return new AuctionWorldScope(
                    AuctionWorldScope.worldScopeKey(worldName),
                    plugin.getConfigManager().getConfiguredWorldDisplayName(worldName),
                    worldName,
                    enabled
            );
        }

        String resolvedGroup = resolveGroupIdentifier(worldName, settings);
        if (resolvedGroup.isBlank()) {
            return new AuctionWorldScope(
                    AuctionWorldScope.worldScopeKey(worldName),
                    plugin.getConfigManager().getConfiguredWorldDisplayName(worldName),
                    worldName,
                    enabled
            );
        }

        return new AuctionWorldScope(
                AuctionWorldScope.groupScopeKey(resolvedGroup),
                prettifyIdentifier(resolvedGroup),
                worldName,
                enabled
        );
    }

    public Map<String, String> getAuctionScopePlaceholders(Player player) {
        return plugin.getConfigManager().getAuctionScopePlaceholders(resolveScope(player));
    }

    public ListResult listItem(Player player, ItemStack item, double price) {
        AuctionHouse house = getAuctionHouse(player);
        return house == null ? ListResult.WORLD_DISABLED : house.listItem(player, item, price);
    }

    public BuyResult buyItem(Player buyer, UUID listingId) {
        AuctionHouse house = getAuctionHouse(buyer);
        return house == null ? BuyResult.WORLD_DISABLED : house.buyItem(buyer, listingId);
    }

    public CancelResult cancelListing(Player actor, UUID listingId, boolean admin) {
        AuctionHouse house = admin ? findHouseByListingId(listingId) : getAuctionHouse(actor);
        if (house == null) {
            return CancelResult.NOT_FOUND;
        }
        return house.cancelListing(actor, listingId, admin);
    }

    public List<AuctionListing> getAccessibleListings(Player player) {
        AuctionHouse house = getAuctionHouse(player);
        return house == null ? List.of() : house.getActiveListings();
    }

    public List<AuctionListing> getPlayerListings(Player player) {
        AuctionHouse house = getAuctionHouse(player);
        return house == null ? List.of() : house.getPlayerListings(player.getUniqueId());
    }

    public List<PlayerTransaction> getPlayerTransactions(Player player) {
        AuctionHouse house = getAuctionHouse(player);
        return house == null ? List.of() : house.getPlayerTransactions(player.getUniqueId());
    }

    public List<AuctionListing> getActiveListings() {
        return houseCache.values().stream()
                .flatMap(house -> house.getActiveListings().stream())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public int getTotalActiveListingCount() {
        return houseCache.values().stream().mapToInt(AuctionHouse::getActiveListingCount).sum();
    }

    public AuctionListing getActiveById(UUID id) {
        for (AuctionHouse house : houseCache.values()) {
            AuctionListing listing = house.getActiveById(id);
            if (listing != null) {
                return listing;
            }
        }
        return null;
    }

    public void deliverPendingReturns(Player player) {
        houseCache.values().forEach(house -> house.deliverPendingReturns(player));
    }

    public void onWorldLoad(World world) {
        if (world != null && isEnabled(world)) {
            getOrCreateHouse(resolveScope(world));
        }
    }

    public void onWorldUnload(World world) {
        if (world == null) {
            return;
        }
        AuctionHouse house = houseCache.get(resolveScope(world).key());
        if (house != null) {
            house.saveNow();
        }
    }

    private void loadCachedHousesForCurrentMode() {
        File modeDirectory = getStorageDirectoryForMode(plugin.getConfigManager().getAuctionWorldMode());
        if (!modeDirectory.exists()) {
            return;
        }

        File[] files = modeDirectory.listFiles(this::isActiveAuctionDataFile);
        if (files == null) {
            return;
        }

        for (File file : files) {
            AuctionWorldScope scope = loadScopeMetadata(file);
            AuctionHouse house = new AuctionHouse(plugin, scope, file);
            house.load();
            houseCache.put(scope.key(), house);
        }
    }

    private AuctionHouse getOrCreateHouse(AuctionWorldScope scope) {
        return houseCache.computeIfAbsent(scope.key(), ignored -> {
            AuctionHouse house = new AuctionHouse(plugin, scope, resolveDataFile(scope));
            house.load();
            return house;
        });
    }

    private AuctionHouse findHouseByListingId(UUID listingId) {
        if (listingId == null) {
            return null;
        }
        for (AuctionHouse house : houseCache.values()) {
            if (house.containsListing(listingId)) {
                return house;
            }
        }
        return null;
    }

    private void startTasks() {
        cancelTasks();
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> houseCache.values()
                        .forEach(AuctionHouse::purgeExpiredListingsAndSaveIfNeeded),
                20L * plugin.getConfigManager().getExpiryCheckSeconds(),
                20L * plugin.getConfigManager().getExpiryCheckSeconds());
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> houseCache.values()
                        .forEach(AuctionHouse::saveAsyncIfDirty),
                20L * plugin.getConfigManager().getAutoSaveSeconds(),
                20L * plugin.getConfigManager().getAutoSaveSeconds());
        returnDeliveryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> houseCache.values()
                        .forEach(AuctionHouse::flushPendingReturns),
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

    private void ensureStorageDirectories() {
        File root = new File(plugin.getDataFolder(), "auction-houses");
        if (!root.exists()) {
            root.mkdirs();
        }
        File grouped = getStorageDirectoryForMode(AuctionWorldMode.GROUPED);
        if (!grouped.exists()) {
            grouped.mkdirs();
        }
        File perWorld = getStorageDirectoryForMode(AuctionWorldMode.PER_WORLD);
        if (!perWorld.exists()) {
            perWorld.mkdirs();
        }
    }

    private void migrateGroupedStorageIfNeeded() {
        if (plugin.getConfigManager().getAuctionWorldMode() != AuctionWorldMode.GROUPED) {
            return;
        }

        File groupedDirectory = getStorageDirectoryForMode(AuctionWorldMode.GROUPED);
        for (String worldName : plugin.getConfigManager().getConfiguredWorldNames()) {
            WorldAuctionSettings settings = plugin.getConfigManager().getWorldSettings(worldName);
            AuctionWorldScope targetScope = resolveScope(worldName, settings);
            if (!targetScope.isGroupScope()) {
                continue;
            }

            File legacyWorldFile = new File(groupedDirectory, sanitizeWorldFileComponent(worldName) + ".yml");
            File targetFile = new File(groupedDirectory, targetScope.sanitizedFileComponent() + ".yml");
            if (!legacyWorldFile.exists() || legacyWorldFile.equals(targetFile)) {
                continue;
            }

            mergeLegacyWorldFileIntoGroupFile(legacyWorldFile, targetFile, targetScope);
        }
    }

    private File resolveDataFile(AuctionWorldScope scope) {
        if (scope.isGlobal()) {
            return new File(plugin.getDataFolder(), "auction-houses/global.yml");
        }
        File directory = getStorageDirectoryForMode(plugin.getConfigManager().getAuctionWorldMode());
        File canonicalFile = new File(directory, scope.sanitizedFileComponent() + ".yml");
        if (plugin.getConfigManager().getAuctionWorldMode() == AuctionWorldMode.GROUPED && isImplicitFamilyGroupScope(scope)) {
            return resolveLegacyFamilyFile(directory, canonicalFile, scope.worldName());
        }
        return canonicalFile;
    }

    private File getStorageDirectoryForMode(AuctionWorldMode mode) {
        return switch (mode) {
            case GLOBAL -> new File(plugin.getDataFolder(), "auction-houses");
            case PER_WORLD -> new File(plugin.getDataFolder(), "auction-houses/per-world");
            case GROUPED -> new File(plugin.getDataFolder(), "auction-houses/grouped");
        };
    }

    private boolean isActiveAuctionDataFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String normalized = file.getName().toLowerCase(Locale.ROOT);
        if (!normalized.endsWith(".yml") || normalized.endsWith(".disabled.yml")) {
            return false;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        return config.getBoolean("options.enabled", true);
    }

    private String resolveGroupIdentifier(String worldName, WorldAuctionSettings settings) {
        if (settings != null && settings.group() != null && !settings.group().isBlank()) {
            return settings.group();
        }
        if (settings != null && settings.explicit()) {
            return "";
        }
        if (plugin.getConfigManager().hasConfiguredWorldGroups()) {
            return "";
        }
        return inferImplicitFamilyGroup(worldName);
    }

    private boolean isImplicitFamilyGroupScope(AuctionWorldScope scope) {
        if (scope == null || !scope.isGroupScope() || scope.worldName().isBlank()) {
            return false;
        }

        WorldAuctionSettings settings = plugin.getConfigManager().getWorldSettings(scope.worldName());
        if (settings.explicit() || plugin.getConfigManager().hasConfiguredWorldGroups()) {
            return false;
        }

        String inferredGroup = inferImplicitFamilyGroup(scope.worldName());
        return !inferredGroup.isBlank() && inferredGroup.equals(scope.scopeIdentifier());
    }

    private File resolveLegacyFamilyFile(File directory, File canonicalFile, String worldName) {
        if (canonicalFile.exists() && canonicalFile.length() > 0L) {
            return canonicalFile;
        }

        for (String candidate : buildImplicitFamilyCandidates(worldName)) {
            File candidateFile = new File(directory, candidate + ".yml");
            if (candidateFile.equals(canonicalFile)) {
                continue;
            }
            if (candidateFile.exists() && candidateFile.isFile() && candidateFile.length() > 0L) {
                return candidateFile;
            }
        }
        return canonicalFile;
    }

    private List<String> buildImplicitFamilyCandidates(String worldName) {
        List<String> candidates = new ArrayList<>();
        String normalizedWorld = AuctionWorldScope.normalizeIdentifier(worldName);
        String familyBase = stripImplicitFamilySuffix(normalizedWorld);

        addCandidate(candidates, normalizedWorld);
        addCandidate(candidates, familyBase);
        addCandidate(candidates, familyBase + "_nether");
        addCandidate(candidates, familyBase + "_the_end");
        addCandidate(candidates, familyBase + "_end");
        addCandidate(candidates, familyBase + "-nether");
        addCandidate(candidates, familyBase + "-the_end");
        addCandidate(candidates, familyBase + "-end");
        return candidates;
    }

    private void addCandidate(List<String> candidates, String candidate) {
        if (candidate != null && !candidate.isBlank() && !candidates.contains(candidate)) {
            candidates.add(candidate);
        }
    }

    private String inferImplicitFamilyGroup(String worldName) {
        String normalizedWorld = AuctionWorldScope.normalizeIdentifier(worldName);
        if (normalizedWorld.isBlank()) {
            return "";
        }
        return stripImplicitFamilySuffix(normalizedWorld);
    }

    private String stripImplicitFamilySuffix(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return "";
        }

        String[] suffixes = {
                "_the_end",
                "_nether",
                "_end",
                "-the_end",
                "-nether",
                "-end"
        };
        for (String suffix : suffixes) {
            if (worldName.endsWith(suffix) && worldName.length() > suffix.length()) {
                return worldName.substring(0, worldName.length() - suffix.length());
            }
        }
        return worldName;
    }

    private AuctionWorldScope loadScopeMetadata(File file) {
        var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        String scopeKey = config.getString("scope.key");
        if (scopeKey == null || scopeKey.isBlank()) {
            String baseName = file.getName().replaceFirst("\\.yml$", "");
            if (file.getParentFile() != null && file.getParentFile().getName().equalsIgnoreCase("grouped")) {
                scopeKey = AuctionWorldScope.groupScopeKey(baseName);
            } else if (file.getParentFile() != null && file.getParentFile().getName().equalsIgnoreCase("per-world")) {
                scopeKey = AuctionWorldScope.worldScopeKey(baseName);
            } else {
                scopeKey = AuctionWorldScope.GLOBAL_SCOPE_KEY;
            }
        }

        String displayName = config.getString("scope.display-name", plugin.getConfigManager().getScopeDisplayName(scopeKey));
        String worldName = config.getString("scope.world-name", "");
        return new AuctionWorldScope(scopeKey, displayName, worldName, true);
    }

    private void mergeLegacyWorldFileIntoGroupFile(File sourceFile, File targetFile, AuctionWorldScope targetScope) {
        if (!sourceFile.isFile()) {
            return;
        }

        YamlConfiguration source = YamlConfiguration.loadConfiguration(sourceFile);
        boolean sourceHasAuctionData = hasAuctionData(source);

        if (!sourceHasAuctionData && !targetFile.exists()) {
            deleteQuietly(sourceFile);
            return;
        }

        YamlConfiguration target = targetFile.exists()
                ? YamlConfiguration.loadConfiguration(targetFile)
                : new YamlConfiguration();

        mergeActiveListings(source, target);
        mergeTransactions(source, target);
        mergeReturns(source, target);
        target.set("scope.key", targetScope.key());
        target.set("scope.display-name", targetScope.displayName());
        target.set("scope.world-name", targetScope.worldName());

        writeYaml(targetFile, target);
        deleteQuietly(sourceFile);
    }

    private boolean hasAuctionData(YamlConfiguration config) {
        return hasKeys(config.getConfigurationSection("active"))
                || hasKeys(config.getConfigurationSection("transactions"))
                || hasKeys(config.getConfigurationSection("returns"));
    }

    private boolean hasKeys(ConfigurationSection section) {
        return section != null && !section.getKeys(false).isEmpty();
    }

    private void mergeActiveListings(YamlConfiguration source, YamlConfiguration target) {
        ConfigurationSection activeSection = source.getConfigurationSection("active");
        if (activeSection == null) {
            return;
        }

        for (String listingId : activeSection.getKeys(false)) {
            String targetPath = "active." + listingId;
            if (target.contains(targetPath)) {
                continue;
            }
            copySection(activeSection.getConfigurationSection(listingId), target, targetPath);
        }
    }

    private void mergeTransactions(YamlConfiguration source, YamlConfiguration target) {
        ConfigurationSection transactionsSection = source.getConfigurationSection("transactions");
        if (transactionsSection == null) {
            return;
        }

        for (String playerKey : transactionsSection.getKeys(false)) {
            ConfigurationSection sourcePlayer = transactionsSection.getConfigurationSection(playerKey);
            if (sourcePlayer == null) {
                continue;
            }

            ConfigurationSection targetPlayer = target.getConfigurationSection("transactions." + playerKey);
            int nextIndex = targetPlayer == null ? 0 : targetPlayer.getKeys(false).stream()
                    .map(this::parseNumericIndex)
                    .max(Comparator.naturalOrder())
                    .orElse(-1) + 1;

            for (String entryKey : sourcePlayer.getKeys(false).stream()
                    .sorted(Comparator.comparingInt(this::parseNumericIndex))
                    .toList()) {
                copySection(sourcePlayer.getConfigurationSection(entryKey),
                        target,
                        "transactions." + playerKey + "." + nextIndex);
                nextIndex++;
            }
        }
    }

    private void mergeReturns(YamlConfiguration source, YamlConfiguration target) {
        ConfigurationSection returnsSection = source.getConfigurationSection("returns");
        if (returnsSection == null) {
            return;
        }

        for (String playerKey : returnsSection.getKeys(false)) {
            ConfigurationSection sourcePlayer = returnsSection.getConfigurationSection(playerKey);
            if (sourcePlayer == null) {
                continue;
            }

            ConfigurationSection targetPlayer = target.getConfigurationSection("returns." + playerKey);
            int nextIndex = targetPlayer == null ? 0 : targetPlayer.getKeys(false).stream()
                    .map(this::parseNumericIndex)
                    .max(Comparator.naturalOrder())
                    .orElse(-1) + 1;

            for (String entryKey : sourcePlayer.getKeys(false).stream()
                    .sorted(Comparator.comparingInt(this::parseNumericIndex))
                    .toList()) {
                copySection(sourcePlayer.getConfigurationSection(entryKey),
                        target,
                        "returns." + playerKey + "." + nextIndex);
                nextIndex++;
            }
        }
    }

    private void copySection(ConfigurationSection sourceSection, YamlConfiguration target, String targetPath) {
        if (sourceSection == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : sourceSection.getValues(true).entrySet()) {
            target.set(targetPath + "." + entry.getKey(), entry.getValue());
        }
    }

    private int parseNumericIndex(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private void writeYaml(File targetFile, YamlConfiguration config) {
        try {
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.writeString(targetFile.toPath(), config.saveToString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to migrate auction data file " + targetFile.getAbsolutePath()
                    + ": " + e.getMessage());
        }
    }

    private void deleteQuietly(File file) {
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Failed to delete legacy grouped auction file: " + file.getAbsolutePath());
        }
    }

    private String sanitizeWorldFileComponent(String worldName) {
        return new AuctionWorldScope(
                AuctionWorldScope.worldScopeKey(worldName),
                plugin.getConfigManager().getConfiguredWorldDisplayName(worldName),
                worldName,
                true
        ).sanitizedFileComponent();
    }

    private String resolveWorldName(World world) {
        if (world == null) {
            return plugin.getConfigManager().getGuiLabel("main.labels.unknown-world", "Unknown World");
        }
        if (multiverseResolver.isAvailable() && multiverseResolver.isKnownWorld(world)) {
            return multiverseResolver.getCanonicalWorldName(world);
        }
        return bukkitResolver.getCanonicalWorldName(world);
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
        return builder.toString();
    }
}
