package dev.nexus.economy.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class AuctionListing {

    private final UUID id;
    private final UUID sellerUUID;
    private final String sellerName;
    private final ItemStack item;
    private double price;
    private final long listedAt;
    private final long expiresAt;
    private boolean expired;
    private boolean sold;

    public AuctionListing(UUID sellerUUID, String sellerName, ItemStack item,
                          double price, int durationHours) {
        this.id = UUID.randomUUID();
        this.sellerUUID = sellerUUID;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.price = price;
        this.listedAt = System.currentTimeMillis();
        this.expiresAt = listedAt + (durationHours * 3600000L);
        this.expired = false;
        this.sold = false;
    }

    // Constructor for loading from storage
    public AuctionListing(UUID id, UUID sellerUUID, String sellerName, ItemStack item,
                          double price, long listedAt, long expiresAt,
                          boolean expired, boolean sold) {
        this.id = id;
        this.sellerUUID = sellerUUID;
        this.sellerName = sellerName;
        this.item = item;
        this.price = price;
        this.listedAt = listedAt;
        this.expiresAt = expiresAt;
        this.expired = expired;
        this.sold = sold;
    }

    public boolean isExpired() {
        if (expired) return true;
        if (System.currentTimeMillis() > expiresAt) {
            expired = true;
            return true;
        }
        return false;
    }

    public long getTimeLeftMillis() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    public String getTimeLeftFormatted() {
        long ms = getTimeLeftMillis();
        long hours = ms / 3600000;
        long minutes = (ms % 3600000) / 60000;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    // Getters
    public UUID getId()          { return id; }
    public UUID getSellerUUID()  { return sellerUUID; }
    public String getSellerName(){ return sellerName; }
    public ItemStack getItem()   { return item.clone(); }
    public double getPrice()     { return price; }
    public long getListedAt()    { return listedAt; }
    public long getExpiresAt()   { return expiresAt; }
    public boolean isSold()      { return sold; }

    public void setPrice(double price)   { this.price = price; }
    public void setSold(boolean sold)    { this.sold = sold; }
    public void setExpired(boolean exp)  { this.expired = exp; }
}
