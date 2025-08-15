package org.maks.fishingPlugin.gui;

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
import org.bukkit.ChatColor;
import org.maks.fishingPlugin.service.TeleportService;

/**
 * Inventory based main menu providing access to the various plugin features.
 */
public class MainMenu implements Listener {

  private final ShopMenu shopMenu;
  private final TeleportService teleportService;
  private final int requiredLevel;

  public MainMenu(ShopMenu shopMenu, TeleportService teleportService, int requiredLevel) {
    this.shopMenu = shopMenu;
    this.teleportService = teleportService;
    this.requiredLevel = requiredLevel;
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

  private ItemStack filler() {
    ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.displayName(Component.text(" "));
      item.setItemMeta(meta);
    }
    return item;
  }

  private Inventory createInventory() {
    Inventory inv = Bukkit.createInventory(new Holder(), 27, "Fishing Menu");
    ItemStack fill = filler();
    for (int i = 0; i < 27; i++) {
      inv.setItem(i, fill);
    }
    inv.setItem(11, button(Material.EMERALD, "Shop"));
    inv.setItem(15, button(Material.ENDER_PEARL, "Teleport to fishing pool"));
    return inv;
  }

  /** Open the main menu if the player meets the level requirement. */
  public void open(Player player) {
    if (player.getLevel() < requiredLevel) {
      player.sendMessage(ChatColor.RED + "You must be at least level " + requiredLevel + "!");
      return;
    }
    player.openInventory(createInventory());
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof Holder)) {
      return;
    }
    event.setCancelled(true);
    Player player = (Player) event.getWhoClicked();
    switch (event.getRawSlot()) {
      case 11 -> shopMenu.open(player);
      case 15 -> {
        if (player.getLevel() < requiredLevel) {
          player.sendMessage(ChatColor.RED + "You must be at least level " + requiredLevel + "!");
        } else {
          teleportService.teleport("fishing_main", player);
        }
      }
      default -> {
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

