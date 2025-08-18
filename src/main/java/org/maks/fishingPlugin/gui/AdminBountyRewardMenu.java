package org.maks.fishingPlugin.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import org.maks.fishingPlugin.service.BountyRewardService;
import org.maks.fishingPlugin.service.TreasureMapService;

/** Inventory menu for editing bounty rewards. */
public class AdminBountyRewardMenu implements Listener {

  private final JavaPlugin plugin;
  private final BountyRewardService rewardService;
  private final Map<UUID, TreasureMapService.Lair> editors = new HashMap<>();

  public AdminBountyRewardMenu(JavaPlugin plugin, BountyRewardService rewardService) {
    this.plugin = plugin;
    this.rewardService = rewardService;
  }

  private Inventory createInventory() {
    Map<Integer, TreasureMapService.Lair> map = new HashMap<>();
    Inventory inv = Bukkit.createInventory(new Holder(map), 27, "Bounty Rewards");
    int slot = 0;
    for (TreasureMapService.Lair lair : TreasureMapService.Lair.values()) {
      ItemStack item = new ItemStack(Material.PAPER);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
        String name = lair.name().substring(0, 1) + lair.name().substring(1).toLowerCase();
        meta.displayName(Component.text(name));
        java.util.List<Component> lore = new java.util.ArrayList<>();
        ItemStack[] rewards = rewardService.getItems(lair);
        if (rewards.length > 0) {
          lore.add(Component.text("Rewards: " + rewards.length + " item(s)"));
        } else {
          lore.add(Component.text("No rewards"));
        }
        lore.add(Component.text("Click to edit"));
        meta.lore(lore);
        item.setItemMeta(meta);
      }
      inv.setItem(slot, item);
      map.put(slot, lair);
      slot++;
    }
    return inv;
  }

  /** Open bounty reward menu. */
  public void open(Player player) {
    player.openInventory(createInventory());
  }

  private void openItemEditor(Player player, TreasureMapService.Lair lair) {
    Inventory inv = Bukkit.createInventory(new ItemEditorHolder(), 27, lair.name() + " Reward");
    ItemStack confirm = new ItemStack(Material.LIME_CONCRETE);
    ItemMeta cm = confirm.getItemMeta();
    if (cm != null) {
      cm.displayName(Component.text("Confirm"));
      confirm.setItemMeta(cm);
    }
    inv.setItem(26, confirm);
    ItemStack[] items = rewardService.getItems(lair);
    for (int i = 0; i < Math.min(items.length, 26); i++) {
      inv.setItem(i, items[i]);
    }
    editors.put(player.getUniqueId(), lair);
    player.openInventory(inv);
    player.sendMessage("Place reward items and click the green block to confirm.");
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (event.getInventory().getHolder() instanceof Holder holder) {
      event.setCancelled(true);
      TreasureMapService.Lair lair = holder.map.get(event.getRawSlot());
      if (lair != null) {
        openItemEditor((Player) event.getWhoClicked(), lair);
      }
      return;
    }

    if (event.getView().getTopInventory().getHolder() instanceof ItemEditorHolder) {
      Player player = (Player) event.getWhoClicked();
      if (event.getClickedInventory() != null
          && event.getClickedInventory().getHolder() instanceof ItemEditorHolder) {
        int slot = event.getRawSlot();
        if (slot == 26) {
          event.setCancelled(true);
          TreasureMapService.Lair lair = editors.remove(player.getUniqueId());
          if (lair != null) {
            Inventory top = event.getView().getTopInventory();
            java.util.List<ItemStack> items = new java.util.ArrayList<>();
            for (int i = 0; i < 26; i++) {
              ItemStack it = top.getItem(i);
              if (it != null && !it.getType().isAir()) {
                items.add(it);
              }
            }
            rewardService.setItems(lair, items.toArray(new ItemStack[0]));
            player.sendMessage("Bounty reward saved for " + lair.name().toLowerCase());
          }
          player.closeInventory();
        } else if (slot < 26) {
          event.setCancelled(false);
        }
      } else {
        event.setCancelled(false);
      }
    }
  }

  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    if (event.getInventory().getHolder() instanceof ItemEditorHolder) {
      Player player = (Player) event.getPlayer();
      editors.remove(player.getUniqueId());
      Bukkit.getScheduler().runTask(plugin, () -> open(player));
    }
  }

  private static class Holder implements InventoryHolder {
    final Map<Integer, TreasureMapService.Lair> map;
    Holder(Map<Integer, TreasureMapService.Lair> map) { this.map = map; }
    @Override public Inventory getInventory() { return null; }
  }

  private static class ItemEditorHolder implements InventoryHolder {
    @Override public Inventory getInventory() { return null; }
  }
}
