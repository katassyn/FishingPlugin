package org.maks.fishingPlugin.service;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.maks.fishingPlugin.model.Category;
import org.maks.fishingPlugin.model.LootEntry;
import org.maks.fishingPlugin.util.ItemSerialization;

/**
 * Grants rolled loot to the player.
 */
public class Awarder {

  private final JavaPlugin plugin;
  private final NamespacedKey keyKey;
  private final NamespacedKey weightKey;

  public Awarder(JavaPlugin plugin) {
    this.plugin = plugin;
    this.keyKey = new NamespacedKey(plugin, "loot-key");
    this.weightKey = new NamespacedKey(plugin, "weight-g");
  }

  /** Result of awarding loot. */
  public record AwardResult(double weightG, ItemStack item) {}

  /**
   * Give the loot to the player.
   * If an ItemStack definition is present it will be used; otherwise fish
   * categories fall back to raw cod items and other categories only send a
   * message.
   */
  public AwardResult give(Player player, LootEntry loot) {
    ItemStack item = null;
    if (loot.itemBase64() != null) {
      item = ItemSerialization.fromBase64(loot.itemBase64());
    } else if (loot.category() == Category.FISH || loot.category() == Category.FISH_PREMIUM) {
      item = new ItemStack(Material.COD);
    }

    if (item == null) {
      player.sendMessage("You obtained: " + loot.key());
      return new AwardResult(0, null);
    }

    ItemMeta meta = item.getItemMeta();
    double weight = 0;
    if (meta != null) {
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keyKey, PersistentDataType.STRING, loot.key());
      if (loot.category() == Category.FISH || loot.category() == Category.FISH_PREMIUM) {
        weight = ThreadLocalRandom.current().nextDouble(loot.minWeightG(), loot.maxWeightG());
        pdc.set(weightKey, PersistentDataType.DOUBLE, weight);
        meta.setDisplayName(loot.key());
      }
      item.setItemMeta(meta);
    }
    player.getInventory().addItem(item);
    if (loot.broadcast()) {
      Bukkit.broadcastMessage(player.getName() + " obtained " + loot.key() + "!");
    }
    return new AwardResult(weight, item);
  }
}
