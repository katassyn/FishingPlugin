package org.maks.fishingPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import org.maks.fishingPlugin.service.QuickSellService;

/**
 * Simple chest-style menu where players place fish to sell.
 */
public class QuickSellMenu implements Listener {

  private final QuickSellService quickSellService;

  public QuickSellMenu(QuickSellService quickSellService) {
    this.quickSellService = quickSellService;
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

  private Inventory createInventory() {
    Inventory inv = Bukkit.createInventory(new Holder(), 54, "Quick Sell");
    ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
    ItemMeta fMeta = filler.getItemMeta();
    if (fMeta != null) {
      fMeta.displayName(Component.text(" "));
      filler.setItemMeta(fMeta);
    }
    for (int i = 45; i < 54; i++) {
      inv.setItem(i, filler);
    }
    inv.setItem(49, button(Material.EMERALD, "Sell"));
    return inv;
  }

  /** Open the quick sell menu. */
  public void open(Player player) {
    player.openInventory(createInventory());
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof Holder)) {
      return;
    }
    int slot = event.getRawSlot();
    if (slot == 49) {
      event.setCancelled(true);
      Inventory inv = event.getInventory();
      ItemStack[] items = new ItemStack[45];
      for (int i = 0; i < 45; i++) {
        items[i] = inv.getItem(i);
      }
      Player player = (Player) event.getWhoClicked();
      double amount = quickSellService.sellItems(player, items);
      if (amount > 0) {
        player.sendMessage("Sold fish for " + quickSellService.currencySymbol() + String.format("%.2f", amount));
        for (int i = 0; i < 45; i++) {
          inv.setItem(i, null);
        }
      } else {
        player.sendMessage("No fish sold");
      }
      return;
    }
    if (slot >= 45 && slot < 54) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    if (!(event.getInventory().getHolder() instanceof Holder)) {
      return;
    }
    Inventory inv = event.getInventory();
    Player player = (Player) event.getPlayer();
    for (int i = 0; i < 45; i++) {
      ItemStack item = inv.getItem(i);
      if (item != null && item.getType() != Material.AIR) {
        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack drop : leftover.values()) {
          player.getWorld().dropItem(player.getLocation(), drop);
        }
      }
    }
  }

  private static class Holder implements InventoryHolder {
    @Override
    public Inventory getInventory() {
      return null;
    }
  }
}
