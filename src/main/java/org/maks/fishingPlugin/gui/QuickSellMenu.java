package org.maks.fishingPlugin.gui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import org.maks.fishingPlugin.model.SellSummary;
import org.maks.fishingPlugin.service.QuickSellService;

/**
 * Inventory based quick sell menu allowing selection of fish to sell.
 */
public class QuickSellMenu implements Listener {

  private final QuickSellService quickSellService;
  private final Map<java.util.UUID, Set<String>> selections = new HashMap<>();

  public QuickSellMenu(QuickSellService quickSellService) {
    this.quickSellService = quickSellService;
  }

  private ItemStack entryItem(SellSummary.Entry e, boolean selected) {
    ItemStack item = new ItemStack(selected ? Material.LIME_DYE : Material.GRAY_DYE);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.displayName(Component.text(e.key() + " [" + e.quality() + "]"));
      java.util.List<Component> lore = new java.util.ArrayList<>();
      lore.add(Component.text("Amount: " + e.amount()));
      lore.add(Component.text("Price: " + quickSellService.currencySymbol()
          + String.format("%.2f", e.price())));
      meta.lore(lore);
      item.setItemMeta(meta);
    }
    return item;
  }

  private ItemStack button(Material mat, String name) {
    ItemStack item = new ItemStack(mat);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.displayName(Component.text(name));
      item.setItemMeta(meta);
    }
    return item;
  }

  private Inventory createInventory(Player player) {
    SellSummary summary = quickSellService.summarize(player);
    Set<String> sel = selections.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
    Inventory inv = Bukkit.createInventory(new Holder(summary), 54, "Quick Sell");
    int slot = 0;
    for (SellSummary.Entry e : summary.entries()) {
      String gk = QuickSellService.groupKey(e.key(), e.quality());
      boolean selected = sel.contains(gk);
      ItemStack item = entryItem(e, selected);
      inv.setItem(slot++, item);
    }
    inv.setItem(52, button(Material.GOLD_INGOT, "Sell Selected"));
    inv.setItem(53, button(Material.REDSTONE_BLOCK, "Sell All"));
    return inv;
  }

  /** Open the quick sell menu. */
  public void open(Player player) {
    player.openInventory(createInventory(player));
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof Holder holder)) {
      return;
    }
    event.setCancelled(true);
    Player player = (Player) event.getWhoClicked();
    Set<String> sel = selections.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());

    int slot = event.getRawSlot();
    if (slot == 52) {
      double amount = quickSellService.sellSelected(player, sel);
      player.sendMessage("Sold fish for " + quickSellService.currencySymbol()
          + String.format("%.2f", amount));
      sel.clear();
      open(player);
      return;
    }
    if (slot == 53) {
      double amount = quickSellService.sellAll(player);
      player.sendMessage("Sold fish for " + quickSellService.currencySymbol()
          + String.format("%.2f", amount));
      sel.clear();
      open(player);
      return;
    }
    if (slot >= 0 && slot < holder.summary.entries().size()) {
      SellSummary.Entry e = holder.summary.entries().get(slot);
      String gk = QuickSellService.groupKey(e.key(), e.quality());
      if (sel.contains(gk)) {
        sel.remove(gk);
      } else {
        sel.add(gk);
      }
      open(player);
    }
  }

  private static class Holder implements InventoryHolder {
    final SellSummary summary;
    Holder(SellSummary summary) {
      this.summary = summary;
    }
    @Override
    public Inventory getInventory() {
      return null;
    }
  }
}

