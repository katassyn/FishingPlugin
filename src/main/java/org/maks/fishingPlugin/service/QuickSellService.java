package org.maks.fishingPlugin.service;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;
import org.maks.fishingPlugin.model.Category;
import org.maks.fishingPlugin.model.LootEntry;

/**
 * Handles quick selling of caught fish.
 */
public class QuickSellService {

  private final LootService lootService;
  private final Economy economy;
  private final NamespacedKey keyKey;
  private final NamespacedKey weightKey;
  private final double globalMultiplier;
  private final double tax;
  private final String currencySymbol;

  public QuickSellService(JavaPlugin plugin, LootService lootService, Economy economy,
      double globalMultiplier, double tax, String currencySymbol) {
    this.lootService = lootService;
    this.economy = economy;
    this.globalMultiplier = globalMultiplier;
    this.tax = tax;
    this.currencySymbol = currencySymbol;
    this.keyKey = new NamespacedKey(plugin, "loot-key");
    this.weightKey = new NamespacedKey(plugin, "weight-g");
  }

  /** Sell all fish items in the player's inventory. */
  public double sellAll(Player player) {
    double total = 0;
    ItemStack[] contents = player.getInventory().getContents();
    for (ItemStack item : contents) {
      if (item == null) continue;
      ItemMeta meta = item.getItemMeta();
      if (meta == null) continue;
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      String key = pdc.get(keyKey, PersistentDataType.STRING);
      Double weight = pdc.get(weightKey, PersistentDataType.DOUBLE);
      if (key == null || weight == null) continue;
      LootEntry entry = lootService.getEntry(key);
      if (entry == null) continue;
      if (entry.category() != Category.FISH && entry.category() != Category.FISH_PREMIUM) {
        continue;
      }
      double price = (entry.priceBase() + (weight / 1000.0) * entry.pricePerKg())
          * entry.payoutMultiplier() * globalMultiplier * item.getAmount();
      total += price;
      player.getInventory().removeItem(item);
    }
    if (total > 0) {
      double payout = total * (1.0 - tax);
      economy.depositPlayer(player, payout);
      return payout;
    }
    return 0;
  }

  public String currencySymbol() {
    return currencySymbol;
  }
}
