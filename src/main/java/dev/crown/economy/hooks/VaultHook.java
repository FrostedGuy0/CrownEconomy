package dev.crown.economy.hooks;

import dev.crown.economy.CrownEconomy;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {

    private final CrownEconomy plugin;
    private Economy economy;
    private boolean enabled;

    public VaultHook(CrownEconomy plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault was not found. Buy and sell actions will be disabled.");
            enabled = false;
            return false;
        }

        RegisteredServiceProvider<Economy> provider = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null || provider.getProvider() == null) {
            plugin.getLogger().warning("Vault is installed, but no economy provider is registered.");
            enabled = false;
            return false;
        }

        economy = provider.getProvider();
        enabled = true;
        plugin.getLogger().info("Vault economy connected: " + economy.getName());
        return true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getBalance(Player player) {
        return enabled ? economy.getBalance(player) : 0.0;
    }

    public boolean has(Player player, double amount) {
        return enabled && economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        return enabled && economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(Player player, double amount) {
        return enabled && economy.depositPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        return enabled && economy.depositPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        return enabled ? economy.format(amount) : String.valueOf(amount);
    }
}
