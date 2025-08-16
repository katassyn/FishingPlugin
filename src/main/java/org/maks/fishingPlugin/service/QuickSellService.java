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
  private final LevelService levelService;
  private final QuestChainService questService;
  private final NamespacedKey keyKey;
  private final NamespacedKey weightKey;
  private final NamespacedKey qualityKey;
  private double globalMultiplier;
  private double tax;
  private String currencySymbol;

  public QuickSellService(JavaPlugin plugin, LootService lootService, Economy economy,
      LevelService levelService, QuestChainService questService, double globalMultiplier,
      double tax, String currencySymbol) {
    this.lootService = lootService;
    this.economy = economy;
    this.levelService = levelService;
    this.questService = questService;
    this.globalMultiplier = globalMultiplier;
    this.tax = tax;
    this.currencySymbol = currencySymbol;
    this.keyKey = new NamespacedKey(plugin, "loot-key");
    this.weightKey = new NamespacedKey(plugin, "weight-g");
    this.qualityKey = new NamespacedKey(plugin, "quality");
  }

  private double computePrice(LootEntry entry, double weight,
      org.maks.fishingPlugin.model.Quality quality, int amount) {
    double base = entry.priceBase() + (weight / 1000.0) * entry.pricePerKg();
    double qualMult = quality.priceMultiplier();
    return base * qualMult * entry.payoutMultiplier() * globalMultiplier * amount;
  }

  private double sellFromInventory(Player player) {
    double total = 0;
    ItemStack[] contents = player.getInventory().getContents();
    for (ItemStack item : contents) {
      if (item == null) continue;
      ItemMeta meta = item.getItemMeta();
      if (meta == null) continue;
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      String key = pdc.get(keyKey, PersistentDataType.STRING);
      Double weight = pdc.get(weightKey, PersistentDataType.DOUBLE);
      String qualStr = pdc.get(qualityKey, PersistentDataType.STRING);
      if (key == null || weight == null || qualStr == null) continue;
      LootEntry entry = lootService.getEntry(key);
      if (entry == null || entry.category() != Category.FISH) {
        continue;
      }
      org.maks.fishingPlugin.model.Quality q;
      try {
        q = org.maks.fishingPlugin.model.Quality.valueOf(qualStr);
      } catch (IllegalArgumentException ex) {
        continue;
      }
      double price = computePrice(entry, weight, q, item.getAmount());
      total += price;
      player.getInventory().removeItem(item);
    }
    if (total > 0) {
      double payout = total * (1.0 - tax);
      economy.depositPlayer(player, payout);
      levelService.addQsEarned(player, Math.round(payout));
      questService.onQuickSell(player, payout);
      return payout;
    }
    return 0;
  }

  /** Sell all fish items in the player's inventory. */
  public double sellAll(Player player) {
    return sellFromInventory(player);
  }

  /** Sell items provided (e.g., from quick sell GUI) without requiring them to be in the player's inventory. */
  public double sellItems(Player player, ItemStack[] items) {
    double total = 0;
    for (ItemStack item : items) {
      if (item == null) continue;
      ItemMeta meta = item.getItemMeta();
      if (meta == null) continue;
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      String key = pdc.get(keyKey, PersistentDataType.STRING);
      Double weight = pdc.get(weightKey, PersistentDataType.DOUBLE);
      String qualStr = pdc.get(qualityKey, PersistentDataType.STRING);
      if (key == null || weight == null || qualStr == null) continue;
      LootEntry entry = lootService.getEntry(key);
      if (entry == null || entry.category() != Category.FISH) {
        continue;
      }
      org.maks.fishingPlugin.model.Quality q;
      try {
        q = org.maks.fishingPlugin.model.Quality.valueOf(qualStr);
      } catch (IllegalArgumentException ex) {
        continue;
      }
      double price = computePrice(entry, weight, q, item.getAmount());
      total += price;
    }
    if (total > 0) {
      double payout = total * (1.0 - tax);
      economy.depositPlayer(player, payout);
      levelService.addQsEarned(player, Math.round(payout));
      questService.onQuickSell(player, payout);
      return payout;
    }
    return 0;
  }

  public String currencySymbol() {
    return currencySymbol;
  }

  /** Get the current global multiplier. */
  public double globalMultiplier() {
    return globalMultiplier;
  }

  /** Get the current quick sell tax. */
  public double tax() {
    return tax;
  }

  /** Update the global payout multiplier. */
  public void setGlobalMultiplier(double globalMultiplier) {
    this.globalMultiplier = globalMultiplier;
  }

  /** Update the quick sell tax. */
  public void setTax(double tax) {
    this.tax = tax;
  }

  /** Update the currency symbol used in menus. */
  public void setCurrencySymbol(String currencySymbol) {
    this.currencySymbol = currencySymbol;
  }
}
