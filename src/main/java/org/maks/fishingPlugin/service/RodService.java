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
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.TextComponent;

/**
 * Handles creation, detection and updating of the custom fishing rod item.
 */
public class RodService {

  private final NamespacedKey rodKey;
  private final NamespacedKey levelKey;
  private final NamespacedKey xpKey;
  private final NamespacedKey adminKey;
  private final NamespacedKey ownerKey;
  private final NamespacedKey ownerNameKey;
  private final LevelService levelService;

  public RodService(JavaPlugin plugin, LevelService levelService) {
    this.levelService = levelService;
    this.rodKey = new NamespacedKey(plugin, "fishing-rod");
    this.levelKey = new NamespacedKey(plugin, "rod-level");
    this.xpKey = new NamespacedKey(plugin, "rod-xp");
    this.adminKey = new NamespacedKey(plugin, "admin-rod");
    this.ownerKey = new NamespacedKey(plugin, "rod-owner");
    this.ownerNameKey = new NamespacedKey(plugin, "rod-owner-name");
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

  /** Determine whether an item is the special admin fishing rod. */
  public boolean isAdminRod(ItemStack item) {
    if (!isRod(item)) return false;
    ItemMeta meta = item.getItemMeta();
    return meta != null && container(meta).has(adminKey, PersistentDataType.BYTE);
  }

  /**
   * Create a new fishing rod item with the given stats and optional owner.
   * If {@code owner} is {@code null} the rod is unclaimed and can be traded
   * until a player uses it for the first time.
   */
  public ItemStack createRod(Player owner, int level, long xp) {
    ItemStack rod = new ItemStack(Material.FISHING_ROD);
    ItemMeta meta = rod.getItemMeta();
    if (meta != null) {
      container(meta).set(rodKey, PersistentDataType.BYTE, (byte) 1);
      if (owner != null) {
        container(meta).set(ownerKey, PersistentDataType.STRING,
            owner.getUniqueId().toString());
        container(meta).set(ownerNameKey, PersistentDataType.STRING, owner.getName());
      }
      String ownerName = owner != null ? owner.getName() : "???";
      updateMeta(meta, level, xp, ownerName);
      rod.setItemMeta(meta);
    }
    return rod;
  }

  /** Create an admin rod starting at level 300 that bypasses drop requirements. */
  public ItemStack createAdminRod(Player owner) {
    ItemStack rod = createRod(owner, 300, 0);
    ItemMeta meta = rod.getItemMeta();
    if (meta != null) {
      container(meta).set(adminKey, PersistentDataType.BYTE, (byte) 1);
      meta.displayName(Component.text("Admin Fishing Rod"));
      rod.setItemMeta(meta);
    }
    return rod;
  }

  private Component progressLine(long xp, long needed) {
    int bars = 10;
    int filled = (int) Math.round((double) xp / needed * bars);
    if (filled > bars) filled = bars;

    TextColor start = TextColor.color(0xFFFF55); // yellow
    TextColor end = TextColor.color(0x55FF55);   // green

    TextComponent.Builder builder = Component.text()
        .append(Component.text("[", NamedTextColor.GRAY));

    for (int i = 0; i < bars; i++) {
      if (i < filled) {
        float t = filled <= 1 ? 0 : (float) i / (filled - 1);
        TextColor color = TextColor.lerp(t, start, end);
        builder.append(Component.text("█", color));
      } else {
        builder.append(Component.text("█", NamedTextColor.GRAY));
      }
    }

    builder.append(Component.text("] ", NamedTextColor.GRAY))
        .append(Component.text(String.valueOf(xp), NamedTextColor.YELLOW))
        .append(Component.text("/" + needed, NamedTextColor.GREEN));
    return builder.build();
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

  private void updateMeta(ItemMeta meta, int level, long xp, String ownerName) {
    container(meta).set(levelKey, PersistentDataType.INTEGER, level);
    container(meta).set(xpKey, PersistentDataType.LONG, xp);
    meta.displayName(Component.text("Fishing Rod"));
    meta.setUnbreakable(true);
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
    long needed = levelService.neededExp(level);
    meta.lore(List.of(
        Component.text("Owner: " + ownerName, NamedTextColor.GRAY),
        Component.text("Level: " + level, NamedTextColor.GRAY),
        progressLine(xp, needed)));

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
          String ownerName = container(meta).get(ownerNameKey, PersistentDataType.STRING);
          String ownerUuid = container(meta).get(ownerKey, PersistentDataType.STRING);
          if (ownerName == null || ownerUuid == null) {
            ownerName = player.getName();
            ownerUuid = player.getUniqueId().toString();
            container(meta).set(ownerNameKey, PersistentDataType.STRING, ownerName);
            container(meta).set(ownerKey, PersistentDataType.STRING, ownerUuid);
          }
          updateMeta(meta, level, xp, ownerName);
          item.setItemMeta(meta);
          inv.setItem(i, item);
        }
        return;
      }
    }
  }

  /** Give a fresh, unclaimed rod to the player. */
  public void giveRod(Player player) {
    player.getInventory().addItem(createRod(null, 1, 0));
  }

  /** Give the admin rod to the player. */
  public void giveAdminRod(Player player) {
    player.getInventory().addItem(createAdminRod(player));
  }

  /**
   * Check if the given player is the owner of the rod item.
   * If the rod has no owner yet, it becomes owned by the player.
   */
  public boolean isOwner(ItemStack item, Player player) {
    if (!isRod(item)) return false;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return false;
    PersistentDataContainer pdc = container(meta);
    String owner = pdc.get(ownerKey, PersistentDataType.STRING);
    if (owner == null) {
      pdc.set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
      pdc.set(ownerNameKey, PersistentDataType.STRING, player.getName());
      Integer level = pdc.get(levelKey, PersistentDataType.INTEGER);
      Long xp = pdc.get(xpKey, PersistentDataType.LONG);
      updateMeta(meta, level == null ? 1 : level, xp == null ? 0 : xp, player.getName());
      item.setItemMeta(meta);
      player.getInventory().setItemInMainHand(item);
      return true;
    }
    return owner.equals(player.getUniqueId().toString());
  }
}
