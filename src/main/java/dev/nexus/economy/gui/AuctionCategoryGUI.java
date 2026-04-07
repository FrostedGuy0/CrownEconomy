package dev.nexus.economy.gui;

import dev.nexus.economy.NexusEconomy;
import dev.nexus.economy.utils.GuiUtil;
import dev.nexus.economy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Map;

/**
 * Category selection GUI (3 rows).
 */
public class AuctionCategoryGUI {

    private static final int SLOT_BACK = 22;

    private final NexusEconomy plugin;
    private final Player player;
    private final AuctionHouseGUI parent;
    private final Inventory inventory;

    private static final List<CategoryEntry> CATEGORIES = List.of(
            new CategoryEntry("All",       Material.COMPASS,           null),
            new CategoryEntry("Weapons",   Material.DIAMOND_SWORD,     "SWORD,BOW,CROSSBOW,TRIDENT,AXE"),
            new CategoryEntry("Armor",     Material.DIAMOND_CHESTPLATE,"HELMET,CHESTPLATE,LEGGINGS,BOOTS"),
            new CategoryEntry("Tools",     Material.IRON_PICKAXE,      "PICKAXE,AXE,SHOVEL,HOE,FISHING_ROD,SHEARS,FLINT_AND_STEEL"),
            new CategoryEntry("Resources", Material.DIAMOND,           "DIAMOND,EMERALD,GOLD_INGOT,IRON_INGOT,NETHERITE,COAL,REDSTONE,LAPIS,AMETHYST"),
            new CategoryEntry("Food",      Material.COOKED_BEEF,       "FOOD"),
            new CategoryEntry("Blocks",    Material.GRASS_BLOCK,       "BLOCK"),
            new CategoryEntry("Misc",      Material.ENDER_PEARL,       "MISC")
    );

    public AuctionCategoryGUI(NexusEconomy plugin, Player player, AuctionHouseGUI parent) {
        this.plugin = plugin;
        this.player = player;
        this.parent = parent;
        this.inventory = Bukkit.createInventory(null, 27,
                MessageUtil.color("&8» &bSelect Category &8«"));
        build();
    }

    private void build() {
        GuiUtil.fillBorder(inventory, Material.BLUE_STAINED_GLASS_PANE);

        // Place categories in center
        int[] catSlots = {10, 11, 12, 13, 14, 15, 16, 22};
        String current = parent.getCategoryFilter();

        for (int i = 0; i < CATEGORIES.size() && i < catSlots.length - 1; i++) {
            CategoryEntry cat = CATEGORIES.get(i);
            boolean selected = cat.name.equals(current) || (current == null && cat.name.equals("All"));
            String prefix = selected ? "&a✔ " : "&7";
            inventory.setItem(catSlots[i], GuiUtil.makeItem(
                    cat.icon,
                    prefix + cat.name,
                    selected ? "&aCurrently selected" : "&7Click to filter by &f" + cat.name
            ));
        }

        inventory.setItem(SLOT_BACK, GuiUtil.makeItem(
                Material.BARRIER, "&cBack", "&7Return to Auction House"));
    }

    public void open() {
        GUIManager.setOpenCategory(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    public void handleClick(int slot) {
        int[] catSlots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < catSlots.length; i++) {
            if (catSlots[i] == slot && i < CATEGORIES.size()) {
                CategoryEntry cat = CATEGORIES.get(i);
                parent.setCategoryFilter(cat.name.equals("All") ? null : cat.name);
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    parent.refresh();
                    GUIManager.setOpenAH(player.getUniqueId(), parent);
                    parent.open();
                }, 1L);
                return;
            }
        }
        if (slot == SLOT_BACK) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                parent.refresh();
                GUIManager.setOpenAH(player.getUniqueId(), parent);
                parent.open();
            }, 1L);
        }
    }

    public Inventory getInventory() { return inventory; }

    private record CategoryEntry(String name, Material icon, String keywords) {}
}
