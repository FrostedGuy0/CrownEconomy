package dev.crown.economy.gui;

import dev.crown.economy.CrownEconomy;
import dev.crown.economy.auction.PlayerTransaction;
import dev.crown.economy.auction.TransactionType;
import dev.crown.economy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionsGUI {

    private final CrownEconomy plugin;
    private final Player player;
    private final MyListingsGUI parent;
    private Inventory inventory;
    private int page = 0;
    private List<PlayerTransaction> transactions = List.of();

    public TransactionsGUI(CrownEconomy plugin, Player player, MyListingsGUI parent) {
        this.plugin = plugin;
        this.player = player;
        this.parent = parent;
        this.inventory = Bukkit.createInventory(null, plugin.getConfigManager().getTransactionsSize(),
                plugin.getConfigManager().getTransactionsTitle());
        refresh();
    }

    public void open() {
        GUIManager.setOpenTransactions(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    public void refresh() {
        if (inventory.getSize() != plugin.getConfigManager().getTransactionsSize()) {
            inventory = Bukkit.createInventory(null, plugin.getConfigManager().getTransactionsSize(),
                    plugin.getConfigManager().getTransactionsTitle());
        }
        inventory.clear();

        transactions = plugin.getAuctionManager().getPlayerTransactions(player.getUniqueId());
        List<Integer> slots = plugin.getConfigManager().getTransactionSlots();
        int slotsPerPage = slots.size();
        int totalPages = Math.max(1, (int) Math.ceil(transactions.size() / (double) slotsPerPage));
        if (page >= totalPages) {
            page = Math.max(0, totalPages - 1);
        }

        int start = page * slotsPerPage;
        for (int i = 0; i < slots.size(); i++) {
            int index = start + i;
            if (index >= transactions.size()) {
                break;
            }
            inventory.setItem(slots.get(i), buildTransactionItem(transactions.get(index)));
        }

        if (transactions.isEmpty()) {
            inventory.setItem(plugin.getConfigManager().getTransactionsEmptyStateSlot(),
                    plugin.getConfigManager().createGuiItem("transactions.empty-state", Map.of()));
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{page}", String.valueOf(page + 1));
        placeholders.put("{pages}", String.valueOf(totalPages));
        placeholders.putAll(buildStatsPlaceholders());
        if (page > 0) {
            putButton("previous", placeholders);
        }
        putButton("refresh", placeholders);
        putButton("stats", placeholders);
        putButton("next", placeholders);
    }

    private ItemStack buildTransactionItem(PlayerTransaction transaction) {
        ItemStack display = transaction.itemCopy();
        if (display.getType().isAir()) {
            display = new ItemStack(Material.PAPER);
        }
        ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{item}", transaction.item().getType().isAir()
                ? plugin.getConfigManager().getGuiLabel("transactions.labels.unknown-item", "Unknown")
                : MessageUtil.getItemName(transaction.item()));
        placeholders.put("{type}", plugin.getConfigManager().getTransactionLabel(transaction.type()));
        placeholders.put("{other}", resolveOtherParty(transaction));
        placeholders.put("{price}", plugin.getConfigManager().formatPrice(transaction.price()));
        placeholders.put("{tax}", plugin.getConfigManager().formatPrice(transaction.tax()));
        placeholders.put("{time}", plugin.getConfigManager().formatTimestamp(transaction.timestamp()));

        String nameTemplate = plugin.getConfigManager().getGui().getString("transactions.transaction-item.name", "&f{item}");
        meta.setDisplayName(MessageUtil.color(applyPlaceholders(nameTemplate, placeholders)));

        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfigManager().getGui().getStringList("transactions.transaction-item.lore")) {
            lore.add(MessageUtil.color(applyPlaceholders(line, placeholders)));
        }
        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private String resolveOtherParty(PlayerTransaction transaction) {
        if (transaction.otherName() != null && !transaction.otherName().isBlank()) {
            return transaction.otherName();
        }

        return switch (transaction.type()) {
            case LISTED, CANCELLED -> plugin.getConfigManager().getGuiLabel("transactions.labels.you", "You");
            case EXPIRED -> plugin.getConfigManager().getGuiLabel("transactions.labels.system", "System");
            case PURCHASED, SOLD -> plugin.getConfigManager().getGuiLabel("transactions.labels.system", "System");
        };
    }

    private void putButton(String key, Map<String, String> placeholders) {
        int slot = plugin.getConfigManager().getButtonSlot("transactions", key);
        if (slot < 0) {
            return;
        }
        ItemStack item = plugin.getConfigManager().createGuiItem("transactions.buttons." + key, placeholders);
        if (item.getType() == Material.BARRIER) {
            return;
        }
        inventory.setItem(slot, item);
    }

    private Map<String, String> buildStatsPlaceholders() {
        double soldTotal = 0.0;
        double boughtTotal = 0.0;
        for (PlayerTransaction transaction : transactions) {
            if (transaction.type() == TransactionType.SOLD) {
                soldTotal += transaction.price();
            } else if (transaction.type() == TransactionType.PURCHASED) {
                boughtTotal += transaction.price();
            }
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{sold-total}", plugin.getConfigManager().formatPrice(soldTotal));
        placeholders.put("{bought-total}", plugin.getConfigManager().formatPrice(boughtTotal));
        return placeholders;
    }

    public void handleClick(int slot) {
        if (slot == plugin.getConfigManager().getButtonSlot("transactions", "previous")) {
            if (page > 0) {
                page--;
                refresh();
                player.updateInventory();
            }
            return;
        }

        if (slot == plugin.getConfigManager().getButtonSlot("transactions", "next")) {
            if ((page + 1) * plugin.getConfigManager().getTransactionSlots().size() < transactions.size()) {
                page++;
                refresh();
                player.updateInventory();
            }
            return;
        }

        if (slot == plugin.getConfigManager().getButtonSlot("transactions", "refresh")) {
            refresh();
            player.updateInventory();
            return;
        }
    }

    public void reopenParent() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            parent.refresh();
            GUIManager.setOpenMyListings(player.getUniqueId(), parent);
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
