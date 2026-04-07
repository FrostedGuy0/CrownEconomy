package dev.nexus.economy.commands;

import dev.nexus.economy.NexusEconomy;
import dev.nexus.economy.auction.AuctionManager;
import dev.nexus.economy.gui.AuctionHouseGUI;
import dev.nexus.economy.gui.GUIManager;
import dev.nexus.economy.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class AuctionHouseCommand implements CommandExecutor, TabCompleter {

    private final NexusEconomy plugin;

    public AuctionHouseCommand(NexusEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-only"));
            return true;
        }

        if (!plugin.getConfigManager().isAHEnabled()) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "§cThe Auction House is currently disabled.");
            return true;
        }

        // /ah — open main GUI
        if (args.length == 0) {
            if (!player.hasPermission("nexuseconomy.ah.open")) {
                player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }
            openAH(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // /ah sell <price>
            case "sell" -> {
                if (!player.hasPermission("nexuseconomy.ah.sell")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "§cUsage: /ah sell <price>");
                    return true;
                }
                double price;
                try {
                    price = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getConfigManager().getMessage("auction-house.invalid-price"));
                    return true;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    player.sendMessage(plugin.getConfigManager().getMessage("auction-house.no-item-hand"));
                    return true;
                }
                // Clone the full stack to list, then remove it from the player's hand
                ItemStack toList = hand.clone();
                AuctionManager.ListResult result = plugin.getAuctionManager().listItem(player, toList, price);
                switch (result) {
                    case SUCCESS -> {
                        // Remove the entire stack from hand
                        player.getInventory().setItemInMainHand(null);
                        String successMsg = plugin.getConfigManager().getMessage("auction-house.listing-success")
                                .replace("{item}", dev.nexus.economy.utils.MessageUtil.getItemName(toList))
                                .replace("{amount}", String.valueOf(toList.getAmount()))
                                .replace("{price}", "$" + MessageUtil.formatPrice(price));
                        player.sendMessage(successMsg);
                    }
                    case BLACKLISTED ->
                        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-failed-blacklist"));
                    case MAX_REACHED -> {
                        String tier = plugin.getLuckPermsHook().getListingTier(player);
                        int max = plugin.getConfigManager().getMaxListings(tier);
                        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-failed-max")
                                .replace("{max}", String.valueOf(max)));
                    }
                    case PRICE_TOO_LOW, PRICE_TOO_HIGH ->
                        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-failed-price")
                                .replace("{min}", "$" + MessageUtil.formatPrice(plugin.getConfigManager().getMinPrice()))
                                .replace("{max}", "$" + MessageUtil.formatPrice(plugin.getConfigManager().getMaxPrice())));
                    case NO_ITEM ->
                        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.no-item-hand"));
                }
            }

            // /ah search <query>
            case "search" -> {
                if (!player.hasPermission("nexuseconomy.ah.search")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "§cUsage: /ah search <query>");
                    return true;
                }
                String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                AuctionHouseGUI gui = new AuctionHouseGUI(plugin, player);
                gui.setSearchQuery(query);
                gui.refresh();
                GUIManager.setOpenAH(player.getUniqueId(), gui);
                gui.open();
            }

            // /ah mylistings
            case "mylistings", "my" -> {
                if (!player.hasPermission("nexuseconomy.ah.open")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                AuctionHouseGUI parentAH = new AuctionHouseGUI(plugin, player);
                GUIManager.setOpenAH(player.getUniqueId(), parentAH);
                dev.nexus.economy.gui.MyListingsGUI myListingsGUI =
                        new dev.nexus.economy.gui.MyListingsGUI(plugin, player, parentAH);
                GUIManager.setOpenMyListings(player.getUniqueId(), myListingsGUI);
                myListingsGUI.open();
            }

            // /ah cancel <id>
            case "cancel" -> {
                if (!player.hasPermission("nexuseconomy.ah.cancel")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "§cUsage: /ah cancel <listing-id>");
                    return true;
                }
                // Try to find listing by partial UUID
                String idStr = args[1];
                UUID listingId = plugin.getAuctionManager().getActiveListings().stream()
                        .filter(l -> l.getId().toString().startsWith(idStr) ||
                                     l.getId().toString().equals(idStr))
                        .map(l -> l.getId())
                        .findFirst().orElse(null);

                if (listingId == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-id-invalid"));
                    return true;
                }
                boolean admin = player.hasPermission("nexuseconomy.ah.cancel.others");
                boolean cancelled = plugin.getAuctionManager().cancelListing(player, listingId, admin);
                if (cancelled) {
                    player.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-cancelled"));
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("auction-house.listing-id-invalid"));
                }
            }

            default -> {
                player.sendMessage(plugin.getConfigManager().getPrefix() +
                        "§cUnknown subcommand. Usage: /ah [sell <price> | search <query> | mylistings | cancel <id>]");
            }
        }

        return true;
    }

    private void openAH(Player player) {
        AuctionHouseGUI gui = new AuctionHouseGUI(plugin, player);
        GUIManager.setOpenAH(player.getUniqueId(), gui);
        gui.open();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("sell", "search", "mylistings", "cancel").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return List.of("100", "500", "1000", "5000", "10000");
        }
        return List.of();
    }
}
