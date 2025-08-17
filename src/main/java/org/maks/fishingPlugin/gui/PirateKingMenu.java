package org.maks.fishingPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.maks.fishingPlugin.service.BountyService;
import org.maks.fishingPlugin.service.TreasureMapService;
import java.util.List;

/**
 * Menu for identifying treasure maps with the Pirate King.
 */
public class PirateKingMenu implements Listener {

  private final JavaPlugin plugin;
  private final TreasureMapService mapService;
  private final BountyService bountyService;
  private final String btnIdentify;
  private final String btnConfirm;
  private final String btnDiscard;
  private final String btnOccupied;
  private final String msgInsertedIdentified;

  public PirateKingMenu(JavaPlugin plugin, TreasureMapService mapService, BountyService bountyService) {
    this.plugin = plugin;
    this.mapService = mapService;
    this.bountyService = bountyService;
    var sec = plugin.getConfig().getConfigurationSection("treasure_maps.buttons");
    this.btnIdentify = sec != null ? sec.getString("identify", "Identify") : "Identify";
    this.btnConfirm = sec != null ? sec.getString("confirm_bounty", "Confirm Bounty") : "Confirm Bounty";
    this.btnDiscard = sec != null ? sec.getString("discard", "Discard") : "Discard";
    this.btnOccupied = sec != null ? sec.getString("occupied", "Occupied") : "Occupied";
    this.msgInsertedIdentified =
        plugin.getConfig().getString("treasure_maps.messages.inserted_identified", "");
  }

  private void debugItem(ItemStack item, String context) {
    if (item == null) {
      plugin.getLogger().info("[PirateKingMenu] " + context + " item=null");
      return;
    }
    var state = mapService.getState(item);
    var lair = mapService.getLair(item);
    var id = mapService.getId(item);
    ItemMeta meta = item.getItemMeta();
    String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "null";
    List<String> lore = meta != null ? meta.getLore() : null;
    boolean unbreakable = meta != null && meta.isUnbreakable();
    plugin
        .getLogger()
        .info(
            "[PirateKingMenu] "
                + context
                + " type="
                + item.getType()
                + " name="
                + name
                + " lore="
                + lore
                + " unbreakable="
                + unbreakable
                + " state="
                + state
                + " lair="
                + lair
                + " id="
                + id);
  }

  private void debugRejection(ItemStack item, String reason) {
    debugItem(item, "rejected-cursor");
    plugin
        .getLogger()
        .info(
            "[PirateKingMenu] reason="
                + reason
                + " required-name="
                + mapService.debugUnidentifiedName()
                + " required-lore="
                + mapService.debugUnidentifiedLore()
                + " required-nbt=[map_state, map_id]");
  }
  /** Open the Pirate King menu for a player. */
  public void open(Player player) {
    Inventory inv = createInventory();
    player.openInventory(inv);
  }

  private String color(String s) {
    return ChatColor.translateAlternateColorCodes('&', s);
  }

