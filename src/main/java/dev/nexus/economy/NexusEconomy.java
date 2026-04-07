package dev.nexus.economy;

import dev.nexus.economy.auction.AuctionManager;
import dev.nexus.economy.commands.AuctionHouseCommand;
import dev.nexus.economy.commands.NexusEconomyCommand;
import dev.nexus.economy.config.ConfigManager;
import dev.nexus.economy.hooks.VaultHook;
import dev.nexus.economy.hooks.LuckPermsHook;
import dev.nexus.economy.listeners.AuctionListener;
import dev.nexus.economy.listeners.ChatInputListener;
import dev.nexus.economy.utils.MessageUtil;
import org.bukkit.plugin.java.JavaPlugin;

public class NexusEconomy extends JavaPlugin {

    private static NexusEconomy instance;

    private ConfigManager configManager;
    private AuctionManager auctionManager;
    private VaultHook vaultHook;
    private LuckPermsHook luckPermsHook;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize config
        configManager = new ConfigManager(this);
        configManager.loadAll();

        // Setup hooks
        vaultHook = new VaultHook(this);
        if (!vaultHook.setup()) {
            getLogger().severe("Vault not found! Economy features will not work.");
            getLogger().severe("Please install Vault and an economy plugin.");
        }

        luckPermsHook = new LuckPermsHook(this);
        luckPermsHook.setup();

        // Initialize managers
        auctionManager = new AuctionManager(this);
        auctionManager.load();

        // Register commands
        getCommand("ah").setExecutor(new AuctionHouseCommand(this));
        getCommand("nexuseconomy").setExecutor(new NexusEconomyCommand(this));

        // Register listeners
        getServer().getPluginManager().registerEvents(new AuctionListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatInputListener(this), this);

        getLogger().info("╔══════════════════════════════╗");
        getLogger().info("║    NexusEconomy v" + getDescription().getVersion() + "       ║");
        getLogger().info("║  Auction House Module: ACTIVE ║");
        getLogger().info("║  Vault: " + (vaultHook.isEnabled() ? "✔ Connected    " : "✘ Not Found    ") + "       ║");
        getLogger().info("║  LuckPerms: " + (luckPermsHook.isEnabled() ? "✔ Connected" : "✘ Not Found") + "       ║");
        getLogger().info("╚══════════════════════════════╝");
    }

    @Override
    public void onDisable() {
        if (auctionManager != null) {
            auctionManager.saveAll();
        }
        getLogger().info("NexusEconomy disabled. All data saved.");
    }

    public void reload() {
        configManager.loadAll();
        auctionManager.reload();
    }

    public static NexusEconomy getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public AuctionManager getAuctionManager() { return auctionManager; }
    public VaultHook getVaultHook() { return vaultHook; }
    public LuckPermsHook getLuckPermsHook() { return luckPermsHook; }
}
