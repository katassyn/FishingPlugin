package org.maks.fishingPlugin.service;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.fishingPlugin.data.BountyRewardRepo;
import org.maks.fishingPlugin.model.BountyReward;
import org.maks.fishingPlugin.util.ItemSerialization;
import org.maks.fishingPlugin.service.TreasureMapService;

/**
 * Manages bounty reward definitions and granting.
 */
public class BountyRewardService {

  private final JavaPlugin plugin;
  private final BountyRewardRepo repo;
  private final Map<TreasureMapService.Lair, String> rewards =
      new EnumMap<>(TreasureMapService.Lair.class);

  public BountyRewardService(JavaPlugin plugin, BountyRewardRepo repo) {
    this.plugin = plugin;
    this.repo = repo;
    try {
      for (BountyReward r : repo.findAll()) {
        rewards.put(r.lair(), r.rewardData());
      }
    } catch (SQLException e) {
      plugin.getLogger().warning("Failed to load bounty rewards: " + e.getMessage());
    }
  }

  /** Get reward items for a lair. */
  public ItemStack[] getItems(TreasureMapService.Lair lair) {
    String data = rewards.getOrDefault(lair, "");
    try {
      return ItemSerialization.fromBase64List(data);
    } catch (Exception e) {
      return new ItemStack[0];
    }
  }

  /** Set reward items for a lair. */
  public void setItems(TreasureMapService.Lair lair, ItemStack[] items) {
    String data = "";
    if (items.length > 0) {
      data = ItemSerialization.toBase64(items);
    }
    rewards.put(lair, data);
    try {
      repo.upsert(new BountyReward(lair, data));
    } catch (SQLException e) {
      plugin.getLogger().warning("Failed to save bounty reward: " + e.getMessage());
    }
  }

  /** Grant rewards for completing a lair. */
  public void give(Player player, TreasureMapService.Lair lair) {
    ItemStack[] items = getItems(lair);
    if (items.length == 0) {
      return;
    }
    Map<Integer, ItemStack> leftover = player.getInventory().addItem(items);
    for (ItemStack it : leftover.values()) {
      player.getWorld().dropItem(player.getLocation(), it);
    }
  }
}
