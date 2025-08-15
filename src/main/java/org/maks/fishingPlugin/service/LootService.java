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
      double w = effectiveWeight(e, rodLevel);
      if (w > 0) {
        byCat.computeIfAbsent(e.category(), k -> new ArrayList<>()).add(e);
      }
    }
    if (byCat.isEmpty()) {
      throw new IllegalStateException("No loot entries available for rod level");
    }
    List<Category> cats = new ArrayList<>(byCat.keySet());
    WeightedPicker<Category> catPicker =
        new WeightedPicker<>(cats, c -> baseCategoryWeights.getOrDefault(c, 1.0));
    Category picked = catPicker.pick(ThreadLocalRandom.current());
    WeightedPicker<LootEntry> picker =
        new WeightedPicker<>(byCat.get(picked), e -> effectiveWeight(e, rodLevel));
    return picker.pick(ThreadLocalRandom.current());
  }

  public LootEntry getEntry(String key) {
    return byKey.get(key);
  }

  /**
   * Calculates the effective weight for the given loot entry at the specified
   * rod level after applying category scaling and level requirements.
   */
  public double effectiveWeight(LootEntry e, int rodLevel) {
    if (rodLevel < e.minRodLevel()) {
      return 0.0;
    }
    ScaleConf conf = scaling.get(e.category());
    double base = e.baseWeight();
    return conf == null ? base : base * conf.mult(rodLevel);
  }

  public double getBaseCategoryWeight(Category cat) {
    return baseCategoryWeights.getOrDefault(cat, 1.0);
  }

  public void setBaseCategoryWeight(Category cat, double weight) {
    baseCategoryWeights.put(cat, weight);
  }
}
