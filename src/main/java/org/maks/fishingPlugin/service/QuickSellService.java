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
  private final NamespacedKey keyKey;
  private final NamespacedKey weightKey;
  private final NamespacedKey qualityKey;
  private double globalMultiplier;
  private double tax;
  private String currencySymbol;
  private double maxItemPrice;

  public QuickSellService(JavaPlugin plugin, LootService lootService, Economy economy,
      LevelService levelService, double globalMultiplier, double tax, String currencySymbol,
      double maxItemPrice) {
    this.lootService = lootService;
    this.economy = economy;
    this.levelService = levelService;
    this.globalMultiplier = globalMultiplier;
    this.tax = tax;
    this.currencySymbol = currencySymbol;
    this.maxItemPrice = maxItemPrice;
    this.keyKey = new NamespacedKey(plugin, "loot-key");
    this.weightKey = new NamespacedKey(plugin, "weight-g");
    this.qualityKey = new NamespacedKey(plugin, "quality");
  }

  public static String groupKey(String key, org.maks.fishingPlugin.model.Quality quality) {
    return key + "|" + quality.name();
  }

  private double computePrice(LootEntry entry, double weight, int amount) {
    double perItem =
        (entry.priceBase() + (weight / 1000.0) * entry.pricePerKg()) * entry.payoutMultiplier()
            * globalMultiplier;
    if (perItem > maxItemPrice) {
      perItem = maxItemPrice;
    }
    return perItem * amount;
  }

  private double sell(Player player, java.util.function.Predicate<String> filter) {
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
      if (entry == null) continue;
      if (entry.category() != Category.FISH && entry.category() != Category.FISH_PREMIUM) {
        continue;
      }
      org.maks.fishingPlugin.model.Quality q;
      try {
        q = org.maks.fishingPlugin.model.Quality.valueOf(qualStr);
      } catch (IllegalArgumentException ex) {
        continue;
      }
      String gk = groupKey(key, q);
      if (!filter.test(gk)) continue;
      double price = computePrice(entry, weight, item.getAmount());
      total += price;
      player.getInventory().removeItem(item);
    }
    if (total > 0) {
      double payout = total * (1.0 - tax);
      economy.depositPlayer(player, payout);
      levelService.addQsEarned(player, Math.round(payout));
      return payout;
    }
    return 0;
  }

  /** Sell all fish items in the player's inventory. */
  public double sellAll(Player player) {
    return sell(player, k -> true);
  }

  /** Sell only items whose group keys are selected. */
  public double sellSelected(Player player, java.util.Set<String> selected) {
    return sell(player, selected::contains);
  }

  public org.maks.fishingPlugin.model.SellSummary summarize(Player player) {
    java.util.Map<String, Mutable> map = new java.util.LinkedHashMap<>();
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
      if (entry == null) continue;
      if (entry.category() != Category.FISH && entry.category() != Category.FISH_PREMIUM) {
        continue;
      }
      org.maks.fishingPlugin.model.Quality q;
      try {
        q = org.maks.fishingPlugin.model.Quality.valueOf(qualStr);
      } catch (IllegalArgumentException ex) {
        continue;
      }
      double price = computePrice(entry, weight, item.getAmount()) * (1.0 - tax);
      String gk = groupKey(key, q);
      Mutable m = map.get(gk);
      if (m == null) {
        m = new Mutable(key, q);
        map.put(gk, m);
      }
      m.amount += item.getAmount();
      m.price += price;
      total += price;
    }
    java.util.List<org.maks.fishingPlugin.model.SellSummary.Entry> entries = new java.util.ArrayList<>();
    for (Mutable m : map.values()) {
      entries.add(new org.maks.fishingPlugin.model.SellSummary.Entry(m.key, m.quality, m.amount, m.price));
    }
    return new org.maks.fishingPlugin.model.SellSummary(entries, total);
  }

  private static class Mutable {
    final String key;
    final org.maks.fishingPlugin.model.Quality quality;
    int amount;
    double price;
    Mutable(String key, org.maks.fishingPlugin.model.Quality quality) {
      this.key = key;
      this.quality = quality;
    }
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
