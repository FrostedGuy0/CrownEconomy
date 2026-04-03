package dev.crown.economy.listeners;

import dev.crown.economy.CrownEconomy;
import dev.crown.economy.gui.AuctionCategoryGUI;
import dev.crown.economy.gui.AuctionHouseGUI;
import dev.crown.economy.gui.ConfirmPurchaseGUI;
import dev.crown.economy.gui.GUIManager;
import dev.crown.economy.gui.MyListingsGUI;
import dev.crown.economy.gui.TransactionsGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class AuctionListener implements Listener {

    private final CrownEconomy plugin;

    public AuctionListener(CrownEconomy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        int slot = event.getRawSlot();
        boolean leftClick = event.isLeftClick();
        boolean rightClick = event.isRightClick();

        AuctionHouseGUI ah = GUIManager.getOpenAH(uuid);
        if (ah != null && event.getView().getTopInventory().equals(ah.getInventory())) {
            event.setCancelled(true);
            if (slot < ah.getInventory().getSize()) {
                ah.handleClick(slot, leftClick);
            }
            return;
        }

        ConfirmPurchaseGUI confirm = GUIManager.getOpenConfirm(uuid);
        if (confirm != null && event.getView().getTopInventory().equals(confirm.getInventory())) {
            event.setCancelled(true);
            if (slot < confirm.getInventory().getSize()) {
                confirm.handleClick(slot);
            }
            return;
        }

        MyListingsGUI myListings = GUIManager.getOpenMyListings(uuid);
        if (myListings != null && event.getView().getTopInventory().equals(myListings.getInventory())) {
            event.setCancelled(true);
            if (slot < myListings.getInventory().getSize()) {
                myListings.handleClick(slot, rightClick);
            }
            return;
        }

        TransactionsGUI transactions = GUIManager.getOpenTransactions(uuid);
        if (transactions != null && event.getView().getTopInventory().equals(transactions.getInventory())) {
            event.setCancelled(true);
            if (slot < transactions.getInventory().getSize()) {
                transactions.handleClick(slot);
            }
            return;
        }

        AuctionCategoryGUI category = GUIManager.getOpenCategory(uuid);
        if (category != null && event.getView().getTopInventory().equals(category.getInventory())) {
            event.setCancelled(true);
            if (slot < category.getInventory().getSize()) {
                category.handleClick(slot);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (GUIManager.getOpenAH(uuid) != null
                || GUIManager.getOpenConfirm(uuid) != null
                || GUIManager.getOpenMyListings(uuid) != null
                || GUIManager.getOpenTransactions(uuid) != null
                || GUIManager.getOpenCategory(uuid) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        boolean playerClosed = event.getReason().name().contains("PLAYER");
        AuctionHouseGUI ah = GUIManager.getOpenAH(uuid);
        if (ah != null && event.getInventory().equals(ah.getInventory())) {
            GUIManager.removeOpenAH(uuid);
            GUIManager.removePendingSearch(uuid);
            return;
        }

        ConfirmPurchaseGUI confirm = GUIManager.getOpenConfirm(uuid);
        if (confirm != null && event.getInventory().equals(confirm.getInventory())) {
            GUIManager.removeOpenConfirm(uuid);
            if (playerClosed) {
                confirm.reopenParent();
            }
            return;
        }

        TransactionsGUI transactions = GUIManager.getOpenTransactions(uuid);
        if (transactions != null && event.getInventory().equals(transactions.getInventory())) {
            GUIManager.removeOpenTransactions(uuid);
            if (playerClosed) {
                transactions.reopenParent();
            }
            return;
        }

        MyListingsGUI myListings = GUIManager.getOpenMyListings(uuid);
        if (myListings != null && event.getInventory().equals(myListings.getInventory())) {
            GUIManager.removeOpenMyListings(uuid);
            if (playerClosed) {
                myListings.reopenParent();
            }
            return;
        }

        AuctionCategoryGUI category = GUIManager.getOpenCategory(uuid);
        if (category != null && event.getInventory().equals(category.getInventory())) {
            GUIManager.removeOpenCategory(uuid);
            if (playerClosed) {
                category.reopenParent();
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        GUIManager.clearAll(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getAuctionManager().deliverPendingReturns(event.getPlayer()));
    }
}
