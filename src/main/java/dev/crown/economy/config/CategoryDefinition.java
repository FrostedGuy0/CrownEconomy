package dev.crown.economy.config;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Set;

public record CategoryDefinition(
        String name,
        Material icon,
        boolean matchAll,
        Set<Material> materials,
        Set<String> keywords
) {

    public boolean matches(ItemStack item) {
        if (matchAll) {
            return true;
        }
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        if (materials != null && materials.contains(item.getType())) {
            return true;
        }

        if (keywords != null) {
            String materialName = item.getType().name().toUpperCase(Locale.ROOT);
            String displayName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName().toUpperCase(Locale.ROOT)
                    : "";
            for (String keyword : keywords) {
                String probe = keyword.toUpperCase(Locale.ROOT);
                if (materialName.contains(probe) || displayName.contains(probe)) {
                    return true;
                }
            }
        }
        return false;
    }
}
