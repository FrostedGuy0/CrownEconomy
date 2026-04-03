package dev.crown.economy.auction;

import dev.crown.economy.CrownEconomy;
import dev.crown.economy.utils.MessageUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class AuctionListing {

    private final UUID id;
    private final UUID sellerUUID;
    private final String sellerName;
    private final ItemStack item;
    private double price;
    private final long listedAt;
    private final long expiresAt;
    private boolean sold;

    public AuctionListing(UUID sellerUUID, String sellerName, ItemStack item, double price, int durationHours) {
        this(UUID.randomUUID(), sellerUUID, sellerName, item, price,
                System.currentTimeMillis(),
                durationHours <= 0 ? -1L : System.currentTimeMillis() + (durationHours * 3_600_000L),
                false);
    }

    public AuctionListing(UUID id, UUID sellerUUID, String sellerName, ItemStack item,
                          double price, long listedAt, long expiresAt, boolean sold) {
        this.id = id;
        this.sellerUUID = sellerUUID;
        this.sellerName = sellerName;
        this.item = item == null ? new ItemStack(Material.AIR) : item.clone();
        this.price = price;
        this.listedAt = listedAt;
        this.expiresAt = expiresAt;
        this.sold = sold;
    }

    public boolean isExpired() {
        return expiresAt > 0L && System.currentTimeMillis() >= expiresAt;
    }

    public long getTimeLeftMillis() {
        if (expiresAt < 0L) {
            return -1L;
        }
        return Math.max(0L, expiresAt - System.currentTimeMillis());
    }

    public String getTimeLeftFormatted() {
        CrownEconomy plugin = CrownEconomy.getInstance();
        if (plugin != null && plugin.getConfigManager() != null) {
            return plugin.getConfigManager().formatTimeLeft(expiresAt);
        }

        if (expiresAt < 0L) {
            return "Never";
        }

        long ms = getTimeLeftMillis();
        long hours = ms / 3_600_000L;
        long minutes = (ms % 3_600_000L) / 60_000L;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    public boolean isPermanent() {
        return expiresAt < 0L;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSellerUUID() {
        return sellerUUID;
    }

    public String getSellerName() {
        return sellerName;
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public double getPrice() {
        return price;
    }

    public long getListedAt() {
        return listedAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isSold() {
        return sold;
    }

    public String getDisplayName() {
        return MessageUtil.getItemName(item);
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setSold(boolean sold) {
        this.sold = sold;
    }
}
