package dev.crown.economy.gui;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GUIManager {

    private static final Map<UUID, AuctionHouseGUI> openAHGuis = new ConcurrentHashMap<>();
    private static final Map<UUID, MyListingsGUI> openMyListings = new ConcurrentHashMap<>();
    private static final Map<UUID, TransactionsGUI> openTransactions = new ConcurrentHashMap<>();
    private static final Map<UUID, ConfirmPurchaseGUI> openConfirms = new ConcurrentHashMap<>();
    private static final Map<UUID, AuctionCategoryGUI> openCategories = new ConcurrentHashMap<>();
    private static final Map<UUID, AuctionHouseGUI> pendingSearches = new ConcurrentHashMap<>();
    private static final Set<UUID> suppressCloseReopen = ConcurrentHashMap.newKeySet();

    private GUIManager() {
    }

    public static void setOpenAH(UUID uuid, AuctionHouseGUI gui) {
        openAHGuis.put(uuid, gui);
    }

    public static AuctionHouseGUI getOpenAH(UUID uuid) {
        return openAHGuis.get(uuid);
    }

    public static void removeOpenAH(UUID uuid) {
        openAHGuis.remove(uuid);
    }

    public static void setOpenMyListings(UUID uuid, MyListingsGUI gui) {
        openMyListings.put(uuid, gui);
    }

    public static MyListingsGUI getOpenMyListings(UUID uuid) {
        return openMyListings.get(uuid);
    }

    public static void removeOpenMyListings(UUID uuid) {
        openMyListings.remove(uuid);
    }

    public static void setOpenTransactions(UUID uuid, TransactionsGUI gui) {
        openTransactions.put(uuid, gui);
    }

    public static TransactionsGUI getOpenTransactions(UUID uuid) {
        return openTransactions.get(uuid);
    }

    public static void removeOpenTransactions(UUID uuid) {
        openTransactions.remove(uuid);
    }

    public static void setOpenConfirm(UUID uuid, ConfirmPurchaseGUI gui) {
        openConfirms.put(uuid, gui);
    }

    public static ConfirmPurchaseGUI getOpenConfirm(UUID uuid) {
        return openConfirms.get(uuid);
    }

    public static void removeOpenConfirm(UUID uuid) {
        openConfirms.remove(uuid);
    }

    public static void setOpenCategory(UUID uuid, AuctionCategoryGUI gui) {
        openCategories.put(uuid, gui);
    }

    public static AuctionCategoryGUI getOpenCategory(UUID uuid) {
        return openCategories.get(uuid);
    }

    public static void removeOpenCategory(UUID uuid) {
        openCategories.remove(uuid);
    }

    public static void setPendingSearch(UUID uuid, AuctionHouseGUI gui) {
        pendingSearches.put(uuid, gui);
    }

    public static AuctionHouseGUI getPendingSearch(UUID uuid) {
        return pendingSearches.get(uuid);
    }

    public static void removePendingSearch(UUID uuid) {
        pendingSearches.remove(uuid);
    }

    public static void clearAll(UUID uuid) {
        openAHGuis.remove(uuid);
        openMyListings.remove(uuid);
        openTransactions.remove(uuid);
        openConfirms.remove(uuid);
        openCategories.remove(uuid);
        pendingSearches.remove(uuid);
        suppressCloseReopen.remove(uuid);
    }

    public static void suppressNextCloseReopen(UUID uuid) {
        suppressCloseReopen.add(uuid);
    }

    public static boolean consumeCloseReopenSuppression(UUID uuid) {
        return suppressCloseReopen.remove(uuid);
    }
}
