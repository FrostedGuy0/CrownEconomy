package dev.nexus.economy.gui;

import dev.nexus.economy.NexusEconomy;
import dev.nexus.economy.auction.AuctionListing;
import dev.nexus.economy.config.ConfigManager;
import dev.nexus.economy.utils.GuiUtil;
import dev.nexus.economy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class AuctionHouseGUI {

    private static final int[] ITEM_SLOTS = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25,
        28,29,30,31,32,33,34,
        37,38,39,40,41,42,43
    };
    private static final int ITEMS_PER_PAGE = ITEM_SLOTS.length;

    private static final int SLOT_PREV    = 45;
    private static final int SLOT_SORT    = 46;
    private static final int SLOT_CAT     = 47;
    private static final int SLOT_SEARCH  = 48;
    private static final int SLOT_INFO    = 49;
    private static final int SLOT_MY_LIST = 50;
    private static final int SLOT_EXPIRED = 51;
    private static final int SLOT_NEXT    = 53;

    public enum SortMode {
        PRICE_ASC("Price: Low → High", Material.GOLD_INGOT),
        PRICE_DESC("Price: High → Low", Material.DIAMOND),
        TIME_ASC("Time: Ending Soon", Material.CLOCK),
        TIME_DESC("Time: Newest First", Material.CLOCK);

        public final String label;
        public final Material icon;
        SortMode(String label, Material icon) { this.label = label; this.icon = icon; }

        public SortMode next() {
            SortMode[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    private final NexusEconomy plugin;
    private final Player player;
    private final Inventory inventory;
    private int page = 0;
    private SortMode sortMode = SortMode.PRICE_ASC;
    private String searchQuery = null;
    private String categoryFilter = null;
    private List<AuctionListing> listings;

    public AuctionHouseGUI(NexusEconomy plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, 54,
                plugin.getConfigManager().getAHTitle());
        refresh();
    }

    public void open() {
        GUIManager.setOpenAH(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    public void refresh() {
        inventory.clear();
        listings = getFilteredSortedListings();

        ConfigManager cfg = plugin.getConfigManager();
        Material filler = parseMaterial(cfg.getFillerMaterial(), Material.BLACK_STAINED_GLASS_PANE);
        GuiUtil.fillBorder(inventory, filler);

        int start = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int listingIdx = start + i;
            if (listingIdx < listings.size()) {
                inventory.setItem(ITEM_SLOTS[i], buildListingItem(listings.get(listingIdx)));
            }
        }

        buildNavBar();
    }

    private void buildNavBar() {
        ConfigManager cfg = plugin.getConfigManager();
        boolean hasNext = (page + 1) * ITEMS_PER_PAGE < listings.size();
        boolean hasPrev = page > 0;

        inventory.setItem(SLOT_PREV, GuiUtil.makeItem(
                hasPrev ? Material.ARROW : Material.BLACK_STAINED_GLASS_PANE,
                hasPrev ? "&b← Previous Page" : "&8← Previous Page",
                hasPrev ? "&7Page &f" + page + " &7→ &f" + (page) : "&8No previous page"
        ));

        inventory.setItem(SLOT_SORT, GuiUtil.makeItem(
                Material.HOPPER,
                "&bSort: &f" + sortMode.label,
                "&7Click to change sorting",
                "&8Current: &e" + sortMode.label
        ));

        String catLabel = categoryFilter == null ? "All" : categoryFilter;
        inventory.setItem(SLOT_CAT, GuiUtil.makeItem(
                Material.COMPASS,
                "&bCategory: &f" + catLabel,
                "&7Click to filter by category"
        ));

        String searchLabel = searchQuery == null ? "&7None" : "&f" + searchQuery;
        inventory.setItem(SLOT_SEARCH, GuiUtil.makeItem(
                Material.SPYGLASS,
                "&bSearch",
                "&7Current: " + searchLabel,
                "&7Click to search listings"
        ));

        int total = plugin.getAuctionManager().getActiveListings().size();
        int myListings = plugin.getAuctionManager().getPlayerListings(player.getUniqueId()).size();
        String tier = plugin.getLuckPermsHook().getListingTier(player);
        int maxListings = cfg.getMaxListings(tier);
        inventory.setItem(SLOT_INFO, GuiUtil.makeItem(
                Material.BOOK,
                "&bAuction House",
                "&7Total listings: &f" + total,
                "&7Your listings: &f" + myListings + " &7/ &f" + maxListings,
                "&7Tax rate: &f" + cfg.getTaxRate() + "%"
        ));

        inventory.setItem(SLOT_MY_LIST, GuiUtil.makeItem(
                Material.CHEST,
                "&bMy Listings",
                "&7View your active listings",
                "&7You have &f" + myListings + " &7active listing(s)"
        ));

        int expiredCount = plugin.getAuctionManager().getExpiredBin(player.getUniqueId()).size();
        inventory.setItem(SLOT_EXPIRED, expiredCount > 0
                ? GuiUtil.makeGlowItem(Material.ENDER_CHEST,
                        "&6⚠ Collect Expired &8(" + expiredCount + ")",
                        "&7You have &e" + expiredCount + " &7expired listing(s)!",
                        "&aClick to collect all")
                : GuiUtil.makeItem(Material.ENDER_CHEST,
                        "&bExpired Items",
                        "&7No expired items to collect")
        );

        inventory.setItem(SLOT_NEXT, GuiUtil.makeItem(
                hasNext ? Material.ARROW : Material.BLACK_STAINED_GLASS_PANE,
                hasNext ? "&bNext Page →" : "&8Next Page →",
                hasNext ? "&7Page &f" + (page + 2) + " &7of &f" + getTotalPages() : "&8No next page"
        ));
    }

    private ItemStack buildListingItem(AuctionListing listing) {
        ItemStack display = listing.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        String itemName = MessageUtil.getItemName(listing.getItem());
        meta.setDisplayName(MessageUtil.color("&f" + itemName));

        List<String> lore = new ArrayList<>();
        
        List<String> original = meta.getLore();
        if (original != null && !original.isEmpty()) {
            lore.addAll(original);
            lore.add("");
        }
        lore.add(MessageUtil.color("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        lore.add(MessageUtil.color("&7Seller: &f" + listing.getSellerName()));
        lore.add(MessageUtil.color("&7Price: &a$" + MessageUtil.formatPrice(listing.getPrice())));
        lore.add(MessageUtil.color("&7Expires: &e" + listing.getTimeLeftFormatted()));
        lore.add(MessageUtil.color("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        lore.add(MessageUtil.color("&aLeft-click &7to &apurchase"));
        lore.add(MessageUtil.color("&8ID: &7" + listing.getId().toString().substring(0, 8)));

        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    public void handleClick(int slot, boolean leftClick) {
        if (slot == SLOT_PREV) {
            if (page > 0) { page--; refresh(); }
            return;
        }
        if (slot == SLOT_NEXT) {
            if ((page + 1) * ITEMS_PER_PAGE < listings.size()) { page++; refresh(); }
            return;
        }
        if (slot == SLOT_SORT) {
            sortMode = sortMode.next();
            page = 0;
            refresh();
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.no-listings")
                    .replace(plugin.getConfigManager().getMessage("auction-house.no-listings"), ""));
            
            return;
        }
        if (slot == SLOT_CAT) {
            player.closeInventory();
            AuctionCategoryGUI catGui = new AuctionCategoryGUI(plugin, player, this);
            GUIManager.setOpenCategory(player.getUniqueId(), catGui);
            catGui.open();
            return;
        }
        if (slot == SLOT_SEARCH) {
            player.closeInventory();
            plugin.getAuctionManager(); 
            GUIManager.setPendingSearch(player.getUniqueId(), this);
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.search-prompt"));
            return;
        }
        if (slot == SLOT_MY_LIST) {
            player.closeInventory();
            MyListingsGUI myListings = new MyListingsGUI(plugin, player, this);
            GUIManager.setOpenMyListings(player.getUniqueId(), myListings);
            myListings.open();
            return;
        }
        if (slot == SLOT_EXPIRED) {
            int count = plugin.getAuctionManager().collectExpired(player);
            if (count == 0) {
                player.sendMessage(plugin.getConfigManager().getMessage("auction-house.no-expired"));
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("auction-house.expired-collected")
                        .replace("{count}", String.valueOf(count)));
            }
            player.closeInventory();
            return;
        }

        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) {
                int idx = page * ITEMS_PER_PAGE + i;
                if (idx < listings.size()) {
                    handleListingClick(listings.get(idx));
                }
                return;
            }
        }
    }

    private void handleListingClick(AuctionListing listing) {
        player.closeInventory();
        ConfirmPurchaseGUI confirmGUI = new ConfirmPurchaseGUI(plugin, player, listing, this);
        GUIManager.setOpenConfirm(player.getUniqueId(), confirmGUI);
        confirmGUI.open();
    }

    private List<AuctionListing> getFilteredSortedListings() {
        List<AuctionListing> result = new ArrayList<>(plugin.getAuctionManager().getActiveListings());

        if (searchQuery != null && !searchQuery.isEmpty()) {
            String q = searchQuery.toLowerCase();
            result = result.stream()
                    .filter(l -> MessageUtil.getItemName(l.getItem()).toLowerCase().contains(q))
                    .collect(Collectors.toList());
        }

        switch (sortMode) {
            case PRICE_ASC  -> result.sort(Comparator.comparingDouble(AuctionListing::getPrice));
            case PRICE_DESC -> result.sort(Comparator.comparingDouble(AuctionListing::getPrice).reversed());
            case TIME_ASC   -> result.sort(Comparator.comparingLong(AuctionListing::getExpiresAt));
            case TIME_DESC  -> result.sort(Comparator.comparingLong(AuctionListing::getExpiresAt).reversed());
        }
        return result;
    }

    private int getTotalPages() {
        return Math.max(1, (int) Math.ceil((double) listings.size() / ITEMS_PER_PAGE));
    }

    public Inventory getInventory() { return inventory; }
    public void setSearchQuery(String q) { this.searchQuery = q; page = 0; }
    public void setCategoryFilter(String cat) { this.categoryFilter = cat; page = 0; }
    public String getCategoryFilter() { return categoryFilter; }
    public SortMode getSortMode() { return sortMode; }

    private static Material parseMaterial(String s, Material def) {
        try { return Material.valueOf(s); } catch (Exception e) { return def; }
    }
}
