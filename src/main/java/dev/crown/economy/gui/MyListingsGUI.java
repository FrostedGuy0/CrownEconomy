package dev.crown.economy.gui;

import dev.crown.economy.CrownEconomy;
import dev.crown.economy.auction.AuctionListing;
import dev.crown.economy.auction.AuctionHouseManager;
import dev.crown.economy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyListingsGUI {

    private final CrownEconomy plugin;
    private final Player player;
    private final AuctionHouseGUI parent;
    private Inventory inventory;
    private List<AuctionListing> listings = List.of();

    public MyListingsGUI(CrownEconomy plugin, Player player, AuctionHouseGUI parent) {
        this.plugin = plugin;
        this.player = player;
        this.parent = parent;
        this.inventory = Bukkit.createInventory(null, plugin.getConfigManager().getMyListingsSize(),
                plugin.getConfigManager().getMyListingsTitle());
        refresh();
    }

    public void open() {
        GUIManager.setOpenMyListings(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    public void refresh() {
        if (inventory.getSize() != plugin.getConfigManager().getMyListingsSize()) {
            inventory = Bukkit.createInventory(null, plugin.getConfigManager().getMyListingsSize(),
                    plugin.getConfigManager().getMyListingsTitle());
        }
        inventory.clear();

        listings = plugin.getAuctionHouseManager().getPlayerListings(player);
        List<Integer> slots = plugin.getConfigManager().getMyListingSlots();
        for (int i = 0; i < slots.size(); i++) {
            int index = i;
            if (index >= listings.size()) {
                break;
            }
            inventory.setItem(slots.get(i), buildListingItem(listings.get(index)));
        }

        putButton("transactions", plugin.getAuctionHouseManager().getAuctionScopePlaceholders(player));

        if (listings.isEmpty()) {
            inventory.setItem(plugin.getConfigManager().getMyListingsEmptyStateSlot(),
                    plugin.getConfigManager().createGuiItem("my-listings.empty-state",
                            plugin.getAuctionHouseManager().getAuctionScopePlaceholders(player)));
        }
    }

    private ItemStack buildListingItem(AuctionListing listing) {
        ItemStack display = listing.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }

        List<String> lore = new ArrayList<>();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{price}", plugin.getConfigManager().formatPrice(listing.getPrice()));
        placeholders.put("{expires}", plugin.getConfigManager().formatTimeLeft(listing.getExpiresAt()));
        placeholders.put("{item}", listing.getDisplayName());
        placeholders.put("{scope}", plugin.getConfigManager().getScopeDisplayName(listing.getScopeKey()));
        String nameTemplate = plugin.getConfigManager().getGui().getString("my-listings.listing-item.name", "&f{item}");
        meta.setDisplayName(MessageUtil.color(applyPlaceholders(nameTemplate, placeholders)));
        for (String line : plugin.getConfigManager().getGui().getStringList("my-listings.listing-item.lore")) {
            lore.add(MessageUtil.color(applyPlaceholders(line, placeholders)));
        }
        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private void putButton(String key, Map<String, String> placeholders) {
        int slot = plugin.getConfigManager().getButtonSlot("my-listings", key);
        if (slot < 0) {
            return;
        }
        inventory.setItem(slot, plugin.getConfigManager().createGuiItem("my-listings.buttons." + key, placeholders));
    }

    public void handleClick(int slot, boolean rightClick) {
        if (slot == plugin.getConfigManager().getButtonSlot("my-listings", "transactions")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                GUIManager.suppressNextCloseReopen(player.getUniqueId());
                TransactionsGUI gui = new TransactionsGUI(plugin, player, this);
                GUIManager.setOpenTransactions(player.getUniqueId(), gui);
                gui.open();
            });
            return;
        }

        List<Integer> slots = plugin.getConfigManager().getMyListingSlots();
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) == slot) {
                int index = i;
                if (index < listings.size() && rightClick) {
                    AuctionListing listing = listings.get(index);
                    AuctionHouseManager.CancelResult result = plugin.getAuctionHouseManager().cancelListing(player, listing.getId(), false);
                    if (result == AuctionHouseManager.CancelResult.SUCCESS) {
                        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-cancelled"));
                    } else if (result == AuctionHouseManager.CancelResult.NOT_OWNER) {
                        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.not-owner"));
                    } else {
                        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-id-invalid"));
                    }
                    refresh();
                    player.updateInventory();
                }
                return;
            }
        }
    }

    public void reopenParent() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            GUIManager.suppressNextCloseReopen(player.getUniqueId());
            parent.refresh();
            GUIManager.setOpenAH(player.getUniqueId(), parent);
            parent.open();
        });
    }

    public Inventory getInventory() {
        return inventory;
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