  private ItemStack filler() {
    ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(" ");
      item.setItemMeta(meta);
    }
    return item;
  }

  private ItemStack identifyButton() {
    ItemStack item = new ItemStack(Material.EMERALD);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      String name = btnIdentify.replace("${cost}", mapService.currencySymbol() + mapService.identifyCost());
      meta.setDisplayName(color(name));
      item.setItemMeta(meta);
    }
    return item;
  }

  private ItemStack confirmButton() {
    ItemStack item = new ItemStack(Material.EMERALD);

    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(color(btnConfirm));
      item.setItemMeta(meta);
    }
    return item;
  }

  private ItemStack discardButton() {
    ItemStack item = new ItemStack(Material.BARRIER);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(color(btnDiscard));
      item.setItemMeta(meta);
    }
    return item;
  }

  private ItemStack occupiedButton() {
    ItemStack item = new ItemStack(Material.GRAY_DYE);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(color(btnOccupied));
      item.setItemMeta(meta);
    }
    return item;
  }

  private ItemStack guideItem() {
    ItemStack item = new ItemStack(Material.PAPER);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(color("&eHow Treasure Maps Work"));
      meta.setLore(
          List.of(
              color("&7Place a map in the center slot"),
              color("&7Use left button to identify or confirm"),
              color("&7Use right button to discard")));
      item.setItemMeta(meta);
    }
    return item;
  }

  private Inventory createInventory() {
    Inventory inv = Bukkit.createInventory(new Holder(), 27, "Pirate King");
    ItemStack fill = filler();
    for (int i = 0; i < 27; i++) inv.setItem(i, fill);
    inv.setItem(13, null);
    inv.setItem(26, guideItem());
    return inv;
  }

  private void refresh(Player player, Inventory inv) {
    ItemStack fill = filler();
    inv.setItem(11, fill);
    inv.setItem(15, fill);
    ItemStack map = inv.getItem(13);
    debugItem(map, "refresh-slot13");
    if (map == null || map.getType() == Material.AIR) {
      return;
    }
    // prevent stacking or foreign items slipping in
    if (!mapService.isUnidentified(map)
        && !mapService.isIdentified(map)
        && !mapService.isAsh(map)) {
      inv.setItem(13, null);
      var leftover = player.getInventory().addItem(map);
      for (ItemStack drop : leftover.values()) {
        player.getWorld().dropItem(player.getLocation(), drop);
      }
      return;
    }
    if (map.getAmount() > 1) {
      ItemStack rest = map.clone();
      rest.setAmount(map.getAmount() - 1);
      map.setAmount(1);
      var leftover = player.getInventory().addItem(rest);
      for (ItemStack drop : leftover.values()) {
        player.getWorld().dropItem(player.getLocation(), drop);
      }
    }
    if (mapService.isAsh(map)) {
      inv.setItem(13, null);
      player.sendMessage(bountyService.ashMessage());
      return;
    }
    if (mapService.isUnidentified(map)) {
      inv.setItem(11, identifyButton());
    } else if (mapService.isIdentified(map)) {
      var lair = mapService.getLair(map);
      if (lair != null && bountyService.isOccupied(lair)) {
        inv.setItem(11, occupiedButton());
      } else {
        inv.setItem(11, confirmButton());
      }
      inv.setItem(15, discardButton());
      if (!msgInsertedIdentified.isEmpty()) {
        player.sendMessage(color(msgInsertedIdentified));
      }
    }
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof Holder)) return;
    Inventory inv = event.getInventory();
    Player player = (Player) event.getWhoClicked();
    int slot = event.getRawSlot();
    if (slot == 11) {
      event.setCancelled(true);
      ItemStack map = inv.getItem(13);
      if (map != null && mapService.isUnidentified(map)) {
        mapService.identify(player, map);
        if (map.getType() == Material.AIR || map.getAmount() == 0) {
          inv.setItem(13, null);
        }
      } else if (map != null && mapService.isIdentified(map)) {
        if (bountyService.confirm(player, map)) {
          inv.setItem(13, null);
        }
      }
      Bukkit.getScheduler().runTask(plugin, () -> refresh(player, inv));
      return;
    }

    if (slot == 15) {
      event.setCancelled(true);
      ItemStack map = inv.getItem(13);
      if (map != null && mapService.isIdentified(map)) {
        inv.setItem(13, null);
        bountyService.discard(player, map);
      }
      Bukkit.getScheduler().runTask(plugin, () -> refresh(player, inv));
      return;
    }

    // handle shift-clicking from player inventory into slot 13
    if (slot >= inv.getSize()) {
      if (event.isShiftClick()) {
        ItemStack stack = event.getCurrentItem();
        if (stack == null || stack.getType() == Material.AIR) return;
        if (!mapService.isUnidentified(stack)
            && !mapService.isIdentified(stack)
            && !mapService.isAsh(stack)) {
          event.setCancelled(true);
          return;
        }
        if (mapService.isAsh(stack)) {
          event.setCancelled(true);
          stack.setAmount(stack.getAmount() - 1);
          player.sendMessage(bountyService.ashMessage());
          return;
        }
        if (inv.getItem(13) == null || inv.getItem(13).getType() == Material.AIR) {
          ItemStack one = stack.clone();
          one.setAmount(1);
          inv.setItem(13, one);
          stack.setAmount(stack.getAmount() - 1);
          Bukkit.getScheduler().runTask(plugin, () -> refresh(player, inv));
        }
        event.setCancelled(true);
      }
      return;
    }

    // only slot 13 accepts maps
    if (slot != 13) {
      event.setCancelled(true);
      return;
    }

    ItemStack cursor = event.getCursor();
    ItemStack current = inv.getItem(13);
    if (cursor != null && cursor.getType() != Material.AIR) {
      debugItem(cursor, "cursor-attempt");
    }

    // placing item into slot 13
    if (cursor != null && cursor.getType() != Material.AIR) {
      if (!mapService.isUnidentified(cursor)
          && !mapService.isIdentified(cursor)
          && !mapService.isAsh(cursor)) {
        var state = mapService.getState(cursor);
        String reason = state == null ? "missing map_state" : "state=" + state;
        event.setCancelled(true);
        debugRejection(cursor, reason);
        return;
      }
      if (current != null && current.getType() != Material.AIR) {
        event.setCancelled(true);
        return;
      }
    }

    // handle shift-clicking map out of slot 13
    if (event.isShiftClick() && slot == 13) {
      event.setCancelled(true);
      ItemStack map = inv.getItem(13);
      if (map != null && map.getType() != Material.AIR) {
        inv.setItem(13, null);
        var leftover = player.getInventory().addItem(map);
        for (ItemStack drop : leftover.values()) {
          player.getWorld().dropItem(player.getLocation(), drop);
        }
      }
      Bukkit.getScheduler().runTask(plugin, () -> refresh(player, inv));
      return;
    }
    // picking up existing item is fine; just refresh afterwards
    Bukkit.getScheduler().runTask(plugin, () -> refresh(player, inv));
  }

  @EventHandler
  public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
    if (event.getInventory().getHolder() instanceof Holder) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    if (!(event.getInventory().getHolder() instanceof Holder)) return;
    Inventory inv = event.getInventory();
    ItemStack item = inv.getItem(13);
    if (item != null && item.getType() != Material.AIR) {
      debugItem(item, "close-return");
      Player player = (Player) event.getPlayer();
      var leftover = player.getInventory().addItem(item);
      for (ItemStack drop : leftover.values()) {
        player.getWorld().dropItem(player.getLocation(), drop);
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
