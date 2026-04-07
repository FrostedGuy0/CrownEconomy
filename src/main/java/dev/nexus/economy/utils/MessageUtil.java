package dev.nexus.economy.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.Locale;

public class MessageUtil {

    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.##");

    public static String color(String s) {
        if (s == null) return "";
        return s.replace("&", "§");
    }

    public static String strip(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    public static String getItemName(ItemStack item) {
        if (item == null) return "Unknown";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return strip(meta.getDisplayName());
        }
        String name = item.getType().name().replace("_", " ");
        // Title case
        String[] words = name.toLowerCase(Locale.ROOT).split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    public static String formatPrice(double price) {
        return FORMAT.format(price);
    }
}
