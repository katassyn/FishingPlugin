package org.maks.fishingPlugin.service;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles creation and detection of the plugin's fishing rod item.
 */
public class RodService {

  private final NamespacedKey rodKey;

  public RodService(JavaPlugin plugin) {
    this.rodKey = new NamespacedKey(plugin, "fishing-rod");
  }

  private boolean isRod(ItemStack item) {
    if (item == null) return false;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return false;
    return meta.getPersistentDataContainer().has(rodKey, PersistentDataType.BYTE);
  }

  /**
   * Checks if the player already has our fishing rod.
   */
  public boolean hasRod(Player player) {
    for (ItemStack item : player.getInventory().getContents()) {
      if (isRod(item)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gives the basic fishing rod to the player if they don't already possess it.
   */
  public void giveRod(Player player) {
    if (hasRod(player)) {
      return;
    }
    ItemStack rod = new ItemStack(Material.FISHING_ROD);
    ItemMeta meta = rod.getItemMeta();
    if (meta != null) {
      meta.setDisplayName("Fishing Rod");
      meta.getPersistentDataContainer().set(rodKey, PersistentDataType.BYTE, (byte) 1);
      rod.setItemMeta(meta);
    }
    player.getInventory().addItem(rod);
  }
}

