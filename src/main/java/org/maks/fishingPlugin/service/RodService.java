package org.maks.fishingPlugin.service;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

/**
 * Handles creation, detection and updating of the custom fishing rod item.
 */
public class RodService {

  private final NamespacedKey rodKey;
  private final NamespacedKey levelKey;
  private final NamespacedKey xpKey;
  private final LevelService levelService;

  public RodService(JavaPlugin plugin, LevelService levelService) {
    this.levelService = levelService;
    this.rodKey = new NamespacedKey(plugin, "fishing-rod");
    this.levelKey = new NamespacedKey(plugin, "rod-level");
    this.xpKey = new NamespacedKey(plugin, "rod-xp");
  }

  private PersistentDataContainer container(ItemMeta meta) {
    return meta.getPersistentDataContainer();
  }

  /** Determine whether an item is our custom fishing rod. */
  public boolean isRod(ItemStack item) {
    if (item == null) return false;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return false;
    return container(meta).has(rodKey, PersistentDataType.BYTE);
  }

  /** Create a new fishing rod item with the given stats. */
  public ItemStack createRod(int level, long xp) {
    ItemStack rod = new ItemStack(Material.FISHING_ROD);
    ItemMeta meta = rod.getItemMeta();
    if (meta != null) {
      container(meta).set(rodKey, PersistentDataType.BYTE, (byte) 1);
      updateMeta(meta, level, xp);
      rod.setItemMeta(meta);
    }
    return rod;
  }

  private String progressLine(long xp, long needed) {
    int bars = 10;
    int filled = (int) Math.round((double) xp / needed * bars);
    if (filled > bars) filled = bars;
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < bars; i++) {
      sb.append(i < filled ? "#" : "-");
    }
    sb.append("] ").append(xp).append("/").append(needed);
    return sb.toString();
  }

  private void applyEnchants(ItemMeta meta, int level) {
    meta.removeEnchant(Enchantment.LUCK);
    meta.removeEnchant(Enchantment.LURE);
    int increments = Math.min(level / 25, 12); // up to level 300
    int luckLevel = (increments + 1) / 2;
    int lureLevel = increments / 2;
    if (luckLevel > 0) {
      meta.addEnchant(Enchantment.LUCK, luckLevel, true);
    }
    if (lureLevel > 0) {
      meta.addEnchant(Enchantment.LURE, lureLevel, true);
    }
  }

  private void updateMeta(ItemMeta meta, int level, long xp) {
    container(meta).set(levelKey, PersistentDataType.INTEGER, level);
    container(meta).set(xpKey, PersistentDataType.LONG, xp);
    meta.displayName(Component.text("Fishing Rod"));
    meta.setUnbreakable(true);
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
    long needed = levelService.neededExp(level);
    meta.lore(List.of(Component.text("Level: " + level),
        Component.text(progressLine(xp, needed))));
    applyEnchants(meta, level);
  }

  /** Update the player's rod in inventory with the given stats. */
  public void updatePlayerRod(Player player, int level, long xp) {
    var inv = player.getInventory();
    for (int i = 0; i < inv.getSize(); i++) {
      ItemStack item = inv.getItem(i);
      if (isRod(item)) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
          updateMeta(meta, level, xp);
          item.setItemMeta(meta);
          inv.setItem(i, item);
        }
        return;
      }
    }
  }

  /** Give a fresh rod to the player. */
  public void giveRod(Player player) {
    player.getInventory().addItem(createRod(1, 0));
  }
}
