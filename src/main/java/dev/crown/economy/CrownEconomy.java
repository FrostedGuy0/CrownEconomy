package dev.crown.economy;

import ch.irixiagroup.ilicence.ILicence;
import dev.crown.economy.auction.AuctionHouseManager;
import dev.crown.economy.commands.AuctionHouseCommand;
import dev.crown.economy.commands.CrownEconomyCommand;
import dev.crown.economy.config.ConfigManager;
import dev.crown.economy.hooks.VaultHook;
import dev.crown.economy.listeners.AuctionListener;
import dev.crown.economy.listeners.SearchInputManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class CrownEconomy extends JavaPlugin {

    private static CrownEconomy instance;

    private ConfigManager configManager;
    private ILicence licence;
    private AuctionHouseManager auctionHouseManager;
    private VaultHook vaultHook;
    private SearchInputManager searchInputManager;

    @Override
    public void onEnable() {
        instance = this;

        configManager = new ConfigManager(this);
        configManager.loadAll();

        licence = ILicence.verify(this, "WB7X-DNSA-CC23-EQ48", "crowneconomy");
        if (licence == null) {
            return;
        }

        vaultHook = new VaultHook(this);
        vaultHook.setup();

        auctionHouseManager = new AuctionHouseManager(this);
        auctionHouseManager.load();
        Bukkit.getOnlinePlayers().forEach(player -> auctionHouseManager.deliverPendingReturns(player));
        searchInputManager = new SearchInputManager(this);

        AuctionHouseCommand ahCommand = new AuctionHouseCommand(this);
        CrownEconomyCommand ceCommand = new CrownEconomyCommand(this);
        registerCommand("ah", ahCommand);
        registerCommand("crowneconomy", ceCommand);

        getServer().getPluginManager().registerEvents(new AuctionListener(this), this);
        getServer().getPluginManager().registerEvents(searchInputManager, this);

        getLogger().info("Crown Economy enabled.");
        getLogger().info("Vault economy: " + (vaultHook.isEnabled() ? "connected" : "not available"));
        getLogger().info("World resolver: " + auctionHouseManager.getWorldProviderName());
    }

    @Override
    public void onDisable() {
        if (licence != null) {
            licence.stop();
        }
        if (searchInputManager != null) {
            searchInputManager.cleanupAllSessions();
        }
        if (auctionHouseManager != null) {
            auctionHouseManager.saveAll();
        }
    }

    public void reloadPlugin() {
        configManager.loadAll();
        if (searchInputManager != null) {
            searchInputManager.cleanupAllSessions();
        }
        if (auctionHouseManager != null) {
            auctionHouseManager.reload();
            Bukkit.getOnlinePlayers().forEach(player -> auctionHouseManager.deliverPendingReturns(player));
        }
    }

    private void registerCommand(String name, Object executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Missing command in plugin.yml: " + name);
        command.setExecutor((org.bukkit.command.CommandExecutor) executor);
        if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    public static CrownEconomy getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public AuctionHouseManager getAuctionHouseManager() {
        return auctionHouseManager;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public SearchInputManager getSearchInputManager() {
        return searchInputManager;
    }
}