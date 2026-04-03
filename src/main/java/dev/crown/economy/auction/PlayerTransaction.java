package dev.crown.economy.auction;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record PlayerTransaction(
        UUID id,
        TransactionType type,
        String otherName,
        UUID otherUUID,
        ItemStack item,
        double price,
        double tax,
        long timestamp,
        UUID listingId
) {

    public PlayerTransaction {
        id = id == null ? UUID.randomUUID() : id;
        type = type == null ? TransactionType.LISTED : type;
        otherName = otherName == null ? "" : otherName;
        item = item == null ? new ItemStack(Material.AIR) : item.clone();
    }

    public ItemStack itemCopy() {
        return item.clone();
    }
}
