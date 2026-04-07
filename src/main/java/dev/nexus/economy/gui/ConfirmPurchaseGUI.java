package dev.nexus.economy.gui;

import dev.nexus.economy.NexusEconomy;
import dev.nexus.economy.auction.AuctionListing;
import dev.nexus.economy.auction.AuctionManager;
import dev.nexus.economy.utils.GuiUtil;
import dev.nexus.economy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Confirm Purchase GUI (3 rows)
 *
 *  [ border ] [ item display ] [ border ]
 *  [ border ] [ confirm: YES ] [ space ] [ confirm: NO ] [ border ]
 *  [ border ] [ border ] [ border ]
 */
public class ConfirmPurchaseGUI {

    private final NexusEconomy plugin;
    private final Player player;
    private final AuctionListing listing;
    private final AuctionHouseGUI parent;
    private final Inventory inventory;

    private static final int SLOT_ITEM    = 13;
    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CANCEL  = 15;

    public ConfirmPurchaseGUI(NexusEconomy plugin, Player player,
                               AuctionListing listing, AuctionHouseGUI parent) {
        this.plugin = plugin;
        this.player = player;
        this.listing = listing;
        this.parent = parent;
        this.inventory = Bukkit.createInventory(null, 27,
                MessageUtil.color("&8» &bConfirm Purchase &8«"));
        build();
    }

    private void build() {
        GuiUtil.fillBorder(inventory, Material.GRAY_STAINED_GLASS_PANE);

        // Display the item being purchased
        var display = listing.getItem().clone();
        var meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            List<String> orig = meta.getLore();
            if (orig != null) lore.addAll(orig);
            if (!lore.isEmpty()) lore.add("");
            lore.add(MessageUtil.color("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            lore.add(MessageUtil.color("&7Seller: &f" + listing.getSellerName()));
            lore.add(MessageUtil.color("&7Price: &a$" + MessageUtil.formatPrice(listing.getPrice())));
            double tax = listing.getPrice() * (plugin.getConfigManager().getTaxRate() / 100.0);
            lore.add(MessageUtil.color("&7Tax (" + (int)plugin.getConfigManager().getTaxRate() + "%): &c-$" + MessageUtil.formatPrice(tax)));
            lore.add(MessageUtil.color("&7Seller gets: &e$" + MessageUtil.formatPrice(listing.getPrice() - tax)));
            lore.add(MessageUtil.color("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        inventory.setItem(SLOT_ITEM, display);

        // Confirm button
        inventory.setItem(SLOT_CONFIRM, GuiUtil.makeGlowItem(
                Material.LIME_STAINED_GLASS_PANE,
                "&a✔ Confirm Purchase",
                "&7Click to buy &f" + MessageUtil.getItemName(listing.getItem()),
                "&7for &a$" + MessageUtil.formatPrice(listing.getPrice())
        ));

        // Cancel button
        inventory.setItem(SLOT_CANCEL, GuiUtil.makeItem(
                Material.RED_STAINED_GLASS_PANE,
                "&c✘ Cancel",
                "&7Go back to the Auction House"
        ));
    }

    public void open() {
        GUIManager.setOpenConfirm(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    public void handleClick(int slot) {
        if (slot == SLOT_CONFIRM) {
            player.closeInventory();
            AuctionManager.BuyResult result = plugin.getAuctionManager().buyItem(player, listing.getId());
            switch (result) {
                case SUCCESS -> player.sendMessage(
                        plugin.getConfigManager().getMessage("auction-house.item-purchased")
                                .replace("{item}", MessageUtil.getItemName(listing.getItem()))
                                .replace("{price}", "$" + MessageUtil.formatPrice(listing.getPrice())));
                case NOT_FOUND -> player.sendMessage(
                        plugin.getConfigManager().getMessage("auction-house.no-listings"));
                case OWN_LISTING -> player.sendMessage(
                        plugin.getConfigManager().getMessage("auction-house.cannot-buy-own"));
                case NOT_ENOUGH_MONEY -> player.sendMessage(
                        plugin.getConfigManager().getMessage("auction-house.not-enough-money")
                                .replace("{price}", "$" + MessageUtil.formatPrice(listing.getPrice())));
            }
            // Reopen main AH after purchase
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                parent.refresh();
                GUIManager.setOpenAH(player.getUniqueId(), parent);
                parent.open();
            }, 1L);
        } else if (slot == SLOT_CANCEL) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                parent.refresh();
                GUIManager.setOpenAH(player.getUniqueId(), parent);
                parent.open();
            }, 1L);
        }
    }

    public Inventory getInventory() { return inventory; }
}
