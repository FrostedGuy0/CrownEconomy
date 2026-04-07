package dev.nexus.economy.commands;

import dev.nexus.economy.NexusEconomy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class NexusEconomyCommand implements CommandExecutor, TabCompleter {

    private final NexusEconomy plugin;

    public NexusEconomyCommand(NexusEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("nexuseconomy.reload")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(plugin.getConfigManager().getMessage("plugin-reloaded"));
            }
            case "version", "info" -> {
                sender.sendMessage(plugin.getConfigManager().getPrefix() +
                        "§7NexusEconomy §bv" + plugin.getDescription().getVersion());
                sender.sendMessage(plugin.getConfigManager().getPrefix() +
                        "§7Vault: " + (plugin.getVaultHook().isEnabled() ? "§a✔ Connected" : "§c✘ Not Found"));
                sender.sendMessage(plugin.getConfigManager().getPrefix() +
                        "§7LuckPerms: " + (plugin.getLuckPermsHook().isEnabled() ? "§a✔ Connected" : "§c✘ Not Found"));
                sender.sendMessage(plugin.getConfigManager().getPrefix() +
                        "§7Active Listings: §f" + plugin.getAuctionManager().getActiveListings().size());
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        String prefix = plugin.getConfigManager().getPrefix();
        sender.sendMessage(prefix + "§8§m                    ");
        sender.sendMessage(prefix + "§bNexusEconomy §7Commands:");
        sender.sendMessage(prefix + "§7/ne reload §8- §fReload configuration");
        sender.sendMessage(prefix + "§7/ne version §8- §fPlugin info & status");
        sender.sendMessage(prefix + "§7/ah §8- §fOpen Auction House");
        sender.sendMessage(prefix + "§7/ah sell <price> §8- §fList held item");
        sender.sendMessage(prefix + "§7/ah search <query> §8- §fSearch listings");
        sender.sendMessage(prefix + "§7/ah cancel <id> §8- §fCancel a listing");
        sender.sendMessage(prefix + "§8§m                    ");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "version").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
