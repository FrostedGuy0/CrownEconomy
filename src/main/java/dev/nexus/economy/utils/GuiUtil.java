package dev.nexus.economy.utils;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GuiUtil {

    /**
     * Create a simple GUI item with name and lore.
     */
    public static ItemStack makeItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.color(name));
            if (lore.length > 0) {
                meta.setLore(Arrays.stream(lore)
                        .map(MessageUtil::color)
                        .collect(Collectors.toList()));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                    ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack makeItem(Material material, String name, List<String> lore) {
        return makeItem(material, name, lore.toArray(new String[0]));
    }

    /**
     * Create a glowing item (has enchant glow without showing it).
     */
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

    /**
     * Filler glass pane for borders.
     */
    public static ItemStack filler(Material material) {
        return makeItem(material, " ");
    }

    /**
     * Fill border slots of a GUI with filler.
     */
    public static void fillBorder(org.bukkit.inventory.Inventory inv, Material material) {
        ItemStack filler = filler(material);
        int size = inv.getSize();
        int rows = size / 9;
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);
        for (int i = size - 9; i < size; i++) inv.setItem(i, filler);
        for (int row = 1; row < rows - 1; row++) {
            inv.setItem(row * 9, filler);
            inv.setItem(row * 9 + 8, filler);
        }
    }
}
