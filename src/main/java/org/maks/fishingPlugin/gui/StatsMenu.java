package org.maks.fishingPlugin.gui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import org.maks.fishingPlugin.model.Category;
import org.maks.fishingPlugin.model.LootEntry;
import org.maks.fishingPlugin.service.LevelService;
import org.maks.fishingPlugin.service.LootService;

/**
 * Inventory menu showing player specific statistics such as rod level,
 * experience and the percentage chance of each loot category.
 */
public class StatsMenu {

  private final LevelService levelService;
  private final LootService lootService;

  public StatsMenu(LevelService levelService, LootService lootService) {
    this.levelService = levelService;
    this.lootService = lootService;
  }

  /** Open the stats inventory for the player. */
  public void open(Player player) {
    int level = levelService.getLevel(player);
    long xp = levelService.getXp(player);
    long needed = levelService.neededExp(level);

    Inventory inv = Bukkit.createInventory(null, 27, "Stats");
    ItemStack info = new ItemStack(Material.FISHING_ROD);
    ItemMeta meta = info.getItemMeta();
    List<Component> lore = new ArrayList<>();
    lore.add(Component.text("Level: " + level));
    lore.add(Component.text("XP: " + xp + "/" + needed));
    if (meta != null) {
      meta.displayName(Component.text("Rod"));
      meta.lore(lore);
      info.setItemMeta(meta);
    }
    inv.setItem(10, info);

    // Compute category percentages based on effective weights
    Map<Category, Double> weights = new EnumMap<>(Category.class);
    double total = 0.0;
    for (LootEntry e : lootService.getEntries()) {
      double w = lootService.effectiveWeight(e, level);
      weights.merge(e.category(), w, Double::sum);
      total += w;
    }
    int slot = 12;
    for (Category cat : Category.values()) {
      double w = weights.getOrDefault(cat, 0.0);
      double pct = total > 0 ? (w / total) * 100.0 : 0.0;
      ItemStack catItem = new ItemStack(Material.PAPER);
      ItemMeta m = catItem.getItemMeta();
      List<Component> l = new ArrayList<>();
      l.add(Component.text(String.format("%.1f%%", pct)));
      if (m != null) {
        m.displayName(Component.text(cat.name()));
        m.lore(l);
        catItem.setItemMeta(m);
      }
      inv.setItem(slot++, catItem);
      if (slot >= inv.getSize()) {
        break;
      }
    }

    player.openInventory(inv);
  }
}

