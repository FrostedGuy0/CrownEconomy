package dev.crown.economy.listeners;

import dev.crown.economy.CrownEconomy;
import dev.crown.economy.gui.AuctionHouseGUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SearchInputManager implements Listener {

    private static final long SEARCH_SIGN_TIMEOUT_TICKS = 20L * 30L;

    private final CrownEconomy plugin;
    private final Map<UUID, SearchSession> sessions = new ConcurrentHashMap<>();

    public SearchInputManager(CrownEconomy plugin) {
        this.plugin = plugin;
    }

    public void openSearchInput(Player player, AuctionHouseGUI gui) {
        if (player == null || gui == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        cleanupSession(uuid);

        Location signLocation = resolveSearchSignLocation(player);
        Block signBlock = signLocation.getBlock();
        BlockState originalState = signBlock.getState();

        player.sendMessage(plugin.getConfigManager().getMessage("auction-house.search-prompt"));

        signBlock.setType(Material.OAK_SIGN, false);
        Sign sign = (Sign) signBlock.getState();
        sign.setWaxed(false);
        sign.update(true, false);

        SearchSession session = new SearchSession(gui, signLocation, originalState);
        sessions.put(uuid, session);

        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            SearchSession current = sessions.get(uuid);
            if (current != session || !player.isOnline()) {
                return;
            }

            BlockState currentState = signLocation.getBlock().getState();
            if (currentState instanceof Sign currentSign) {
                player.openSign(currentSign, Side.FRONT);
            } else {
                cleanupSession(uuid);
            }
        });

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            SearchSession current = sessions.get(uuid);
            if (current == session) {
                cleanupSession(uuid);
            }
        }, SEARCH_SIGN_TIMEOUT_TICKS);
    }

    public void cleanupAllSessions() {
        sessions.keySet().forEach(this::cleanupSession);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        SearchSession session = sessions.get(uuid);
        if (session == null || !sameBlock(session.location(), event.getBlock().getLocation())) {
            return;
        }

        event.setCancelled(true);
        sessions.remove(uuid);

        String input = Arrays.stream(event.getLines())
                .map(line -> line == null ? "" : line.trim())
                .filter(line -> !line.isBlank())
                .collect(Collectors.joining(" "))
                .trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            restoreOriginalBlock(session);
            reopenAuctionWithSearch(player, session.gui(), input);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanupSession(event.getPlayer().getUniqueId());
    }

    private void reopenAuctionWithSearch(Player player, AuctionHouseGUI gui, String input) {
        if (!player.isOnline()) {
            return;
        }

        if (input.isBlank()) {
            gui.setSearchQuery(null);
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.search-cleared"));
        } else if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.search-cancelled"));
        } else {
            gui.setSearchQuery(input);
            player.sendMessage(plugin.getConfigManager().getMessage("auction-house.search-applied")
                    .replace("{query}", input));
        }

        gui.refresh();
        gui.open();
        player.updateInventory();
    }

    private void cleanupSession(UUID uuid) {
        SearchSession session = sessions.remove(uuid);
        if (session != null) {
            restoreOriginalBlock(session);
        }
    }

    private void restoreOriginalBlock(SearchSession session) {
        if (session == null) {
            return;
        }
        session.originalState().update(true, false);
    }

    private Location resolveSearchSignLocation(Player player) {
        World world = player.getWorld();
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        int highestY = world.getHighestBlockYAt(x, z) + 1;
        int preferredY = Math.max(highestY, player.getLocation().getBlockY() + 2);
        int clampedY = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1, preferredY));
        return new Location(world, x, clampedY, z);
    }

    private boolean sameBlock(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            return false;
        }
        return first.getWorld().equals(second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private record SearchSession(AuctionHouseGUI gui, Location location, BlockState originalState) {
    }
}