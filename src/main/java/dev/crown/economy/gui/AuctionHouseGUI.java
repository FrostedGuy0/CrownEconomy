package dev.crown.economy.gui;

import dev.crown.economy.CrownEconomy;
import dev.crown.economy.auction.AuctionListing;
import dev.crown.economy.config.ConfigManager;
import dev.crown.economy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AuctionHouseGUI {

    private final CrownEconomy plugin;
    private final Player player;
    private Inventory inventory;
    private int page = 0;
    private SortMode sortMode = SortMode.PRICE_ASC;
    private String searchQuery;
    private String categoryFilter;
    private List<AuctionListing> visibleListings = List.of();

    public AuctionHouseGUI(CrownEconomy plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, plugin.getConfigManager().getMainSize(),
                plugin.getConfigManager().getMainTitle());
        refresh();
    }

    public void open() {
        GUIManager.setOpenAH(player.getUniqueId(), this);
        GUIManager.removePendingSearch(player.getUniqueId());
        player.openInventory(inventory);
    }

    public void refresh() {
        ConfigManager cfg = plugin.getConfigManager();
        if (inventory.getSize() != cfg.getMainSize()) {
            inventory = Bukkit.createInventory(null, cfg.getMainSize(), cfg.getMainTitle());
        }
        inventory.clear();

        visibleListings = getFilteredSortedListings();
        List<Integer> slots = cfg.getMainListingSlots();
        int slotsPerPage = slots.size();
        int totalPages = Math.max(1, (int) Math.ceil(visibleListings.size() / (double) slotsPerPage));
        if (page >= totalPages) {
            page = Math.max(0, totalPages - 1);
        }

        int start = page * slotsPerPage;
        for (int i = 0; i < slots.size(); i++) {
            int index = start + i;
            if (index >= visibleListings.size()) {
                break;
            }
            inventory.setItem(slots.get(i), buildListingItem(visibleListings.get(index)));
        }

        if (visibleListings.isEmpty()) {
            Map<String, String> placeholders = Map.of(
                    "{sort}", cfg.getSortLabel(sortMode),
                    "{category}", getCategoryLabel(),
                    "{search}", getSearchLabel()
            );
            inventory.setItem(cfg.getMainEmptyStateSlot(), plugin.getConfigManager().createGuiItem("main.empty-state", placeholders));
        }

        buildButtons(totalPages);
    }

    private void buildButtons(int totalPages) {
        ConfigManager cfg = plugin.getConfigManager();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{sort}", cfg.getSortLabel(sortMode));
        placeholders.put("{category}", getCategoryLabel());
        placeholders.put("{search}", getSearchLabel());
        placeholders.put("{my-count}", String.valueOf(plugin.getAuctionManager().getPlayerListings(player.getUniqueId()).size()));
        placeholders.put("{page}", String.valueOf(page + 1));
        placeholders.put("{pages}", String.valueOf(totalPages));

        if (page > 0) {
            putButton("previous", placeholders);
        }
        putButton("sort", placeholders);
        putButton("category", placeholders);
        putButton("search", placeholders);
        putButton("refresh", placeholders);
        putButton("my-listings", placeholders);
        putButton("next", placeholders);
    }

    private void putButton(String key, Map<String, String> placeholders) {
        int slot = plugin.getConfigManager().getButtonSlot("main", key);
        if (slot < 0) {
            return;
        }
        inventory.setItem(slot, plugin.getConfigManager().createGuiItem("main.buttons." + key, placeholders));
    }

    private ItemStack buildListingItem(AuctionListing listing) {
        ItemStack display = listing.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }

        List<String> lore = new ArrayList<>();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{seller}", listing.getSellerName() == null || listing.getSellerName().isBlank()
                ? plugin.getConfigManager().getGuiLabel("main.labels.unknown-seller", "Unknown")
                : listing.getSellerName());
        placeholders.put("{price}", plugin.getConfigManager().formatPrice(listing.getPrice()));
        placeholders.put("{expires}", plugin.getConfigManager().formatTimeLeft(listing.getExpiresAt()));
        placeholders.put("{item}", listing.getDisplayName());
        String nameTemplate = plugin.getConfigManager().getGui().getString("main.listing-item.name", "&f{item}");
        meta.setDisplayName(MessageUtil.color(applyPlaceholders(nameTemplate, placeholders)));
        for (String line : plugin.getConfigManager().getGui().getStringList("main.listing-item.lore")) {
            lore.add(MessageUtil.color(applyPlaceholders(line, placeholders)));
        }
        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    public void handleClick(int slot, boolean leftClick) {
        ConfigManager cfg = plugin.getConfigManager();
        if (slot == cfg.getButtonSlot("main", "previous")) {
            if (page > 0) {
                page--;
                refresh();
                player.updateInventory();
            }
            return;
        }

        if (slot == cfg.getButtonSlot("main", "next")) {
            if ((page + 1) * cfg.getMainListingSlots().size() < visibleListings.size()) {
                page++;
                refresh();
                player.updateInventory();
            }
            return;
        }

        if (slot == cfg.getButtonSlot("main", "sort")) {
            sortMode = sortMode.next();
            page = 0;
            refresh();
            player.updateInventory();
            return;
        }

        if (slot == cfg.getButtonSlot("main", "category")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                AuctionCategoryGUI categoryGUI = new AuctionCategoryGUI(plugin, player, this);
                GUIManager.setOpenCategory(player.getUniqueId(), categoryGUI);
                categoryGUI.open();
            });
            return;
        }

        if (slot == cfg.getButtonSlot("main", "search")) {
            GUIManager.setPendingSearch(player.getUniqueId(), this);
            player.sendMessage(cfg.getMessage("auction-house.search-prompt"));
            return;
        }

        if (slot == cfg.getButtonSlot("main", "refresh")) {
            refresh();
            player.updateInventory();
            return;
        }

        if (slot == cfg.getButtonSlot("main", "my-listings")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                MyListingsGUI gui = new MyListingsGUI(plugin, player, this);
                GUIManager.setOpenMyListings(player.getUniqueId(), gui);
                gui.open();
            });
            return;
        }

        List<Integer> slots = cfg.getMainListingSlots();
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) == slot) {
                int index = page * slots.size() + i;
                if (leftClick && index < visibleListings.size()) {
                    openConfirm(visibleListings.get(index));
                }
                return;
            }
        }
    }

    private void openConfirm(AuctionListing listing) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ConfirmPurchaseGUI confirmGUI = new ConfirmPurchaseGUI(plugin, player, listing, this);
            GUIManager.setOpenConfirm(player.getUniqueId(), confirmGUI);
            confirmGUI.open();
        });
    }

    private List<AuctionListing> getFilteredSortedListings() {
        List<AuctionListing> listings = new ArrayList<>(plugin.getAuctionManager().getActiveListings());

        if (searchQuery != null && !searchQuery.isBlank()) {
            String query = searchQuery.toLowerCase(Locale.ROOT);
            listings.removeIf(listing -> {
                String itemName = listing.getDisplayName().toLowerCase(Locale.ROOT);
                String seller = listing.getSellerName() == null ? "" : listing.getSellerName().toLowerCase(Locale.ROOT);
                String material = listing.getItem().getType().name().toLowerCase(Locale.ROOT);
                return !(itemName.contains(query) || seller.contains(query) || material.contains(query));
            });
        }

        if (categoryFilter != null && plugin.getConfigManager().isCategoryEnabled()) {
            listings.removeIf(listing -> !plugin.getConfigManager().matchesCategory(categoryFilter, listing.getItem()));
        }

        return switch (sortMode) {
            case PRICE_ASC -> listings.stream().sorted(Comparator.comparingDouble(AuctionListing::getPrice)).toList();
            case PRICE_DESC -> listings.stream().sorted(Comparator.comparingDouble(AuctionListing::getPrice).reversed()).toList();
            case TIME_ASC -> listings.stream().sorted(Comparator.comparingLong(AuctionListing::getExpiresAt)).toList();
            case TIME_DESC -> listings.stream().sorted(Comparator.comparingLong(AuctionListing::getExpiresAt).reversed()).toList();
        };
    }

    private String getCategoryLabel() {
        return categoryFilter == null || categoryFilter.isBlank()
                ? plugin.getConfigManager().getGuiLabel("main.labels.all", "All")
                : categoryFilter;
    }

    private String getSearchLabel() {
        return searchQuery == null || searchQuery.isBlank()
                ? plugin.getConfigManager().getGuiLabel("main.labels.none", "None")
                : searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery == null || searchQuery.isBlank() ? null : searchQuery;
        this.page = 0;
    }

    public void setCategoryFilter(String categoryFilter) {
        this.categoryFilter = categoryFilter;
        this.page = 0;
    }

    public String getCategoryFilter() {
        return categoryFilter;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public enum SortMode {
        PRICE_ASC,
        PRICE_DESC,
        TIME_ASC,
        TIME_DESC;

        public SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
