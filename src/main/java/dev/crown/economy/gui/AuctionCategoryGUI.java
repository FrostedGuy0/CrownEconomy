package dev.crown.economy.gui;

import dev.crown.economy.CrownEconomy;
import dev.crown.economy.config.CategoryDefinition;
import dev.crown.economy.utils.GuiUtil;
import dev.crown.economy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuctionCategoryGUI {

    private final CrownEconomy plugin;
    private final Player player;
    private final AuctionHouseGUI parent;
    private final Inventory inventory;

    public AuctionCategoryGUI(CrownEconomy plugin, Player player, AuctionHouseGUI parent) {
        this.plugin = plugin;
        this.player = player;
        this.parent = parent;
        this.inventory = Bukkit.createInventory(null, plugin.getConfigManager().getCategorySize(),
                plugin.getConfigManager().getCategoryTitle());
        build();
    }

    private void build() {
        inventory.clear();

        List<CategoryDefinition> categories = plugin.getConfigManager().getCategories();
        List<Integer> slots = plugin.getConfigManager().getCategorySlots();
        String selected = parent.getCategoryFilter();

        for (int i = 0; i < categories.size() && i < slots.size(); i++) {
            CategoryDefinition category = categories.get(i);
            boolean isSelected = (selected == null && category.matchAll()) || category.name().equalsIgnoreCase(selected);

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("{category}", category.name());
            placeholders.put("{count}", String.valueOf(plugin.getAuctionHouseManager().getAccessibleListings(player).stream()
                    .filter(listing -> category.matches(listing.getItem()))
                    .count()));
            placeholders.put("{status}", isSelected
                    ? plugin.getConfigManager().getGuiLabel("categories.labels.selected", "&aSelected")
                    : plugin.getConfigManager().getGuiLabel("categories.labels.click-to-filter", "&7Click to filter."));

            List<String> loreTemplate = plugin.getConfigManager().getGui().getStringList("categories.category-item.lore");
            String loreLineOne = loreTemplate.size() > 0 ? loreTemplate.get(0) : "&7Items: &f{count}";
            String loreLineTwo = loreTemplate.size() > 1 ? loreTemplate.get(1) : "{status}";

            ItemStack item = GuiUtil.makeItem(category.icon(),
                    MessageUtil.color(applyPlaceholders(plugin.getConfigManager().getGui().getString("categories.category-item.name", "&f{category}"), placeholders)),
                    MessageUtil.color(applyPlaceholders(loreLineOne, placeholders)),
                    MessageUtil.color(applyPlaceholders(loreLineTwo, placeholders)));

            if (isSelected) {
                item = GuiUtil.makeGlowItem(category.icon(),
                        MessageUtil.color(applyPlaceholders(plugin.getConfigManager().getGui().getString("categories.category-item.name", "&f{category}"), placeholders)),
                        MessageUtil.color(applyPlaceholders(loreLineOne, placeholders)),
                        MessageUtil.color(applyPlaceholders(loreLineTwo, placeholders)));
            }

            inventory.setItem(slots.get(i), item);
        }

        putButton("back", plugin.getAuctionHouseManager().getAuctionScopePlaceholders(player));
        putButton("refresh", plugin.getAuctionHouseManager().getAuctionScopePlaceholders(player));
    }

    private void putButton(String key, Map<String, String> placeholders) {
        int slot = plugin.getConfigManager().getButtonSlot("categories", key);
        if (slot < 0) {
            return;
        }
        inventory.setItem(slot, plugin.getConfigManager().createGuiItem("categories.buttons." + key, placeholders));
    }

    public void open() {
        GUIManager.setOpenCategory(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    public void refresh() {
        build();
    }

    public void handleClick(int slot) {
        List<CategoryDefinition> categories = plugin.getConfigManager().getCategories();
        List<Integer> slots = plugin.getConfigManager().getCategorySlots();

        for (int i = 0; i < categories.size() && i < slots.size(); i++) {
            if (slots.get(i) == slot) {
                CategoryDefinition category = categories.get(i);
                parent.setCategoryFilter(category.matchAll() ? null : category.name());
                reopenParent();
                return;
            }
        }

        if (slot == plugin.getConfigManager().getButtonSlot("categories", "back")) {
            reopenParent();
            return;
        }

        if (slot == plugin.getConfigManager().getButtonSlot("categories", "refresh")) {
            build();
            player.updateInventory();
            return;
        }
    }

    public void reopenParent() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            GUIManager.suppressNextCloseReopen(player.getUniqueId());
            parent.refresh();
            GUIManager.setOpenAH(player.getUniqueId(), parent);
            parent.open();
        });
    }

    public Inventory getInventory() {
        return inventory;
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
