package org.maks.fishingPlugin.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.maks.fishingPlugin.model.LootEntry;

/**
 * Event fired when a player receives a fishing reward.
 * Allows external plugins to react, e.g. doubling the reward.
 */
public class FishRewardEvent extends Event {
  private static final HandlerList handlers = new HandlerList();
  private final Player player;
  private final LootEntry loot;
  private final ItemStack item;
  private final double weightG;

  public FishRewardEvent(Player player, LootEntry loot, ItemStack item, double weightG) {
    this.player = player;
    this.loot = loot;
    this.item = item;
    this.weightG = weightG;
  }

  public Player getPlayer() { return player; }
  public LootEntry getLoot() { return loot; }
  public ItemStack getItem() { return item; }
  public double getWeightG() { return weightG; }

  @Override
  public HandlerList getHandlers() { return handlers; }
  public static HandlerList getHandlerList() { return handlers; }
}
