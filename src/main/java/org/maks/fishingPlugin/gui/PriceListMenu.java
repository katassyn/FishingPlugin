package org.maks.fishingPlugin.gui;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import org.maks.fishingPlugin.model.Category;
import org.maks.fishingPlugin.model.LootEntry;
import org.maks.fishingPlugin.service.LootService;
import org.maks.fishingPlugin.service.QuickSellService;
import org.maks.fishingPlugin.util.ItemSerialization;

/**
 * Simple inventory menu displaying the configured prices for fish loot
 * entries.  The menu is informational only and contains no interactive
 * elements.
 */
public class PriceListMenu {

  private final LootService lootService;
  private final QuickSellService quickSellService;

  public PriceListMenu(LootService lootService, QuickSellService quickSellService) {
    this.lootService = lootService;
    this.quickSellService = quickSellService;
  }

  /** Open the price list inventory for the player. */
  public void open(Player player) {
    List<LootEntry> entries = lootService.getEntries();
    int size = ((entries.size() / 9) + 1) * 9;
    if (size > 54) {
      size = 54; // limit to one double chest
    }
    Inventory inv = Bukkit.createInventory(null, size, "Price List");

    String symbol = quickSellService.currencySymbol();
    for (LootEntry entry : entries) {
      if (entry.category() != Category.FISH && entry.category() != Category.FISH_PREMIUM) {
        continue; // only list fish
      }
      ItemStack item;
      try {
        item = ItemSerialization.fromBase64(entry.itemBase64());
      } catch (Exception ex) {
        item = new ItemStack(org.bukkit.Material.PAPER);
      }
      ItemMeta meta = item.getItemMeta();
      List<Component> lore = new ArrayList<>();
      lore.add(Component.text("Base: " + symbol + String.format("%.2f", entry.priceBase())));
      lore.add(Component.text("Per Kg: " + symbol + String.format("%.2f", entry.pricePerKg())));
      lore.add(Component.text("Weight: " + String.format("%.0f-%.0f g", entry.minWeightG(), entry.maxWeightG())));
      if (meta != null) {
        meta.displayName(Component.text(entry.key()));
        meta.lore(lore);
        item.setItemMeta(meta);
      }
      inv.addItem(item);
      if (inv.firstEmpty() == -1) {
        break; // inventory full
      }
    }

    player.openInventory(inv);
  }
}

