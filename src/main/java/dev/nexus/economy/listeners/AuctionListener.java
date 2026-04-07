package dev.nexus.economy.listeners;

import dev.nexus.economy.NexusEconomy;
import dev.nexus.economy.gui.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.UUID;

public class AuctionListener implements Listener {

    private final NexusEconomy plugin;

    public AuctionListener(NexusEconomy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        var inv = event.getInventory();
        int slot = event.getRawSlot();
        boolean rightClick = event.isRightClick();
        boolean leftClick = event.isLeftClick();

        // ── Auction House GUI ──────────────────────────────────
        AuctionHouseGUI ah = GUIManager.getOpenAH(uuid);
        if (ah != null && inv.equals(ah.getInventory())) {
            event.setCancelled(true);
            if (slot >= 0 && slot < inv.getSize()) {
                ah.handleClick(slot, leftClick);
            }
            return;
        }

        // ── Confirm Purchase GUI ───────────────────────────────
        ConfirmPurchaseGUI confirm = GUIManager.getOpenConfirm(uuid);
        if (confirm != null && inv.equals(confirm.getInventory())) {
            event.setCancelled(true);
            if (slot >= 0 && slot < inv.getSize()) {
                confirm.handleClick(slot);
            }
            return;
        }

        // ── My Listings GUI ────────────────────────────────────
        MyListingsGUI myListings = GUIManager.getOpenMyListings(uuid);
        if (myListings != null && inv.equals(myListings.getInventory())) {
            event.setCancelled(true);
            if (slot >= 0 && slot < inv.getSize()) {
                myListings.handleClick(slot, rightClick);
            }
            return;
        }

        // ── Category GUI ───────────────────────────────────────
        AuctionCategoryGUI cat = GUIManager.getOpenCategory(uuid);
        if (cat != null && inv.equals(cat.getInventory())) {
            event.setCancelled(true);
            if (slot >= 0 && slot < inv.getSize()) {
                cat.handleClick(slot);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        AuctionHouseGUI ah = GUIManager.getOpenAH(uuid);
        if (ah != null && event.getInventory().equals(ah.getInventory())) {
            GUIManager.removeOpenAH(uuid);
        }

        ConfirmPurchaseGUI confirm = GUIManager.getOpenConfirm(uuid);
        if (confirm != null && event.getInventory().equals(confirm.getInventory())) {
            GUIManager.removeOpenConfirm(uuid);
        }

        MyListingsGUI myListings = GUIManager.getOpenMyListings(uuid);
        if (myListings != null && event.getInventory().equals(myListings.getInventory())) {
            GUIManager.removeOpenMyListings(uuid);
        }

        AuctionCategoryGUI cat = GUIManager.getOpenCategory(uuid);
        if (cat != null && event.getInventory().equals(cat.getInventory())) {
            GUIManager.removeOpenCategory(uuid);
        }
    }
}
