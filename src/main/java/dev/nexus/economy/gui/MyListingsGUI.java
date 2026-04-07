package dev.nexus.economy.gui;

import dev.nexus.economy.NexusEconomy;
import dev.nexus.economy.auction.AuctionListing;
import dev.nexus.economy.utils.GuiUtil;
import dev.nexus.economy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * My Listings GUI (6 rows) - shows the player's own active listings with cancel option.
 */
public class MyListingsGUI {

    private static final int[] ITEM_SLOTS = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25,
        28,29,30,31,32,33,34,
        37,38,39,40,41,42,43
    };

    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_NEXT = 53;

    private final NexusEconomy plugin;
    private final Player player;
    private final AuctionHouseGUI parent;
    private final Inventory inventory;
    private int page = 0;
    private List<AuctionListing> myListings;

    public MyListingsGUI(NexusEconomy plugin, Player player, AuctionHouseGUI parent) {
        this.plugin = plugin;
        this.player = player;
        this.parent = parent;
        this.inventory = Bukkit.createInventory(null, 54,
                MessageUtil.color("&8» &bMy Listings &8«"));
        refresh();
    }

    public void open() {
        GUIManager.setOpenMyListings(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    private void refresh() {
        inventory.clear();
        myListings = plugin.getAuctionManager().getPlayerListings(player.getUniqueId());

        GuiUtil.fillBorder(inventory, Material.CYAN_STAINED_GLASS_PANE);

        int start = page * ITEM_SLOTS.length;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int idx = start + i;
            if (idx < myListings.size()) {
                inventory.setItem(ITEM_SLOTS[i], buildListingItem(myListings.get(idx)));
            }
        }

        // Nav
        boolean hasPrev = page > 0;
        boolean hasNext = (page + 1) * ITEM_SLOTS.length < myListings.size();

        inventory.setItem(SLOT_PREV, GuiUtil.makeItem(
                hasPrev ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE,
                hasPrev ? "&b← Previous" : "&8← Previous"));

        inventory.setItem(SLOT_BACK, GuiUtil.makeItem(
                Material.BARRIER, "&cBack to Auction House",
                "&7Return to browsing"));

        inventory.setItem(SLOT_NEXT, GuiUtil.makeItem(
                hasNext ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE,
                hasNext ? "&bNext →" : "&8Next →"));

        // Show empty state
        if (myListings.isEmpty()) {
            inventory.setItem(22, GuiUtil.makeItem(Material.PAPER,
                    "&eNo Active Listings",
                    "&7You don't have any items listed.",
                    "&7Use &b/ah sell <price> &7to list an item!"));
        }
    }

    private ItemStack buildListingItem(AuctionListing listing) {
        ItemStack display = listing.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        meta.setDisplayName(MessageUtil.color("&f" + MessageUtil.getItemName(listing.getItem())));
        List<String> lore = new ArrayList<>();
        List<String> orig = meta.getLore();
        if (orig != null && !orig.isEmpty()) {
            lore.addAll(orig);
            lore.add("");
        }
        lore.add(MessageUtil.color("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        lore.add(MessageUtil.color("&7Price: &a$" + MessageUtil.formatPrice(listing.getPrice())));
        lore.add(MessageUtil.color("&7Expires: &e" + listing.getTimeLeftFormatted()));
        lore.add(MessageUtil.color("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        lore.add(MessageUtil.color("&cRight-click &7to &ccancel listing"));
        lore.add(MessageUtil.color("&8ID: &7" + listing.getId().toString().substring(0, 8)));
        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    public void handleClick(int slot, boolean rightClick) {
        if (slot == SLOT_PREV) {
            if (page > 0) { page--; refresh(); }
            return;
        }
        if (slot == SLOT_NEXT) {
            if ((page + 1) * ITEM_SLOTS.length < myListings.size()) { page++; refresh(); }
            return;
        }
        if (slot == SLOT_BACK) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                parent.refresh();
                GUIManager.setOpenAH(player.getUniqueId(), parent);
                parent.open();
            }, 1L);
            return;
        }

        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) {
                int idx = page * ITEM_SLOTS.length + i;
                if (idx < myListings.size() && rightClick) {
                    AuctionListing listing = myListings.get(idx);
                    boolean cancelled = plugin.getAuctionManager()
                            .cancelListing(player, listing.getId(), false);
                    if (cancelled) {
                        player.sendMessage(plugin.getConfigManager()
                                .getMessage("auction-house.listing-cancelled"));
                    }
                    refresh();
                }
                return;
            }
        }
    }

    public Inventory getInventory() { return inventory; }
}
