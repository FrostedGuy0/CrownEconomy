package dev.crown.economy.listeners;

import dev.crown.economy.CrownEconomy;
import dev.crown.economy.gui.AuctionHouseGUI;
import dev.crown.economy.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

public class ChatInputListener implements Listener {

    private final CrownEconomy plugin;

    public ChatInputListener(CrownEconomy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        AuctionHouseGUI pendingAH = GUIManager.getPendingSearch(uuid);
        if (pendingAH == null) {
            return;
        }

        event.setCancelled(true);
        GUIManager.removePendingSearch(uuid);

        String input = event.getMessage().trim();
        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.search-cancelled"));
            Bukkit.getScheduler().runTask(plugin, () -> {
                pendingAH.refresh();
                GUIManager.setOpenAH(uuid, pendingAH);
                player.updateInventory();
            });
            return;
        }

        pendingAH.setSearchQuery(input);
        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.search-applied")
                .replace("{query}", input));
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingAH.refresh();
            GUIManager.setOpenAH(uuid, pendingAH);
            player.updateInventory();
        });
    }
}
