package dev.nexus.economy.hooks;

import dev.nexus.economy.NexusEconomy;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class LuckPermsHook {

    private final NexusEconomy plugin;
    private LuckPerms luckPerms;
    private boolean enabled = false;

    public LuckPermsHook(NexusEconomy plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            return false;
        }
        RegisteredServiceProvider<LuckPerms> provider = plugin.getServer()
                .getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) return false;
        luckPerms = provider.getProvider();
        enabled = luckPerms != null;
        return enabled;
    }

    public boolean isEnabled() { return enabled; }

    /**
     * Returns the player's primary group from LuckPerms.
     * Falls back to "default" if LuckPerms isn't available.
     */
    public String getPrimaryGroup(Player player) {
        if (!enabled) return "default";
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "default";
        return user.getPrimaryGroup();
    }

    /**
     * Resolves the max listing tier for a player:
     * admin > mvp > vip > default
     */
    public String getListingTier(Player player) {
        if (player.hasPermission("nexuseconomy.admin")) return "admin";
        if (player.hasPermission("nexuseconomy.ah.maxlistings.mvp")) return "mvp";
        if (player.hasPermission("nexuseconomy.ah.maxlistings.vip")) return "vip";
        return "default";
    }
}
