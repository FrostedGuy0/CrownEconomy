package dev.nexus.economy.listeners;

import dev.nexus.economy.NexusEconomy;
import dev.nexus.economy.gui.AuctionHouseGUI;
import dev.nexus.economy.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

public class ChatInputListener implements Listener {

    private final NexusEconomy plugin;

    public ChatInputListener(NexusEconomy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        AuctionHouseGUI pendingAH = GUIManager.getPendingSearch(uuid);
        if (pendingAH == null) return;

        event.setCancelled(true);
        GUIManager.removePendingSearch(uuid);

        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.search-cancelled"));
            Bukkit.getScheduler().runTask(plugin, () -> {
                pendingAH.refresh();
                pendingAH.open();
            });
            return;
        }

        pendingAH.setSearchQuery(input);
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingAH.refresh();
            GUIManager.setOpenAH(uuid, pendingAH);
            pendingAH.open();
        });
    }
}
