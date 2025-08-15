package org.maks.fishingPlugin.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.fishingPlugin.model.Category;
import org.maks.fishingPlugin.model.LootEntry;
import org.maks.fishingPlugin.model.MirrorItem;
import org.maks.fishingPlugin.service.MirrorItemService;
import org.maks.fishingPlugin.util.ItemSerialization;
import org.maks.fishingPlugin.util.WeightedPicker;

/**
 * Grants rolled loot to the player.
 */
public class Awarder {

  private final JavaPlugin plugin;
  private final NamespacedKey keyKey;
  private final NamespacedKey weightKey;
  private final NamespacedKey qualityKey;
  private final MirrorItemService mirrorItems;

  public Awarder(JavaPlugin plugin, MirrorItemService mirrorItems) {
    this.plugin = plugin;
    this.mirrorItems = mirrorItems;
    this.keyKey = new NamespacedKey(plugin, "loot-key");
    this.weightKey = new NamespacedKey(plugin, "weight-g");
    this.qualityKey = new NamespacedKey(plugin, "quality");
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
    MirrorItem mirror = mirrorItems.get(loot.key());
    if (mirror != null) {
      item = ItemSerialization.fromBase64(mirror.itemBase64());
    } else if (loot.itemBase64() != null) {
      item = ItemSerialization.fromBase64(loot.itemBase64());
    } else if (loot.category() == Category.FISH) {
      item = new ItemStack(Material.SALMON);
    }

    if (item == null) {
      return new AwardResult(0, null);
    }

    ItemMeta meta = item.getItemMeta();
    double weight = 0;
    if (meta != null) {
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keyKey, PersistentDataType.STRING, loot.key());
      if (loot.category() == Category.FISH) {
        weight = ThreadLocalRandom.current().nextDouble(loot.minWeightG(), loot.maxWeightG());
        pdc.set(weightKey, PersistentDataType.DOUBLE, weight);
        org.maks.fishingPlugin.model.Quality quality = pickQuality(loot);
        pdc.set(qualityKey, PersistentDataType.STRING, quality.name());
        meta.setDisplayName(loot.key());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + String.format(Locale.US, "%.1f kg", weight / 1000.0));
        lore.add(ChatColor.GRAY + "Quality: " + quality.name());
        meta.setLore(lore);
      }
      item.setItemMeta(meta);
    }
    player.getInventory().addItem(item);
    if (loot.category() == Category.RUNE) {
      Bukkit.broadcastMessage("[FISHING POOL] " + player.getName() + " caught a rune!");
    } else if (loot.category() == Category.TREASURE_MAP) {
      Bukkit.broadcastMessage("[FISHING POOL] " + player.getName() + " found a treasure map!");
    } else if (loot.category() == Category.TREASURE) {
      Bukkit.broadcastMessage("[FISHING POOL] " + player.getName() + " found an oceanic treasure!");
    }
    return new AwardResult(weight, item);
  }

  private org.maks.fishingPlugin.model.Quality pickQuality(LootEntry loot) {
    java.util.List<org.maks.fishingPlugin.model.Quality> quals = java.util.List.of(
        org.maks.fishingPlugin.model.Quality.S,
        org.maks.fishingPlugin.model.Quality.A,
        org.maks.fishingPlugin.model.Quality.B,
        org.maks.fishingPlugin.model.Quality.C);
    WeightedPicker<org.maks.fishingPlugin.model.Quality> picker = new WeightedPicker<>(quals, q -> {
      return switch (q) {
        case S -> loot.qualitySWeight();
        case A -> loot.qualityAWeight();
        case B -> loot.qualityBWeight();
        case C -> loot.qualityCWeight();
      };
    });
    return picker.pick(ThreadLocalRandom.current());
  }
}
