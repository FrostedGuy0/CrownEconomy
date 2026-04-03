package dev.crown.economy.utils;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.Locale;

public final class MessageUtil {

    private MessageUtil() {
    }

    public static String color(String input) {
        return input == null ? "" : ChatColor.translateAlternateColorCodes('&', input);
    }

    public static String strip(String input) {
        return input == null ? "" : ChatColor.stripColor(color(input));
    }

    public static String formatPrice(double price, int decimalPlaces) {
        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimalPlaces > 0) {
            pattern.append('.');
            pattern.append("0".repeat(decimalPlaces));
        }
        return new DecimalFormat(pattern.toString()).format(price);
    }

    public static String getItemName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "Unknown";
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return strip(meta.getDisplayName());
        }

        String[] words = item.getType().name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }
}
