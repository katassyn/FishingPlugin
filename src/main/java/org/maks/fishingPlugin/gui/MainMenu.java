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
import org.maks.fishingPlugin.service.TeleportService;

/**
 * Inventory based main menu providing access to the various plugin features.
 */
public class MainMenu implements Listener {

  private final QuickSellMenu quickSellMenu;
  private final ShopMenu shopMenu;
  private final QuestMenu questMenu;
  private final PriceListMenu priceListMenu;
  private final StatsMenu statsMenu;
  private final TeleportService teleportService;
  private final int requiredLevel;

  public MainMenu(QuickSellMenu quickSellMenu, ShopMenu shopMenu, QuestMenu questMenu,
      PriceListMenu priceListMenu, StatsMenu statsMenu, TeleportService teleportService,
      int requiredLevel) {
    this.quickSellMenu = quickSellMenu;
    this.shopMenu = shopMenu;
    this.questMenu = questMenu;
    this.priceListMenu = priceListMenu;
    this.statsMenu = statsMenu;
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

  private Inventory createInventory() {
    Inventory inv = Bukkit.createInventory(new Holder(), 27, "Fishing Menu");
    inv.setItem(10, button(Material.CHEST, "Quick Sell"));
    inv.setItem(11, button(Material.EMERALD, "Shop"));
    inv.setItem(12, button(Material.BOOK, "Quests"));
    inv.setItem(13, button(Material.PAPER, "Price List"));
    inv.setItem(14, button(Material.OAK_SIGN, "Stats"));
    inv.setItem(16, button(Material.ENDER_PEARL, "Teleport"));
    return inv;
  }

  /** Open the main menu. */
  public void open(Player player) {
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
      case 10 -> quickSellMenu.open(player);
      case 11 -> shopMenu.open(player);
      case 12 -> questMenu.open(player);
      case 13 -> priceListMenu.open(player);
      case 14 -> statsMenu.open(player);
      case 16 -> {
        if (player.getLevel() < requiredLevel) {
          player.sendMessage("You need level " + requiredLevel + " to teleport.");
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

