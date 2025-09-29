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
import org.bukkit.ChatColor;

import org.maks.fishingPlugin.service.QuickSellService;
import org.maks.fishingPlugin.service.LootService;
import org.maks.fishingPlugin.model.LootEntry;
import org.maks.fishingPlugin.model.Category;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Simple chest-style menu where players place fish to sell.
 */
public class QuickSellMenu implements Listener {

  private final QuickSellService quickSellService;
  private final LootService lootService;
  private final NamespacedKey keyKey;

  public QuickSellMenu(QuickSellService quickSellService, LootService lootService, JavaPlugin plugin) {
    this.quickSellService = quickSellService;
    this.lootService = lootService;
    this.keyKey = new NamespacedKey(plugin, "loot-key");
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

  /** Check if an item is a fish (can be sold) */
  private boolean isFish(ItemStack item) {
    if (item == null) return false;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return false;
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    String key = pdc.get(keyKey, PersistentDataType.STRING);
    if (key == null) return false;
    LootEntry entry = lootService.getEntry(key);
    return entry != null && entry.category() == Category.FISH;
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
    Player player = (Player) event.getWhoClicked();

    // Handle placing items in the selling area
    if (slot >= 0 && slot < 45) {
      ItemStack cursor = event.getCursor();
      if (cursor != null && cursor.getType() != Material.AIR && !isFish(cursor)) {
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "You can only sell fish here!");
        return;
      }
    }

    if (slot == 49) {
      event.setCancelled(true);
      Inventory inv = event.getInventory();
      ItemStack[] items = new ItemStack[45];
      for (int i = 0; i < 45; i++) {
        items[i] = inv.getItem(i);
      }
      double amount = quickSellService.sellItems(player, items);
      if (amount > 0) {
        player.sendMessage(ChatColor.GREEN + "Sold fish for " + ChatColor.YELLOW
            + quickSellService.currencySymbol() + String.format("%.2f", amount));

        for (int i = 0; i < 45; i++) {
          ItemStack item = inv.getItem(i);
          if (item != null && isFish(item)) {
            inv.setItem(i, null);
          }
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
