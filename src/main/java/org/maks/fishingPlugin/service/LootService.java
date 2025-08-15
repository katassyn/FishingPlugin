package org.maks.fishingPlugin.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.maks.fishingPlugin.model.Category;
import org.maks.fishingPlugin.model.LootEntry;
import org.maks.fishingPlugin.model.ScaleConf;
import org.maks.fishingPlugin.util.WeightedPicker;

/**
 * Rolls loot using weighted roulette without soft caps.
 * Loot entries can be registered at runtime; the service applies
 * scaling multipliers per category depending on rod level.
 */
public class LootService {

  private final List<LootEntry> entries = new ArrayList<>();
  private final Map<Category, ScaleConf> scaling;
  private final Map<Category, Double> baseCategoryWeights;
  private final Map<String, LootEntry> byKey = new java.util.HashMap<>();

  public LootService(Map<Category, ScaleConf> scaling, Map<Category, Double> baseCategoryWeights) {
    this.scaling = new EnumMap<>(scaling);
    this.baseCategoryWeights = new EnumMap<>(baseCategoryWeights);
  }

  /** Register a loot entry. */
  public void addEntry(LootEntry entry) {
    entries.add(entry);
    byKey.put(entry.key(), entry);
  }

  /** Replace existing entry by key. */
  public void updateEntry(LootEntry entry) {
    for (int i = 0; i < entries.size(); i++) {
      if (entries.get(i).key().equals(entry.key())) {
        entries.set(i, entry);
        byKey.put(entry.key(), entry);
        return;
      }
    }
    addEntry(entry);
  }

  /** Expose registered entries. */
  public List<LootEntry> getEntries() {
    return List.copyOf(entries);
  }

  public ScaleConf getScale(Category cat) {
    return scaling.get(cat);
  }

  public void setScale(Category cat, ScaleConf conf) {
    scaling.put(cat, conf);
  }

  /**
   * Rolls a random loot entry based on rod level.
   */
  public LootEntry roll(int rodLevel) {
    if (entries.isEmpty()) {
      throw new IllegalStateException("No loot entries registered");
    }

    Map<Category, List<LootEntry>> byCat = new EnumMap<>(Category.class);
    for (LootEntry e : entries) {
      if (rodLevel < e.minRodLevel()) {
        continue;
      }
      byCat.computeIfAbsent(e.category(), k -> new ArrayList<>()).add(e);
    }
    Map<Category, Double> weights = new EnumMap<>(Category.class);
    for (Map.Entry<Category, List<LootEntry>> en : byCat.entrySet()) {
      double w = effectiveCategoryWeight(en.getKey(), rodLevel);
      if (w > 0) {
        weights.put(en.getKey(), w);
      }
    }
    if (weights.isEmpty()) {
      throw new IllegalStateException("No loot entries available for rod level");
    }
    WeightedPicker<Category> catPicker =
        new WeightedPicker<>(new ArrayList<>(weights.keySet()), c -> weights.get(c));
    Category picked = catPicker.pick(ThreadLocalRandom.current());
    WeightedPicker<LootEntry> picker =
        new WeightedPicker<>(byCat.get(picked), LootEntry::baseWeight);
    return picker.pick(ThreadLocalRandom.current());
  }

  /**
   * Rolls loot for the admin rod: only non-fish categories with equal weights
   * and ignoring level requirements.
   */
  public LootEntry rollAdmin() {
    if (entries.isEmpty()) {
      throw new IllegalStateException("No loot entries registered");
    }
    Map<Category, List<LootEntry>> byCat = new EnumMap<>(Category.class);
    for (LootEntry e : entries) {
      if (e.category() == Category.FISH) {
        continue;
      }
      byCat.computeIfAbsent(e.category(), k -> new ArrayList<>()).add(e);
    }
    List<Category> cats = new ArrayList<>(byCat.keySet());
    if (cats.isEmpty()) {
      throw new IllegalStateException("No loot entries available for admin rod");
    }
    Category picked = cats.get(ThreadLocalRandom.current().nextInt(cats.size()));
    WeightedPicker<LootEntry> picker =
        new WeightedPicker<>(byCat.get(picked), LootEntry::baseWeight);
    return picker.pick(ThreadLocalRandom.current());
  }

  public LootEntry getEntry(String key) {
    return byKey.get(key);
  }

  /**
   * Calculates the effective weight for the given loot entry at the specified
   * rod level after applying category scaling and level requirements.
   */
  private double effectiveCategoryWeight(Category cat, int rodLevel) {
    if (cat == Category.RUNE && rodLevel < 25) {
      return 0.0;
    }
    if (cat == Category.TREASURE && rodLevel < 50) {
      return 0.0;
    }
    ScaleConf conf = scaling.get(cat);
    double base = baseCategoryWeights.getOrDefault(cat, 0.0);
    return conf == null ? base : base * conf.mult(rodLevel);
  }

  public double effectiveWeight(LootEntry e, int rodLevel) {
    return rodLevel < e.minRodLevel() ? 0.0 : e.baseWeight();
  }

  public double getBaseCategoryWeight(Category cat) {
    return baseCategoryWeights.getOrDefault(cat, 1.0);
  }

  public void setBaseCategoryWeight(Category cat, double weight) {
    baseCategoryWeights.put(cat, weight);
  }
}
