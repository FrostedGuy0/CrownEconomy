package dev.crown.economy.gui;

import dev.crown.economy.CrownEconomy;
import dev.crown.economy.auction.AuctionListing;
import dev.crown.economy.auction.AuctionHouseManager;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class ConfirmPurchaseGUI {

    private final CrownEconomy plugin;
    private final org.bukkit.entity.Player player;
    private final AuctionListing listing;
    private final AuctionHouseGUI parent;
    private final Inventory inventory;

    public ConfirmPurchaseGUI(CrownEconomy plugin, org.bukkit.entity.Player player, AuctionListing listing, AuctionHouseGUI parent) {
        this.plugin = plugin;
        this.player = player;
        this.listing = listing;
        this.parent = parent;
        this.inventory = Bukkit.createInventory(null, plugin.getConfigManager().getConfirmSize(),
                plugin.getConfigManager().getConfirmTitle());
        build();
    }

    private void build() {
        inventory.clear();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{item}", listing.getDisplayName());
        placeholders.put("{seller}", listing.getSellerName());
        placeholders.put("{price}", plugin.getConfigManager().formatPrice(listing.getPrice()));
        double tax = listing.getPrice() * (plugin.getConfigManager().getTaxRate() / 100.0);
        placeholders.put("{tax}", plugin.getConfigManager().formatPrice(tax));
        placeholders.put("{scope}", plugin.getConfigManager().getScopeDisplayName(listing.getScopeKey()));

        int itemSlot = plugin.getConfigManager().getGui().getInt("confirm.item-slot", 13);
        int confirmSlot = plugin.getConfigManager().getGui().getInt("confirm.confirm-slot", 11);
        int cancelSlot = plugin.getConfigManager().getGui().getInt("confirm.cancel-slot", 15);
        int infoSlot = plugin.getConfigManager().getGui().getInt("confirm.info-slot", 22);

        inventory.setItem(itemSlot, listing.getItem());
        inventory.setItem(confirmSlot, plugin.getConfigManager().createGuiItem("confirm.confirm-item", placeholders));
        inventory.setItem(cancelSlot, plugin.getConfigManager().createGuiItem("confirm.cancel-item", placeholders));
        inventory.setItem(infoSlot, createInfoItem(placeholders));
    }

    private ItemStack createInfoItem(Map<String, String> placeholders) {
        return plugin.getConfigManager().createGuiItem("confirm.info-item", placeholders);
    }

    public void open() {
        GUIManager.setOpenConfirm(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    public void handleClick(int slot) {
        int confirmSlot = plugin.getConfigManager().getGui().getInt("confirm.confirm-slot", 11);
        int cancelSlot = plugin.getConfigManager().getGui().getInt("confirm.cancel-slot", 15);

        if (slot == confirmSlot) {
            AuctionHouseManager.BuyResult result = plugin.getAuctionHouseManager().buyItem(player, listing.getId());
            switch (result) {
                case SUCCESS -> player.sendMessage(plugin.getConfigManager().getMessage("auction-house.item-purchased")
                        .replace("{item}", listing.getDisplayName())
                        .replace("{price}", plugin.getConfigManager().formatPrice(listing.getPrice())));
                case WORLD_DISABLED -> player.sendMessage(plugin.getConfigManager().getMessage("auction-house.disabled-in-world",
                        plugin.getAuctionHouseManager().getAuctionScopePlaceholders(player)));
                case NOT_FOUND -> player.sendMessage(plugin.getConfigManager().getMessage("auction-house.no-listings"));
                case OWN_LISTING -> player.sendMessage(plugin.getConfigManager().getMessage("auction-house.cannot-buy-own"));
                case NOT_ENOUGH_MONEY -> player.sendMessage(plugin.getConfigManager().getMessage("auction-house.not-enough-money")
                        .replace("{price}", plugin.getConfigManager().formatPrice(listing.getPrice())));
                case ECONOMY_UNAVAILABLE -> player.sendMessage(plugin.getConfigManager().getMessage("auction-house.economy-unavailable"));
            }
            reopenParent();
            return;
        }

        if (slot == cancelSlot) {
            reopenParent();
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
}
