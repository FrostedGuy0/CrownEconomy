package dev.crown.economy.commands;

import dev.crown.economy.CrownEconomy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public class CrownEconomyCommand implements CommandExecutor, TabCompleter {

    private final CrownEconomy plugin;

    public CrownEconomyCommand(CrownEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help", "?" -> sendHelp(sender);
            case "reload", "refresh" -> {
                if (!sender.hasPermission("crowneconomy.reload") && !sender.hasPermission("crowneconomy.refresh")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("general.no-permission"));
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(plugin.getConfigManager().getMessage("general.reloaded"));
            }
            case "version", "info" -> {
                int globalMax = plugin.getConfigManager().getGlobalMaxListings();
                String limit = globalMax > 0 ? " &8/ &f" + globalMax : "";
                sender.sendMessage(plugin.getConfigManager().getMessage("general.info-version")
                        .replace("{version}", plugin.getDescription().getVersion()));
                sender.sendMessage(plugin.getConfigManager().getMessage("general.info-vault")
                        .replace("{status}", plugin.getConfigManager().getMessageRaw(
                                plugin.getVaultHook().isEnabled() ? "general.vault-connected" : "general.vault-unavailable")));
                sender.sendMessage(plugin.getConfigManager().getMessage("general.info-listings")
                        .replace("{count}", String.valueOf(plugin.getAuctionHouseManager().getTotalActiveListingCount()))
                        .replace("{limit}", limit));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getConfigManager().getMessage("general.commands-divider"));
        sender.sendMessage(plugin.getConfigManager().getMessage("general.commands-header"));
        sender.sendMessage(plugin.getConfigManager().getMessage("general.commands.ce-help"));
        sender.sendMessage(plugin.getConfigManager().getMessage("general.commands.ce-reload"));
        sender.sendMessage(plugin.getConfigManager().getMessage("general.commands.ce-refresh"));
        sender.sendMessage(plugin.getConfigManager().getMessage("general.commands.ce-version"));
        sender.sendMessage(plugin.getConfigManager().getMessage("general.commands.ah-open"));
        sender.sendMessage(plugin.getConfigManager().getMessage("general.commands.ah-sell"));
        sender.sendMessage(plugin.getConfigManager().getMessage("general.commands.ah-search"));
        sender.sendMessage(plugin.getConfigManager().getMessage("general.commands.ah-cancel"));
        sender.sendMessage(plugin.getConfigManager().getMessage("general.commands.ah-refresh"));
        sender.sendMessage(plugin.getConfigManager().getMessage("general.commands-divider"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("help", "reload", "refresh", "version", "info").stream()
                    .filter(entry -> entry.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
