package dev.crown.economy.commands;

import dev.crown.economy.CrownEconomy;
import dev.crown.economy.auction.AuctionListing;
import dev.crown.economy.auction.AuctionHouseManager;
import dev.crown.economy.gui.AuctionHouseGUI;
import dev.crown.economy.gui.AuctionCategoryGUI;
import dev.crown.economy.gui.GUIManager;
import dev.crown.economy.gui.MyListingsGUI;
import dev.crown.economy.gui.TransactionsGUI;
import dev.crown.economy.utils.MessageUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AuctionHouseCommand implements CommandExecutor, TabCompleter {

    private final CrownEconomy plugin;

    public AuctionHouseCommand(CrownEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("general.player-only"));
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("help") || args[0].equals("?"))) {
            sendHelp(player);
            return true;
        }

        if (!ensureAuctionAvailable(player)) {
            return true;
        }

        if (args.length == 0) {
            openAH(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell" -> handleSell(player, args);
            case "search" -> handleSearch(player, args);
            case "mylistings", "my" -> handleMyListings(player);
            case "cancel" -> handleCancel(player, args);
            case "refresh" -> refreshOpenGui(player);
            default -> {
                player.sendMessage(plugin.getConfigManager().getMessage("auction-house.unknown-subcommand")
                        .replace("{subcommand}", args[0]));
                sendHelp(player);
            }
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.help.header"));
        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.help.title"));
        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.help.intro"));
        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.help.open"));
        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.help.help"));
        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.help.sell"));
        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.help.search"));
        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.help.mylistings"));
        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.help.cancel"));
        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.help.refresh"));
        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.help.examples"));
        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.help.note"));
        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.help.footer"));
    }

    private void handleSell(Player player, String[] args) {
        if (!player.hasPermission("crowneconomy.ah.sell")) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.no-permission"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.usage.sell"));
            return;
        }

        double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.invalid-price"));
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.no-item-hand"));
            return;
        }

        ItemStack toList = hand.clone();
        AuctionHouseManager.ListResult result = plugin.getAuctionHouseManager().listItem(player, toList, price);
        switch (result) {
            case SUCCESS -> {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                player.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-success")
                        .replace("{item}", MessageUtil.getItemName(toList))
                        .replace("{price}", plugin.getConfigManager().formatPrice(price)));
            }
            case WORLD_DISABLED -> sendWorldDisabled(player);
            case BLACKLISTED -> player.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-failed-blacklist"));
            case GLOBAL_MAX_REACHED -> player.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-failed-global-max")
                    .replace("{max}", String.valueOf(plugin.getConfigManager().getGlobalMaxListings())));
            case MAX_REACHED -> player.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-failed-max")
                    .replace("{max}", String.valueOf(plugin.getConfigManager().getMaxListings(player))));
            case PRICE_TOO_LOW, PRICE_TOO_HIGH -> player.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-failed-price")
                    .replace("{min}", plugin.getConfigManager().formatPrice(plugin.getConfigManager().getMinPrice()))
                    .replace("{max}", plugin.getConfigManager().formatPrice(plugin.getConfigManager().getMaxPrice())));
            case NO_ITEM -> player.sendMessage(plugin.getConfigManager().getMessage("auction-house.no-item-hand"));
        }
    }

    private void handleSearch(Player player, String[] args) {
        if (!player.hasPermission("crowneconomy.ah.open") || !player.hasPermission("crowneconomy.ah.search")) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.no-permission"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.usage.search"));
            return;
        }

        String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        AuctionHouseGUI gui = new AuctionHouseGUI(plugin, player);
        gui.setSearchQuery(query);
        gui.refresh();
        GUIManager.setOpenAH(player.getUniqueId(), gui);
        gui.open();
    }

    private void handleMyListings(Player player) {
        if (!player.hasPermission("crowneconomy.ah.open")) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.no-permission"));
            return;
        }
        AuctionHouseGUI parent = GUIManager.getOpenAH(player.getUniqueId());
        if (parent == null) {
            parent = new AuctionHouseGUI(plugin, player);
            GUIManager.setOpenAH(player.getUniqueId(), parent);
        }
        MyListingsGUI gui = new MyListingsGUI(plugin, player, parent);
        GUIManager.setOpenMyListings(player.getUniqueId(), gui);
        gui.open();
    }

    private void handleCancel(Player player, String[] args) {
        if (!player.hasPermission("crowneconomy.ah.cancel")) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.no-permission"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.usage.cancel"));
            return;
        }

        boolean admin = player.hasPermission("crowneconomy.ah.cancel.others") || player.hasPermission("crowneconomy.admin");
        UUID listingId = resolveListingId(player, args[1], admin);
        if (listingId == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-id-invalid"));
            return;
        }

        AuctionHouseManager.CancelResult result = plugin.getAuctionHouseManager().cancelListing(player, listingId, admin);
        if (result == AuctionHouseManager.CancelResult.SUCCESS) {
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-cancelled"));
        } else if (result == AuctionHouseManager.CancelResult.NOT_OWNER) {
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.not-owner"));
        } else {
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-id-invalid"));
        }
    }

    private void refreshOpenGui(Player player) {
        AuctionCategoryGUI category = GUIManager.getOpenCategory(player.getUniqueId());
        if (category != null) {
            category.refresh();
            player.updateInventory();
            return;
        }

        MyListingsGUI myListings = GUIManager.getOpenMyListings(player.getUniqueId());
        if (myListings != null) {
            myListings.refresh();
            player.updateInventory();
            return;
        }

        TransactionsGUI transactions = GUIManager.getOpenTransactions(player.getUniqueId());
        if (transactions != null) {
            transactions.refresh();
            player.updateInventory();
            return;
        }

        AuctionHouseGUI open = GUIManager.getOpenAH(player.getUniqueId());
        if (open == null) {
            if (!player.hasPermission("crowneconomy.ah.open")) {
                player.sendMessage(plugin.getConfigManager().getMessage("general.no-permission"));
                return;
            }
            openAH(player);
            return;
        }
        open.refresh();
        player.updateInventory();
    }

    private void openAH(Player player) {
        if (!player.hasPermission("crowneconomy.ah.open")) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.no-permission"));
            return;
        }
        if (!ensureAuctionAvailable(player)) {
            return;
        }
        AuctionHouseGUI gui = new AuctionHouseGUI(plugin, player);
        GUIManager.setOpenAH(player.getUniqueId(), gui);
        gui.open();
    }

    private UUID resolveListingId(Player player, String input, boolean admin) {
        List<AuctionListing> listings = admin
                ? plugin.getAuctionHouseManager().getActiveListings()
                : plugin.getAuctionHouseManager().getAccessibleListings(player);
        return listings.stream()
                .filter(listing -> listing.getId().toString().equalsIgnoreCase(input)
                        || listing.getId().toString().startsWith(input))
                .map(AuctionListing::getId)
                .findFirst()
                .orElse(null);
    }

    private boolean ensureAuctionAvailable(Player player) {
        if (!plugin.getConfigManager().isAHEnabled()) {
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.disabled"));
            return false;
        }
        if (!plugin.getAuctionHouseManager().isEnabled(player.getWorld())) {
            sendWorldDisabled(player);
            return false;
        }
        return true;
    }

    private void sendWorldDisabled(Player player) {
        Map<String, String> placeholders = plugin.getAuctionHouseManager().getAuctionScopePlaceholders(player);
        placeholders.put("{world}", player.getWorld().getName());
        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.disabled-in-world", placeholders));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("help", "sell", "search", "mylistings", "cancel", "refresh").stream()
                    .filter(entry -> entry.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return List.of("100", "500", "1000", "5000", "10000");
        }
        return List.of();
    }
}
