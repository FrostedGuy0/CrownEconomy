package dev.nexus.economy.hooks;

import dev.nexus.economy.NexusEconomy;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {

    private final NexusEconomy plugin;
    private Economy economy;
    private boolean enabled = false;

    public VaultHook(NexusEconomy plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        // Vault plugin itself must be present
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("Vault plugin not found! Install Vault and an economy plugin (e.g. EssentialsX).");
            return false;
        }

        // The economy *service* is registered by the economy plugin (e.g. EssentialsX),
        // not by Vault itself. If it's null here, the economy plugin hasn't loaded yet.
        RegisteredServiceProvider<Economy> rsp = plugin.getServer()
                .getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            plugin.getLogger().warning("Vault found but no economy provider is registered.");
            plugin.getLogger().warning("Make sure an economy plugin (EssentialsX, CMI, GemsEconomy, etc.) is installed.");
            plugin.getLogger().warning("Retrying in 5 seconds...");

            // Retry once after a short delay — some economy plugins register slightly late
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                RegisteredServiceProvider<Economy> retry = plugin.getServer()
                        .getServicesManager().getRegistration(Economy.class);
                if (retry != null) {
                    economy = retry.getProvider();
                    enabled = economy != null;
                    if (enabled) {
                        plugin.getLogger().info("Vault economy connected on retry: " + economy.getName());
                    } else {
                        plugin.getLogger().severe("Vault retry failed. Economy features disabled.");
                    }
                } else {
                    plugin.getLogger().severe("Still no economy provider after retry. Economy features disabled.");
                    plugin.getLogger().severe("Install an economy plugin like EssentialsX or CMI.");
                }
            }, 100L); // 5 seconds (100 ticks)

            return false;
        }

        economy = rsp.getProvider();
        enabled = economy != null;

        if (enabled) {
            plugin.getLogger().info("Vault economy connected: " + economy.getName());
        }

        return enabled;
    }

    public boolean isEnabled() { return enabled; }

    public double getBalance(Player player) {
        if (!enabled) return 0;
        return economy.getBalance(player);
    }

    public boolean has(Player player, double amount) {
        if (!enabled) return false;
        return economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        if (!enabled) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(Player player, double amount) {
        if (!enabled) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (!enabled) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        if (!enabled) return String.valueOf(amount);
        return economy.format(amount);
    }
}
