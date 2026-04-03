package dev.crown.economy.utils;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GuiUtil {

    private GuiUtil() {
    }

    public static ItemStack makeItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.color(name));
            if (lore != null && lore.length > 0) {
                List<String> lines = new ArrayList<>(lore.length);
                for (String line : lore) {
                    lines.add(MessageUtil.color(line));
                }
                meta.setLore(lines);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack makeItem(Material material, String name, List<String> lore) {
        return makeItem(material, name, lore == null ? new String[0] : lore.toArray(new String[0]));
    }

    public static ItemStack makeGlowItem(Material material, String name, String... lore) {
        ItemStack item = makeItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack fromSection(FileConfiguration config, String path, Map<String, String> placeholders) {
        Material material = parseMaterial(config.getString(path + ".material", "BARRIER"), Material.BARRIER);
        int amount = Math.max(1, config.getInt(path + ".amount", 1));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String name = config.getString(path + ".name");
        if (name != null) {
            meta.setDisplayName(applyPlaceholders(name, placeholders));
        }

        List<String> lore = config.getStringList(path + ".lore");
        if (!lore.isEmpty()) {
            List<String> applied = new ArrayList<>(lore.size());
            for (String line : lore) {
                applied.add(applyPlaceholders(line, placeholders));
            }
            meta.setLore(applied);
        }

        if (config.getBoolean(path + ".glow", false)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        if (config.contains(path + ".custom-model-data")) {
            meta.setCustomModelData(config.getInt(path + ".custom-model-data"));
        }

        item.setItemMeta(meta);
        return item;
    }

    public static String applyPlaceholders(String input, Map<String, String> placeholders) {
        String result = input == null ? "" : input;
        if (placeholders == null || placeholders.isEmpty()) {
            return MessageUtil.color(result);
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return MessageUtil.color(result);
    }

    public static Material parseMaterial(String name, Material fallback) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
